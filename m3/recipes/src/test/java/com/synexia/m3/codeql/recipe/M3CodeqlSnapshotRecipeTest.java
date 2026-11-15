// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.text.PlainText;

class M3CodeqlSnapshotRecipeTest {
    private static final String JAVA_PATH = "m3/runtime/src/main/java/Example.java";
    private static final String JAVA_TEXT = "class Example { String text = \"fake } ; //\"; }\r\n";
    private static final String QUERY_PATH = "m3/ql/java/Inventory.ql";
    private static final String QUERY_TEXT = "// Unicode: λ, हिन्दी\nimport java\nselect 1\n";

    @TempDir Path temporary;

    @Test
    void actualOpenRewriteRunGeneratesReviewedPostimagesAndPreservesUnrelatedSources() throws IOException {
        Snapshot snapshot = fixture();
        SourceFile unrelated = source("java/ql/lib/Keep.qll", "// upstream unchanged\n");
        List<Result> changes = SnapshotTool.execute(new M3CodeqlSnapshotRecipe(snapshot), List.of(unrelated));
        assertEquals(2, changes.size());
        Map<String, String> actual = new TreeMap<>();
        for (Result change : changes) {
            assertNull(change.getBefore(), "Additive recipes cannot edit preexisting files");
            SourceFile after = change.getAfter();
            assertNotNull(after);
            actual.put(path(after), after.printAll());
        }
        assertEquals(Map.of(JAVA_PATH, JAVA_TEXT, QUERY_PATH, QUERY_TEXT), actual);
        assertEquals("// upstream unchanged\n", unrelated.printAll());
    }

    @Test
    void actualOpenRewriteRunReachesAZeroChangeFixedPoint() throws IOException {
        Snapshot snapshot = fixture();
        List<SourceFile> generated = new ArrayList<>();
        for (Result result : SnapshotTool.execute(new M3CodeqlSnapshotRecipe(snapshot), List.of())) {
            generated.add(result.getAfter());
        }
        assertTrue(SnapshotTool.execute(new M3CodeqlSnapshotRecipe(snapshot), generated).isEmpty());
    }

    @Test
    void emptyGeneratedExpectedFileIsRetainedThroughRealReplay() throws IOException {
        String path = "m3/ql/java-tests/evidence/ContractAssertions.expected";
        Snapshot emptyFile = new Snapshot(List.of(entry(path, "")));
        Path resources = temporary.resolve("empty-snapshot");
        SnapshotTool.writeSnapshot(emptyFile, resources);
        Snapshot reloaded = Snapshot.read(resources);
        Path target = Files.createDirectory(temporary.resolve("empty-target"));
        assertEquals(1, SnapshotTool.apply(reloaded, target));
        assertTrue(Files.isRegularFile(target.resolve(path)));
        assertEquals(0, Files.size(target.resolve(path)));
        SnapshotTool.verify(reloaded, target);
        assertEquals(0, SnapshotTool.apply(reloaded, target));
    }

    @Test
    void missingFilesAreAddedWhenSomeExactPostimagesAlreadyExist() throws IOException {
        List<Result> changes = SnapshotTool.execute(new M3CodeqlSnapshotRecipe(fixture()),
                List.of(source(JAVA_PATH, JAVA_TEXT)));
        assertEquals(1, changes.size());
        assertEquals(QUERY_PATH, path(changes.getFirst().getAfter()));
    }

    @Test
    void reorderedInputsProduceTheSameOutput() throws IOException {
        SourceFile present = source(JAVA_PATH, JAVA_TEXT);
        SourceFile unrelated = source("README.md", "upstream\n");
        List<Result> first = SnapshotTool.execute(new M3CodeqlSnapshotRecipe(fixture()), List.of(present, unrelated));
        List<Result> second = SnapshotTool.execute(new M3CodeqlSnapshotRecipe(fixture()), List.of(unrelated, present));
        assertEquals(first.getFirst().getAfter().printAll(), second.getFirst().getAfter().printAll());
        assertEquals(path(first.getFirst().getAfter()), path(second.getFirst().getAfter()));
    }

    @Test
    void differingExistingBytesRefuseTheEntireRecipe() {
        assertThrows(IOException.class, () -> SnapshotTool.execute(new M3CodeqlSnapshotRecipe(fixture()),
                List.of(source(JAVA_PATH, "class ExistingUserWork {}\n"))));
    }

    @Test
    void duplicateInputsRefuseTheEntireRecipe() {
        assertThrows(IOException.class, () -> SnapshotTool.execute(new M3CodeqlSnapshotRecipe(fixture()),
                List.of(source(JAVA_PATH, JAVA_TEXT), source(JAVA_PATH, JAVA_TEXT))));
    }

    @Test
    void aliasedInputPathIsRejected() {
        assertThrows(IOException.class, () -> SnapshotTool.execute(new M3CodeqlSnapshotRecipe(fixture()),
                List.of(source("m3/runtime/../runtime/src/main/java/Example.java", JAVA_TEXT))));
    }

    @Test
    void malformedUtf16CannotBecomeAReplacementCharacterPostimage() {
        assertThrows(IOException.class, () -> SnapshotTool.execute(new M3CodeqlSnapshotRecipe(fixture()),
                List.of(source(JAVA_PATH, "broken\ud800"))));
    }

    @Test
    void manifestRejectsAbsoluteTraversalAliasesDuplicatesAndUpstreamChanges() {
        for (String path : List.of("/m3/runtime/A.java", "m3/../README.md", "m3//runtime/A.java",
                "m3/runtime/./A.java", "C:/m3/runtime/A.java", "m3\\runtime\\A.java",
                "m3/runtime/A\n.java", "README.md", "java/ql/lib/A.qll", "m3/recipes/A.java")) {
            assertThrows(IllegalArgumentException.class, () -> entry(path, "test\n"), path);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new Snapshot(List.of(entry(JAVA_PATH, JAVA_TEXT), entry(JAVA_PATH, JAVA_TEXT))));
        assertThrows(IllegalArgumentException.class,
                () -> new Snapshot(List.of(entry(JAVA_PATH, JAVA_TEXT), entry(QUERY_PATH, QUERY_TEXT))));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(List.of(
                entry("m3/runtime", "file cannot be a parent directory\n"), entry(JAVA_PATH, JAVA_TEXT))));
    }

    @Test
    void tamperedResourceDoesNotMatchItsPinnedDigest() {
        Map<String, byte[]> resources = resources(fixture());
        resources.put("objects/" + fixture().entries().getFirst().sha256(), "tamper\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> Snapshot.load(resources::get));
    }

    @Test
    void tamperedManifestDoesNotMatchItsPinnedDigest() {
        Map<String, byte[]> resources = resources(fixture());
        resources.put("manifest.tsv", "tamper\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> Snapshot.load(resources::get));
    }

    @Test
    void invalidUtf8WithAnOtherwiseCorrectHashIsRejected() {
        byte[] invalid = new byte[] {(byte) 0xc3, (byte) 0x28};
        String hash = Snapshot.hash(invalid);
        String manifest = Snapshot.HEADER + QUERY_PATH + "\t" + hash + "\t2\t100644\n";
        Map<String, byte[]> resources = new HashMap<>();
        resources.put("manifest.tsv", Snapshot.encode(manifest));
        resources.put("manifest.sha256", Snapshot.encode(Snapshot.hash(Snapshot.encode(manifest)) + "\n"));
        resources.put("objects/" + hash, invalid);
        assertThrows(IOException.class, () -> Snapshot.load(resources::get));
    }

    @Test
    void utf8WithoutBomAndNulIsAnExplicitResourceContract() {
        assertThrows(IllegalArgumentException.class, () -> entry(JAVA_PATH, "\ufeffclass Example {}"));
        assertThrows(IllegalArgumentException.class, () -> entry(JAVA_PATH, "a\0b"));
    }

    @Test
    void writeReadCaptureAndActualApplyAreByteExactAndIdempotent() throws IOException {
        Path source = Files.createDirectory(temporary.resolve("source"));
        write(source, JAVA_PATH, JAVA_TEXT);
        write(source, QUERY_PATH, QUERY_TEXT);
        write(source, "m3/runtime/target/generated.txt", "ignored build output");
        write(source, "m3/ql/java/.testproj/temporary.txt", "ignored CodeQL output");
        write(source, "m3/ql/java/evidence.testproj/database.bin", "\0binary CodeQL output");
        write(source, "m3/ql/java/Inventory.actual", "ignored CodeQL output");
        write(source, "m3/ql/java/Inventory.qlx.lock", "ignored CodeQL output");
        write(source, "m3/ql/java/Inventory.qlx.log", "ignored CodeQL output");
        Snapshot captured = SnapshotTool.capture(source, List.of("m3/runtime", "m3/ql"));
        assertEquals(fixture().manifest(), captured.manifest());
        Path resources = temporary.resolve("snapshot");
        SnapshotTool.writeSnapshot(captured, resources);
        Snapshot reread = Snapshot.read(resources);
        assertEquals(captured.manifestSha256(), reread.manifestSha256());
        Path target = Files.createDirectory(temporary.resolve("target"));
        write(target, "README.md", "original upstream\n");
        assertEquals(2, SnapshotTool.apply(reread, target));
        SnapshotTool.verify(reread, target);
        assertEquals(0, SnapshotTool.apply(reread, target));
        assertEquals(JAVA_TEXT, Files.readString(target.resolve(JAVA_PATH)));
        assertEquals(QUERY_TEXT, Files.readString(target.resolve(QUERY_PATH)));
        assertEquals("original upstream\n", Files.readString(target.resolve("README.md")));
    }

    @Test
    void diskPreflightConflictDoesNotPartiallyMaterializeTheSnapshot() throws IOException {
        Path target = Files.createDirectory(temporary.resolve("target"));
        write(target, JAVA_PATH, "user changes\n");
        assertThrows(IOException.class, () -> SnapshotTool.apply(fixture(), target));
        assertEquals("user changes\n", Files.readString(target.resolve(JAVA_PATH)));
        assertFalse(Files.exists(target.resolve(QUERY_PATH)));
    }

    @Test
    void verifyRejectsMissingAndChangedDestinations() throws IOException {
        Path target = Files.createDirectory(temporary.resolve("target"));
        assertThrows(IOException.class, () -> SnapshotTool.verify(fixture(), target));
        SnapshotTool.apply(fixture(), target);
        write(target, QUERY_PATH, "different query\n");
        assertThrows(IOException.class, () -> SnapshotTool.verify(fixture(), target));
    }

    @Test
    void captureRejectsDuplicateIncludesAndSymbolicLinks() throws IOException {
        Path source = Files.createDirectory(temporary.resolve("source"));
        write(source, JAVA_PATH, JAVA_TEXT);
        assertThrows(IOException.class,
                () -> SnapshotTool.capture(source, List.of("m3/runtime", JAVA_PATH)));
        Path outside = Files.writeString(temporary.resolve("outside.txt"), "private source");
        Files.createSymbolicLink(source.resolve("m3/runtime/linked.txt"), outside);
        assertThrows(IOException.class, () -> SnapshotTool.capture(source, List.of("m3/runtime")));
    }

    @Test
    void symlinkAncestorCannotRedirectRecipeWrites() throws IOException {
        Path target = Files.createDirectory(temporary.resolve("target"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.createSymbolicLink(target.resolve("m3"), outside);
        assertThrows(IOException.class, () -> SnapshotTool.apply(fixture(), target));
        try (var children = Files.list(outside)) {
            assertEquals(0, children.count());
        }
    }

    @Test
    void snapshotResourceSymlinksAreRejected() throws IOException {
        Path resources = temporary.resolve("snapshot");
        SnapshotTool.writeSnapshot(fixture(), resources);
        Path manifest = resources.resolve("manifest.tsv");
        Path outside = temporary.resolve("manifest-outside.tsv");
        Files.move(manifest, outside);
        Files.createSymbolicLink(manifest, outside);
        assertThrows(IOException.class, () -> Snapshot.read(resources));
    }

    @Test
    void captureOrderAndRepeatedResourceWriteAreDeterministic() throws IOException {
        Path source = Files.createDirectory(temporary.resolve("source"));
        write(source, JAVA_PATH, JAVA_TEXT);
        write(source, QUERY_PATH, QUERY_TEXT);
        Snapshot first = SnapshotTool.capture(source, List.of(JAVA_PATH, QUERY_PATH));
        Snapshot second = SnapshotTool.capture(source, List.of(QUERY_PATH, JAVA_PATH));
        assertEquals(first.manifest(), second.manifest());
        Path resources = temporary.resolve("snapshot");
        SnapshotTool.writeSnapshot(first, resources);
        SnapshotTool.writeSnapshot(second, resources);
        assertEquals(first.manifest(), Files.readString(resources.resolve("manifest.tsv")));
    }

    @Test
    void changedCaptureRemovesOnlyObsoleteSnapshotObjects() throws IOException {
        Path resources = temporary.resolve("snapshot");
        SnapshotTool.writeSnapshot(fixture(), resources);
        Snapshot smaller = new Snapshot(List.of(entry(QUERY_PATH, QUERY_TEXT)));
        SnapshotTool.writeSnapshot(smaller, resources);
        assertEquals(smaller.manifestSha256(), Snapshot.read(resources).manifestSha256());
        try (var objects = Files.list(resources.resolve("objects"))) {
            assertEquals(1, objects.count());
        }
    }

    @Test
    void executableModeRoundTripsOnPosix() throws IOException {
        Path source = Files.createDirectory(temporary.resolve("source"));
        String path = "m3/scripts/verify.sh";
        write(source, path, "#!/bin/sh\nexit 0\n");
        Path script = source.resolve(path);
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(script).supportsFileAttributeView("posix"));
        Files.setPosixFilePermissions(script, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        Snapshot snapshot = SnapshotTool.capture(source, List.of(path));
        assertTrue(snapshot.entries().getFirst().executable());
        Path target = Files.createDirectory(temporary.resolve("target"));
        SnapshotTool.apply(snapshot, target);
        assertTrue(SnapshotTool.executable(target.resolve(path)));
        Files.setPosixFilePermissions(target.resolve(path),
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IOException.class, () -> SnapshotTool.verify(snapshot, target));
    }

    private static Snapshot fixture() {
        return new Snapshot(List.of(entry(QUERY_PATH, QUERY_TEXT), entry(JAVA_PATH, JAVA_TEXT)));
    }

    private static Snapshot.Entry entry(String path, String text) {
        byte[] bytes = Snapshot.encode(text);
        return new Snapshot.Entry(path, Snapshot.hash(bytes), bytes.length, false, text);
    }

    private static SourceFile source(String path, String text) {
        return PlainText.builder().sourcePath(Path.of(path)).text(text)
                .charsetName(StandardCharsets.UTF_8.name()).build();
    }

    private static String path(SourceFile file) {
        return file.getSourcePath().toString().replace('\\', '/');
    }

    private static Map<String, byte[]> resources(Snapshot snapshot) {
        Map<String, byte[]> result = new HashMap<>();
        result.put("manifest.tsv", Snapshot.encode(snapshot.manifest()));
        result.put("manifest.sha256", Snapshot.encode(snapshot.manifestSha256() + "\n"));
        for (Snapshot.Entry entry : snapshot.entries()) {
            result.put("objects/" + entry.sha256(), Snapshot.encode(entry.text()));
        }
        return result;
    }

    private static void write(Path root, String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
