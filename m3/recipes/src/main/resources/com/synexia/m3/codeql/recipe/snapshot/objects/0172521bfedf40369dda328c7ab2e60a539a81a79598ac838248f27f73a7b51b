package com.synexia.m3.codeql;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Emits evidence receipts and explicit limitations, including on a failed analysis. */
final class Reports {
    static final String INVENTORY_HEADER = Delimited.line("path", "module", "package", "symbols", "modifiedMillis",
            "bytes", "fastHash", "sha256", "change");
    static final List<String> REQUIRED = List.of("STATUS.tsv", "FINAL_REPORT.md", "RUN_CONTEXT.tsv", "PROVENANCE.tsv",
            "VERIFY_CONTRACT.tsv", "OUTPUT_CONTRACT.tsv", "INVENTORY.tsv", "CLUSTERS_EXACT.tsv",
            "CLUSTERS_NORMALIZED.tsv", "ACTION_QUEUE.tsv", "SUMMARY.tsv", "EVIDENCE.tsv", "FINGERPRINTS.tsv",
            "RESIDUE.tsv", "PROOF_RECEIPTS.tsv", "TASK_PACKET.tsv");

    private Reports() {}

    static Map<String, SourceSnapshot.FileRecord> previous(Path manifest) throws IOException {
        Map<String, SourceSnapshot.FileRecord> result = new TreeMap<>();
        try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!(INVENTORY_HEADER.stripTrailing()).equals(header)) throw new IOException("Unsupported previous inventory schema");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split("\t", -1);
                if (cells.length != 9) throw new IOException("Previous inventory row must have nine fields");
                for (int i = 0; i < cells.length; i++) cells[i] = Delimited.untsv(cells[i]);
                if (!List.of("ADDED", "CHANGED", "UNCHANGED", "DELETED").contains(cells[8])) throw new IOException("Invalid previous change classification");
                if (cells[8].equals("DELETED")) continue;
                if (!cells[7].matches("[0-9a-f]{64}") || !cells[6].matches("[0-9a-f]{8}")) {
                    throw new IOException("Previous inventory contains an invalid hash");
                }
                Path path;
                try {
                    path = Path.of(cells[0]);
                } catch (RuntimeException invalid) {
                    throw new IOException("Invalid path in previous inventory", invalid);
                }
                if (path.isAbsolute() || cells[0].isBlank() || !path.normalize().equals(path)
                        || !path.toString().replace(path.getFileSystem().getSeparator(), "/").equals(cells[0])) {
                    throw new IOException("Previous inventory path must be normalized and relative");
                }
                for (Path part : path) if (part.toString().equals("..")) throw new IOException("Parent traversal in previous inventory");
                try {
                    SourceSnapshot.FileRecord record = new SourceSnapshot.FileRecord(cells[0], Long.parseLong(cells[5]),
                            Long.parseLong(cells[4]), cells[6], cells[7]);
                    if (record.bytes() < 0 || result.putIfAbsent(record.path(), record) != null) {
                        throw new IOException("Invalid or duplicate previous inventory row");
                    }
                } catch (NumberFormatException invalid) {
                    throw new IOException("Invalid previous inventory size or timestamp", invalid);
                }
            }
        }
        return result;
    }

    static void write(Path output, Map<String, String> context, SourceSnapshot before, SourceSnapshot after,
                      Map<String, SourceSnapshot.FileRecord> previous, EvidenceStore evidence,
                      List<CommandRunner.Receipt> receipts, boolean success, String failure) throws IOException {
        Map<String, SourceSnapshot.FileRecord> current = before == null ? Map.of() : before.files();
        int[] order = evidence.sortedUniqueIndices();
        EvidenceFingerprints.Result patterns = EvidenceFingerprints.analyze(evidence, order);
        Map<String, SymbolSummary> inventorySymbols = new TreeMap<>();
        for (int index : order) {
            EvidenceStore.Row row = evidence.row(index);
            if (row.kind().equals("callable") || row.kind().equals("method") || row.kind().equals("constructor")) {
                SymbolSummary summary = inventorySymbols.computeIfAbsent(row.path(), ignored -> new SymbolSummary());
                summary.packages.add(row.packageName());
                summary.count++;
            }
        }
        Map<String, Integer> changes = new LinkedHashMap<>();
        for (String kind : List.of("ADDED", "CHANGED", "UNCHANGED", "DELETED")) changes.put(kind, 0);
        try (var writer = writer(output, "INVENTORY.tsv")) {
            writer.write(INVENTORY_HEADER);
            TreeMap<String, SourceSnapshot.FileRecord> union = new TreeMap<>(previous);
            union.putAll(current);
            for (var entry : union.entrySet()) {
                var file = entry.getValue();
                String change = !current.containsKey(entry.getKey()) ? "DELETED"
                        : !previous.containsKey(entry.getKey()) ? "ADDED"
                        : previous.get(entry.getKey()).sha256().equals(file.sha256()) ? "UNCHANGED" : "CHANGED";
                changes.merge(change, 1, Integer::sum);
                SymbolSummary symbols = inventorySymbols.get(file.path());
                String packageName = symbols == null ? "NOT_ANALYZED" : String.join(",", symbols.packages);
                String symbolSummary = symbols == null ? "NOT_ANALYZED" : Integer.toString(symbols.count);
                writer.write(Delimited.line(file.path(), "SOURCE_ROOT", packageName, symbolSummary,
                        Long.toString(file.modifiedMillis()), Long.toString(file.bytes()), file.fastHash(), file.sha256(), change));
            }
        }
        try (var writer = writer(output, "EVIDENCE.tsv")) {
            writer.write(Delimited.line("path", "package", "owner", "signature", "kind", "subject", "startLine", "startColumn",
                    "endLine", "endColumn", "sourceSha256", "freshness"));
            for (int index : order) {
                EvidenceStore.Row row = evidence.row(index);
                writer.write(Delimited.line(row.path(), row.packageName(), row.owner(), row.signature(), row.kind(), row.subject(),
                        Integer.toString(row.startLine()), Integer.toString(row.startColumn()), Integer.toString(row.endLine()),
                        Integer.toString(row.endColumn()), current.get(row.path()).sha256(), success ? "BOUND_TO_SNAPSHOT" : "UNVERIFIED_RUN"));
            }
        }
        try (var exact = writer(output, "CLUSTERS_EXACT.tsv"); var queue = writer(output, "ACTION_QUEUE.tsv")) {
            exact.write(Delimited.line("sha256", "path", "interpretation"));
            queue.write(Delimited.line("priority", "path", "candidate", "note"));
            Map<String, List<String>> clusters = new TreeMap<>();
            current.values().forEach(file -> clusters.computeIfAbsent(file.sha256(), ignored -> new ArrayList<>()).add(file.path()));
            for (var cluster : clusters.entrySet()) {
                if (cluster.getValue().size() < 2) continue;
                for (String path : cluster.getValue()) {
                    exact.write(Delimited.line(cluster.getKey(), path, "IDENTICAL_CONTENT_HASH_NOT_SEMANTIC_PROOF"));
                    queue.write(Delimited.line("P1", path, "EXACT_CONTENT_REVIEW",
                            "These files may be similar. Please confirm via hash or diff."));
                }
            }
            for (var candidate : patterns.candidates()) {
                queue.write(Delimited.line(candidate.kind() == EvidenceFingerprints.Kind.EXACT_FACT_PATTERN ? "P2" : "P3",
                        candidate.left().path(), candidate.kind().name(),
                        "left=" + callableId(candidate.left()) + ";right=" + callableId(candidate.right())
                        + ";hamming=" + candidate.hammingDistance()
                        + ";EXPORTED_FACTS_ONLY;REQUIRES_CONTRACT_PROOF;"
                        + (success ? "BOUND_TO_SNAPSHOT" : "UNVERIFIED_RUN")));
            }
        }
        try (var writer = writer(output, "FINGERPRINTS.tsv")) {
            writer.write(Delimited.line("callableId", "path", "owner", "signature", "startLine", "startColumn",
                    "endLine", "endColumn", "factPatternSha256", "simHash64", "factCount", "sourceSha256", "freshness"));
            for (var fingerprint : patterns.fingerprints()) {
                var identity = fingerprint.identity();
                writer.write(Delimited.line(callableId(identity), identity.path(), identity.owner(), identity.signature(),
                        Integer.toString(identity.startLine()), Integer.toString(identity.startColumn()),
                        Integer.toString(identity.endLine()), Integer.toString(identity.endColumn()),
                        fingerprint.factPatternSha256(), String.format(java.util.Locale.ROOT, "%016x", fingerprint.simHash64()),
                        Integer.toString(fingerprint.factCount()), current.get(identity.path()).sha256(),
                        success ? "BOUND_TO_SNAPSHOT" : "UNVERIFIED_RUN"));
            }
        }
        try (var writer = writer(output, "CLUSTERS_NORMALIZED.tsv")) {
            writer.write(Delimited.line("kind", "leftCallableId", "rightCallableId", "hammingDistance", "interpretation", "freshness"));
            for (var candidate : patterns.candidates()) {
                writer.write(Delimited.line(candidate.kind().name(), callableId(candidate.left()), callableId(candidate.right()),
                        Integer.toString(candidate.hammingDistance()), "EXPORTED_FACT_PATTERN_REVIEW_ONLY_NOT_BODY_EQUIVALENCE",
                        success ? "BOUND_TO_SNAPSHOT" : "UNVERIFIED_RUN"));
            }
        }
        write(output, "STATUS.tsv", Delimited.line("stage", "status", "detail")
                + Delimited.line(context.get("command"), success ? "COMPLETED" : "FAILED", success
                ? "Evidence only; no source changes or semantic-equivalence certification" : failure));
        try (var writer = writer(output, "RUN_CONTEXT.tsv")) {
            writer.write(Delimited.line("key", "value"));
            for (var entry : context.entrySet()) writer.write(Delimited.line(entry.getKey(), entry.getValue()));
        }
        Files.copy(output.resolve("RUN_CONTEXT.tsv"), output.resolve("TASK_PACKET.tsv"));
        write(output, "PROVENANCE.tsv", Delimited.line("input", "identity", "interpretation")
                + Delimited.line("source-before", before == null ? "UNAVAILABLE" : before.contentHash(), "PATHS_AND_CONTENT_SHA256")
                + Delimited.line("source-after", after == null ? "UNAVAILABLE" : after.contentHash(), "PATHS_AND_CONTENT_SHA256")
                + Delimited.line("analyzed-snapshot", context.getOrDefault("analyzedSnapshotSha256", "NOT_USED"), "VERIFIED_INPUT_COPY_SHA256")
                + Delimited.line("query-pack", context.getOrDefault("queryInputSha256", "NOT_USED"), "DECLARED_QUERY_PACK_INPUTS")
                + Delimited.line("structural-evidence", evidence.structuralEvidenceHash(order), "EVIDENCE_STRUCTURE_NOT_LOGIC_EQUIVALENCE"));
        try (var writer = writer(output, "PROOF_RECEIPTS.tsv")) {
            writer.write(Delimited.line("sequence", "stage", "status", "exitCode", "elapsedMillis", "log", "arguments"));
            int sequence = 0;
            for (var receipt : receipts) {
                StringBuilder arguments = new StringBuilder();
                for (String argument : receipt.command()) {
                    if (!arguments.isEmpty()) arguments.append(" | ");
                    arguments.append(argument.length()).append(':').append(argument);
                }
                writer.write(Delimited.line(Integer.toString(++sequence), receipt.stage(), receipt.status(),
                        Integer.toString(receipt.exitCode()), Long.toString(receipt.elapsedMillis()),
                        output.relativize(receipt.log()).toString(), arguments.toString()));
            }
        }
        boolean unchanged = before != null && after != null && before.contentHash().equals(after.contentHash());
        write(output, "VERIFY_CONTRACT.tsv", Delimited.line("order", "gate", "status", "detail")
                + Delimited.line("1", "original-input-boundary", unchanged ? "PASS" : "NOT_VERIFIED", "Original source before/after content comparison only")
                + Delimited.line("2", "analyzed-copy-boundary", context.getOrDefault("analyzedCopyVerification", "NOT_APPLICABLE"), "Separately verified analysis-copy content before/after CodeQL")
                + Delimited.line("3", "evidence-schema", !context.get("command").equals("analyze") ? "NOT_APPLICABLE" : success ? "PASS" : "NOT_VERIFIED", "Source binding and ten-column CSV validation")
                + Delimited.line("4", "query-compile-evaluate", success && context.get("command").equals("analyze") ? "PASS" : "NOT_RUN_OR_FAILED", "See ordered process receipts")
                + Delimited.line("5", "target-application-tests", "NOT_RUN", "Application behavioral proof is outside evidence collection")
                + Delimited.line("6", "target-application-runtime", "NOT_RUN", "No application execution or source promotion"));
        write(output, "RESIDUE.tsv", Delimited.line("dimension", "state", "reason")
                + Delimited.line("logic-equivalence", "TYPED_EXCLUSION", "Evidence fingerprints and content equality do not prove equivalent behavior")
                + Delimited.line("complete-contract", "TYPED_EXCLUSION", "Values, all exceptions, ordering, concurrency, serialization, reflection and resource ownership require additional proof")
                + Delimited.line("transitive-effects", "TYPED_EXCLUSION", "Direct AST effects do not prove the absence of transitive or dynamic effects")
                + Delimited.line("non-java", "TYPED_EXCLUSION", "This adapter analyzes Java no-build databases; inventory covers every input file")
                + Delimited.line("fact-pattern-candidates", "CANDIDATE_ONLY", "Sorted exported kind/subject facts and SimHash omit method bodies; matching patterns require separate contract proof")
                + Delimited.line("candidate-search", patterns.truncated() ? "TRUNCATED" : "COMPLETE_WITHIN_PROFILE",
                        "Exact representative/member pairs and near-pattern representatives only; Hamming distance <=3; at most 1000000 comparisons and 10000 pairs")
                + Delimited.line("dependency-environment", "TYPED_EXCLUSION", "No-build extraction can resolve external dependencies; snapshots do not seal external repositories or the environment")
                + (success ? "" : Delimited.line("analysis-run", "FAILED", failure)));
        StringBuilder summary = new StringBuilder(Delimited.line("metric", "value"));
        summary.append(Delimited.line("source_files", Integer.toString(current.size())));
        summary.append(Delimited.line("evidence_rows", Integer.toString(order.length)));
        summary.append(Delimited.line("dictionary_entries", Integer.toString(evidence.dictionarySize())));
        summary.append(Delimited.line("structural_evidence_sha256", evidence.structuralEvidenceHash(order)));
        summary.append(Delimited.line("callable_fingerprints", Integer.toString(patterns.fingerprints().size())));
        summary.append(Delimited.line("fact_pattern_candidates", Integer.toString(patterns.candidates().size())));
        summary.append(Delimited.line("candidate_work", Long.toString(patterns.candidateWork())));
        summary.append(Delimited.line("candidate_search_truncated", Boolean.toString(patterns.truncated())));
        for (var entry : changes.entrySet()) summary.append(Delimited.line(entry.getKey(), Integer.toString(entry.getValue())));
        write(output, "SUMMARY.tsv", summary.toString());
        write(output, "FINAL_REPORT.md", "# M3 CodeQL execution\n\nStatus: **" + (success ? "COMPLETED" : "FAILED") + "**.\n\n"
                + "Command: `" + context.get("command") + "`. Inventoried files: " + current.size() + "; unique evidence rows: " + order.length + ".\n\n"
                + "Callable fact fingerprints: " + patterns.fingerprints().size() + "; review candidate pairs: " + patterns.candidates().size()
                + "; candidate search truncated: " + patterns.truncated() + ". Equal or similar exported facts do not establish equal method bodies.\n\n"
                + (success ? "" : "Failure: " + failure.replace("\n", " ") + "\n\n")
                + "## Files changed\n\nOnly the new output directory is written by this adapter. CodeQL analyzes a byte-verified input copy; the copy and original are checked again after analysis.\n\n"
                + "## Exact delta\n\n" + changes + ". Incremental labels are file-content comparisons, not cross-file semantic-cache validity.\n\n"
                + "## Verification executed\n\nThe source-content boundary comparison was " + (unchanged ? "equal" : "not verified")
                + ". Actual process exits and logs are in PROOF_RECEIPTS.tsv; application compile/tests/runtime were not executed.\n\n"
                + "## Limits and blockers\n\nRead RESIDUE.tsv. Exported evidence does not certify behavioral equivalence, whole-program effect completeness, or an immutable external dependency environment."
                + " No source mutation or promotion is performed. A failed run's evidence is explicitly unverified.\n\n"
                + "## Artifact paths\n\n" + String.join("\n", REQUIRED.stream().map(file -> "- `" + file + "`").toList()) + "\n");
        try (var writer = writer(output, "OUTPUT_CONTRACT.tsv")) {
            writer.write(Delimited.line("artifact", "status"));
            for (String artifact : REQUIRED) {
                writer.write(Delimited.line(artifact, artifact.equals("OUTPUT_CONTRACT.tsv") || Files.isRegularFile(output.resolve(artifact)) ? "PRESENT" : "MISSING"));
            }
        }
    }

    private static BufferedWriter writer(Path output, String name) throws IOException {
        return Files.newBufferedWriter(output.resolve(name), StandardCharsets.UTF_8);
    }

    private static String callableId(EvidenceFingerprints.Identity identity) {
        MessageDigest digest = Hashes.sha256();
        Hashes.field(digest, "m3-callable-evidence-id-v1");
        for (String field : List.of(identity.path(), identity.owner(), identity.signature(),
                Integer.toString(identity.startLine()), Integer.toString(identity.startColumn()),
                Integer.toString(identity.endLine()), Integer.toString(identity.endColumn()))) {
            Hashes.field(digest, field);
        }
        return Hashes.hex(digest.digest());
    }

    private static final class SymbolSummary {
        final TreeSet<String> packages = new TreeSet<>();
        int count;
    }

    private static void write(Path output, String name, String text) throws IOException {
        Files.writeString(output.resolve(name), text, StandardCharsets.UTF_8);
    }
}
