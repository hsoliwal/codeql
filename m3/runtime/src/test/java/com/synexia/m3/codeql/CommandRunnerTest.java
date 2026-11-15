package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandRunnerTest {
    @TempDir Path temporary;

    @Test
    void codeqlJvmOptionFollowsTheSelectedSubcommand() {
        List<String> command = M3Codeql.command(Path.of("/tools/codeql"), 1400,
                "query", "run", "/pack with spaces/Inventory.ql", "--database=/db with spaces");
        assertEquals("query", command.get(1));
        assertEquals("run", command.get(2));
        assertEquals("-J-Xmx1400m", command.getLast());
        assertTrue(command.contains("/pack with spaces/Inventory.ql"));
        assertTrue(command.contains("--database=/db with spaces"));
    }

    private List<String> fixture(String mode) throws Exception {
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        Path java = Path.of(System.getProperty("java.home"), "bin", "java" + suffix);
        Path classes = Path.of(ProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return List.of(java.toString(), "-cp", classes.toString(), ProcessFixture.class.getName(), mode);
    }

    @Test
    void actualProcessOutputAndExitAreRecorded() throws Exception {
        var runner = new CommandRunner();
        Path log = temporary.resolve("success.log");
        runner.run("fixture-only", fixture("success"), temporary, log, Duration.ofSeconds(10));
        assertEquals("EXIT_ZERO", runner.receipts().getFirst().status());
        assertTrue(Files.readString(log).contains("process fixture output"));
    }

    @Test
    void nonzeroCannotBecomeSuccessfulReceipt() throws Exception {
        var runner = new CommandRunner();
        assertThrows(IOException.class, () -> runner.run("fixture-only", fixture("nonzero"), temporary,
                temporary.resolve("nonzero.log"), Duration.ofSeconds(10)));
        assertEquals("EXIT_NONZERO", runner.receipts().getFirst().status());
        assertEquals(7, runner.receipts().getFirst().exitCode());
    }

    @Test
    void timeoutIsBoundedAndRecorded() throws Exception {
        var runner = new CommandRunner();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(IOException.class,
                () -> runner.run("fixture-only", fixture("timeout"), temporary,
                        temporary.resolve("timeout.log"), Duration.ofMillis(150))));
        assertEquals("TIMED_OUT", runner.receipts().getFirst().status());
    }

    @Test
    void missingExecutableIsAStartFailure() {
        var runner = new CommandRunner();
        assertThrows(IOException.class, () -> runner.run("fixture-only", List.of(temporary.resolve("missing").toString()), temporary,
                temporary.resolve("missing.log"), Duration.ofSeconds(1)));
        assertEquals("START_FAILED", runner.receipts().getFirst().status());
    }
}
