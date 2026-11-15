package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EvidenceStoreTest {
    private static EvidenceStore.Row row(String path, String subject) {
        return new EvidenceStore.Row(path, "p", "p.Example", "run()", "callable", subject, 1, 1, 1, 10);
    }

    @Test
    void ownsSharedStringsAndMaterializesRowsOnlyOnDemand() {
        var store = new EvidenceStore(100);
        store.add(row(new String("Example.java"), new String("same")));
        store.add(row(new String("Example.java"), new String("same")));
        assertSame(store.row(0).path(), store.row(1).path());
        assertSame(store.row(0).subject(), store.row(1).subject());
        assertNotSame(store.row(0), store.row(0));
        assertEquals(6, store.dictionarySize());
        assertEquals(1, store.sortedUniqueIndices().length);
    }

    @Test
    void deterministicFanInDeduplicatesAndHashesIndependentOfArrivalOrder() {
        var first = new EvidenceStore(200);
        var second = new EvidenceStore(200);
        for (int i = 99; i >= 0; i--) first.add(row("File" + i + ".java", "subject" + i));
        for (int i = 0; i < 100; i++) second.add(row("File" + i + ".java", "subject" + i));
        first.add(row("File50.java", "subject50"));
        int[] a = first.sortedUniqueIndices();
        int[] b = second.sortedUniqueIndices();
        assertEquals(100, a.length);
        for (int i = 0; i < a.length; i++) assertEquals(first.row(a[i]), second.row(b[i]));
        assertEquals(first.structuralEvidenceHash(a), second.structuralEvidenceHash(b));
        second.add(row("changed.java", "new effect"));
        assertNotEquals(first.structuralEvidenceHash(a), second.structuralEvidenceHash(second.sortedUniqueIndices()));
    }

    @Test
    void capacityLimitFailsInsteadOfSilentlyDroppingEvidence() {
        var store = new EvidenceStore(1);
        store.add(row("Example.java", "subject"));
        assertThrows(IllegalStateException.class, () -> store.add(row("Other.java", "subject")));
        assertEquals(1, store.size());
        assertThrows(IndexOutOfBoundsException.class, () -> store.row(1));
    }

    @Test
    void dictionaryBudgetAccountsForEntryOverheadAndRejectsRowsAtomically() {
        var store = new EvidenceStore(100, 16_384);
        store.add(row("Example.java", "subject"));
        int entries = store.dictionarySize();
        assertThrows(IllegalStateException.class, () -> store.add(row("Other.java", "x".repeat(5000))));
        assertEquals(entries, store.dictionarySize());
        assertEquals(1, store.size());
        boolean limited = false;
        for (int i = 0; i < store.rowLimit(); i++) {
            int before = store.dictionarySize();
            try {
                store.add(new EvidenceStore.Row("p" + i, "q" + i, "r" + i, "s" + i, "t" + i, "u" + i, 1, 1, 1, 1));
            } catch (IllegalStateException budget) {
                assertTrue(budget.getMessage().contains("heap budget"));
                assertEquals(before, store.dictionarySize());
                limited = true;
                break;
            }
        }
        assertTrue(limited);
        assertTrue(store.size() < store.rowLimit());
    }
}
