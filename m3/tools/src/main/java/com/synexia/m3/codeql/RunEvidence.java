// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns a fresh output directory and the advisory lock for this run's evidence. */
final class RunEvidence implements AutoCloseable {
    private final CliOptions options;
    private final FileChannel channel;
    private final FileLock lock;
    private final Map<String, ProcessRunner.Result> results = new LinkedHashMap<>();
    private List<CodeQlPlan.Step> steps = List.of();
    private String state = "RUNNING";
    private int exitCode;

    private RunEvidence(CliOptions options, FileChannel channel, FileLock lock) {
        this.options = options;
        this.channel = channel;
        this.lock = lock;
    }

    static RunEvidence open(CliOptions options) throws IOException {
        Files.createDirectories(options.output().getParent());
        Files.createDirectory(options.output()); // Atomic admission: no existing evidence is ever reused.
        FileChannel channel = FileChannel.open(options.output().resolve("RUN.lock"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        FileLock lock = channel.tryLock();
        if (lock == null) { channel.close(); throw new IOException("Output directory is locked."); }
        RunEvidence evidence = new RunEvidence(options, channel, lock);
        try { evidence.initialize(); }
        catch (IOException | RuntimeException e) { evidence.close(); throw e; }
        return evidence;
    }

    private void initialize() throws IOException {
        List<List<?>> context = new ArrayList<>(List.of(
                List.of("tool", "m3-codeql-tools/0.1.0"), List.of("started_utc", Instant.now()),
                List.of("command", options.command()), List.of("root", options.root()),
                List.of("output", options.output()), List.of("execute_requested", options.execute()),
                List.of("codeql_executable", options.codeql()), List.of("threads_requested", options.threads()),
                List.of("ram_mib_requested", options.ram()), List.of("ram_semantics", "CodeQL budget hint; may be raised by CodeQL; no OS memory cap"),
                List.of("stage_wall_timeout_seconds", options.timeout().toSeconds()), List.of("shards", options.shards()),
                List.of("shard_zero_based", options.shard()), List.of("java_version", System.getProperty("java.version")),
                List.of("os", System.getProperty("os.name") + " " + System.getProperty("os.arch")),
                List.of("provenance_scope", "Inventory hashes .ql files; verify/analyze hash copied M3 inputs, java/ql/lib, and shared dependency packs."),
                List.of("database_side_effects", "CodeQL analyze/query run may write results and caches into the caller-supplied database.")));
        if (options.database() != null) context.add(List.of("database", options.database()));
        Tsv.write(path("RUN_CONTEXT.tsv"), List.of("key", "value"), context);
        Tsv.write(path("PROVENANCE.tsv"), List.of("role", "path", "bytes", "sha256"), List.of());
        Tsv.write(path("PLAN.tsv"), List.of("ordinal", "stage", "argument_index", "argument", "working_directory"), List.of());
        Tsv.write(path("VERIFY_CONTRACT.tsv"), List.of("contract", "requirement"), List.of(
                List.of("EXECUTION", "verify/analyze are plan-only unless --execute; PLAN_ONLY is not QL qualification"),
                List.of("ORDER", "prepare inputs, version, format check, full QL compile, exact QL tests, optional analysis and exports; stop on first failure"),
                List.of("SOURCE", "Never rewrite source; execute against hashed M3 input copies with standard compilation caches disabled"),
                List.of("PROCESS", "Literal ProcessBuilder arguments, disk logs, closed stdin, per-stage wall timeout, process-tree termination on timeout/interruption"),
                List.of("RESOURCES", "Positive thread setting and RAM budget hint passed to CodeQL; limits are not OS resource isolation"),
                List.of("ARTIFACTS", "Analysis stages require zero exit and a nonempty regular expected output; failed-stage output is unqualified"),
                List.of("INVENTORY", "Sorted relative .ql paths, streaming SHA-256, header QLDoc metadata, global duplicate-ID validation before shard selection"),
                List.of("ESCAPING", "TSV v1: UTF-8, LF, schema_version column; backslash/tab/CR/LF escaped as double-backslash, backslash-t/r/n")));
        status(new ProcessRunner.Result("prepare", ProcessRunner.State.RUNNING, null, 0, "", "", "Preparing inputs."));
    }

    void plan(CodeQlPlan plan) throws IOException {
        steps = plan.steps();
        List<List<?>> rows = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            for (int j = 0; j < step.arguments().size(); j++) {
                rows.add(List.of(i + 1, step.name(), j, step.arguments().get(j), options.output()));
            }
            results.put(step.name(), new ProcessRunner.Result(step.name(), ProcessRunner.State.NOT_RUN, null, 0,
                    "", "", "No process has been started for this stage."));
        }
        Tsv.write(path("PLAN.tsv"), List.of("ordinal", "stage", "argument_index", "argument", "working_directory"), rows);
        plan.writeProvenance();
        refresh();
    }

    void status(ProcessRunner.Result result) throws IOException { results.put(result.stage(), result); refresh(); }

    void local(String stage, ProcessRunner.State result, String detail) throws IOException {
        status(new ProcessRunner.Result(stage, result, null, 0, "", "", detail));
    }

    void finish(String state, int exitCode) throws IOException {
        this.state = state;
        this.exitCode = exitCode;
        refresh();
    }

    private void refresh() throws IOException {
        List<List<?>> status = new ArrayList<>();
        for (var result : results.values()) status.add(List.of(result.stage(), result.state(),
                result.exitCode() == null ? "" : result.exitCode(), result.elapsedMillis(), result.stdout(), result.stderr(), result.detail()));
        Tsv.write(path("STATUS.tsv"), List.of("stage", "state", "exit_code", "elapsed_ms", "stdout", "stderr", "detail"), status);
        StringBuilder report = new StringBuilder("# M3 CodeQL run\n\n**State: " + state + "**\n\n");
        report.append("Command: ").append(options.command().name().toLowerCase(java.util.Locale.ROOT))
                .append(". Runner exit code: ").append(exitCode).append(".\n\n");
        if (state.equals("PLAN_ONLY")) report.append("No CodeQL process was run. This plan is not a passing QL verification.\n\n");
        if (options.command() == CliOptions.Command.INVENTORY) report.append("This is a lexical file inventory. No QL query was compiled or executed.\n\n");
        report.append("| Stage | State | Process exit | Elapsed ms |\n|---|---|---:|---:|\n");
        for (var result : results.values()) report.append("| ").append(result.stage()).append(" | ").append(result.state())
                .append(" | ").append(result.exitCode() == null ? "" : result.exitCode()).append(" | ")
                .append(result.elapsedMillis()).append(" |\n");
        report.append("\nPLAN.tsv preserves each argument separately; no shell command reconstruction is required.\n")
                .append("STATUS.tsv identifies attempted, failed, and unrun stages. Logs and partial outputs are retained.\n")
                .append("PROVENANCE.tsv identifies hashed M3 and dependency inputs copied into the execution workspace.\n")
                .append("OUTPUT_CONTRACT.tsv lists expected artifacts and their presence; presence alone does not qualify a failed stage.\n")
                .append("RAM is a CodeQL budget hint. Timeouts bound each external process stage, with additional bounded cleanup time.\n");
        Tsv.text(path("FINAL_REPORT.md"), report.toString());
        List<List<?>> outputs = new ArrayList<>();
        for (String file : List.of("RUN.lock", "RUN_CONTEXT.tsv", "PROVENANCE.tsv", "PLAN.tsv", "STATUS.tsv",
                "VERIFY_CONTRACT.tsv", "FINAL_REPORT.md", "OUTPUT_CONTRACT.tsv")) {
            outputs.add(outputRow(file, "runner", "ALWAYS"));
        }
        if (options.command() == CliOptions.Command.INVENTORY) for (String file : List.of("INVENTORY.tsv", "CLUSTERS_EXACT.tsv",
                "CLUSTERS_NORMALIZED.tsv", "ACTION_QUEUE.tsv", "SUMMARY.tsv")) outputs.add(outputRow(file, "inventory", "AFTER_INPUT_SCAN"));
        for (var step : steps) {
            outputs.add(outputRow("logs/" + step.name() + ".stdout.log", step.name(), "WHEN_ATTEMPTED"));
            outputs.add(outputRow("logs/" + step.name() + ".stderr.log", step.name(), "WHEN_ATTEMPTED"));
            for (Path output : step.outputs()) outputs.add(outputRow(PathSafety.relative(options.output(), output), step.name(), "WHEN_STAGE_PASSES"));
        }
        Tsv.write(path("OUTPUT_CONTRACT.tsv"), List.of("path", "producer", "required_when", "presence"), outputs);
    }

    private List<?> outputRow(String file, String producer, String when) {
        return List.of(file, producer, when, Files.isRegularFile(path(file)) ? "PRESENT" : "ABSENT");
    }

    private Path path(String name) { return options.output().resolve(name); }

    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
