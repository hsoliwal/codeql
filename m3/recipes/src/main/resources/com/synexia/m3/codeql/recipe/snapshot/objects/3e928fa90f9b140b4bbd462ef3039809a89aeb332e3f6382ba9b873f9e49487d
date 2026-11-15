package com.synexia.m3.codeql;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parent-owned primitive columns and one bounded VM-local dictionary own the evidence.
 * Public row records are materialized only on request; no OS-shared-string claim is made.
 */
public final class EvidenceStore {
    public record Row(String path, String packageName, String owner, String signature, String kind,
                      String subject, int startLine, int startColumn, int endLine, int endColumn) {}

    private final int maxRows;
    private final int maxDictionaryEntries;
    private final long dictionaryBudgetBytes;
    private final Map<String, Integer> dictionaryIds = new HashMap<>();
    private final List<String> dictionary = new ArrayList<>();
    private final int[][] columns = new int[10][];
    private long estimatedDictionaryBytes;
    private int size;

    public EvidenceStore(int requestedMaxRows) {
        this(requestedMaxRows, Runtime.getRuntime().maxMemory());
    }

    EvidenceStore(int requestedMaxRows, long heapBudgetBytes) {
        long heapRowLimit = Math.max(1, heapBudgetBytes / 256);
        if (requestedMaxRows < 1 || requestedMaxRows > 5_000_000) {
            throw new IllegalArgumentException("Maximum evidence rows must be between 1 and 5000000");
        }
        maxRows = (int) Math.min(requestedMaxRows, heapRowLimit);
        maxDictionaryEntries = Math.max(64, Math.multiplyExact(maxRows, 6));
        dictionaryBudgetBytes = heapBudgetBytes / 4;
        int capacity = Math.min(64, maxRows);
        for (int i = 0; i < columns.length; i++) columns[i] = new int[capacity];
    }

    public void add(Row row) {
        if (size == maxRows) throw new IllegalStateException("Evidence row limit reached: " + maxRows);
        String[] text = {row.path(), row.packageName(), row.owner(), row.signature(), row.kind(), row.subject()};
        List<String> additions = new ArrayList<>(6);
        long addedBytes = 0;
        for (String value : text) {
            Objects.requireNonNull(value, "Evidence string");
            if (!dictionaryIds.containsKey(value) && !additions.contains(value)) {
                additions.add(value);
                // Reserve String, map node/table, boxed ID, list slot, and UTF-16 payload conservatively.
                addedBytes += 192L + 2L * value.length();
            }
        }
        if (dictionary.size() + additions.size() > maxDictionaryEntries
                || estimatedDictionaryBytes + addedBytes > dictionaryBudgetBytes) {
            throw new IllegalStateException("Evidence dictionary heap budget reached");
        }
        if (size == columns[0].length) grow();
        for (int i = 0; i < text.length; i++) columns[i][size] = id(text[i]);
        estimatedDictionaryBytes += addedBytes;
        columns[6][size] = row.startLine();
        columns[7][size] = row.startColumn();
        columns[8][size] = row.endLine();
        columns[9][size] = row.endColumn();
        size++;
    }

    private int id(String text) {
        Integer existing = dictionaryIds.get(text);
        if (existing != null) return existing;
        int id = dictionary.size();
        dictionary.add(text);
        dictionaryIds.put(text, id);
        return id;
    }

    private void grow() {
        int next = Math.min(maxRows, columns[0].length + Math.max(1, columns[0].length / 2));
        for (int i = 0; i < columns.length; i++) columns[i] = Arrays.copyOf(columns[i], next);
    }

    public int size() { return size; }
    public int dictionarySize() { return dictionary.size(); }
    public int rowLimit() { return maxRows; }

    public Row row(int index) {
        java.util.Objects.checkIndex(index, size);
        return new Row(text(0, index), text(1, index), text(2, index), text(3, index),
                text(4, index), text(5, index), columns[6][index], columns[7][index], columns[8][index], columns[9][index]);
    }

    private String text(int column, int row) { return dictionary.get(columns[column][row]); }

    private int compare(int left, int right) {
        for (int column = 0; column < 10; column++) {
            int comparison = column < 6
                    ? text(column, left).compareTo(text(column, right))
                    : Integer.compare(columns[column][left], columns[column][right]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    /** A primitive index array provides deterministic fan-in without allocating one object per row. */
    public int[] sortedUniqueIndices() {
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        int[] work = new int[size];
        for (int width = 1; width < size; width *= 2) {
            for (int start = 0; start < size; start += width * 2) {
                int middle = Math.min(start + width, size);
                int end = Math.min(start + width * 2, size);
                int left = start, right = middle, destination = start;
                while (left < middle || right < end) {
                    if (right == end || (left < middle && compare(indices[left], indices[right]) <= 0)) {
                        work[destination++] = indices[left++];
                    } else {
                        work[destination++] = indices[right++];
                    }
                }
            }
            int[] swap = indices;
            indices = work;
            work = swap;
        }
        int unique = 0;
        for (int index : indices) {
            if (unique == 0 || compare(indices[unique - 1], index) != 0) indices[unique++] = index;
        }
        return Arrays.copyOf(indices, unique);
    }

    /** Hash of the exported evidence structure; intentionally not named a semantic logic hash. */
    public String structuralEvidenceHash(int[] orderedIndices) {
        MessageDigest digest = Hashes.sha256();
        Hashes.field(digest, "m3-structural-evidence-v1");
        for (int index : orderedIndices) {
            for (int column = 0; column < 10; column++) {
                Hashes.field(digest, column < 6 ? text(column, index) : Integer.toString(columns[column][index]));
            }
        }
        return Hashes.hex(digest.digest());
    }
}
