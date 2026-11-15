package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M3CodeqlTest {
    @TempDir Path temporary;

    private int run(String... arguments) {
        var sink = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        return M3Codeql.run(arguments, sink, sink);
    }

    @Test
    void inventoryEmitsCompleteReportsAndCorrectIncrementalDeletion() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Files.writeString(source.resolve("changed.txt"), "old");
        Files.writeString(source.resolve("stable.txt"), "stable");
        Files.writeString(source.resolve("removed.txt"), "remove");
        Path first = temporary.resolve("first");
        assertEquals(0, run("inventory", "--source", source.toString(), "--output", first.toString(), "--workers", "3"));
        for (String artifact : Reports.REQUIRED) assertTrue(Files.isRegularFile(first.resolve(artifact)), artifact);
        assertTrue(Files.readString(first.resolve("TASK_PACKET.tsv")).contains("PUBLIC_API=LOCKED"));
        assertTrue(Files.readString(first.resolve("VERIFY_CONTRACT.tsv")).contains("target-application-tests\tNOT_RUN"));
        Files.writeString(source.resolve("changed.txt"), "new");
        Files.delete(source.resolve("removed.txt"));
        Files.writeString(source.resolve("added.txt"), "new file");
        Path second = temporary.resolve("second");
        assertEquals(0, run("inventory", "--source", source.toString(), "--output", second.toString(),
                "--previous", first.resolve("INVENTORY.tsv").toString()));
        List<String> rows = Files.readAllLines(second.resolve("INVENTORY.tsv"));
        assertTrue(rows.stream().anyMatch(line -> line.startsWith("added.txt\t") && line.endsWith("\tADDED")));
        assertTrue(rows.stream().anyMatch(line -> line.startsWith("changed.txt\t") && line.endsWith("\tCHANGED")));
        assertTrue(rows.stream().anyMatch(line -> line.startsWith("stable.txt\t") && line.endsWith("\tUNCHANGED")));
        assertTrue(rows.stream().anyMatch(line -> line.startsWith("removed.txt\t") && line.endsWith("\tDELETED")));
        assertFalse(Reports.previous(second.resolve("INVENTORY.tsv")).containsKey("removed.txt"));
    }

    @Test
    void missingCodeqlWritesFailureReceiptsAndDoesNotCertifyAnalysis() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Files.writeString(source.resolve("Example.java"), "class Example {}\n");
        String original = SourceSnapshot.capture(source, 1).contentHash();
        Path queries = Files.createDirectory(temporary.resolve("queries"));
        for (String query : List.of("Inventory", "Calls", "Effects")) Files.writeString(queries.resolve(query + ".ql"), "select 1");
        Path output = temporary.resolve("failed");
        assertEquals(1, run("analyze", "--source", source.toString(), "--output", output.toString(),
                "--queries", queries.toString(), "--search-path", queries.toString(), "--codeql", temporary.resolve("missing-codeql").toString()));
        for (String artifact : Reports.REQUIRED) assertTrue(Files.isRegularFile(output.resolve(artifact)), artifact);
        assertTrue(Files.readString(output.resolve("STATUS.tsv")).contains("FAILED"));
        assertFalse(Files.readString(output.resolve("STATUS.tsv")).contains("COMPLETED"));
        assertTrue(Files.readString(output.resolve("PROOF_RECEIPTS.tsv")).contains("START_FAILED"));
        assertEquals(original, SourceSnapshot.capture(source, 1).contentHash());
    }

    @Test
    void invalidOptionsAndExistingOrNestedOutputAreRejectedWithoutOverwrite() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        assertEquals(2, run("inventory", "--source", source.toString(), "--output", source.resolve("out").toString()));
        Path existing = Files.createDirectory(temporary.resolve("existing"));
        Files.writeString(existing.resolve("keep"), "untouched");
        assertEquals(2, run("inventory", "--source", source.toString(), "--output", existing.toString()));
        assertEquals("untouched", Files.readString(existing.resolve("keep")));
        assertEquals(2, run("inventory", "--source", source.toString(), "--output", temporary.resolve("x").toString(), "--workers", "0"));
        assertEquals(2, run("inventory", "--source", source.toString(), "--source", source.toString(), "--output", temporary.resolve("y").toString()));
        assertFalse(Files.exists(temporary.resolve("x")));
        assertFalse(Files.exists(temporary.resolve("y")));
    }

    @Test
    void malformedPreviousManifestFailsWithReceipts() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path prior = Files.writeString(temporary.resolve("bad.tsv"), "path\tsha256\nx\tno\n");
        Path output = temporary.resolve("failed");
        assertEquals(1, run("inventory", "--source", source.toString(), "--output", output.toString(), "--previous", prior.toString()));
        assertTrue(Files.readString(output.resolve("STATUS.tsv")).contains("FAILED"));
    }

    @Test
    void outputCannotOverlapQueryPackOrLocalLibraryInputs() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path repository = Files.createDirectory(temporary.resolve("repository"));
        Path queries = Files.createDirectories(repository.resolve("m3/ql/java"));
        Path library = Files.createDirectories(repository.resolve("java/ql/lib"));
        Path shared = Files.createDirectory(repository.resolve("shared"));
        for (Path forbidden : List.of(queries.resolve("output"), library.resolve("output"), shared.resolve("output"))) {
            assertEquals(2, run("analyze", "--source", source.toString(), "--output", forbidden.toString(),
                    "--queries", queries.toString(), "--search-path", repository.toString(),
                    "--codeql", temporary.resolve("unused-cli").toString()));
            assertFalse(Files.exists(forbidden));
        }
    }

    @Test
    void unchangedOriginalDoesNotCertifyChangedAnalysisCopy() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Files.writeString(source.resolve("Example.java"), "class Example {}\n");
        SourceSnapshot original = SourceSnapshot.capture(source, 1);
        Path output = Files.createDirectory(temporary.resolve("report"));
        Reports.write(output, Map.of("command", "analyze", "analyzedCopyVerification", "FAILED"), original, original,
                Map.of(), new EvidenceStore(100), List.of(), false, "Analyzed copy changed");
        String gates = Files.readString(output.resolve("VERIFY_CONTRACT.tsv"));
        assertTrue(gates.contains("original-input-boundary\tPASS"));
        assertTrue(gates.contains("analyzed-copy-boundary\tFAILED"));
        assertTrue(Files.readString(output.resolve("STATUS.tsv")).contains("FAILED"));
    }
}
