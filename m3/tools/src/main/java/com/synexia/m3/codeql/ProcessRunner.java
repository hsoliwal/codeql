// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

final class ProcessRunner {
    private static final long DESCENDANT_EXIT_GRACE_MILLIS = 1000;

    /**
     * On Windows the JDK joins the argument list into one command line. Its default ("ambiguous") mode wraps arguments
     * that contain spaces in quotes but does not escape embedded double quotes, so the child splits such an argument.
     * The JDK's strict mode escapes embedded double quotes and the backslashes before them by the Windows argument
     * rules. It does not preserve an argument that already starts and ends with a double quote (that argument is passed
     * through as written, so the child receives it without the outer quotes) and it rejects such an argument if it also
     * contains a double quote inside. Plan arguments are flags and native paths, which never take that form. The
     * property is read on every {@code ProcessBuilder.start()}, so enabling it before the first launch is sufficient; an
     * explicit operator setting is respected.
     */
    private static final String STRICT_WINDOWS_ARGUMENTS = "jdk.lang.Process.allowAmbiguousCommands";

    static {
        if (System.getProperty("os.name", "").startsWith("Windows") && System.getProperty(STRICT_WINDOWS_ARGUMENTS) == null) {
            System.setProperty(STRICT_WINDOWS_ARGUMENTS, "false");
        }
    }

    enum State { NOT_RUN, RUNNING, PASSED, FAILED, BLOCKED, TIMED_OUT, INTERRUPTED }
    record Result(String stage, State state, Integer exitCode, long elapsedMillis,
                  String stdout, String stderr, String detail) {}

    Result run(CodeQlPlan.Step step, Path output, Duration timeout) throws IOException {
        Path logs = output.resolve("logs");
        Files.createDirectories(logs);
        Path stdout = Files.createFile(logs.resolve(step.name() + ".stdout.log"));
        Path stderr = Files.createFile(logs.resolve(step.name() + ".stderr.log"));
        long start = System.nanoTime();
        Process process = null;
        Thread shutdownHook = null;
        State state;
        Integer exitCode = null;
        String detail;
        boolean interrupted = false;
        // An interrupt consumed by a bounded cleanup wait is restored before returning, on every path.
        boolean deferredInterrupt = false;
        Set<ProcessHandle> descendants = new CopyOnWriteArraySet<>();
        try {
            for (Path artifact : step.outputs()) {
                if (Files.exists(artifact, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Refusing existing stage output: " + artifact);
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted before process start");
            process = new ProcessBuilder(step.arguments()).directory(output.toFile())
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
            Process owned = process;
            shutdownHook = new Thread(() -> terminate(owned, descendants), "m3-codeql-process-cleanup");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            process.getOutputStream().close();
            long deadline = start + timeout.toNanos();
            boolean complete = false;
            while (System.nanoTime() < deadline) {
                descendants.addAll(process.descendants().toList());
                long remaining = Math.max(1, deadline - System.nanoTime());
                if (process.waitFor(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS)) {
                    complete = true;
                    break;
                }
            }
            if (!complete) {
                deferredInterrupt |= terminate(process, descendants);
                state = State.TIMED_OUT;
                detail = "Wall-clock timeout after " + timeout.toMillis() + " ms; process tree termination requested.";
            } else {
                exitCode = process.exitValue();
                deferredInterrupt |= awaitProcessObject(process);
                state = exitCode == 0 ? State.PASSED : State.FAILED;
                detail = "Process exit code " + exitCode + ".";
                // A descendant still tearing down (on Windows, the console host of a console child) gets a bounded
                // grace period to exit on its own; a descendant that outlives it is a leaked process tree. The wait
                // returns at once when no descendant is alive.
                deferredInterrupt |= waitBriefly(new ArrayList<>(descendants), DESCENDANT_EXIT_GRACE_MILLIS);
                if (descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                    deferredInterrupt |= terminate(process, descendants);
                    state = State.FAILED;
                    detail = "Process exited with live descendants; process tree termination requested.";
                }
                if (state == State.PASSED) for (Path artifact : step.outputs()) {
                    if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS) || Files.size(artifact) == 0) {
                        state = State.FAILED;
                        detail = "Missing or empty expected output: " + artifact;
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.interrupted();
            if (process != null) deferredInterrupt |= terminate(process, descendants);
            state = State.INTERRUPTED;
            detail = "Interrupted; process tree termination requested.";
        } catch (IOException | RuntimeException e) {
            if (process != null) deferredInterrupt |= terminate(process, descendants);
            state = process == null ? State.BLOCKED : State.FAILED;
            detail = e.toString();
            Files.writeString(stderr, detail + "\n", StandardOpenOption.APPEND);
        } finally {
            if (shutdownHook != null) {
                try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                catch (IllegalStateException | SecurityException ignored) { /* Shutdown is already in progress. */ }
            }
            if (interrupted || deferredInterrupt) Thread.currentThread().interrupt();
        }
        return new Result(step.name(), state, exitCode, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                PathSafety.relative(output, stdout), PathSafety.relative(output, stderr), detail);
    }

    /** Terminates the process tree; returns whether an interrupt arrived during the bounded waits. */
    private static boolean terminate(Process process, Set<ProcessHandle> known) {
        boolean interrupted = false;
        known.addAll(process.descendants().toList());
        List<ProcessHandle> children = new ArrayList<>(known);
        java.util.Collections.reverse(children);
        children.forEach(ProcessHandle::destroy);
        interrupted |= waitBriefly(children, 200);
        children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        interrupted |= waitBriefly(children, 500);
        process.destroy();
        try { process.waitFor(200, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { interrupted = true; }
        if (process.isAlive()) {
            process.destroyForcibly();
            try { process.waitFor(500, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { interrupted = true; }
        }
        interrupted |= awaitProcessObject(process);
        // A child started while the tree was being terminated was not in the earlier snapshot; sweep once more
        // after the root can no longer start processes.
        List<ProcessHandle> late = process.descendants().filter(ProcessHandle::isAlive).toList();
        late.forEach(ProcessHandle::destroyForcibly);
        known.addAll(late);
        interrupted |= waitBriefly(late, 500);
        return interrupted;
    }

    /**
     * Once the exit code is set, waits for the process object itself. On Windows the exit code is set when termination
     * starts, but the process keeps its handles, including the inherited step-log handles, until its process object is
     * signalled. {@code isAlive()}, {@code onExit()} and the timed {@code waitFor} all return as soon as the exit code is
     * set; only the untimed {@link Process#waitFor()} waits for the signal. It is called only after the exit code is set,
     * so the wait covers just the remaining teardown. Returns whether an interrupt had to be deferred.
     */
    private static boolean awaitProcessObject(Process process) {
        if (process.isAlive()) return false;
        boolean interrupted = Thread.interrupted();
        while (true) {
            try {
                process.waitFor();
                return interrupted;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
    }

    /**
     * Polls until no handle is alive or the deadline passes; returns at once if none is alive. This is a bounded best
     * effort for descendants: on Windows {@code isAlive()} turns false once the exit code is set, which can be before
     * the process has closed its handles. Returns whether an interrupt arrived while waiting, for the caller to restore.
     */
    private static boolean waitBriefly(List<ProcessHandle> handles, long millis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        boolean interrupted = false;
        while (handles.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            try { Thread.sleep(10); }
            catch (InterruptedException e) { interrupted = true; }
        }
        return interrupted;
    }
}
