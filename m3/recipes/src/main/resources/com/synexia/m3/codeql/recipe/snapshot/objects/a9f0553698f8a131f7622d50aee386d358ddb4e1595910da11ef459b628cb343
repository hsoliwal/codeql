package com.synexia.m3.codeql;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Rejects symlinks instead of silently inventorying targets outside the declared scope. */
final class PathGuard {
    private PathGuard() {}

    static Path directory(Path supplied) throws IOException {
        Path path = supplied.toAbsolutePath().normalize();
        noSymlinkComponents(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a source directory: " + path);
        }
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    static void noSymlinkComponents(Path supplied) throws IOException {
        Path path = supplied.toAbsolutePath().normalize();
        Path current = path.getRoot();
        for (Path component : path) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symlink is outside the supported input contract: " + current);
            }
        }
    }

    static Path createOutput(Path supplied, Path source) throws IOException {
        Path output = supplied.toAbsolutePath().normalize();
        noSymlinkComponents(output);
        requireDisjointOutput(output, source);
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Output must be a new directory; refusing to overwrite: " + output);
        }
        Files.createDirectories(output.getParent());
        Files.createDirectory(output);
        return output;
    }

    static void requireDisjointOutput(Path suppliedOutput, Path suppliedInput) throws IOException {
        Path output = suppliedOutput.toAbsolutePath().normalize();
        Path input = suppliedInput.toAbsolutePath().normalize();
        if (output.startsWith(input) || input.startsWith(output)) {
            throw new IOException("Output must not overlap any source or query input: " + input);
        }
    }

    static String relativeInput(Path root, String supplied) throws IOException {
        if (supplied.isBlank() || supplied.indexOf('\0') >= 0) {
            throw new IOException("Empty or invalid source path in evidence");
        }
        Path input;
        try {
            input = Path.of(supplied);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid source path in evidence", invalid);
        }
        for (Path segment : input) {
            if (segment.toString().equals("..")) {
                throw new IOException("Parent traversal in evidence path: " + supplied);
            }
        }
        Path resolved = (input.isAbsolute() ? input : root.resolve(input)).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("Evidence source escapes declared root: " + supplied);
        }
        noSymlinkComponents(resolved);
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Evidence source is not a regular file: " + supplied);
        }
        return root.relativize(resolved).toString().replace(resolved.getFileSystem().getSeparator(), "/");
    }
}
