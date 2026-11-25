// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class M3CodeQlTest {
    @TempDir Path temp;

    /**
     * The runner canonicalizes its root, output and database with {@code toRealPath()} before planning, so expected paths
     * must be built from the canonical temporary directory. A Windows runner's temp path contains an 8.3 short name
     * ({@code C:\Users\RUNNER~1\...}) and macOS links {@code /var} to {@code /private/var}.
     */
    @BeforeEach void useCanonicalTemporaryDirectory() throws IOException {
        temp = temp.toRealPath();
    }

    @Test void inventoryIsSortedStableAndUsesZeroBasedDisjointShards() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        write(root.resolve("z.ql"), "select 3\n");
        write(root.resolve("a.ql"), "select 1\n");
        write(root.resolve("m.ql"), "select 2\n");
        Path one = temp.resolve("one");
        Path two = temp.resolve("two");
        Path other = temp.resolve("other");
        assertEquals(0, invoke("inventory", root, one, "--shards", "2", "--shard", "0"));
        assertTrue(Files.isRegularFile(one.resolve("INVENTORY.tsv")), "inventory must be emitted");
        assertEquals(0, invoke("inventory", root, two, "--shards", "2", "--shard", "0"));
        assertEquals(0, invoke("inventory", root, other, "--shards", "2", "--shard", "1"));
        assertArrayEquals(Files.readAllBytes(one.resolve("INVENTORY.tsv")),
                Files.readAllBytes(two.resolve("INVENTORY.tsv")));
        List<Map<String, String>> rows = rows(one.resolve("INVENTORY.tsv"));
        assertEquals(List.of("a.ql", "m.ql", "z.ql"), rows.stream().map(r -> r.get("path")).toList());
        assertEquals(List.of("0", "1", "0"), rows.stream().map(r -> r.get("assigned_shard")).toList());
        assertEquals(List.of("true", "false", "true"), rows.stream().map(r -> r.get("selected")).toList());
        assertEquals(List.of("false", "true", "false"), rows(other.resolve("INVENTORY.tsv"))
                .stream().map(r -> r.get("selected")).toList());
        assertTrue(rows.stream().allMatch(r -> r.get("sha256").matches("[a-f0-9]{64}")));
        assertEquals("8903abffd68710879da59014708c6187f8e839fadc2bdf94ae73a036aa591623", rows.getFirst().get("sha256"));
        assertEquals("NOT_IMPLEMENTED", rows(one.resolve("CLUSTERS_NORMALIZED.tsv")).getFirst().get("state"));
    }

    @Test void metadataComesOnlyFromLeadingQldocAndNotStringsOrLaterComments() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        write(root.resolve("header.ql"), """
                /* ordinary license @id wrong/license */
                // @id wrong/line
                /**
                 * @name Actual query
                 * @kind table
                 * @id m3/actual
                 */
                import java
                /** @id wrong/later */
                select "@id wrong/string", "/* @kind problem */"
                """);
        write(root.resolve("decoy.ql"), "select \"/** @id wrong/string */\"\n");
        Path output = temp.resolve("result");
        assertEquals(0, invoke("inventory", root, output));
        List<Map<String, String>> rows = rows(output.resolve("INVENTORY.tsv"));
        assertEquals("", rows.get(0).get("id"));
        assertEquals("m3/actual", rows.get(1).get("id"));
        assertEquals("Actual query", rows.get(1).get("name"));
        assertEquals("table", rows.get(1).get("kind"));
    }

    @Test void hashesEveryByteAcrossMultipleReadBuffers() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        write(root.resolve("large.ql"), "/*" + "a".repeat(200_000) + "*/\nselect 1\n");
        Path output = temp.resolve("result");
        assertEquals(0, invoke("inventory", root, output));
        assertEquals("21fdaf4363429482c6431ae28ee247960bdc5717b41e076d2bd006a3033332d8",
                rows(output.resolve("INVENTORY.tsv")).getFirst().get("sha256"));
    }

    @Test void metadataContinuationsPreserveEveryTagAndNormalizeWhitespace() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        write(root.resolve("multiline.ql"), """
                /**
                 * @name Multiline
                 *       query name
                 * @description A first line
                 *              followed by another line.
                 *
                 * @tags reliability
                 *       readability
                 *       external/cwe/cwe-484
                 * @kind problem
                 * @id m3/multiline
                 */
                select "@tags string-decoy"
                """);
        Path output = temp.resolve("result");
        assertEquals(0, invoke("inventory", root, output));
        Map<String, String> row = rows(output.resolve("INVENTORY.tsv")).getFirst();
        assertEquals("reliability readability external/cwe/cwe-484", row.get("tags"));
        assertEquals("Multiline query name", row.get("name"));
        assertEquals("m3/multiline", row.get("id"));
        var catalogue = new QueryInventory().scan(CliOptions.parse(new String[]{"inventory", "--root", root.toString(),
                "--output", output.toString()}));
        assertEquals("A first line followed by another line.", catalogue.queries().getFirst().field("description"));
    }

    @Test void duplicateIdsFailAcrossShardsAndPreserveEvidence() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        String query = "/**\n * @id m3/duplicate\n */\nselect 1\n";
        write(root.resolve("a.ql"), query);
        write(root.resolve("b.ql"), query);
        Path output = temp.resolve("result");
        assertNotEquals(0, invoke("inventory", root, output, "--shards", "2", "--shard", "0"));
        assertEquals(2, rows(output.resolve("INVENTORY.tsv")).size());
        assertTrue(rows(output.resolve("ACTION_QUEUE.tsv")).stream()
                .anyMatch(r -> "DUPLICATE_ID".equals(r.get("action"))));
        assertEquals(2, rows(output.resolve("CLUSTERS_EXACT.tsv")).size());
        assertTrue(Files.readString(output.resolve("FINAL_REPORT.md")).contains("FAILED"));
        assertContracts(output);
    }

    @Test void duplicateMetadataKeysAreRejectedInsteadOfSilentlyChoosingOne() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        write(root.resolve("a.ql"), "/**\n * @id m3/first\n * @id m3/second\n */\nselect 1\n");
        Path output = temp.resolve("result");
        assertNotEquals(0, invoke("inventory", root, output));
        assertTrue(Files.readString(output.resolve("ACTION_QUEUE.tsv")).contains("INVALID_METADATA"));
    }

    @Test @EnabledOnOs({OS.LINUX, OS.MAC})
    void tsvPreservesUnicodeSpacesTabsNewlinesAndBackslashes() throws Exception {
        Path root = Files.createDirectory(temp.resolve("query root ü"));
        String name = "sp ace_é\tline\nback\\slash.ql";
        write(root.resolve(name), "select 1\n");
        Path output = temp.resolve("evidence space");
        assertEquals(0, invoke("inventory", root, output));
        List<String> lines = Files.readAllLines(output.resolve("INVENTORY.tsv"));
        assertEquals(2, lines.size(), "a path must not inject a physical TSV record");
        assertEquals(name, rows(output.resolve("INVENTORY.tsv")).getFirst().get("path"));
    }

    @Test void refusesSourceOverlapAndDoesNotCreateOutputInsideSource() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        Path output = root.resolve("evidence");
        assertNotEquals(0, invoke("inventory", root, output));
        assertFalse(Files.exists(output));
    }

    @Test @EnabledOnOs({OS.LINUX, OS.MAC})
    void refusesSymlinkEscapeAndRetainsFailureReport() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        Path outside = write(temp.resolve("outside.ql"), "select 1\n");
        Files.createSymbolicLink(root.resolve("linked.ql"), outside);
        Path output = temp.resolve("result");
        assertNotEquals(0, invoke("inventory", root, output));
        assertTrue(Files.readString(output.resolve("STATUS.tsv")).contains("FAILED"));
        assertContracts(output);
    }

    @Test @EnabledOnOs({OS.LINUX, OS.MAC})
    void refusesOutputOverlapThroughParentSymlink() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        Files.createSymbolicLink(temp.resolve("alias"), root);
        assertNotEquals(0, invoke("inventory", root, temp.resolve("alias/result")));
        assertFalse(Files.exists(root.resolve("result")));
    }

    @Test void neverOverwritesAnExistingEvidenceDirectory() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        write(root.resolve("a.ql"), "select 1\n");
        Path output = temp.resolve("result");
        assertEquals(0, invoke("inventory", root, output));
        byte[] before = Files.readAllBytes(output.resolve("RUN_CONTEXT.tsv"));
        assertNotEquals(0, invoke("inventory", root, output));
        assertArrayEquals(before, Files.readAllBytes(output.resolve("RUN_CONTEXT.tsv")));
    }

    @Test void verifyIsPlanOnlyByDefaultAndKeepsOrderedLiteralArguments() throws Exception {
        Path root = pack("checkout with spaces");
        Path output = temp.resolve("plan evidence");
        assertEquals(0, invoke("verify", root, output, "--codeql", "/missing/codeql"));
        assertTrue(Files.readString(output.resolve("FINAL_REPORT.md")).contains("PLAN_ONLY"));
        List<Map<String, String>> plan = rows(output.resolve("PLAN.tsv"));
        assertEquals(List.of("version", "format", "compile", "test"), plan.stream()
                .filter(r -> r.get("argument_index").equals("0")).map(r -> r.get("stage")).toList());
        List<String> format = args(plan, "format");
        assertTrue(format.contains("--check-only"));
        assertTrue(format.contains(output.resolve("work/m3/ql/src/Test.ql").toString()));
        assertFalse(Files.exists(output.resolve("work")), "plan mode must not stage copies or run processes");
        assertTrue(rows(output.resolve("STATUS.tsv")).stream()
                .filter(r -> !r.get("stage").equals("prepare")).allMatch(r -> r.get("state").equals("NOT_RUN")));
        assertContracts(output);
    }

    @Test void missingExecutableFailsFastWithPlanAndLogs() throws Exception {
        Path root = pack("checkout");
        Path output = temp.resolve("result");
        assertNotEquals(0, invoke("verify", root, output, "--codeql", temp.resolve("missing executable").toString(), "--execute"));
        List<Map<String, String>> status = rows(output.resolve("STATUS.tsv"));
        assertEquals("BLOCKED", status.stream().filter(r -> r.get("stage").equals("version")).findFirst().orElseThrow().get("state"));
        assertEquals("NOT_RUN", status.stream().filter(r -> r.get("stage").equals("format")).findFirst().orElseThrow().get("state"));
        assertTrue(Files.exists(output.resolve("logs/version.stderr.log")));
        assertTrue(Files.readString(output.resolve("logs/version.stderr.log")).contains("Cannot run program"));
        assertTrue(Files.exists(output.resolve("PLAN.tsv")));
        assertContracts(output);
    }

    @Test void realNonzeroProcessFailsFastWithoutTouchingSource() throws Exception {
        Path root = pack("checkout");
        Path source = root.resolve("m3/ql/src/Test.ql");
        byte[] before = Files.readAllBytes(source);
        Path output = temp.resolve("result");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        assertNotEquals(0, invoke("verify", root, output, "--codeql", java, "--execute"));
        List<Map<String, String>> status = rows(output.resolve("STATUS.tsv"));
        assertEquals("FAILED", status.stream().filter(r -> r.get("stage").equals("version")).findFirst().orElseThrow().get("state"));
        assertEquals("NOT_RUN", status.stream().filter(r -> r.get("stage").equals("format")).findFirst().orElseThrow().get("state"));
        assertArrayEquals(before, Files.readAllBytes(source));
        assertTrue(Files.isRegularFile(output.resolve("work/m3/ql/src/Test.ql")));
        assertTrue(Files.isRegularFile(output.resolve("work/codeql-workspace.yml")));
    }

    @Test void analyzePlanNamesOnlyTheOptInSuiteAndThreeTableExports() throws Exception {
        Path root = pack("checkout");
        write(root.resolve("m3/ql/src/m3-java.qls"), "- query: Test.ql\n");
        for (String name : List.of("CallableInventory", "CallDependencies", "NativeBoundaryInventory")) {
            write(root.resolve("m3/ql/src/inventory/" + name + ".ql"), "select 1\n");
        }
        Path database = Files.createDirectory(temp.resolve("database with spaces"));
        write(database.resolve("codeql-database.yml"), "primaryLanguage: java\n");
        Path output = temp.resolve("result");
        assertEquals(0, invoke("analyze", root, output, "--database", database.toString()));
        List<Map<String, String>> plan = rows(output.resolve("PLAN.tsv"));
        assertEquals(11, plan.stream().filter(r -> r.get("argument_index").equals("0")).count());
        assertTrue(args(plan, "analyze").contains("path:" + output.resolve("work/m3/ql/src/m3-java.qls")));
        assertTrue(args(plan, "analyze").contains("--format=sarifv2.1.0"));
        assertTrue(args(plan, "run-CallableInventory").contains("--database=" + database));
        assertTrue(args(plan, "decode-CallableInventory").contains("--format=csv"));
        assertFalse(Files.exists(output.resolve("m3-java.sarif")));
    }

    @Test void executionCopiesOnlyRequiredDependenciesAndRecordsTheirHashes() throws Exception {
        Path root = pack("checkout");
        write(root.resolve("java/ql/lib/qlpack.yml"), "name: codeql/java-all\n");
        write(root.resolve("java/ql/lib/java.qll"), "// library bytes\n");
        write(root.resolve("java/ql/test/unrelated.txt"), "not a library\n");
        write(root.resolve("shared/util/qlpack.yml"), "name: codeql/util\n");
        write(root.resolve("shared/util/Util.qll"), "// shared bytes\n");
        Path output = temp.resolve("result");
        assertNotEquals(0, invoke("verify", root, output, "--codeql", temp.resolve("missing").toString(), "--execute"));
        assertFalse(Files.isSymbolicLink(output.resolve("work/java")), "execution must work without symlink privileges");
        assertFalse(Files.isSymbolicLink(output.resolve("work/shared")));
        assertEquals("// library bytes\n", Files.readString(output.resolve("work/java/ql/lib/java.qll")));
        assertFalse(Files.exists(output.resolve("work/java/ql/test")), "do not duplicate upstream tests");
        assertTrue(rows(output.resolve("PROVENANCE.tsv")).stream().anyMatch(r ->
                r.get("role").equals("DEPENDENCY_INPUT") && r.get("path").equals("java/ql/lib/java.qll")
                        && r.get("sha256").matches("[0-9a-f]{64}")));
    }

    @Test void invalidOptionsAreRejectedBeforeCreatingOutput() throws Exception {
        Path root = Files.createDirectory(temp.resolve("queries"));
        for (List<String> extra : List.of(List.of("--threads", "0"), List.of("--shards", "2", "--shard", "2"),
                List.of("--ram", "1024"), List.of("--timeout-seconds", "0"), List.of("--unknown", "x"))) {
            Path output = temp.resolve("invalid" + extra.hashCode());
            assertNotEquals(0, invoke("inventory", root, output, extra.toArray(String[]::new)));
            assertFalse(Files.exists(output));
        }
    }

    Path pack(String name) throws IOException {
        Path root = Files.createDirectory(temp.resolve(name));
        write(root.resolve("m3/ql/src/qlpack.yml"), "name: test/queries\nversion: 0.0.0\n");
        write(root.resolve("m3/ql/src/Test.ql"), "select 1\n");
        write(root.resolve("m3/ql/test/qlpack.yml"), "name: test/tests\nversion: 0.0.0\n");
        write(root.resolve("m3/ql/test/Test.qlref"), "Test.ql\n");
        return root;
    }

    int invoke(String command, Path root, Path output, String... extra) {
        List<String> args = new ArrayList<>(List.of(command, "--root", root.toString(), "--output", output.toString()));
        args.addAll(List.of(extra));
        return M3CodeQl.run(args.toArray(String[]::new), new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
    }

    static Path write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    static void assertContracts(Path output) {
        for (String name : List.of("STATUS.tsv", "FINAL_REPORT.md", "RUN_CONTEXT.tsv", "PROVENANCE.tsv", "VERIFY_CONTRACT.tsv", "OUTPUT_CONTRACT.tsv")) {
            assertTrue(Files.isRegularFile(output.resolve(name)), name);
        }
    }

    static List<String> args(List<Map<String, String>> plan, String stage) {
        return plan.stream().filter(r -> r.get("stage").equals(stage)).map(r -> r.get("argument")).toList();
    }

    static List<Map<String, String>> rows(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String[] keys = lines.getFirst().split("\t", -1);
        List<Map<String, String>> result = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.split("\t", -1);
            assertEquals(keys.length, values.length, "TSV column count");
            result.add(java.util.stream.IntStream.range(0, keys.length).boxed()
                    .collect(Collectors.toMap(i -> keys[i], i -> unescape(values[i]))));
        }
        return result;
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') { result.append(c); continue; }
            char escaped = value.charAt(++i);
            result.append(switch (escaped) {
                case 't' -> '\t'; case 'n' -> '\n'; case 'r' -> '\r'; case '\\' -> '\\';
                default -> throw new AssertionError("Unknown TSV escape: " + escaped);
            });
        }
        return result.toString();
    }
}
