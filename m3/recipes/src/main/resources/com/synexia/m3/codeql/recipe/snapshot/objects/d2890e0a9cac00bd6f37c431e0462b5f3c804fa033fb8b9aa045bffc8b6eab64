package com.synexia.m3.codeql;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Executes argument arrays without a shell and records failures, nonzero exits, and timeouts. */
final class CommandRunner {
    record Receipt(String stage, List<String> command, String status, int exitCode, long elapsedMillis, Path log) {}

    private final List<Receipt> receipts = new ArrayList<>();

    List<Receipt> receipts() { return List.copyOf(receipts); }

    void run(String stage, List<String> command, Path workingDirectory, Path log, Duration timeout) throws IOException {
        long started = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(command).directory(workingDirectory.toFile())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        } catch (IOException failure) {
            receipts.add(new Receipt(stage, List.copyOf(command), "START_FAILED", -1, elapsed(started), log));
            throw new IOException(stage + " could not start: " + failure.getMessage(), failure);
        }
        try {
            process.getOutputStream().close();
        } catch (IOException setupFailure) {
            stop(process);
            receipts.add(new Receipt(stage, List.copyOf(command), "SETUP_FAILED", -1, elapsed(started), log));
            throw new IOException(stage + " could not close process input", setupFailure);
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                receipts.add(new Receipt(stage, List.copyOf(command), "TIMED_OUT", -1, elapsed(started), log));
                throw new IOException(stage + " exceeded " + timeout.toSeconds() + " seconds; see " + log);
            }
            int exit = process.exitValue();
            receipts.add(new Receipt(stage, List.copyOf(command), exit == 0 ? "EXIT_ZERO" : "EXIT_NONZERO",
                    exit, elapsed(started), log));
            if (exit != 0) throw new IOException(stage + " exited " + exit + "; see " + log);
        } catch (InterruptedException interrupted) {
            stop(process);
            receipts.add(new Receipt(stage, List.copyOf(command), "INTERRUPTED", -1, elapsed(started), log));
            Thread.currentThread().interrupt();
            throw new IOException(stage + " interrupted", interrupted);
        }
    }

    private static long elapsed(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }

    private static void stop(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        for (int i = descendants.size() - 1; i >= 0; i--) descendants.get(i).destroyForcibly();
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
