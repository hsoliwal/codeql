package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvidenceReaderTest {
    @TempDir Path temporary;
    SourceSnapshot source;

    @BeforeEach
    void source() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("source"));
        Files.writeString(root.resolve("Example.java"), "class Example {\n  void run() {}\n}\n");
        source = SourceSnapshot.capture(root, 1);
    }

    @Test
    void acceptsRealCsvHeadersQuotedDataAndSourceAnchors() throws Exception {
        Path csv = Files.writeString(temporary.resolve("valid.csv"), String.join(",", EvidenceReader.HEADER) + "\n"
                + "Example.java,p,p.Example,run(),callable,\"quote \"\" comma, and\nnewline\",2,8,2,10\n");
        var evidence = new EvidenceStore(100);
        new EvidenceReader(source, evidence).read(csv);
        assertEquals(1, evidence.size());
        assertEquals("quote \" comma, and\nnewline", evidence.row(0).subject());
        assertEquals("Example.java", evidence.row(0).path());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../Example.java,p,p.Example,run(),callable,x,1,1,1,3",
            "Missing.java,p,p.Example,run(),callable,x,1,1,1,3",
            "Example.java,p,p.Example,run(),callable,x,0,1,1,3",
            "Example.java,p,p.Example,run(),callable,x,2,9,2,8",
            "Example.java,p,p.Example,run(),callable,x,2,1,99,1",
            "Example.java,p,p.Example,run(),callable,x,2,1,2,9000",
            "Example.java,p,,run(),callable,x,1,1,1,3",
            "Example.java,p,p.Example,run(),callable,x,1,1,1"
    })
    void rejectsUnboundOrMalformedEvidence(String row) throws Exception {
        Path csv = Files.writeString(temporary.resolve("invalid.csv"), String.join(",", EvidenceReader.HEADER) + "\n" + row + "\n");
        assertThrows(IOException.class, () -> new EvidenceReader(source, new EvidenceStore(100)).read(csv));
    }

    @Test
    void missingOrIncorrectSchemaCannotBecomeEmptySuccessfulEvidence() throws Exception {
        Path empty = Files.writeString(temporary.resolve("empty.csv"), "");
        Path wrong = Files.writeString(temporary.resolve("wrong.csv"), "path,owner\n");
        assertThrows(IOException.class, () -> new EvidenceReader(source, new EvidenceStore(100)).read(empty));
        assertThrows(IOException.class, () -> new EvidenceReader(source, new EvidenceStore(100)).read(wrong));
    }
}
