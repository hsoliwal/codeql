package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DelimitedTest {
    @Test
    void parsesQuotedCommasEscapesNewlinesAndFinalEmptyFields() throws Exception {
        List<List<String>> rows = new ArrayList<>();
        Delimited.csv(new StringReader("a,\"b,c\",\"a\"\"b\",\"line1\r\nline2\",\r\nlast,"), rows::add);
        assertEquals(List.of(List.of("a", "b,c", "a\"b", "line1\r\nline2", ""), List.of("last", "")), rows);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"unterminated", "a\"b,c", "\"closed\"junk,c", "\"x\" \n"})
    void rejectsMalformedCsv(String csv) {
        assertThrows(IOException.class, () -> Delimited.csv(new StringReader(csv), ignored -> {}));
    }

    @Test
    void tsvEscapingIsReversibleAndPreservesEmptyFirstColumn() throws Exception {
        String text = "literal\\t\tline1\nline2\r";
        assertEquals(text, Delimited.untsv(Delimited.tsv(text)));
        assertEquals("\tvalue\n", Delimited.line("", "value"));
        assertThrows(IOException.class, () -> Delimited.untsv("\\x"));
    }

    @Test
    void fieldCountBoundAlsoAppliesAtEndOfFile() throws Exception {
        String valid = String.join(",", java.util.Collections.nCopies(64, "x"));
        String invalid = valid + ",x";
        assertDoesNotThrow(() -> Delimited.csv(new StringReader(valid), ignored -> {}));
        assertThrows(IOException.class, () -> Delimited.csv(new StringReader(invalid), ignored -> {}));
        assertThrows(IOException.class, () -> Delimited.csv(new StringReader(invalid + "\n"), ignored -> {}));
    }
}
