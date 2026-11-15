package com.synexia.m3.codeql;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Java 21 entry point for additive, read-only M3 inventory and CodeQL evidence collection. */
public final class M3Codeql {
    private static final String CANON_SHA256 = "5ec31d51207bda02d2f1f9a7fcd940f2ae5e1bfc9f0ef9358139fe62b47f393b";

    private M3Codeql() {}

    public static void main(String[] arguments) {
        int status = run(arguments, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    /** Returns 0 for completed evidence collection, 1 for a run failure, and 2 for invalid input. */
    public static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (arguments.length == 0 || (arguments.length == 1 && arguments[0].equals("--help"))) {
            usage(output);
            return arguments.length == 0 ? 2 : 0;
        }
        Options options;
        Path source;
        Path destination;
        try {
            options = Options.parse(arguments);
            source = PathGuard.directory(options.source());
            if (options.command().equals("analyze")) {
                Path queries = PathGuard.directory(options.queries());
                Path search = options.searchPath() == null ? findSearchPath(queries) : PathGuard.directory(options.searchPath());
                PathGuard.requireDisjointOutput(options.output(), queries);
                for (String local : List.of("java/ql/lib", "shared")) {
                    Path dependency = search.resolve(local);
                    if (Files.isDirectory(dependency, LinkOption.NOFOLLOW_LINKS)) {
                        PathGuard.requireDisjointOutput(options.output(), PathGuard.directory(dependency));
                    }
                }
            }
            destination = PathGuard.createOutput(options.output(), source);
        } catch (IOException | IllegalArgumentException invalid) {
            error.println("Invalid M3 input: " + invalid.getMessage());
            return 2;
        }
        Map<String, String> context = context(options, source, destination);
        SourceSnapshot before = null;
        SourceSnapshot after = null;
        Map<String, SourceSnapshot.FileRecord> previous = Map.of();
        EvidenceStore evidence = new EvidenceStore(options.maxRows());
        CommandRunner commands = new CommandRunner();
        boolean success = false;
        String failure = "";
        try {
            before = SourceSnapshot.capture(source, options.workers());
            context.put("sourceInputSha256", before.contentHash());
            if (options.previous() != null) previous = Reports.previous(options.previous());
            if (options.command().equals("analyze")) analyze(options, destination, before, evidence, commands, context);
            after = SourceSnapshot.capture(source, options.workers());
            before.requireSameContent(after);
            success = true;
        } catch (IOException | RuntimeException problem) {
            failure = problem.getMessage() == null ? problem.getClass().getSimpleName() : problem.getMessage();
        }
        if (before != null && after == null) {
            try {
                after = SourceSnapshot.capture(source, options.workers());
                before.requireSameContent(after);
            } catch (IOException | RuntimeException changed) {
                failure += "; final source check: " + changed.getMessage();
            }
        }
        try {
            Reports.write(destination, context, before, after, previous, evidence, commands.receipts(), success, failure);
        } catch (IOException | RuntimeException reportFailure) {
            error.println("Could not finish run receipts in " + destination + ": " + reportFailure.getMessage());
            return 1;
        }
        if (!success) {
            error.println("M3 run failed: " + failure + ". Receipts: " + destination);
            return 1;
        }
        output.println("M3 " + options.command() + " completed: " + destination);
        output.println("Source SHA-256: " + before.contentHash() + "; evidence rows: " + evidence.sortedUniqueIndices().length);
        output.println("Evidence collection does not certify behavioral equivalence or promote source changes.");
        return 0;
    }

    private static void analyze(Options options, Path output, SourceSnapshot source, EvidenceStore evidence,
                                CommandRunner runner, Map<String, String> context) throws IOException {
        Path queries = PathGuard.directory(options.queries());
        List<String> names = new ArrayList<>(List.of("Inventory", "Calls", "Effects"));
        if (Files.isRegularFile(queries.resolve("ControlFlow.ql"), LinkOption.NOFOLLOW_LINKS)) names.add("ControlFlow");
        for (String name : names) {
            Path query = queries.resolve(name + ".ql");
            PathGuard.noSymlinkComponents(query);
            if (!Files.isRegularFile(query, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing query: " + query);
        }
        SourceSnapshot queryInputs = SourceSnapshot.captureQueryInputs(queries, options.workers());
        context.put("queryInputSha256", queryInputs.contentHash());
        Path searchPath = options.searchPath() == null ? findSearchPath(queries) : PathGuard.directory(options.searchPath());
        context.put("querySearchPath", searchPath.toString());
        Map<String, SourceSnapshot> localLibraries = new LinkedHashMap<>();
        for (String local : List.of("java/ql/lib", "shared")) {
            Path candidate = searchPath.resolve(local);
            if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                SourceSnapshot library = SourceSnapshot.captureQueryInputs(candidate, options.workers());
                localLibraries.put(local, library);
                context.put("localLibrarySha256." + local, library.contentHash());
            }
        }
        context.put("querySnapshotExclusions", "Directories named .cache only; source snapshots have no exclusions");
        context.put("dependencyClosure", localLibraries.size() == 2 ? "LOCAL_JAVA_AND_SHARED_ROOTS_RECORDED;EXTERNAL_RESIDUE" : "UNRESOLVED_EXTERNAL_PACK_CLOSURE");
        Path executable = options.codeql().toAbsolutePath().normalize();
        context.put("codeqlExecutable", executable.toString());
        SourceSnapshot sealed = source.materialize(output.resolve("source-snapshot"), options.workers());
        context.put("analyzedSnapshotSha256", sealed.contentHash());
        context.put("analyzedCopyVerification", "NOT_VERIFIED");
        Files.createDirectory(output.resolve("logs"));
        Files.createDirectory(output.resolve("results"));
        Duration timeout = Duration.ofSeconds(options.timeoutSeconds());
        Path versionLog = output.resolve("logs/version.log");
        runner.run("codeql-version", command(executable, options.codeqlHeapMb(), "version", "--format=terse"), output, versionLog, timeout);
        context.put("codeqlVersion", Files.readString(versionLog, StandardCharsets.UTF_8).strip());
        Path database = output.resolve("database");
        runner.run("database-create", command(executable, options.codeqlHeapMb(), "database", "create", database.toString(),
                "--language=java", "--build-mode=none", "--source-root=" + sealed.root(), "--threads=" + options.workers(),
                "--ram=" + options.ramMb()), output, output.resolve("logs/database-create.log"), timeout);
        if (!Files.isDirectory(database, LinkOption.NOFOLLOW_LINKS)) throw new IOException("CodeQL did not create a database");
        EvidenceReader reader = new EvidenceReader(sealed, evidence);
        for (String name : names) {
            Path bqrs = output.resolve("results/" + name + ".bqrs");
            Path csv = output.resolve("results/" + name + ".csv");
            runner.run("query-" + name, command(executable, options.codeqlHeapMb(), "query", "run", queries.resolve(name + ".ql").toString(),
                    "--database=" + database, "--output=" + bqrs, "--threads=" + options.workers(),
                    "--ram=" + options.ramMb(), "--warnings=error", "--search-path=" + searchPath),
                    output, output.resolve("logs/query-" + name + ".log"), timeout);
            runner.run("decode-" + name, command(executable, options.codeqlHeapMb(), "bqrs", "decode", bqrs.toString(),
                    "--result-set=#select", "--format=csv", "--output=" + csv), output, output.resolve("logs/decode-" + name + ".log"), timeout);
            reader.read(csv);
        }
        SourceSnapshot queryAfter = SourceSnapshot.captureQueryInputs(queries, options.workers());
        context.put("queryInputAfterSha256", queryAfter.contentHash());
        queryInputs.requireSameContent(queryAfter);
        for (var library : localLibraries.entrySet()) {
            library.getValue().requireSameContent(SourceSnapshot.captureQueryInputs(library.getValue().root(), options.workers()));
        }
        try {
            sealed.requireSameContent(SourceSnapshot.capture(sealed.root(), options.workers()));
            context.put("analyzedCopyVerification", "PASS");
        } catch (IOException | RuntimeException changed) {
            context.put("analyzedCopyVerification", "FAILED");
            throw changed;
        }
    }

    static List<String> command(Path executable, int heapMb, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(List.of(arguments));
        // CodeQL accepts JVM switches on the selected command, not before its subcommand.
        command.add("-J-Xmx" + heapMb + "m");
        return List.copyOf(command);
    }

    private static Path findSearchPath(Path queries) throws IOException {
        for (Path ancestor = queries; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.isRegularFile(ancestor.resolve("java/ql/lib/qlpack.yml"))) return ancestor;
        }
        throw new IOException("Cannot locate the CodeQL repository; supply --search-path with the dependency pack root");
    }

    private static Map<String, String> context(Options options, Path source, Path output) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("task_packet", "active");
        context.put("source_doc", "M3Scale v3.0 locked canon; sha256=" + CANON_SHA256);
        context.put("invariant_id", "M3-CODEQL-EVIDENCE-1");
        context.put("run.mode", "dry-run");
        context.put("run.profile", options.command().equals("inventory") ? "inventory" : "java-semantic-evidence");
        context.put("run.workers", Integer.toString(options.workers()));
        context.put("run.shard_count", "1");
        context.put("run.shard_index", "0");
        context.put("targets.files", source.toString());
        context.put("invariant_locks", "PUBLIC_API=LOCKED;EXTERNAL_BEHAVIOR=LOCKED;DISABLED_PATHS=NO_EDITS;EXCLUDED_PATHS=NO_EDITS;BUILD_SCRIPTS=LOCKED");
        context.put("allowed_delta", "New output reports, verified input copy and CodeQL database only");
        context.put("forbidden", "Source mutation; source promotion; semantic-equivalence claims from hashes");
        context.put("verify_order", "snapshot;process receipts;schema/source binding;final snapshot comparison;output contract");
        context.put("transformation_verify_order", "diff;lint;compile;tests;runtime (not invoked: no transformation)");
        context.put("output_contract", String.join(",", Reports.REQUIRED));
        context.put("stop_conditions", "Invalid scope/schema;source/query mutation;tool failure;timeout;capacity limit;missing output");
        context.put("command", options.command());
        context.put("output", output.toString());
        context.put("timeoutSeconds", Integer.toString(options.timeoutSeconds()));
        context.put("codeqlRamMb", Integer.toString(options.ramMb()));
        context.put("codeqlHeapMb", Integer.toString(options.codeqlHeapMb()));
        context.put("maxEvidenceRows", Integer.toString(options.maxRows()));
        context.put("javaVersion", Runtime.version().toString());
        return context;
    }

    private static void usage(PrintStream output) {
        output.println("M3 CodeQL (Java 21)\n"
                + "  inventory --source DIR --output NEW_DIR [--previous INVENTORY.tsv] [--workers 1..64]\n"
                + "  analyze --source DIR --output NEW_DIR --codeql EXEC --queries PACK_DIR\n"
                + "          [--search-path PACK_ROOT] [--previous INVENTORY.tsv] [--workers 1..64]\n"
                + "          [--timeout-seconds 900] [--ram-mb 3072] [--codeql-heap-mb 1400]\n"
                + "          [--max-evidence-rows 1000000]\n"
                + "Output must be new and outside source. Source symlinks are rejected.\n"
                + "Incremental inventory does not skip fresh whole-source CodeQL analysis.");
    }

    private record Options(String command, Path source, Path output, Path previous, Path codeql, Path queries,
                           Path searchPath, int workers, int timeoutSeconds, int ramMb, int codeqlHeapMb, int maxRows) {
        static Options parse(String[] arguments) {
            String command = arguments[0];
            if (!command.equals("inventory") && !command.equals("analyze")) throw new IllegalArgumentException("Expected inventory or analyze");
            Set<String> allowed = Set.of("--source", "--output", "--previous", "--codeql", "--queries", "--search-path",
                    "--workers", "--timeout-seconds", "--ram-mb", "--codeql-heap-mb", "--max-evidence-rows");
            Map<String, String> options = new LinkedHashMap<>();
            for (int i = 1; i < arguments.length; i += 2) {
                if (!allowed.contains(arguments[i]) || i + 1 == arguments.length || arguments[i + 1].isBlank()
                        || options.putIfAbsent(arguments[i], arguments[i + 1]) != null) {
                    throw new IllegalArgumentException("Unknown, missing, or duplicate option: " + arguments[i]);
                }
            }
            Path source = required(options, "--source");
            Path output = required(options, "--output");
            Path codeql = command.equals("analyze") ? required(options, "--codeql") : optional(options, "--codeql");
            Path queries = command.equals("analyze") ? required(options, "--queries") : optional(options, "--queries");
            if (command.equals("inventory") && (codeql != null || queries != null || options.containsKey("--search-path"))) {
                throw new IllegalArgumentException("CodeQL options require the analyze command");
            }
            return new Options(command, source, output, optional(options, "--previous"), codeql, queries,
                    optional(options, "--search-path"), integer(options, "--workers", Math.min(4, Runtime.getRuntime().availableProcessors()), 1, 64),
                    integer(options, "--timeout-seconds", 900, 1, 86400), integer(options, "--ram-mb", 3072, 512, 65536),
                    integer(options, "--codeql-heap-mb", 1400, 512, 65536),
                    integer(options, "--max-evidence-rows", 1_000_000, 1, 5_000_000));
        }

        private static int integer(Map<String, String> options, String key, int fallback, int minimum, int maximum) {
            String text = options.get(key);
            if (text == null) return fallback;
            int value;
            try { value = Integer.parseInt(text); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid integer for " + key, invalid); }
            if (value < minimum || value > maximum) throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
            return value;
        }

        private static Path required(Map<String, String> options, String key) {
            Path path = optional(options, key);
            if (path == null) throw new IllegalArgumentException("Required option: " + key);
            return path;
        }

        private static Path optional(Map<String, String> options, String key) {
            return options.containsKey(key) ? Path.of(options.get(key)) : null;
        }
    }
}
