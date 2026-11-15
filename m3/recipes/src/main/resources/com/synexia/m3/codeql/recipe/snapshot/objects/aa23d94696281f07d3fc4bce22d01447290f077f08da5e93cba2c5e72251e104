package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSnapshotTest {
    @TempDir Path temporary;

    @Test
    void parallelInventoryIsDeterministicAndIncludesDescriptorsAndHiddenInputs() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Files.createDirectories(source.resolve("z/nested"));
        for (int i = 0; i < 70; i++) Files.writeString(source.resolve("z/nested/File" + i + ".java"), "class C" + i + " {}\n");
        Files.writeString(source.resolve("pom.xml"), "<project/>\n");
        Files.writeString(source.resolve(".config"), "value");
        Files.write(source.resolve("binary.dat"), new byte[] {0, -1, 10, 13});
        var serial = SourceSnapshot.capture(source, 1);
        var parallel = SourceSnapshot.capture(source, 7);
        assertEquals(serial.files(), parallel.files());
        assertEquals(serial.contentHash(), parallel.contentHash());
        assertEquals(73, serial.files().size());
        List<String> expected = new ArrayList<>(serial.files().keySet());
        expected.sort(String::compareTo);
        assertEquals(expected, new ArrayList<>(serial.files().keySet()));
        Files.setLastModifiedTime(source.resolve("pom.xml"), FileTime.fromMillis(123456));
        assertEquals(serial.contentHash(), SourceSnapshot.capture(source, 3).contentHash());
        Files.writeString(source.resolve("pom.xml"), "<project>changed</project>\n");
        assertThrows(IOException.class, () -> serial.requireSameContent(SourceSnapshot.capture(source, 2)));
    }

    @Test
    void materializedInputSurvivesLaterOriginalMutationAndRejectsStaleCopy() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path input = Files.writeString(source.resolve("Example.java"), "class Example {}\n");
        var before = SourceSnapshot.capture(source, 1);
        var copy = before.materialize(temporary.resolve("sealed"), 2);
        assertEquals(before.contentHash(), copy.contentHash());
        Files.writeString(input, "class Changed {}\n");
        assertEquals("class Example {}\n", Files.readString(copy.root().resolve("Example.java")));
        assertThrows(IOException.class, () -> before.materialize(temporary.resolve("stale"), 1));
        assertThrows(IOException.class, () -> before.requireSameContent(SourceSnapshot.capture(source, 1)));
    }

    @Test
    void sourceSymlinkFilesAndDirectoriesAreRejected() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path outside = Files.writeString(temporary.resolve("outside.txt"), "outside");
        Files.createSymbolicLink(source.resolve("link"), outside);
        assertThrows(IOException.class, () -> SourceSnapshot.capture(source, 1));
        Files.delete(source.resolve("link"));
        Files.createSymbolicLink(source.resolve("directory"), temporary);
        assertThrows(IOException.class, () -> SourceSnapshot.capture(source, 1));
    }

    @Test
    void onlyQueryProfileExcludesGeneratedCache() throws Exception {
        Path pack = Files.createDirectory(temporary.resolve("pack"));
        Files.writeString(pack.resolve("Inventory.ql"), "select 1");
        var before = SourceSnapshot.captureQueryInputs(pack, 1);
        Files.createDirectory(pack.resolve(".cache"));
        Files.writeString(pack.resolve(".cache/generated"), "compiler output");
        assertEquals(before.contentHash(), SourceSnapshot.captureQueryInputs(pack, 2).contentHash());
        assertNotEquals(before.contentHash(), SourceSnapshot.capture(pack, 1).contentHash());
        Files.writeString(pack.resolve("Inventory.ql"), "select 2");
        assertNotEquals(before.contentHash(), SourceSnapshot.captureQueryInputs(pack, 1).contentHash());
    }

    @Test
    void invalidWorkerBoundsAndOutputScopeAreRejected() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        assertThrows(IllegalArgumentException.class, () -> SourceSnapshot.capture(source, 0));
        assertThrows(IllegalArgumentException.class, () -> SourceSnapshot.capture(source, 65));
        assertThrows(IOException.class, () -> PathGuard.createOutput(source.resolve("output"), source));
        assertThrows(IOException.class, () -> PathGuard.createOutput(temporary, source));
    }
}
