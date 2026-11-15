// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.text.PlainText;

/** Capture, verify and apply the same resource-backed OpenRewrite recipe used by tests. */
public final class SnapshotTool {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            "target", ".git", ".cache", ".codeql", ".testproj", "__pycache__");

    private SnapshotTool() {
    }

    /**
     * capture ROOT RESOURCE_DIRECTORY PATH... | verify ROOT [RESOURCE_DIRECTORY] |
     * apply ROOT [RESOURCE_DIRECTORY]. No command modifies an existing destination file.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException(usage());
        }
        String operation = args[0];
        Path root = Path.of(args[1]);
        if (operation.equals("capture")) {
            if (args.length < 4) {
                throw new IllegalArgumentException(usage());
            }
            Snapshot snapshot = capture(root, List.of(args).subList(3, args.length));
            writeSnapshot(snapshot, Path.of(args[2]));
            System.out.println("Captured " + snapshot.entries().size() + " files; manifest SHA-256 "
                    + snapshot.manifestSha256());
        } else if (operation.equals("verify") || operation.equals("apply")) {
            if (args.length > 3) {
                throw new IllegalArgumentException(usage());
            }
            Snapshot snapshot = args.length == 3 ? Snapshot.read(Path.of(args[2])) : Snapshot.bundled();
            if (operation.equals("apply")) {
                int written = apply(snapshot, root);
                System.out.println("OpenRewrite generated " + written + " new files; manifest SHA-256 "
                        + snapshot.manifestSha256());
            } else {
                verify(snapshot, root);
                System.out.println("Verified " + snapshot.entries().size() + " files; manifest SHA-256 "
                        + snapshot.manifestSha256());
            }
        } else {
            throw new IllegalArgumentException(usage());
        }
    }

    private static String usage() {
        return "Usage: capture ROOT RESOURCE_DIRECTORY PATH... | verify ROOT [RESOURCE_DIRECTORY] | "
                + "apply ROOT [RESOURCE_DIRECTORY]";
    }

    public static Snapshot capture(Path sourceRoot, List<String> includes) throws IOException {
        Path root = Snapshot.checkedRoot(sourceRoot);
        TreeMap<String, Snapshot.Entry> entries = new TreeMap<>();
        for (String include : includes) {
            Snapshot.requireOwnedPath(include);
            Path start = Snapshot.checkedPath(root, include);
            if (!Files.exists(start, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Capture input does not exist: " + include);
            }
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                        throws IOException {
                    String relative = relative(root, directory);
                    if (EXCLUDED_DIRECTORIES.contains(directory.getFileName().toString())
                            || directory.getFileName().toString().endsWith(".testproj")
                            || relative.equals("m3/recipes")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Snapshot.requireNoSymlinks(directory);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                        throw new IOException("Non-regular capture input: " + file);
                    }
                    String path = relative(root, file);
                    if (!excludedGeneratedFile(path)) {
                        Snapshot.requireOwnedPath(path);
                        Path checked = Snapshot.checkedExistingFile(root, path);
                        byte[] bytes = Files.readAllBytes(checked);
                        Snapshot.Entry entry = new Snapshot.Entry(path, Snapshot.hash(bytes), bytes.length,
                                executable(checked), Snapshot.decode(bytes));
                        if (entries.putIfAbsent(path, entry) != null) {
                            throw new IOException("Duplicate capture input: " + path);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return new Snapshot(List.copyOf(entries.values()));
    }

    private static boolean excludedGeneratedFile(String path) {
        return path.endsWith(".bqrs") || path.endsWith(".qlx") || path.endsWith(".qlx.lock")
                || path.endsWith(".actual")
                || path.endsWith(".log") || path.endsWith(".pyc");
    }

    public static void writeSnapshot(Snapshot snapshot, Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        Snapshot.requireNoSymlinks(root);
        Files.createDirectories(root.resolve("objects"));
        Snapshot.requireNoSymlinks(root.resolve("objects"));
        for (Snapshot.Entry entry : snapshot.entries()) {
            Path object = Snapshot.checkedPath(root, "objects/" + entry.sha256());
            byte[] expected = Snapshot.encode(entry.text());
            if (Files.exists(object, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(object, LinkOption.NOFOLLOW_LINKS)
                        || !java.util.Arrays.equals(expected, Files.readAllBytes(object))) {
                    throw new IOException("Existing snapshot object differs: " + entry.sha256());
                }
            } else {
                Files.write(object, expected, StandardOpenOption.CREATE_NEW);
            }
        }
        atomicWrite(root, "manifest.tsv", Snapshot.encode(snapshot.manifest()));
        atomicWrite(root, "manifest.sha256", Snapshot.encode(snapshot.manifestSha256() + "\n"));
        Set<String> referenced = new HashSet<>();
        for (Snapshot.Entry entry : snapshot.entries()) {
            referenced.add(entry.sha256());
        }
        // Only obsolete, well-formed objects created by this tool may be removed.
        try (var paths = Files.list(root.resolve("objects"))) {
            for (Path object : paths.toList()) {
                String name = object.getFileName().toString();
                if (!name.matches("[0-9a-f]{64}") || !Files.isRegularFile(object, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unexpected file in snapshot objects directory: " + object);
                }
                if (!referenced.contains(name)) {
                    Files.delete(object);
                }
            }
        }
        Snapshot reread = Snapshot.read(root);
        if (!reread.manifestSha256().equals(snapshot.manifestSha256())) {
            throw new IOException("Snapshot changed during capture");
        }
    }

    private static void atomicWrite(Path root, String path, byte[] bytes) throws IOException {
        Path target = Snapshot.checkedPath(root, path);
        Path temporary = Files.createTempFile(root, ".snapshot-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void verify(Snapshot snapshot, Path sourceRoot) throws IOException {
        Path root = Snapshot.checkedRoot(sourceRoot);
        for (Snapshot.Entry entry : snapshot.entries()) {
            Path file = Snapshot.checkedExistingFile(root, entry.path());
            verifyEntry(entry, file);
        }
    }

    private static void verifyEntry(Snapshot.Entry entry, Path file) throws IOException {
        if (Files.size(file) != entry.bytes() || !Snapshot.hash(Files.readAllBytes(file)).equals(entry.sha256())) {
            throw new IOException("Existing content differs: " + entry.path());
        }
        if (supportsPosix(file) && executable(file) != entry.executable()) {
            throw new IOException("Existing executable mode differs: " + entry.path());
        }
    }

    /**
     * Preflight the complete snapshot, execute OpenRewrite, then write only CREATE_NEW files.
     * Errors roll back files created by this call. Unrelated source files are never opened for writing.
     */
    public static int apply(Snapshot snapshot, Path sourceRoot) throws IOException {
        Path root = Snapshot.checkedRoot(sourceRoot);
        List<SourceFile> input = new ArrayList<>();
        for (Snapshot.Entry entry : snapshot.entries()) {
            Path file = Snapshot.checkedPath(root, entry.path());
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                Snapshot.checkedExistingFile(root, entry.path());
                verifyEntry(entry, file);
                input.add(PlainText.builder().sourcePath(Path.of(entry.path()))
                        .charsetName(StandardCharsets.UTF_8.name()).text(Snapshot.decode(Files.readAllBytes(file)))
                        .fileAttributes(new org.openrewrite.FileAttributes(null, null, null, true, true,
                                entry.executable(), entry.bytes())).build());
            }
        }
        List<Result> results = execute(new M3CodeqlSnapshotRecipe(snapshot), input);
        Map<String, SourceFile> additions = new TreeMap<>();
        for (Result result : results) {
            SourceFile after = result.getAfter();
            if (result.getBefore() != null || after == null) {
                throw new IOException("Recipe attempted an existing-file edit or deletion");
            }
            String path = after.getSourcePath().toString().replace('\\', '/');
            Snapshot.Entry entry = snapshot.entry(path);
            if (entry == null || !Snapshot.hash(Snapshot.encode(after.printAll())).equals(entry.sha256())) {
                throw new IOException("OpenRewrite emitted an unexpected postimage: " + path);
            }
            if (additions.putIfAbsent(path, after) != null) {
                throw new IOException("OpenRewrite emitted a duplicate path: " + path);
            }
        }
        if (input.size() + additions.size() != snapshot.entries().size()) {
            throw new IOException("OpenRewrite output did not cover the complete snapshot");
        }
        List<SourceFile> complete = new ArrayList<>(input);
        complete.addAll(additions.values());
        if (!execute(new M3CodeqlSnapshotRecipe(snapshot), complete).isEmpty()) {
            throw new IOException("Recipe did not reach a fixed point");
        }
        List<Path> created = new ArrayList<>();
        try {
            for (Map.Entry<String, SourceFile> addition : additions.entrySet()) {
                Path file = Snapshot.checkedPath(root, addition.getKey());
                Files.createDirectories(file.getParent());
                Snapshot.requireNoSymlinks(file);
                Files.write(file, Snapshot.encode(addition.getValue().printAll()), StandardOpenOption.CREATE_NEW);
                created.add(file);
                applyMode(file, snapshot.entry(addition.getKey()).executable());
            }
            verify(snapshot, root);
        } catch (IOException | RuntimeException failure) {
            for (int i = created.size() - 1; i >= 0; i--) {
                try {
                    Path file = created.get(i);
                    // Do not remove a concurrent writer's replacement or follow a symbolic link.
                    Snapshot.Entry entry = snapshot.entry(relative(root, file));
                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            && Snapshot.hash(Files.readAllBytes(file)).equals(entry.sha256())) {
                        Files.delete(file);
                    }
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
        return created.size();
    }

    static List<Result> execute(M3CodeqlSnapshotRecipe recipe, List<SourceFile> input) throws IOException {
        List<Throwable> errors = new ArrayList<>();
        List<Result> results;
        try {
            results = recipe.run(new InMemoryLargeSourceSet(input), new InMemoryExecutionContext(errors::add), 1)
                    .getChangeset().getAllResults();
        } catch (RuntimeException failure) {
            throw new IOException("OpenRewrite rejected the snapshot", failure);
        }
        if (!errors.isEmpty()) {
            IOException failure = new IOException("OpenRewrite rejected the snapshot", errors.getFirst());
            for (int i = 1; i < errors.size(); i++) {
                failure.addSuppressed(errors.get(i));
            }
            throw failure;
        }
        return results;
    }

    static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    static boolean executable(Path file) throws IOException {
        if (!supportsPosix(file)) {
            // Windows does not model Git's executable bit; capture the portable default.
            return false;
        }
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
        return permissions.contains(PosixFilePermission.OWNER_EXECUTE)
                || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                || permissions.contains(PosixFilePermission.OTHERS_EXECUTE);
    }

    private static boolean supportsPosix(Path file) throws IOException {
        return Files.getFileStore(file).supportsFileAttributeView("posix");
    }

    private static void applyMode(Path file, boolean executable) throws IOException {
        if (supportsPosix(file)) {
            Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(file));
            permissions.remove(PosixFilePermission.OWNER_EXECUTE);
            permissions.remove(PosixFilePermission.GROUP_EXECUTE);
            permissions.remove(PosixFilePermission.OTHERS_EXECUTE);
            if (executable) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            Files.setPosixFilePermissions(file, permissions);
        }
    }
}
