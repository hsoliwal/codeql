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
                terminate(process, descendants);
                state = State.TIMED_OUT;
                detail = "Wall-clock timeout after " + timeout.toMillis() + " ms; process tree termination requested.";
            } else {
                exitCode = process.exitValue();
                state = exitCode == 0 ? State.PASSED : State.FAILED;
                detail = "Process exit code " + exitCode + ".";
                if (descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                    terminate(process, descendants);
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
            if (process != null) terminate(process, descendants);
            state = State.INTERRUPTED;
            detail = "Interrupted; process tree termination requested.";
        } catch (IOException | RuntimeException e) {
            if (process != null) terminate(process, descendants);
            state = process == null ? State.BLOCKED : State.FAILED;
            detail = e.toString();
            Files.writeString(stderr, detail + "\n", StandardOpenOption.APPEND);
        } finally {
            if (shutdownHook != null) {
                try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                catch (IllegalStateException | SecurityException ignored) { /* Shutdown is already in progress. */ }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
        return new Result(step.name(), state, exitCode, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                PathSafety.relative(output, stdout), PathSafety.relative(output, stderr), detail);
    }

    private static void terminate(Process process, Set<ProcessHandle> known) {
        known.addAll(process.descendants().toList());
        List<ProcessHandle> children = new ArrayList<>(known);
        java.util.Collections.reverse(children);
        children.forEach(ProcessHandle::destroy);
        waitBriefly(children, 200);
        children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        waitBriefly(children, 500);
        process.destroy();
        try { process.waitFor(200, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.interrupted(); }
        if (process.isAlive()) {
            process.destroyForcibly();
            try { process.waitFor(500, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { Thread.interrupted(); }
        }
    }

    private static void waitBriefly(List<ProcessHandle> handles, long millis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (handles.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            try { Thread.sleep(10); }
            catch (InterruptedException e) { Thread.interrupted(); }
        }
    }
}
