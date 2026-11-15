// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import java.io.IOException;
import java.io.PrintStream;

/** Entry point for read-only query inventory and opt-in CodeQL execution. */
public final class M3CodeQl {
    private M3CodeQl() {}

    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("help"))) { out.print(CliOptions.USAGE); return 0; }
        if (args.length == 1 && args[0].equals("--version")) { out.println("m3-codeql-tools 0.1.0"); return 0; }
        final CliOptions options;
        try { options = PathSafety.canonical(CliOptions.parse(args)); }
        catch (IllegalArgumentException | IOException e) { err.println(Tsv.escape(e.getMessage())); err.print(CliOptions.USAGE); return 2; }
        boolean interrupted = false;
        try (RunEvidence evidence = RunEvidence.open(options)) {
            String active = "prepare";
            try {
                if (options.command() == CliOptions.Command.INVENTORY) {
                    evidence.local("prepare", ProcessRunner.State.PASSED, "Fresh isolated evidence directory admitted.");
                    active = "inventory";
                    evidence.local(active, ProcessRunner.State.RUNNING, "Reading sorted .ql inputs.");
                    QueryInventory inventory = new QueryInventory();
                    var catalogue = inventory.scan(options);
                    inventory.write(catalogue, options.output());
                    boolean valid = catalogue.valid();
                    evidence.local(active, valid ? ProcessRunner.State.PASSED : ProcessRunner.State.FAILED,
                            catalogue.queries().size() + " .ql inputs; metadata validation " + (valid ? "passed." : "failed; inspect ACTION_QUEUE.tsv."));
                    evidence.finish(valid ? "PASSED" : "FAILED", valid ? 0 : 1);
                    out.println((valid ? "PASSED" : "FAILED") + ": inventory evidence at " + Tsv.escape(options.output()));
                    return valid ? 0 : 1;
                }
                CodeQlPlan plan = CodeQlPlan.create(options);
                evidence.plan(plan);
                if (!options.execute()) {
                    evidence.local("prepare", ProcessRunner.State.PASSED, "Inputs validated and command plan recorded; execution was not requested.");
                    evidence.finish("PLAN_ONLY", 0);
                    out.println("PLAN_ONLY: no CodeQL process ran. Evidence at " + Tsv.escape(options.output()));
                    return 0;
                }
                plan.snapshot();
                evidence.local("prepare", ProcessRunner.State.PASSED, "M3 and dependency snapshot hashes verified; execution workspace created without symbolic links.");
                ProcessRunner runner = new ProcessRunner();
                for (CodeQlPlan.Step step : plan.steps()) {
                    active = step.name();
                    evidence.local(active, ProcessRunner.State.RUNNING, "Process starting.");
                    out.println("Running " + active + "; logs in " + Tsv.escape(options.output().resolve("logs")));
                    ProcessRunner.Result result = runner.run(step, options.output(), options.timeout());
                    interrupted |= Thread.interrupted(); // Flush evidence before restoring an interrupt to the caller.
                    evidence.status(result);
                    if (result.state() != ProcessRunner.State.PASSED) {
                        int exit = switch (result.state()) {
                            case TIMED_OUT -> 124;
                            case INTERRUPTED -> 130;
                            case BLOCKED -> 3;
                            default -> 1;
                        };
                        evidence.finish(result.state().name(), exit);
                        err.println(result.state() + " at " + active + ": " + Tsv.escape(result.detail()));
                        return exit;
                    }
                }
                evidence.finish("PASSED", 0);
                out.println("PASSED: all declared CodeQL stages completed. Evidence at " + Tsv.escape(options.output()));
                return 0;
            } catch (IOException | RuntimeException e) {
                interrupted |= Thread.interrupted();
                evidence.local(active, ProcessRunner.State.FAILED, e.toString());
                evidence.finish("FAILED", 1);
                err.println("FAILED at " + active + ": " + Tsv.escape(e.getMessage()));
                return 1;
            }
        } catch (IOException | RuntimeException e) {
            err.println("Cannot record run evidence: " + Tsv.escape(e.getMessage()));
            return 1;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
