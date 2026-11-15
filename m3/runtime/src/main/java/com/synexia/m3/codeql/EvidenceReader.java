package com.synexia.m3.codeql;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Binds each decoded tuple to an inventoried source and a real source range. */
final class EvidenceReader {
    static final List<String> HEADER = List.of("path", "package", "owner", "signature", "kind", "subject",
            "startLine", "startColumn", "endLine", "endColumn");

    private final SourceSnapshot snapshot;
    private final EvidenceStore store;
    private final Map<String, int[]> lineLengths = new LinkedHashMap<>(16, 0.75f, true);

    EvidenceReader(SourceSnapshot snapshot, EvidenceStore store) {
        this.snapshot = snapshot;
        this.store = store;
    }

    void read(Path csv) throws IOException {
        boolean[] sawHeader = {false};
        try (var input = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            Delimited.csv(input, fields -> {
                if (!sawHeader[0]) {
                    if (!fields.equals(HEADER)) throw new IOException("Unexpected evidence CSV schema in " + csv);
                    sawHeader[0] = true;
                    return;
                }
                if (fields.size() != HEADER.size()) throw new IOException("Evidence row must have ten fields");
                String path = PathGuard.relativeInput(snapshot.root(), fields.get(0));
                if (!snapshot.files().containsKey(path)) throw new IOException("Evidence is not bound to source inventory: " + path);
                if (fields.get(2).isBlank() || fields.get(3).isBlank() || fields.get(4).isBlank()) {
                    throw new IOException("Evidence owner, signature, and kind must be present");
                }
                int startLine = positive(fields.get(6));
                int startColumn = positive(fields.get(7));
                int endLine = positive(fields.get(8));
                int endColumn = positive(fields.get(9));
                int[] lengths = lengths(path);
                if (endLine < startLine || endLine > lengths.length || startLine > lengths.length
                        || (startLine == endLine && endColumn < startColumn)
                        || startColumn > lengths[startLine - 1] + 1 || endColumn > lengths[endLine - 1] + 1) {
                    throw new IOException("Evidence range is outside source: " + path);
                }
                try {
                    store.add(new EvidenceStore.Row(path, fields.get(1), fields.get(2), fields.get(3), fields.get(4),
                            fields.get(5), startLine, startColumn, endLine, endColumn));
                } catch (IllegalStateException capacity) {
                    throw new IOException(capacity.getMessage(), capacity);
                }
            });
        }
        if (!sawHeader[0]) throw new IOException("Missing evidence CSV schema in " + csv);
    }

    private static int positive(String text) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException failure) {
            throw new IOException("Evidence location must be a positive integer: " + text, failure);
        }
    }

    private int[] lengths(String path) throws IOException {
        int[] cached = lineLengths.get(path);
        if (cached != null) return cached;
        List<Integer> lengths = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(snapshot.root().resolve(path), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) lengths.add(line.length());
        }
        int[] result = new int[lengths.size()];
        for (int i = 0; i < result.length; i++) result[i] = lengths.get(i);
        if (lineLengths.size() == 128) lineLengths.remove(lineLengths.keySet().iterator().next());
        lineLengths.put(path, result);
        return result;
    }
}
