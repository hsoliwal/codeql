// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A reviewed, strictly additive set of UTF-8 files; hashes describe bytes, not semantics. */
public final class Snapshot {
    public static final String RESOURCE_ROOT = "/com/synexia/m3/codeql/recipe/snapshot/";
    static final String HEADER = "path\tsha256\tbytes\tmode\n";
    static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    /** One immutable text postimage. Paths are repository-relative and slash-separated. */
    public record Entry(String path, String sha256, long bytes, boolean executable, String text) {
        public Entry {
            requireOwnedPath(path);
            Objects.requireNonNull(text, "text");
            if (text.startsWith("\ufeff") || text.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Snapshot text must be UTF-8 without a BOM or NUL");
            }
            requireSha(sha256);
            byte[] encoded = encode(text);
            if (bytes != encoded.length || bytes > MAX_FILE_BYTES || !hash(encoded).equals(sha256)) {
                throw new IllegalArgumentException("Snapshot postimage digest or length differs: " + path);
            }
        }

        public String mode() {
            return executable ? "100755" : "100644";
        }
    }

    @FunctionalInterface
    interface ResourceReader {
        byte[] read(String name) throws IOException;
    }

    private final List<Entry> entries;
    private final Map<String, Entry> byPath;
    private final String manifest;

    /** Entries must already be sorted by path, with no duplicates. */
    public Snapshot(List<Entry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Snapshot must contain at least one file");
        }
        List<Entry> ordered = List.copyOf(entries);
        Map<String, Entry> indexed = new LinkedHashMap<>();
        String previous = "";
        StringBuilder contents = new StringBuilder(HEADER);
        for (Entry entry : ordered) {
            if (previous.compareTo(entry.path()) >= 0) {
                throw new IllegalArgumentException("Unsorted or duplicate snapshot path: " + entry.path());
            }
            if (!previous.isEmpty() && entry.path().startsWith(previous + "/")) {
                throw new IllegalArgumentException("File and directory destinations overlap: " + previous);
            }
            previous = entry.path();
            indexed.put(entry.path(), entry);
            contents.append(entry.path()).append('\t').append(entry.sha256()).append('\t')
                    .append(entry.bytes()).append('\t').append(entry.mode()).append('\n');
        }
        if (encode(contents.toString()).length > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("Snapshot manifest exceeds size limit");
        }
        this.entries = ordered;
        this.byPath = Map.copyOf(indexed);
        this.manifest = contents.toString();
    }

    public List<Entry> entries() {
        return entries;
    }

    public Entry entry(String path) {
        return byPath.get(path);
    }

    public String manifest() {
        return manifest;
    }

    public String manifestSha256() {
        return hash(encode(manifest));
    }

    public static Snapshot bundled() {
        try {
            return load(name -> {
                try (InputStream stream = Snapshot.class.getResourceAsStream(RESOURCE_ROOT + name)) {
                    if (stream == null) {
                        throw new IOException("Missing bundled snapshot resource: " + name);
                    }
                    return boundedRead(stream, name);
                }
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load reviewed M3 CodeQL snapshot", failure);
        }
    }

    public static Snapshot read(Path directory) throws IOException {
        Path root = checkedRoot(directory);
        return load(name -> Files.readAllBytes(checkedExistingFile(root, name)));
    }

    static Snapshot load(ResourceReader reader) throws IOException {
        byte[] manifestBytes = reader.read("manifest.tsv");
        if (manifestBytes.length > MAX_MANIFEST_BYTES) {
            throw new IOException("Snapshot manifest exceeds size limit");
        }
        String pinned = decode(reader.read("manifest.sha256")).strip();
        requireSha(pinned);
        if (!hash(manifestBytes).equals(pinned)) {
            throw new IOException("Snapshot manifest SHA-256 mismatch");
        }
        String text = decode(manifestBytes);
        if (!text.startsWith(HEADER) || !text.endsWith("\n")) {
            throw new IOException("Invalid snapshot manifest header or final newline");
        }
        List<Entry> entries = new ArrayList<>();
        String[] lines = text.substring(HEADER.length()).split("\n", -1);
        for (int i = 0; i < lines.length - 1; i++) {
            String[] cells = lines[i].split("\t", -1);
            if (cells.length != 4 || !(cells[3].equals("100644") || cells[3].equals("100755"))) {
                throw new IOException("Invalid snapshot manifest row " + (i + 2));
            }
            requireOwnedPath(cells[0]);
            requireSha(cells[1]);
            long size;
            try {
                size = Long.parseLong(cells[2]);
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid snapshot byte count", invalid);
            }
            if (size < 0 || size > MAX_FILE_BYTES) {
                throw new IOException("Snapshot file exceeds size limit: " + cells[0]);
            }
            byte[] payload = reader.read("objects/" + cells[1]);
            if (payload.length != size || !hash(payload).equals(cells[1])) {
                throw new IOException("Snapshot resource SHA-256 mismatch: " + cells[0]);
            }
            entries.add(new Entry(cells[0], cells[1], size, cells[3].equals("100755"), decode(payload)));
        }
        Snapshot result = new Snapshot(entries);
        if (!result.manifest().equals(text)) {
            throw new IOException("Snapshot manifest is not in canonical form");
        }
        return result;
    }

    static byte[] boundedRead(InputStream stream, String name) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Snapshot resource exceeds size limit: " + name);
        }
        return bytes;
    }

    static Path checkedRoot(Path root) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        requireNoSymlinks(absolute);
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected an existing directory: " + absolute);
        }
        return absolute.toRealPath();
    }

    static Path checkedExistingFile(Path root, String relative) throws IOException {
        Path candidate = checkedPath(root, relative);
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a regular file: " + relative);
        }
        if (Files.size(candidate) > MAX_FILE_BYTES) {
            throw new IOException("Snapshot resource exceeds size limit: " + relative);
        }
        return candidate;
    }

    static Path checkedPath(Path root, String relative) throws IOException {
        requireRelativePath(relative);
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            throw new IOException("Path escapes root: " + relative);
        }
        requireNoSymlinks(candidate);
        return candidate;
    }

    static void requireNoSymlinks(Path absolute) throws IOException {
        Path current = absolute.getRoot();
        for (Path component : absolute) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are outside the snapshot contract: " + current);
            }
        }
    }

    static void requireOwnedPath(String path) {
        requireRelativePath(path);
        if (!(path.startsWith("m3/") && !path.startsWith("m3/recipes/")
                && !path.equals("m3/recipes"))
                && !path.equals(".github/workflows/m3-codeql.yml")) {
            throw new IllegalArgumentException("Destination is outside the additive M3 namespace: " + path);
        }
        for (String component : path.split("/")) {
            if (component.equals(".git") || component.equals("target") || component.endsWith(".testproj")
                    || component.equals(".cache") || component.equals(".codeql")) {
                throw new IllegalArgumentException("Generated/cache path is not a snapshot source: " + path);
            }
        }
    }

    static void requireRelativePath(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.endsWith("/")
                || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.contains("//")) {
            throw new IllegalArgumentException("Invalid relative snapshot path: " + path);
        }
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) {
                throw new IllegalArgumentException("Control character in snapshot path");
            }
        }
        for (String component : path.split("/")) {
            if (component.equals(".") || component.equals("..")) {
                throw new IllegalArgumentException("Non-canonical snapshot path: " + path);
            }
        }
        if (Path.of(path).isAbsolute()) {
            throw new IllegalArgumentException("Absolute snapshot path: " + path);
        }
    }

    private static void requireSha(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 digest");
        }
    }

    static String decode(byte[] value) throws CharacterCodingException {
        String decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value)).toString();
        if (decoded.startsWith("\ufeff") || decoded.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Snapshot text must be UTF-8 without a BOM or NUL");
        }
        return decoded;
    }

    static byte[] encode(String text) {
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(text));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Malformed UTF-16 text", invalid);
        }
    }

    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
