package com.synexia.m3.codeql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EvidenceFingerprintsTest {
    private static EvidenceStore.Row row(String path, String kind, String subject) {
        return new EvidenceStore.Row(path, "p", "p." + path, "run()", kind, subject, 1, 2, 1, 7);
    }

    private static EvidenceStore store(List<EvidenceStore.Row> rows) {
        var store = new EvidenceStore(Math.max(1, rows.size()));
        rows.forEach(store::add);
        return store;
    }

    private static EvidenceFingerprints.Result analyze(List<EvidenceStore.Row> rows, long work, int candidates) {
        var store = store(rows);
        return EvidenceFingerprints.analyze(store, store.sortedUniqueIndices(), work, candidates);
    }

    private static List<EvidenceStore.Row> nearRows(String path, String variant) {
        var rows = new ArrayList<EvidenceStore.Row>();
        for (int i = 0; i < 32; i++) rows.add(row(path, "contract/example", "common" + i));
        rows.add(row(path, "effect/example", variant));
        return rows;
    }

    @Test
    void patternsIgnoreIdentityAndCoordinatesButRetainChangedEffects() {
        var rows = new ArrayList<>(List.of(row("a.java", "callable", "()V"), row("a.java", "effect/write-array", "[I")));
        rows.add(new EvidenceStore.Row("z.java", "other", "other.Owner", "different()", "callable", "()V", 9, 1, 9, 11));
        rows.add(new EvidenceStore.Row("z.java", "other", "other.Owner", "different()", "effect/write-array", "[I", 9, 1, 9, 11));
        rows.add(row("b.java", "callable", "()V"));
        rows.add(row("b.java", "effect/read-field", "p.Target#value"));
        var result = analyze(rows, 100, 100);
        var a = result.fingerprints().get(0);
        var b = result.fingerprints().get(1);
        var z = result.fingerprints().get(2);
        assertEquals(a.factPatternSha256(), z.factPatternSha256());
        assertEquals(a.simHash64(), z.simHash64());
        assertNotEquals(a.factPatternSha256(), b.factPatternSha256());
        assertEquals(2, a.factCount());
        assertTrue(result.candidates().stream().anyMatch(c -> c.kind() == EvidenceFingerprints.Kind.EXACT_FACT_PATTERN
                && c.left().path().equals("a.java") && c.right().path().equals("z.java")));
    }

    @Test
    void deterministicFanInDeduplicatesFactsAndUsesCanonicalRepresentatives() {
        var rows = new ArrayList<>(nearRows("a.java", "A"));
        rows.addAll(nearRows("b.java", "B"));
        rows.addAll(nearRows("z.java", "A"));
        var expected = analyze(rows, 100, 100);
        rows.addAll(nearRows("a.java", "A"));
        Collections.shuffle(rows, new Random(771));
        assertEquals(expected, analyze(rows, 100, 100));
        assertEquals(2, expected.candidates().size());
        assertEquals(1, expected.candidates().stream().filter(c -> c.kind() == EvidenceFingerprints.Kind.NEAR_FACT_PATTERN).count());
        assertFalse(expected.truncated());
        assertThrows(UnsupportedOperationException.class, () -> expected.fingerprints().clear());
        assertThrows(UnsupportedOperationException.class, () -> expected.candidates().clear());
    }

    @Test
    void stableSimHashAndFourBandSearchFindNearCandidatesWithoutDuplicates() {
        var rows = new ArrayList<>(nearRows("a.java", "A"));
        rows.addAll(nearRows("b.java", "B"));
        var result = analyze(rows, 1, 1);
        assertEquals(0x4080176ea287f59bL, result.fingerprints().get(0).simHash64());
        assertEquals(0x4080176ea2a7f59bL, result.fingerprints().get(1).simHash64());
        assertEquals(1, result.candidates().size());
        assertEquals(EvidenceFingerprints.Kind.NEAR_FACT_PATTERN, result.candidates().get(0).kind());
        assertEquals(1, result.candidates().get(0).hammingDistance());
        assertEquals(1, result.candidateWork());
        assertFalse(result.truncated());
        assertEquals(0, EvidenceFingerprints.hammingDistance(Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(64, EvidenceFingerprints.hammingDistance(0, -1));
        assertEquals(2, EvidenceFingerprints.hammingDistance(0xaa, 0xa9));
        var boundary = new ArrayList<>(nearRows("a.java", "A"));
        boundary.addAll(nearRows("b.java", "variant14"));
        assertEquals(3, analyze(boundary, 100, 100).candidates().getFirst().hammingDistance());
        var outside = new ArrayList<>(nearRows("a.java", "A"));
        outside.addAll(nearRows("b.java", "variant1"));
        var outsideResult = analyze(outside, 100, 100);
        assertEquals(4, EvidenceFingerprints.hammingDistance(outsideResult.fingerprints().get(0).simHash64(),
                outsideResult.fingerprints().get(1).simHash64()));
        assertTrue(outsideResult.candidates().isEmpty());
    }

    @Test
    void exactAndNearSearchBothRespectWorkAndCandidateCaps() {
        var exact = new ArrayList<EvidenceStore.Row>();
        for (int i = 0; i < 20; i++) exact.add(row("file" + i + ".java", "callable", "()V"));
        var workLimited = analyze(exact, 5, 100);
        assertEquals(20, workLimited.fingerprints().size());
        assertEquals(5, workLimited.candidateWork());
        assertEquals(5, workLimited.candidates().size());
        assertTrue(workLimited.truncated());
        var candidateLimited = analyze(exact, 100, 1);
        assertEquals(1, candidateLimited.candidates().size());
        assertTrue(candidateLimited.truncated());
        var near = new ArrayList<>(nearRows("a.java", "A"));
        near.addAll(nearRows("b.java", "B"));
        assertTrue(analyze(near, 0, 100).truncated());
        assertTrue(analyze(near, 100, 0).truncated());
        assertTrue(analyze(near, 0, 100).candidates().isEmpty());
    }

    @Test
    void anchorsRemainDistinctAndInvalidBudgetsFailExplicitly() {
        var rows = List.of(row("a.java", "callable", "()V"),
                new EvidenceStore.Row("a.java", "p", "p.a.java", "run()", "callable", "()V", 2, 2, 2, 7));
        assertEquals(2, analyze(rows, 100, 100).fingerprints().size());
        assertThrows(IllegalArgumentException.class, () -> analyze(rows, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> analyze(rows, 1, -1));
        assertFalse(analyze(List.of(), 0, 0).truncated());
    }
}
