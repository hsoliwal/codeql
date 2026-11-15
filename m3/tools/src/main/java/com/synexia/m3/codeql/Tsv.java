// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Version 1: UTF-8, LF records; backslash, tab, CR and LF use reversible backslash escapes. */
final class Tsv {
    private Tsv() {}

    static String escape(Object value) {
        return value == null ? "" : value.toString().replace("\\", "\\\\")
                .replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    static void write(Path path, List<String> columns, List<? extends List<?>> rows) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "tsv-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write("schema_version");
                for (String column : columns) { writer.write('\t'); writer.write(column); }
                writer.write('\n');
                for (List<?> row : rows) {
                    if (row.size() != columns.size()) throw new IllegalArgumentException("TSV column mismatch for " + path);
                    writer.write("1");
                    for (Object cell : row) { writer.write('\t'); writer.write(escape(cell)); }
                    writer.write('\n');
                }
            }
            replace(temporary, path);
        } finally { Files.deleteIfExists(temporary); }
    }

    static void text(Path path, String content) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "report-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            replace(temporary, path);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void replace(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
}
