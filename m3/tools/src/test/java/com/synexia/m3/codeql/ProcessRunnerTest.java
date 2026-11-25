// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class ProcessRunnerTest {
    @TempDir Path temp;

    @Test void argumentsRemainLiteralAndBothLogsAreOnDisk() throws Exception {
        String literal = "space ' quote \" dollar $(untouched) unicode ü\t\n";
        var result = new ProcessRunner().run(step("echo", literal), temp, Duration.ofSeconds(10));
        assertEquals(ProcessRunner.State.PASSED, result.state());
        assertTrue(Files.isRegularFile(temp.resolve(result.stdout())), "stdout log must exist");
        assertEquals(literal, Files.readString(temp.resolve(result.stdout())));
        assertEquals("error stream", Files.readString(temp.resolve(result.stderr())));
    }

    @Test void propagatesRealExitCodeAndPreservesFailureLogs() throws Exception {
        var result = new ProcessRunner().run(step("fail"), temp, Duration.ofSeconds(10));
        assertEquals(ProcessRunner.State.FAILED, result.state());
        assertEquals(7, result.exitCode());
        assertTrue(Files.readString(temp.resolve(result.stderr())).contains("deliberate failure"));
    }

    @Test @Timeout(15) void timeoutKillsChildTreeAndRetainsOutput() throws Exception {
        var result = new ProcessRunner().run(step("spawn"), temp, Duration.ofSeconds(2));
        assertEquals(ProcessRunner.State.TIMED_OUT, result.state());
        long child = Long.parseLong(Files.readString(temp.resolve(result.stdout())).strip());
        assertTrue(ProcessHandle.of(child).map(p -> !p.isAlive()).orElse(true), "child process must be terminated");
        assertTrue(result.elapsedMillis() < 7000, "wall timeout plus bounded cleanup");
    }

    @Test @Timeout(15) void interruptionKillsTreeAndPreservesInterruptFlag() throws Exception {
        AtomicReference<ProcessRunner.Result> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            try {
                result.set(new ProcessRunner().run(step("spawn"), temp, Duration.ofSeconds(30)));
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable e) { error.set(e); }
        });
        thread.start();
        Path log = temp.resolve("logs/process.stdout.log");
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while ((!Files.exists(log) || Files.size(log) == 0) && thread.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        thread.interrupt();
        thread.join(5000);
        assertFalse(thread.isAlive());
        assertNull(error.get());
        assertEquals(ProcessRunner.State.INTERRUPTED, result.get().state());
        assertTrue(interrupted.get());
        long child = Long.parseLong(Files.readString(log).strip());
        assertTrue(ProcessHandle.of(child).map(p -> !p.isAlive()).orElse(true));
    }

    @Test void successfulExitWithoutPromisedArtifactIsAFailure() throws Exception {
        CodeQlPlan.Step echo = step("echo", "ok");
        var step = new CodeQlPlan.Step(echo.name(), echo.arguments(), List.of(temp.resolve("missing.bqrs")));
        var result = new ProcessRunner().run(step, temp, Duration.ofSeconds(10));
        assertEquals(ProcessRunner.State.FAILED, result.state());
        assertEquals(0, result.exitCode());
        assertTrue(result.detail().contains("missing.bqrs"));
    }

    @Test @Timeout(15) @EnabledOnOs({OS.LINUX, OS.MAC})
    void jvmShutdownTerminatesTheActiveChildTree() throws Exception {
        Process runner = new ProcessBuilder(java(), "-XX:-UsePerfData", "-cp", System.getProperty("java.class.path"),
                RunnerProcess.class.getName(), temp.toString()).start();
        List<ProcessHandle> descendants = List.of();
        try {
            Path log = temp.resolve("logs/process.stdout.log");
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while ((!Files.exists(log) || Files.size(log) == 0) && System.nanoTime() < deadline) Thread.sleep(10);
            long child = Long.parseLong(Files.readString(log).strip());
            descendants = runner.descendants().toList();
            runner.destroy(); // SIGTERM invokes the Java shutdown hook; no process-group broadcast.
            assertTrue(runner.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(ProcessHandle.of(child).map(p -> !p.isAlive()).orElse(true), "shutdown must terminate CodeQL descendants");
        } finally {
            descendants.forEach(ProcessHandle::destroyForcibly);
            runner.destroyForcibly();
        }
    }

    static CodeQlPlan.Step step(String... args) {
        List<String> command = new ArrayList<>(List.of(java(), "-XX:-UsePerfData", "-cp",
                System.getProperty("java.class.path"), TestProcess.class.getName()));
        command.addAll(List.of(args));
        return new CodeQlPlan.Step("process", command, List.of());
    }

    static String java() { return Path.of(System.getProperty("java.home"), "bin", "java").toString(); }

    public static class TestProcess {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "echo" -> {
                    // Write UTF-8 explicitly: a redirected Windows stdout otherwise uses the ANSI code page.
                    System.out.write(args[1].getBytes(StandardCharsets.UTF_8));
                    System.out.flush();
                    System.err.print("error stream");
                }
                case "fail" -> { System.err.println("deliberate failure"); System.exit(7); }
                case "spawn" -> {
                    Process child = new ProcessBuilder(java(), "-XX:-UsePerfData", "-cp",
                            System.getProperty("java.class.path"), TestProcess.class.getName(), "sleep").start();
                    System.out.println(child.pid());
                    System.out.flush();
                    child.waitFor();
                }
                case "sleep" -> Thread.sleep(30_000);
                default -> throw new IllegalArgumentException("Unknown test mode");
            }
        }
    }

    public static class RunnerProcess {
        public static void main(String[] args) throws Exception {
            new ProcessRunner().run(step("spawn"), Path.of(args[0]), Duration.ofSeconds(30));
        }
    }
}
