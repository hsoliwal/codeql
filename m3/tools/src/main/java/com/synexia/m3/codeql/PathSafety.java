// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

final class PathSafety {
    private PathSafety() {}

    static CliOptions canonical(CliOptions o) throws IOException {
        Path root = o.root().toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Source root must be a directory: " + root);
        Path output = prospectiveRealPath(o.output());
        disjoint(root, output, "source root", "output");
        Path database = o.database() == null ? null : o.database().toRealPath();
        if (database != null) {
            if (!Files.isDirectory(database)) throw new IOException("Database must be a directory: " + database);
            disjoint(database, output, "database", "output");
        }
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Output already exists; choose a fresh run directory: " + output);
        }
        return new CliOptions(o.command(), root, output, o.codeql(), database, o.threads(), o.ram(),
                o.timeout(), o.shards(), o.shard(), o.execute());
    }

    private static Path prospectiveRealPath(Path requested) throws IOException {
        Path absolute = requested.toAbsolutePath().normalize();
        Path ancestor = absolute;
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.getParent();
            if (ancestor == null) throw new IOException("Cannot resolve output path: " + requested);
        }
        return ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
    }

    private static void disjoint(Path first, Path second, String a, String b) throws IOException {
        if (first.startsWith(second) || second.startsWith(first)) {
            throw new IOException("Paths overlap: " + a + " and " + b + ".");
        }
    }

    static List<Path> files(Path root) throws IOException {
        if (Files.isSymbolicLink(root)) throw new IOException("Symbolic links are not accepted in input trees: " + root);
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return !dir.equals(root) && Set.of(".git", ".hg", ".svn").contains(dir.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) throw new IOException("Symbolic links are not accepted in input trees: " + file);
                if (!attrs.isRegularFile()) throw new IOException("Non-regular input file: " + file);
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(path -> relative(root, path)));
        return List.copyOf(files);
    }

    static String relative(Path root, Path path) { return root.relativize(path).toString().replace(java.io.File.separatorChar, '/'); }

    static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("Required SHA-256 provider missing", e); }
        try (InputStream stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = stream.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
