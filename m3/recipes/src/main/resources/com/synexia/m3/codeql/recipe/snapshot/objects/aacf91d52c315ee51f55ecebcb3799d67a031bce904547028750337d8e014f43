package com.synexia.m3.codeql;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Fingerprints exported fact patterns, never method bodies or semantic equivalence.
 * Exact and near matches are review candidates only; neither authorizes promotion.
 */
public final class EvidenceFingerprints {
    public record Identity(String path, String owner, String signature,
                           int startLine, int startColumn, int endLine, int endColumn) {
        static Identity of(EvidenceStore.Row row) {
            return new Identity(row.path(), row.owner(), row.signature(), row.startLine(),
                    row.startColumn(), row.endLine(), row.endColumn());
        }
    }

    public record Fingerprint(Identity identity, String factPatternSha256, long simHash64, int factCount) {}
    public enum Kind { EXACT_FACT_PATTERN, NEAR_FACT_PATTERN }
    public record Candidate(Identity left, Identity right, Kind kind, int hammingDistance) {}
    public record Result(List<Fingerprint> fingerprints, List<Candidate> candidates,
                         long candidateWork, boolean truncated) {
        public Result {
            fingerprints = List.copyOf(fingerprints);
            candidates = List.copyOf(candidates);
        }
    }

    private record Fact(String kind, String subject) {}
    private static final Comparator<Identity> ID_ORDER = Comparator.comparing(Identity::path)
            .thenComparing(Identity::owner).thenComparing(Identity::signature)
            .thenComparingInt(Identity::startLine).thenComparingInt(Identity::startColumn)
            .thenComparingInt(Identity::endLine).thenComparingInt(Identity::endColumn);
    private static final Comparator<Fact> FACT_ORDER = Comparator.comparing(Fact::kind).thenComparing(Fact::subject);

    private EvidenceFingerprints() {}

    public static Result analyze(EvidenceStore store, int[] sortedUniqueIndices) {
        return analyze(store, sortedUniqueIndices, 1_000_000, 10_000);
    }

    /**
     * Hashes sorted unique (kind, subject) facts, excluding grouping identity and coordinates.
     * Identifiers within a subject remain significant. Exact groups use their first identity
     * as representative; near matching compares these representatives, not all group members.
     * Work counts exact representative/member pairs and distinct near pair comparisons.
     * Each near pair can appear in at most four bands, so band visits are bounded by four
     * times near work, plus the first unexamined pair. Caps never discard fingerprints.
     */
    public static Result analyze(EvidenceStore store, int[] sortedUniqueIndices,
                                 long maxCandidateWork, int maxCandidates) {
        if (maxCandidateWork < 0 || maxCandidates < 0) throw new IllegalArgumentException("Negative candidate budget");
        Map<Identity, TreeSet<Fact>> grouped = new TreeMap<>(ID_ORDER);
        for (int index : sortedUniqueIndices) {
            EvidenceStore.Row row = store.row(index);
            grouped.computeIfAbsent(Identity.of(row), ignored -> new TreeSet<>(FACT_ORDER))
                    .add(new Fact(row.kind(), row.subject()));
        }
        List<Fingerprint> fingerprints = new ArrayList<>(grouped.size());
        grouped.forEach((identity, facts) -> fingerprints.add(fingerprint(identity, facts)));
        grouped.clear();
        return candidates(fingerprints, maxCandidateWork, maxCandidates);
    }

    private static Fingerprint fingerprint(Identity identity, TreeSet<Fact> facts) {
        MessageDigest pattern = Hashes.sha256();
        Hashes.field(pattern, "m3-fact-pattern-v1");
        int[] votes = new int[64];
        MessageDigest token = Hashes.sha256();
        for (Fact fact : facts) {
            Hashes.field(pattern, fact.kind());
            Hashes.field(pattern, fact.subject());
            token.reset();
            Hashes.field(token, "m3-fact-simhash-token-v1");
            Hashes.field(token, fact.kind());
            Hashes.field(token, fact.subject());
            byte[] hash = token.digest();
            long bits = 0;
            for (int i = 0; i < Long.BYTES; i++) bits = (bits << 8) | (hash[i] & 255L);
            for (int bit = 0; bit < 64; bit++) votes[bit] += ((bits >>> bit) & 1) == 1 ? 1 : -1;
        }
        long simHash = 0;
        for (int bit = 0; bit < 64; bit++) if (votes[bit] > 0) simHash |= 1L << bit;
        return new Fingerprint(identity, Hashes.hex(pattern.digest()), simHash, facts.size());
    }

    private static Result candidates(List<Fingerprint> fingerprints, long maxWork, int maxCandidates) {
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Integer> exact = new HashMap<>();
        int[] representatives = new int[fingerprints.size()];
        int representativeCount = 0;
        long work = 0;
        for (int i = 0; i < fingerprints.size(); i++) {
            Fingerprint current = fingerprints.get(i);
            Integer representative = exact.putIfAbsent(current.factPatternSha256(), i);
            if (representative == null) {
                representatives[representativeCount++] = i;
            } else {
                if (work == maxWork || candidates.size() == maxCandidates) {
                    return result(fingerprints, candidates, work, true);
                }
                work++;
                candidates.add(new Candidate(fingerprints.get(representative).identity(), current.identity(),
                        Kind.EXACT_FACT_PATTERN, 0));
            }
        }
        if (representativeCount < 2) return result(fingerprints, candidates, work, false);
        // Hamming distance <= 3 leaves at least one of four 16-bit bands unchanged.
        // Primitive bucket chains avoid boxed pair sets and all-pairs scanning.
        int[] heads = new int[4 * 65_536];
        Arrays.fill(heads, -1);
        int[][] next = new int[4][fingerprints.size()];
        int[] visited = new int[fingerprints.size()];
        for (int position = 0; position < representativeCount; position++) {
            int currentIndex = representatives[position];
            Fingerprint current = fingerprints.get(currentIndex);
            for (int band = 0; band < 4; band++) {
                int bucket = bucket(current.simHash64(), band);
                for (int prior = heads[bucket]; prior >= 0; prior = next[band][prior]) {
                    if (visited[prior] == currentIndex + 1) continue;
                    visited[prior] = currentIndex + 1;
                    if (work == maxWork) return result(fingerprints, candidates, work, true);
                    work++;
                    Fingerprint previous = fingerprints.get(prior);
                    int distance = hammingDistance(previous.simHash64(), current.simHash64());
                    if (distance <= 3) {
                        if (candidates.size() == maxCandidates) return result(fingerprints, candidates, work, true);
                        candidates.add(new Candidate(previous.identity(), current.identity(), Kind.NEAR_FACT_PATTERN, distance));
                    }
                }
            }
            for (int band = 0; band < 4; band++) {
                int bucket = bucket(current.simHash64(), band);
                next[band][currentIndex] = heads[bucket];
                heads[bucket] = currentIndex;
            }
        }
        return result(fingerprints, candidates, work, false);
    }

    private static int bucket(long fingerprint, int band) {
        return (band << 16) | (int) ((fingerprint >>> (band * 16)) & 0xffffL);
    }

    public static int hammingDistance(long left, long right) { return Long.bitCount(left ^ right); }

    private static Result result(List<Fingerprint> fingerprints, List<Candidate> candidates, long work, boolean truncated) {
        candidates.sort(Comparator.comparing(Candidate::left, ID_ORDER).thenComparing(Candidate::right, ID_ORDER)
                .thenComparing(Candidate::kind));
        return new Result(fingerprints, candidates, work, truncated);
    }
}
