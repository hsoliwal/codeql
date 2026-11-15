package com.synexia.m3.codeql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32C;

/** A complete regular-file manifest; no source, descriptor, hidden file, or language is silently excluded. */
public final class SourceSnapshot {
    public record FileRecord(String path, long bytes, long modifiedMillis, String fastHash, String sha256) {}

    private final Path root;
    private final Map<String, FileRecord> files;
    private final String contentHash;

    private SourceSnapshot(Path root, List<FileRecord> records) {
        this.root = root;
        Map<String, FileRecord> ordered = new LinkedHashMap<>();
        MessageDigest digest = Hashes.sha256();
        Hashes.field(digest, "m3-source-manifest-v1");
        for (FileRecord file : records) {
            ordered.put(file.path(), file);
            Hashes.field(digest, file.path());
            Hashes.field(digest, file.sha256());
        }
        this.files = Collections.unmodifiableMap(ordered);
        this.contentHash = Hashes.hex(digest.digest());
    }

    public static SourceSnapshot capture(Path suppliedRoot, int workers) throws IOException {
        return capture(suppliedRoot, workers, false);
    }

    /** CodeQL compilation caches are generated outputs, not source inputs to a query pack. */
    static SourceSnapshot captureQueryInputs(Path suppliedRoot, int workers) throws IOException {
        return capture(suppliedRoot, workers, true);
    }

    private static SourceSnapshot capture(Path suppliedRoot, int workers, boolean excludeQueryCache) throws IOException {
        if (workers < 1 || workers > 64) {
            throw new IllegalArgumentException("Workers must be between 1 and 64");
        }
        Path root = PathGuard.directory(suppliedRoot);
        List<Path> paths = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                return excludeQueryCache && !directory.equals(root) && directory.getFileName().toString().equals(".cache")
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                    throw new IOException("Only regular source files are supported: " + file);
                }
                paths.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        paths.sort((a, b) -> relative(root, a).compareTo(relative(root, b)));
        List<FileRecord> records = new ArrayList<>(paths.size());
        ThreadLocal<byte[]> buffers = ThreadLocal.withInitial(() -> new byte[64 * 1024]);
        // Submission is batched so the executor never owns an unbounded work queue.
        try (var executor = Executors.newFixedThreadPool(workers)) {
            for (int offset = 0; offset < paths.size(); offset += workers) {
                List<Future<FileRecord>> batch = new ArrayList<>(workers);
                for (int i = offset; i < Math.min(offset + workers, paths.size()); i++) {
                    Path file = paths.get(i);
                    batch.add(executor.submit(() -> hash(root, file, buffers.get())));
                }
                for (Future<FileRecord> future : batch) {
                    try {
                        records.add(future.get());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Source inventory interrupted", interrupted);
                    } catch (ExecutionException failure) {
                        if (failure.getCause() instanceof IOException io) {
                            throw io;
                        }
                        throw new IOException("Source inventory failed", failure.getCause());
                    }
                }
            }
        }
        return new SourceSnapshot(root, records);
    }

    private static FileRecord hash(Path root, Path file, byte[] buffer) throws IOException {
        PathGuard.noSymlinkComponents(file);
        BasicFileAttributes before = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) {
            throw new IOException("Source file changed type during inventory: " + file);
        }
        MessageDigest digest = Hashes.sha256();
        CRC32C fast = new CRC32C();
        long bytes = 0;
        try (InputStream stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            int count;
            while ((count = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                fast.update(buffer, 0, count);
                bytes += count;
            }
        }
        BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || bytes != before.size() || after.size() != before.size()
                || !after.lastModifiedTime().equals(before.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Source changed while hashing: " + file);
        }
        return new FileRecord(relative(root, file), bytes, after.lastModifiedTime().toMillis(),
                java.util.HexFormat.of().toHexDigits((int) fast.getValue()), Hashes.hex(digest.digest()));
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    public Path root() { return root; }
    public Map<String, FileRecord> files() { return files; }
    public String contentHash() { return contentHash; }

    /** Copies only inventoried inputs, then verifies every copied byte before analysis can begin. */
    public SourceSnapshot materialize(Path destination, int workers) throws IOException {
        PathGuard.noSymlinkComponents(destination);
        Files.createDirectory(destination);
        for (FileRecord file : files.values()) {
            Path input = root.resolve(file.path());
            Path target = destination.resolve(file.path());
            PathGuard.noSymlinkComponents(input);
            Files.createDirectories(target.getParent());
            Files.copy(input, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
        }
        SourceSnapshot copy = capture(destination, workers);
        requireSameContent(copy);
        return copy;
    }

    public void requireSameContent(SourceSnapshot after) throws IOException {
        if (!contentHash.equals(after.contentHash)) {
            throw new IOException("Source content changed during the run; evidence is not fresh");
        }
    }
}
