// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A lexical catalogue of .ql inputs; this does not parse or execute QL semantics. */
final class QueryInventory {
    record Query(String path, long bytes, String sha256, Map<String, String> metadata, int assignedShard, boolean selected) {
        Query { metadata = Map.copyOf(metadata); }
        String field(String key) { return metadata.getOrDefault(key, ""); }
    }
    record Action(String severity, String action, String path, String detail) {}
    record Catalogue(List<Query> queries, List<Action> actions) {
        Catalogue { queries = List.copyOf(queries); actions = List.copyOf(actions); }
        boolean valid() { return actions.stream().noneMatch(a -> a.severity().equals("ERROR")); }
    }
    private static final Pattern TAG = Pattern.compile("^\\s*\\*?\\s*@([A-Za-z][A-Za-z0-9_.-]*)(?:\\s+(.*?))?\\s*$");
    private static final int MAX_HEADER_CHARS = 1_048_576;

    Catalogue scan(CliOptions options) throws IOException {
        List<Query> queries = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        Map<String, List<String>> ids = new TreeMap<>();
        for (Path path : PathSafety.files(options.root())) {
            if (!path.getFileName().toString().endsWith(".ql")) continue;
            String relative = PathSafety.relative(options.root(), path);
            Map<String, String> metadata = metadata(path, relative, actions);
            int shard = queries.size() % options.shards();
            Query query = new Query(relative, Files.size(path), PathSafety.sha256(path), metadata, shard, shard == options.shard());
            queries.add(query);
            String id = query.field("id");
            if (id.isBlank()) actions.add(new Action("INFO", "MISSING_ID", relative, "May be a test or an ad-hoc query; no ID inferred."));
            else ids.computeIfAbsent(id, ignored -> new ArrayList<>()).add(relative);
        }
        ids.forEach((id, paths) -> {
            if (paths.size() > 1) for (String path : paths) {
                actions.add(new Action("ERROR", "DUPLICATE_ID", path, id + " occurs in " + paths.size() + " files across the complete inventory."));
            }
        });
        actions.sort(java.util.Comparator.comparing(Action::path).thenComparing(Action::action));
        return new Catalogue(queries, actions);
    }

    private Map<String, String> metadata(Path file, String path, List<Action> actions) throws IOException {
        Map<String, StringBuilder> fields = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            int read = 0;
            int c = reader.read();
            if (c == '\uFEFF') c = reader.read();
            while (c >= 0) {
                if (++read > MAX_HEADER_CHARS) throw new IOException("Header exceeds 1 MiB characters: " + path);
                if (Character.isWhitespace(c)) { c = reader.read(); continue; }
                if (c != '/') break;
                int next = reader.read();
                if (next == '/') {
                    while ((c = reader.read()) >= 0 && c != '\n') {
                        if (++read > MAX_HEADER_CHARS) throw new IOException("Header exceeds 1 MiB characters: " + path);
                    }
                    continue;
                }
                if (next != '*') break;
                StringBuilder comment = new StringBuilder();
                int previous = -1;
                boolean closed = false;
                while ((c = reader.read()) >= 0) {
                    if (++read > MAX_HEADER_CHARS) throw new IOException("Header exceeds 1 MiB characters: " + path);
                    if (previous == '*' && c == '/') {
                        comment.setLength(comment.length() - 1);
                        closed = true;
                        break;
                    }
                    comment.append((char)c);
                    previous = c;
                }
                if (!closed) throw new IOException("Unclosed header comment: " + path);
                if (!comment.isEmpty() && comment.charAt(0) == '*') {
                    StringBuilder current = null;
                    for (String line : comment.substring(1).split("\\R", -1)) {
                        Matcher match = TAG.matcher(line);
                        if (match.matches()) {
                            current = new StringBuilder(normalize(match.group(2) == null ? "" : match.group(2)));
                            if (fields.putIfAbsent(match.group(1), current) != null) {
                                actions.add(new Action("ERROR", "INVALID_METADATA", path,
                                        "Repeated @" + match.group(1) + " in leading QLDoc metadata."));
                                current = null;
                            }
                        } else if (current != null) {
                            String continuation = line.strip();
                            if (continuation.startsWith("*")) continuation = continuation.substring(1);
                            continuation = normalize(continuation);
                            if (!continuation.isEmpty()) {
                                if (!current.isEmpty()) current.append(' ');
                                current.append(continuation);
                            }
                        }
                    }
                }
                c = reader.read();
            }
        }
        Map<String, String> metadata = new HashMap<>();
        fields.forEach((key, value) -> metadata.put(key, value.toString()));
        return metadata;
    }

    private static String normalize(String value) { return value.strip().replaceAll("(?U)\\s+", " "); }

    void write(Catalogue catalogue, Path output) throws IOException {
        List<List<?>> inventory = new ArrayList<>();
        List<List<?>> provenance = new ArrayList<>();
        Map<String, List<Query>> hashes = new TreeMap<>();
        for (Query q : catalogue.queries()) {
            inventory.add(List.of(q.path(), q.bytes(), q.sha256(), q.field("id"), q.field("name"), q.field("kind"),
                    q.field("precision"), q.field("problem.severity"), q.field("security-severity"), q.field("tags"),
                    q.assignedShard(), q.selected()));
            provenance.add(List.of("QUERY", q.path(), q.bytes(), q.sha256()));
            hashes.computeIfAbsent(q.sha256(), ignored -> new ArrayList<>()).add(q);
        }
        Tsv.write(output.resolve("INVENTORY.tsv"), List.of("path", "bytes", "sha256", "id", "name", "kind",
                "precision", "problem_severity", "security_severity", "tags", "assigned_shard", "selected"), inventory);
        Tsv.write(output.resolve("PROVENANCE.tsv"), List.of("role", "path", "bytes", "sha256"), provenance);
        List<List<?>> exact = new ArrayList<>();
        hashes.forEach((hash, queries) -> {
            if (queries.size() > 1) for (Query q : queries) exact.add(List.of(hash, queries.size(), q.path()));
        });
        Tsv.write(output.resolve("CLUSTERS_EXACT.tsv"), List.of("sha256", "members", "path"), exact);
        Tsv.write(output.resolve("CLUSTERS_NORMALIZED.tsv"), List.of("state", "detail"),
                List.of(List.of("NOT_IMPLEMENTED", "No normalized or semantic equivalence claims; exact byte hashes only.")));
        List<List<?>> actions = new ArrayList<>();
        for (Action a : catalogue.actions()) actions.add(List.of(a.severity(), a.action(), a.path(), a.detail()));
        Tsv.write(output.resolve("ACTION_QUEUE.tsv"), List.of("severity", "action", "path", "detail"), actions);
        Tsv.write(output.resolve("SUMMARY.tsv"), List.of("metric", "value"), List.of(
                List.of("queries", catalogue.queries().size()),
                List.of("selected_queries", catalogue.queries().stream().filter(Query::selected).count()),
                List.of("validation_errors", catalogue.actions().stream().filter(a -> a.severity().equals("ERROR")).count()),
                List.of("exact_duplicate_groups", hashes.values().stream().filter(q -> q.size() > 1).count()),
                List.of("normalized_clustering", "NOT_IMPLEMENTED")));
    }
}
