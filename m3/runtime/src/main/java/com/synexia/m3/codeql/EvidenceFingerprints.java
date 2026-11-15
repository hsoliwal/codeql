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
/* M3-ATOM kind=TYPE id=da471270ed23673b8084d0452267611aa9e2c508c642a4b4a4aa487525267e4a contract=PUBLIC_API patterns=PLAIN_TYPE */
public final class EvidenceFingerprints {
    /* M3-ATOM kind=TYPE id=3effb6206d26bb0a7915b27a71c078342b9af6c22a45e397a1d511e6ff23afa3 contract=PUBLIC_API patterns=RECORD */
    public record Identity(String path, String owner, String signature,
                           int startLine, int startColumn, int endLine, int endColumn) {
        /* M3-ATOM kind=METHOD id=9baf535e6f51d3f14c07ff1f5dfcb15084e433f791e947dd14f46cdf2f0af90a contract=INTERNAL_OR_PACKAGE patterns=STRAIGHT_LINE */
        static Identity of(EvidenceStore.Row row) {
            /* M3-LEAF kind=RETURN id=fe599a3a826fe475d02a79db7538aa36da6c426de0d59185b2b23621bd2f99d9 contract=INTERNAL_OR_PACKAGE patterns=RETURN */
            return /* M3-LEAF kind=CONSTRUCTION id=2f4c991831a3187921b8a2deb771131f4c1344e15ae7ca84048fd00dbfb9ee08 contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
 new Identity(/* M3-LEAF kind=INVOCATION id=97fc409748bc033a3e30862f2a11ec039596acaac28219bd718c7a2a18c232a9 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
row.path(/* M3-LEAF kind=EMPTY id=178cdff556427973c4c38fee651d97463fd2093e15329118cb8e56518a78d4e2 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=7e290f4a67b277fc2c563da7f566f07bc1685884317cc32ce42cd27c9cc3e144 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 row.owner(/* M3-LEAF kind=EMPTY id=abd2ecfa766d9839c9585a5ab080bf36897f5ba75ff1c5f1f7d34d3e00f4e5ee contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=953af3b7644e1808674a4013556fcc138ffa48d9fde6e150a7bb7ea470450091 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 row.signature(/* M3-LEAF kind=EMPTY id=c4058f20df50ffeab56f10825cebf1bc1999d6bd1cacb59100c111052e5b3b63 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=8c4cafc3e5e444dc5349c9ad1e6410e64746b9ce6ffb0bd1d15a2d042cf93ba7 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 row.startLine(/* M3-LEAF kind=EMPTY id=b0ee1ca0a57df53463101848dbed8112b7d476a4ad8067cced006b577dbdc5ab contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
),
                    /* M3-LEAF kind=INVOCATION id=c6c0349fe6f23e1ca853f502b7b4c3f959c50c87bed0347d0375e2ea8a70b9b0 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
                    row.startColumn(/* M3-LEAF kind=EMPTY id=4761e9096b43513a72aaac71d7288b6fc88bb7b0815f5fd5cc5844eb3d0bf0b1 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=d1d899dde20ff12a1d717b1722e950d9758bf846fd7e154ac13106ca4438075a contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 row.endLine(/* M3-LEAF kind=EMPTY id=1976c77509c0091469076d4051d38a83d9776a6eed7d841ec471229faf431c44 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=d49cd3c37c637aa79d53bf9d078a4ebf554d61b382d7c8e4076c7098fd81e5fd contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 row.endColumn(/* M3-LEAF kind=EMPTY id=954971a450a3724f321c7e47799a3dfa5e38144140b67770697800c1c8ca2448 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
));
        }
    }

    /* M3-ATOM kind=TYPE id=ee7f5ce5ab5ed35c3d2d746244d1bc62ece4e3c7712925913aeae546c5498829 contract=PUBLIC_API patterns=RECORD */
    public record Fingerprint(Identity identity, String factPatternSha256, long simHash64, int factCount) {}
    /* M3-ATOM kind=TYPE id=f06ca1f6d13717ff2c1454b9b696db5e4162f708817ceb913e9816bab639bcb2 contract=PUBLIC_API patterns=PLAIN_TYPE */
    public enum Kind { EXACT_FACT_PATTERN, NEAR_FACT_PATTERN }
    /* M3-ATOM kind=TYPE id=7c119790d60e296fa4ce78b5985e5e86835bfa5a9deab166f06790d02f53c417 contract=PUBLIC_API patterns=RECORD */
    public record Candidate(Identity left, Identity right, Kind kind, int hammingDistance) {}
    /* M3-ATOM kind=TYPE id=5fa44e1c4a1710677789e156462b6589adc448cd262803947ba75d9f37dcc428 contract=PUBLIC_API patterns=RECORD */
    public record Result(List<Fingerprint> fingerprints, List<Candidate> candidates,
                         long candidateWork, boolean truncated) {
        /* M3-ATOM kind=METHOD id=f8bf0ed446d2597641bfc097fb6afea7a6f555a6411e3403f3f152943573a5b6 contract=PUBLIC_API patterns=STRAIGHT_LINE */
        public Result {
            /* M3-LEAF kind=MUTATION id=168d0112a9f9c9214d25e365d869d04d0b19825a76957baf4bfa5e3e27d536e5 contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
            fingerprints = /* M3-LEAF kind=INVOCATION id=d042189eb717917a883244726cbabc7e42785746f61add5d684c40d87cee35ff contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 List.copyOf(fingerprints);
            /* M3-LEAF kind=MUTATION id=84131d1b9f762360459447d274e10280c7c626ec3ce3c536e75e70f4a03f04e6 contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
            candidates = /* M3-LEAF kind=INVOCATION id=71f39a19413a34298f6a36a5169f4182f71e42d6c5995e2bd87b04f13cf4098c contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 List.copyOf(candidates);
        }
    }

    /* M3-ATOM kind=TYPE id=488af1869a5a927be878b592809abca40acfb14195ac5490967913e5854e5b0a contract=INTERNAL_OR_PACKAGE patterns=RECORD */
    private record Fact(String kind, String subject) {}
    /* M3-LEAF kind=FIELD_DECLARATION id=827c995e306d6c1b668dc92d0a101f677f31fdf1a6e03305b99c63c800220716 contract=INTERNAL_OR_PACKAGE patterns=FIELD_DECLARATION */
    private static final Comparator<Identity> ID_ORDER = Comparator.comparing(Identity::path)
            .thenComparing(Identity::owner).thenComparing(Identity::signature)
            .thenComparingInt(Identity::startLine).thenComparingInt(Identity::startColumn)
            .thenComparingInt(Identity::endLine).thenComparingInt(Identity::endColumn);
    /* M3-LEAF kind=FIELD_DECLARATION id=8d6504cacb6f8d9a3e6c6716e6c698b4a761b49f85b52b03b1806c41272a4a82 contract=INTERNAL_OR_PACKAGE patterns=FIELD_DECLARATION */
    private static final Comparator<Fact> FACT_ORDER = Comparator.comparing(Fact::kind).thenComparing(Fact::subject);

    /* M3-ATOM kind=METHOD id=01ddd56cfb25ab6bbe8c6133b78870daa50d885055c47beda016148c7e966772 contract=INTERNAL_OR_PACKAGE patterns=STRAIGHT_LINE */
    private EvidenceFingerprints() {}

    /* M3-ATOM kind=METHOD id=6242ba0c2e94392ec5c07897bdeb85df98e584c42593e15f26e6025009692bfb contract=PUBLIC_API patterns=STRAIGHT_LINE */
    public static Result analyze(EvidenceStore store, int[] sortedUniqueIndices) {
        /* M3-LEAF kind=RETURN id=5b639d3f5c2bad1fff453e29cb79ae70aa625b991c7c1d6ed448de7fe03bd711 contract=INTERNAL_OR_PACKAGE patterns=RETURN */
        return /* M3-LEAF kind=INVOCATION id=899e3ef2639f4357f4eb504916394d15b0d9b1bf1b3d3eacf7a284cfc23654bd contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 analyze(store, sortedUniqueIndices, 1_000_000, 10_000);
    }

    /**
     * Hashes sorted unique (kind, subject) facts, excluding grouping identity and coordinates.
     * Identifiers within a subject remain significant. Exact groups use their first identity
     * as representative; near matching compares these representatives, not all group members.
     * Work counts exact representative/member pairs and distinct near pair comparisons.
     * Each near pair can appear in at most four bands, so band visits are bounded by four
     * times near work, plus the first unexamined pair. Caps never discard fingerprints.
     */
    /* M3-ATOM kind=METHOD id=e2f0277b92e22b78baad8e505568b3485de4fe2915a71ee100687d777c099533 contract=PUBLIC_API patterns=BRANCHING,EXCEPTION_BOUNDARY,FACTORY_CANDIDATE,ITERATION,LAMBDA */
    public static Result analyze(EvidenceStore store, int[] sortedUniqueIndices,
                                 long maxCandidateWork, int maxCandidates) {
        /* M3-LEAF kind=BRANCH id=addf00a21355e8899f9e02b2b97fde2321277ac2665139ef1c26ef360d5e74c8 contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
        if (maxCandidateWork < 0 || maxCandidates < 0) /* M3-LEAF kind=THROW id=7a48f20804f44c431ca0b71278f2c7cf7039a00484969c6ff5712a5c71c669be contract=INTERNAL_OR_PACKAGE patterns=EXCEPTION_BOUNDARY */
 throw /* M3-LEAF kind=CONSTRUCTION id=4859d8c63fc57c903cda513e424001fa645c1fb1a649ac07407864404ca7ba7d contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
 new IllegalArgumentException("Negative candidate budget");
        /* M3-LEAF kind=LOCAL_DECLARATION id=cf0fbc4b747f269c198b2c05e7521562a564895ea54516155d8715073e7d0363 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        Map<Identity, TreeSet<Fact>> grouped = /* M3-LEAF kind=CONSTRUCTION id=37f77a082c2a5e5b303b3fc4eb345f82631adf710d4b51a756c8ad63d54ef76c contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
 new TreeMap</* M3-LEAF kind=EMPTY id=59effa663d27ea3b4952f964095c67e2bcfe71a77e06c225d5b6c19d968a2f07 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
>(ID_ORDER);
        /* M3-LEAF kind=ITERATION id=28d062760c9f27a71159eadca99918101eb95471bd572e21364ba3f56e39eb49 contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
        for (int index : sortedUniqueIndices) {
            /* M3-LEAF kind=LOCAL_DECLARATION id=b7fe53decca9dd331dbde49632ee227baa54394beeeaf9e8eb2b1d96983b5e06 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
            EvidenceStore.Row row = /* M3-LEAF kind=INVOCATION id=af4907de104e0ac00316f0c60a2296253455b8d2417802bff6c4e69a9b8d6b31 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 store.row(index);
            /* M3-LEAF kind=INVOCATION id=eb2b03fc238a35d61707a4cbb401a45b8ac314547c0ad765cafba65674e638cc contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
            /* M3-LEAF kind=INVOCATION id=073f66d7cdf7df5fe4eb87b96576cd285b839151c7f555bf1962754287d9fec1 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
            grouped.computeIfAbsent(/* M3-LEAF kind=INVOCATION id=006bade4fa167968f1377b45096c9698d7f0665d60cb8127014b0d4a70e829e0 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
Identity.of(row), ignored -> new TreeSet</* M3-LEAF kind=EMPTY id=c316e7723eb1cd717e56b21312bc22ec4100b75057b0875c5a9668c8cc3c04e9 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
>(FACT_ORDER))
                    .add(/* M3-LEAF kind=CONSTRUCTION id=840d4fe22b28982550d3708cced8b6a7866ce26e40b3af543ee1d833e9138985 contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
new Fact(/* M3-LEAF kind=INVOCATION id=a4f2239aa5a86825e7a3070a350f5d7a3843771e5f7296dae298ce69e8d91ae4 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
row.kind(/* M3-LEAF kind=EMPTY id=469a4e249e5abb0e2ffae0807a19cbdc53828b4396895a662de827b8add7166e contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=1c0db4911d5f733b2994031e6a27c59ad2fee815ed0d97ddafde2d80b1d132a6 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 row.subject(/* M3-LEAF kind=EMPTY id=bd49d99d4d3fd1b50bf09ae64b82713ccbb179f5f67c2e28c1aa8ddaa7c3e93d contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
)));
        }
        /* M3-LEAF kind=LOCAL_DECLARATION id=51ed807dd8976e7b301391ba6391ac7ef219edbc9e07ec2ce1ba245a74dad1cc contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        List<Fingerprint> fingerprints = /* M3-LEAF kind=CONSTRUCTION id=1edcc3e00da97b8214a6d07c1403769a7b3fac5e70171d6dd7b8b0a4d3eed79d contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
 new ArrayList</* M3-LEAF kind=EMPTY id=8514722e53d7f1743be81019c4de6a2dc443e873c990887fea827959f132fc3e contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
>(/* M3-LEAF kind=INVOCATION id=6d6c749dcf55445a14d36f207ad9b9d4e0f5c95df466e6630219a51b7c46bf49 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
grouped.size(/* M3-LEAF kind=EMPTY id=62054a2ce198fdbeb5b09547f7372350243106f53a531e98f0487c4ad78d24dd contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
));
        /* M3-LEAF kind=INVOCATION id=fc25c3f99bff838f3f2dc112fdc996171901caa26d1143a0a9b3cdeb5f034ef2 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
        grouped.forEach((identity, facts) -> fingerprints.add(/* M3-LEAF kind=INVOCATION id=2c1d6cad6258406ffcf2d1dff5aefcde6facdd0c9770969cd1071326faffdf63 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
fingerprint(identity, facts)));
        /* M3-LEAF kind=INVOCATION id=986ddd9ba4c0655297bf37d5d9d2289fd13e0a2087a2f919870d7e5c123a6c30 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
        grouped.clear(/* M3-LEAF kind=EMPTY id=9a65aff4aa5abe72c73af12feddab112ea69016c6604f34b600bf7aaf7b096d4 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
);
        /* M3-LEAF kind=RETURN id=9815351e593ed431896a59453568565c9222ce4d7902a3b4e34a539f77d8281f contract=INTERNAL_OR_PACKAGE patterns=RETURN */
        return /* M3-LEAF kind=INVOCATION id=da06eeb12bed4a2077cb3589bdfa6c13399d272e3438d620fb2ffb72b3cbae5d contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 candidates(fingerprints, maxCandidateWork, maxCandidates);
    }

    /* M3-ATOM kind=METHOD id=9b049276d1667d382b89a35d9abfa9aa94c2f4f12983768e7b68746643d5b724 contract=INTERNAL_OR_PACKAGE patterns=BRANCHING,ITERATION */
    private static Fingerprint fingerprint(Identity identity, TreeSet<Fact> facts) {
        /* M3-LEAF kind=LOCAL_DECLARATION id=4896af362ca8cd7c465e3d250d1da7cf5edb72cd67af2a91cce7a05b8d6b1a79 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        MessageDigest pattern = /* M3-LEAF kind=INVOCATION id=522d90cbc7c89df0ee964951d4e2a6936ce683d0777de6c0d18ac6ac0d4df790 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 Hashes.sha256(/* M3-LEAF kind=EMPTY id=8948004d301d68856865dccf4c7b16c01c576daca20dea5426993be321cb8811 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
);
        /* M3-LEAF kind=INVOCATION id=3686f830477a6d270501853d15fa11585ae91d27e64d699d4d880fbf1a4af485 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
        Hashes.field(pattern, "m3-fact-pattern-v1");
        /* M3-LEAF kind=LOCAL_DECLARATION id=ce7e5307456d08a9ac4ec958a7e03f91e42d39570d6e31278ca7a6a2ce67e39d contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        int[] votes = new int[64];
        /* M3-LEAF kind=LOCAL_DECLARATION id=389d6b12470833efce3f1a6c71b3f925e12170f1a7ac236ab0f33f325cabf84b contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        MessageDigest token = /* M3-LEAF kind=INVOCATION id=087eb9b0de0a81ea42945ea939f46647b010b3c3eaa4ae6b1666eed29563a026 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 Hashes.sha256(/* M3-LEAF kind=EMPTY id=409278c640a0593ee4bdd7f5f9ba6318fae2502ad546c2c0c9e2ee966da1e520 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
);
        /* M3-LEAF kind=ITERATION id=c16a360fa08ca642767f8e5c214ba0b9c41fac48b394bb19167eedc00b5fef07 contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
        for (Fact fact : facts) {
            /* M3-LEAF kind=INVOCATION id=8ddfbc2b64a5af278932194056b831d305f51ca835c2e8ba1e4613bbce3891c2 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
            Hashes.field(pattern, /* M3-LEAF kind=INVOCATION id=5edd23409c6027d289ca7868641d03672f274306e10fcd4def66b51b16d0ee32 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 fact.kind(/* M3-LEAF kind=EMPTY id=c45820e1c0e54f667c32d454b4f39f7d8541cff0ebbf0d59c203cfc6c975ccd3 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
));
            /* M3-LEAF kind=INVOCATION id=15bc7c36913f1d60a9f3cf6ab1dabd3da7968b1001aede3c394e0a1b15527b3c contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
            Hashes.field(pattern, /* M3-LEAF kind=INVOCATION id=42c6ee38dd278249ce0ed00b429a04769c5d3cf15a2ce6a9cb477dadb816ba99 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 fact.subject(/* M3-LEAF kind=EMPTY id=b071d64a951464ec1b3730ec0081a371cf4b9fef3e33bc2680c5b5f180f69787 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
));
            /* M3-LEAF kind=INVOCATION id=60ae0780c5cedcbb9d5a2fa097fc56ae56e84672c9b6e9c1a2c3a105f415cdb8 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
            token.reset(/* M3-LEAF kind=EMPTY id=099f48e12d4e96abc441297911bef86361d6c74f9e5af9bb69d277d9c3c77043 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
);
            /* M3-LEAF kind=INVOCATION id=57d667f8cbe142b9ec6e55381ccc09eca06d7cfbabe08fad69741a4b5706213a contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
            Hashes.field(token, "m3-fact-simhash-token-v1");
            /* M3-LEAF kind=INVOCATION id=f51eeccf4c7a20ed38511ccbf7cab96c999f303a755d83f036abb117a73ffe2d contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
            Hashes.field(token, /* M3-LEAF kind=INVOCATION id=8a4d592047564fdccc1f8730f1d2736e23f9edc4af54d32221f09d0d397fd95d contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 fact.kind(/* M3-LEAF kind=EMPTY id=cc99fa01ce35f9d12f9ead02bb94639e2e5f765b6c3227e2e941cfe37759656a contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
));
            /* M3-LEAF kind=INVOCATION id=d3e76bd86cbb4a7ca000e175b8f6f79bb6b61e09bae3da105c6a32323e214ecb contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
            Hashes.field(token, /* M3-LEAF kind=INVOCATION id=d9203dd28110a0bc5be8626a7b29356e08437e942c269e29a0a6167730f38663 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 fact.subject(/* M3-LEAF kind=EMPTY id=23fe300cb20cd01c564cb6c947b62a31bf254540e8be384b0b6a9ebe427c1992 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
));
            /* M3-LEAF kind=LOCAL_DECLARATION id=33839fe0db38e722091673b794df072f4fa88b37964b629e473a665b5de2b569 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
            byte[] hash = /* M3-LEAF kind=INVOCATION id=5ea2f1ab5dee45dc2df1463e7d02cafafcd64f12f3ece7f4f1875cdfc9d4485b contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 token.digest(/* M3-LEAF kind=EMPTY id=d682c1f69b62680d68180906cce947618a66548ad93de5286dc3c3e28144cb38 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
);
            /* M3-LEAF kind=LOCAL_DECLARATION id=1485c27f27f99ef59b63bc0af808b96395ef39785e40e4222caccd796a222c20 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
            long bits = 0;
            /* M3-LEAF kind=ITERATION id=aa1f139c744652e695ee38bbf8a3da91ecc712851db11a5d5b6821ba7bd728ef contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
            for (int i = 0; i < /* M3-LEAF kind=FIELDACCESS id=3e063c576d7e19d64fb4e780ff3cdfe7a4f40507ff7166ed383ed7cf6b8f9bd4 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
 Long.BYTES; i++) /* M3-LEAF kind=MUTATION id=33972a3ca9e58f95972532723d51f132a7fcba5e9b800c4ebf2d7650dcc2bdce contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
 bits = (bits << 8) | (hash[i] & 255L);
            /* M3-LEAF kind=ITERATION id=8cfbbd6c555d3948548d76aabe65b671c7cb537f709ce64af3b62b5f2d485f89 contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
            for (int bit = 0; bit < 64; bit++) /* M3-LEAF kind=MUTATION id=0ea75a9dbff9638133386b9913bcc2ea1f146c99928a845b13d814673c3d15d0 contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
 votes[bit] += /* M3-LEAF kind=TERNARY id=62e3134a9ed76c0af6835cb4634d2abab79a608b5742aac573df8a3cfae03893 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
 ((bits >>> bit) & 1) == 1 ? 1 : -1;
        }
        /* M3-LEAF kind=LOCAL_DECLARATION id=e7fceb8800fd3646946e4be3aeaef2fac62ec3a86a66399c33012e0e9f0a1656 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        long simHash = 0;
        /* M3-LEAF kind=ITERATION id=707cc25ec316b5bde2c4c54ebf1c578b7d1af96fec3a2f48281ba53ddca1e15d contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
        for (int bit = 0; bit < 64; bit++) /* M3-LEAF kind=BRANCH id=7dd46b816830d8b6e2b6c4cc706b5064a5e6c8812826d5000586f5037f0d212f contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
 if (votes[bit] > 0) /* M3-LEAF kind=MUTATION id=f08de188e59e339f6ff9e7b0e01a313ef4d93db40e61b0e7bf7a891657f3ffe7 contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
 simHash |= 1L << bit;
        /* M3-LEAF kind=RETURN id=bae8a4012a35e236d77eafe041465c4366a34c68b0f351cdfd3a01454ff9dee2 contract=INTERNAL_OR_PACKAGE patterns=RETURN */
        return /* M3-LEAF kind=CONSTRUCTION id=c6419678d9cc936624edb2a28b27e6041bf56d7f262eb83aa231dc0905256d95 contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
 new Fingerprint(identity, /* M3-LEAF kind=INVOCATION id=ea134a087de57076e6461c989df54397475acd18413f13709d9747d4c47ff4e4 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 Hashes.hex(/* M3-LEAF kind=INVOCATION id=a2340b5a1f426ff20b63e3931721d62e77863fd8cf4251a8cf56c33d91c22834 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
pattern.digest(/* M3-LEAF kind=EMPTY id=01c58d013b9ee7affd6d35c3669c3b9da1815675e28afb64d29a717812215432 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
)), simHash, /* M3-LEAF kind=INVOCATION id=2ad7893f1c28f704ed64ffa174dd178fb213461333632746a9347cbbcd44debc contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 facts.size(/* M3-LEAF kind=EMPTY id=b22f9b2380ebe942e8b345f2aa4fb742b8a7c2c5b509c861779c967e973611f2 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
));
    }

    /* M3-ATOM kind=METHOD id=a76d354de4ffe0f800df01472243c1570216918e633468c49103b2e419ab46c9 contract=INTERNAL_OR_PACKAGE patterns=BRANCHING,ITERATION */
    private static Result candidates(List<Fingerprint> fingerprints, long maxWork, int maxCandidates) {
        /* M3-LEAF kind=LOCAL_DECLARATION id=75dbc478113a5ad0d78fb7fd082b8248b27ae5fb96cc335683ee114c90fd65f4 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        List<Candidate> candidates = /* M3-LEAF kind=CONSTRUCTION id=09fc085cb02aad0534c168731367d29dd6175f409d5035c9d382692ba4a0c5b6 contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
 new ArrayList</* M3-LEAF kind=EMPTY id=30936ce11cb585e3405a66dc247995155a3beae697122d3a138e3b8fb4d3d9a6 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
>(/* M3-LEAF kind=EMPTY id=b1ba75825f32293317f6dc078b52b09a156ef31e6c6e971781486deaa85375bc contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
);
        /* M3-LEAF kind=LOCAL_DECLARATION id=f3fbe20d84eb6abb62e6241282f9dc400f8658846e75e559c4b09ce0d98211cb contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        Map<String, Integer> exact = /* M3-LEAF kind=CONSTRUCTION id=dc929f3a1b63b0efbdde6e6eb87e15697e62ad3da1180b75db0bf0dd13af6f4e contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
 new HashMap</* M3-LEAF kind=EMPTY id=ecdca7917e0a4df8d32ae3948ef7ac278db23833b1cad03fc4055be543cfa9fa contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
>(/* M3-LEAF kind=EMPTY id=accb866d4b9a9b5a28e930986338a1b9b0be3c5b06c0321f48f4fe8502399d33 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
);
        /* M3-LEAF kind=LOCAL_DECLARATION id=3a83b8e31169191e97020d9f529c9a416b077d329a9c42a5f16643b2ca8caad0 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        int[] representatives = new int[/* M3-LEAF kind=INVOCATION id=4c6acee97255a813466468780c466d36ff59de6ef1822c9f4cd5bb0663a0cf86 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
fingerprints.size(/* M3-LEAF kind=EMPTY id=ffb6578245403acd3c314b336294612f7d4841412a28452445453543823ae8d8 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
)];
        /* M3-LEAF kind=LOCAL_DECLARATION id=deee3300592601f7869b438fce7bcec99420bd21a47b0a62d8205f92857aa742 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        int representativeCount = 0;
        /* M3-LEAF kind=LOCAL_DECLARATION id=27f62d06244d1170935d2c99ee3eb8d63ed7aab017fe570556ac1fe2bfc8e60d contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        long work = 0;
        /* M3-LEAF kind=ITERATION id=8b045ab27cd0c30e17c8dd7a33ad0d6ad4acd64380beacc3f1bb0f5b1935e447 contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
        for (int i = 0; i < /* M3-LEAF kind=INVOCATION id=808ab089bc1fa7b72f42be7beefc8b14bd779241c1a97b73b15666f548c1b20e contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 fingerprints.size(/* M3-LEAF kind=EMPTY id=d93022c2dead84e29455f095f839bdd62add9f9c70b7822b5cd7bf3434d6c3e8 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
); i++) {
            /* M3-LEAF kind=LOCAL_DECLARATION id=14c27b06f9e0c5c61e4acd3b537f9c15b28a7053d7289860b31d1cf6694ab32d contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
            Fingerprint current = /* M3-LEAF kind=INVOCATION id=084828bbfa5d963dc44942da61d4967772ac3d2d13cf8963495e0c209b6457fc contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 fingerprints.get(i);
            /* M3-LEAF kind=LOCAL_DECLARATION id=16d6c1f53e24d492db75def5c3d75c8faa888251480a93fdb9ba8d07375ad10a contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
            Integer representative = /* M3-LEAF kind=INVOCATION id=d19e7650d3ccfa63c684378e59c3701e16bf87743260799eb74c3266599e8713 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 exact.putIfAbsent(/* M3-LEAF kind=INVOCATION id=b49f532e84cd290a7925f676c0684ddbb3a5850ae463249d7b3435af8c35f712 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
current.factPatternSha256(/* M3-LEAF kind=EMPTY id=7777921c11b9d04626f783b3d2a727d358bc7bea3a78996cae3526f43c985f5f contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), i);
            /* M3-LEAF kind=BRANCH id=58a4662c8e3778beef97f705b58d67504b70d5de25029ded631855d294b6616a contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
            if (representative == null) {
                /* M3-LEAF kind=MUTATION id=ea041cb44f4deb1e42eb76f0c87f97d5eb2ea489564b6f2602c5d07bf656ae8e contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
                representatives[/* M3-LEAF kind=UNARY id=1a1c1fe3679201c817786b43d5c3b9c123bffe987c72b257781b2a184106463c contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
representativeCount++] = i;
            } else {
                /* M3-LEAF kind=BRANCH id=d1b5fb2abc74e377756ab50ffd52f8338216f33016340a432695ca722cf959df contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
                if (work == maxWork || /* M3-LEAF kind=INVOCATION id=2489d52cde38ac8b3be060f8110e5d608b025e38c6140f9015dd6118f5451709 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 candidates.size(/* M3-LEAF kind=EMPTY id=52042a41a9312dc80096bb34b53f3868ae6055538304aed823703d3e5aa20463 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
) == maxCandidates) {
                    /* M3-LEAF kind=RETURN id=45367dcf1a02fd68ab25eb704c7f330b219392dd2684648c6521ab09b1d8a524 contract=INTERNAL_OR_PACKAGE patterns=RETURN */
                    return /* M3-LEAF kind=INVOCATION id=dab608a425d9f74b194df6c348e8be17f87e890e520f61f8e0344b5dc08480e9 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 result(fingerprints, candidates, work, true);
                }
                /* M3-LEAF kind=UNARY id=24a04671239e83384c0fb13d213baf91e195985affc9b091a29521e755be455b contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
                work++;
                /* M3-LEAF kind=INVOCATION id=a03a27eaa6e08ba6fe382938b305eb8a4ed669d2f3af95588b37a5bc3ae753b0 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
                candidates.add(/* M3-LEAF kind=CONSTRUCTION id=d0acd991c3750ee321142491280a545d4b2ff46f91ec194cb8a11553854fa1cd contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
new Candidate(/* M3-LEAF kind=INVOCATION id=4fe09c10aed506302b4134189751f3a03cc7c22e0f3815b65d854252fc269a15 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
/* M3-LEAF kind=INVOCATION id=2b9da0bb0f919d02c821a2c51eadc7fed062c3a96b62001c086e39ca73457460 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
fingerprints.get(representative).identity(/* M3-LEAF kind=EMPTY id=46795e27b106aaeaac7e9d237a4f7dedbde401093cf98fea48e10bccd358cc77 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=2df5203892486bbae775d502800245fda63adb15915a4b8ae379a24542e9fd30 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 current.identity(/* M3-LEAF kind=EMPTY id=a2f268176f913aff7137b301213d14b4a675f5cfdf5307e2fc459e601d092db8 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
),
                        /* M3-LEAF kind=FIELDACCESS id=b6f01efa22bcacf884892b2bc5140b42fabb402ca1605584d9f31e9e7e0b77ee contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
                        Kind.EXACT_FACT_PATTERN, 0));
            }
        }
        /* M3-LEAF kind=BRANCH id=6f4bcd0591af6b47990ed028d262a5be73325689fca9926506a896bdd6794299 contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
        if (representativeCount < 2) /* M3-LEAF kind=RETURN id=509afb3eeda8d0aab050ada6573a28891b8f4a3fc740d18ffe80984b8efd6b3e contract=INTERNAL_OR_PACKAGE patterns=RETURN */
 return /* M3-LEAF kind=INVOCATION id=35a6d2155a37547206f18d8b6c73ae5615da23119f8f1b1ac962dae62ccb4456 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 result(fingerprints, candidates, work, false);
        // Hamming distance <= 3 leaves at least one of four 16-bit bands unchanged.
        // Primitive bucket chains avoid boxed pair sets and all-pairs scanning.
        /* M3-LEAF kind=LOCAL_DECLARATION id=cead456c9b8f751f8fa7108c9efa604ff317f918609b5a4b1fb0b74e20719863 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        int[] heads = new int[4 * 65_536];
        /* M3-LEAF kind=INVOCATION id=5548108d7d2d9a96124f4be97d78cb14a3a4cc5be2035d2f3e4c12896ffc7809 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
        Arrays.fill(heads, -1);
        /* M3-LEAF kind=LOCAL_DECLARATION id=7888c864c8758402bf5b27b3e25d54123d2f64455735456466370b503195acfc contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        int[][] next = new int[4][/* M3-LEAF kind=INVOCATION id=fdbcff2fc89c89b5970a42db4c5328c7c9ffbc4553a377dd98a0f313850f2d45 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
fingerprints.size(/* M3-LEAF kind=EMPTY id=2a1f4017faab26d6228cf57108a57623925c99b5b434f66dad0c63cf64e94030 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
)];
        /* M3-LEAF kind=LOCAL_DECLARATION id=a08fe5457b96de87c59bea723c172d658f8c174d218d53b393d3d4debf56187e contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
        int[] visited = new int[/* M3-LEAF kind=INVOCATION id=c984d6b889237a891de71528064f52366dc79562e403050691fce305a09e0bad contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
fingerprints.size(/* M3-LEAF kind=EMPTY id=1c381170ea24e370ea088484205e34e4560a7bef9f9f9ba31caaef64de00bd12 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
)];
        /* M3-LEAF kind=ITERATION id=0e8b2355adc74d564482f54431ec14dcdb8b6166aaf138dcf0ad853333bca218 contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
        for (int position = 0; position < representativeCount; position++) {
            /* M3-LEAF kind=LOCAL_DECLARATION id=f6b0cc4a91e1f683bd358771ce1be17cce2eadefa3fc53d4de7fd39b9b1ccd9e contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
            int currentIndex = representatives[position];
            /* M3-LEAF kind=LOCAL_DECLARATION id=86049eaa482c3181d35fe43de1ca22d9b755c7bdc7713885a6a05b6240a0e190 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
            Fingerprint current = /* M3-LEAF kind=INVOCATION id=c3f462254706249342add8fdbe85e88cf70525d838e222c732fb0e06c62701de contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 fingerprints.get(currentIndex);
            /* M3-LEAF kind=ITERATION id=9bc8e2447c5bbc61c0098ed57fdd280146105d41dacd72a7c4e0b81e6c8b208e contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
            for (int band = 0; band < 4; band++) {
                /* M3-LEAF kind=LOCAL_DECLARATION id=2c5859c83fd6a297008deadefc34be718bba04417592619f92ca4855c1dce5bd contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
                int bucket = /* M3-LEAF kind=INVOCATION id=5e0e23e77db1f94bb2e8b6bd7ddcf2614cd30b1d5a61829e92c4cfe61662be08 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 bucket(/* M3-LEAF kind=INVOCATION id=af57247f6678d514fbad3655310335a328952ba4c281b10bd4f374c2d4cb7f4b contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
current.simHash64(/* M3-LEAF kind=EMPTY id=f34e3159e71707d57d3261bf5c47086ef5c2158af5caad23b6154821f3bf78d0 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), band);
                /* M3-LEAF kind=ITERATION id=70bd44bbfd44f69693833bf2a21754e5affdc576ca2506b1ad3a983eda1d7024 contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
                for (int prior = heads[bucket]; prior >= 0; prior = next[band][prior]) {
                    /* M3-LEAF kind=BRANCH id=7718fc492f91654741bb7af4217c3f19cf662e3e53aded79784867dc4ee1ad89 contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
                    if (visited[prior] == currentIndex + 1) /* M3-LEAF kind=CONTINUE id=ed8d49d1e2c5b43c81fbf9116bee2a712602be76b7c8a979ca4b02c47b530edf contract=INTERNAL_OR_PACKAGE patterns=CONTROL_TRANSFER */
 continue;
                    /* M3-LEAF kind=MUTATION id=329ca466bad8232590447a31853140fd9de9364468a935f34f7602a42302d114 contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
                    visited[prior] = currentIndex + 1;
                    /* M3-LEAF kind=BRANCH id=c4cfa186f7be59f74cb32896e89ceb9621ab0194d6877c80e582b727380dd763 contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
                    if (work == maxWork) /* M3-LEAF kind=RETURN id=467078b1e849adfadecdaa36313d26d0cabbb414135b9253940a7f5a1daa97db contract=INTERNAL_OR_PACKAGE patterns=RETURN */
 return /* M3-LEAF kind=INVOCATION id=33bfbf7d915540198eceb76281f53ce74dbb5474b51d8f7be7a70c14af56518d contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 result(fingerprints, candidates, work, true);
                    /* M3-LEAF kind=UNARY id=5e1c569cc4d4a0084792dca56bca02d61874ac1bb53b2d83138d405712c56b29 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
                    work++;
                    /* M3-LEAF kind=LOCAL_DECLARATION id=2f74644b5dffce0dc3ec829dd22d7d95ca84c6072ed275bbab193ab4c3da4fc3 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
                    Fingerprint previous = /* M3-LEAF kind=INVOCATION id=742a973029084d3a551fc8660409a16c3620dacc3e42dd290a808350e0c7881c contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 fingerprints.get(prior);
                    /* M3-LEAF kind=LOCAL_DECLARATION id=a3074c0142ff711794d7a911f61a92da4b8e78fe6b99fb00a66a0828da6d3042 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
                    int distance = /* M3-LEAF kind=INVOCATION id=9bf16a25787a790bdcd03a29a4570f08fb3383b1dda657419a7f6b6d6458cab1 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 hammingDistance(/* M3-LEAF kind=INVOCATION id=93825898585656bd8890815edf3bbd95a7fe4fa003cbb0ddbfb0d370bbfedb9f contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
previous.simHash64(/* M3-LEAF kind=EMPTY id=595111e59136790035cce8813c54b00de7d9ab2b65c2634e8d16f6089f06ba39 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=162a018f07e5e79cc971e648170ace0ed4cfc10b1f0ad9cc3dac775d2647419f contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 current.simHash64(/* M3-LEAF kind=EMPTY id=04d04d5e7c9348043032f615c42ef5c4c15cdd882f8495ef9a6cbac276339e43 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
));
                    /* M3-LEAF kind=BRANCH id=ab8770bdafbf18c8bed5d87f92294905d90bef9433a364444df577f73ac3d1b9 contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
                    if (distance <= 3) {
                        /* M3-LEAF kind=BRANCH id=7bea9df8a28bd401f6b266856e2c1efd05ffd5ed01f1b42002acc74ac45c7a88 contract=INTERNAL_OR_PACKAGE patterns=BRANCHING */
                        if (/* M3-LEAF kind=INVOCATION id=7e5164742ba4da08ca08c319ce13e14867163d859bea462a958664e2ba29175e contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
candidates.size(/* M3-LEAF kind=EMPTY id=bfb1a4e29c1217db180bff22a19b0030ea657c9230507c6ea43ace1966144227 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
) == maxCandidates) /* M3-LEAF kind=RETURN id=80fe4c4b5aac32d4680072245e899edb80ad03327c6f49a75d8d3946fd10c0d3 contract=INTERNAL_OR_PACKAGE patterns=RETURN */
 return /* M3-LEAF kind=INVOCATION id=e4f7ba3eedf81f23061d0eb76263c211fb2fbd049d57136fde25bab8665ac39c contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 result(fingerprints, candidates, work, true);
                        /* M3-LEAF kind=INVOCATION id=fdc8938a4c9e0b3a09d662da9d6a177024356dd4aebd589b75fa5371bb07dd0d contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
                        candidates.add(/* M3-LEAF kind=CONSTRUCTION id=aa89a9d2813424fbcd5c37c5d8f4045e2bd1fbb26f8f1b87a78f530012275644 contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
new Candidate(/* M3-LEAF kind=INVOCATION id=fbc37df062269cf53cb55a475c93428b5e7fd0441f08e6879c89b41956e7ea09 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
previous.identity(/* M3-LEAF kind=EMPTY id=e370a480e8f1d446c0d24f2a63c32bcb61590d1509c2aa021d2f48f6424d52b5 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=INVOCATION id=1969b82a4b9bf57f369a3f1796f2c4be284c45bef9d9859d3687b02e947e40b7 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 current.identity(/* M3-LEAF kind=EMPTY id=6902c53dcbddb4ec4404d88d26720c4683e509a1204bfdf075d48c7788215250 contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), /* M3-LEAF kind=FIELDACCESS id=83c1f8a9c1b4dcaca11659492f62b774b7766a97a5d3269feda322b22727206c contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
 Kind.NEAR_FACT_PATTERN, distance));
                    }
                }
            }
            /* M3-LEAF kind=ITERATION id=1a6c059a1218f8f0b8b78cc2af63d40eccb31d6c9c3461e2bf07971bbebf73b7 contract=INTERNAL_OR_PACKAGE patterns=ITERATION */
            for (int band = 0; band < 4; band++) {
                /* M3-LEAF kind=LOCAL_DECLARATION id=373dc0279d4c85f7f9e7d47d2f072b1706444c325a633a279872786632cf17d1 contract=INTERNAL_OR_PACKAGE patterns=LOCAL_DECLARATION */
                int bucket = /* M3-LEAF kind=INVOCATION id=a74a2061cf338e26c1456ba21478f8298444e640f36dcdf983961da2ac0c72f0 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 bucket(/* M3-LEAF kind=INVOCATION id=59318a1f072e1192155741dd9aaae1e4a6db9048f88a5da9f5ed3c8e87dc273a contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
current.simHash64(/* M3-LEAF kind=EMPTY id=3e372a04b2e820683c96690a0326ba6436b9d7bf3252ac41668327df93a6772c contract=INTERNAL_OR_PACKAGE patterns=STATEMENT */
), band);
                /* M3-LEAF kind=MUTATION id=ff7f4bc4b8beb1b9b3c7ed7f6f397f4beecc9c37928e2e2110d58c19ff1264b8 contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
                next[band][currentIndex] = heads[bucket];
                /* M3-LEAF kind=MUTATION id=d33379b620b23a3386d8913ca3b4f2f2c89f63e3e07840f74a3b4d0ae955c6e0 contract=INTERNAL_OR_PACKAGE patterns=MUTATION */
                heads[bucket] = currentIndex;
            }
        }
        /* M3-LEAF kind=RETURN id=88850d409aca68d938d32df809c850a5209729a0744077a9920ea59177e08fd3 contract=INTERNAL_OR_PACKAGE patterns=RETURN */
        return /* M3-LEAF kind=INVOCATION id=75eed0a5cae6dcdeafe85473ef4cd38f8d319d6700f4d70f896832662ce1c1a8 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 result(fingerprints, candidates, work, false);
    }

    /* M3-ATOM kind=METHOD id=2a1e6478997ea6e9f72563c95b138b992e69515e65a367f70755b708c9fe496e contract=INTERNAL_OR_PACKAGE patterns=STRAIGHT_LINE */
    private static int bucket(long fingerprint, int band) {
        /* M3-LEAF kind=RETURN id=68b123dfbb516c35a03f993e6348362f2c0f418512a8419cbe16dba71c27db5d contract=INTERNAL_OR_PACKAGE patterns=RETURN */
        return /* M3-LEAF kind=INVOCATION id=5b4abbd49807bc1b64489a4d1dd7f0c06e613482193862233df9ca2925d71c05 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 _m3Atom_1(fingerprint, band);
    }

    /* M3-ATOM kind=METHOD id=f94b3bcec515cbff057d80107cd77b5f20d862f0dcb3d996bcce5daca9689811 contract=PUBLIC_API patterns=STRAIGHT_LINE */
    public static int hammingDistance(long left, long right) { /* M3-LEAF kind=RETURN id=2651217c6c96be8128c712e798ead5d780224c54c062e762548e6a3ceeb8874d contract=INTERNAL_OR_PACKAGE patterns=RETURN */
 return /* M3-LEAF kind=INVOCATION id=66071aa910af8b59394e0082907308d54d647d9739ddee9b3c79553bd99959b1 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
 Long.bitCount(left ^ right); }

    /* M3-ATOM kind=METHOD id=681f3a2fbf701cb0bf0ea012d2fd20b099fda46a849c649404911aaa9fa9e0c2 contract=INTERNAL_OR_PACKAGE patterns=STRAIGHT_LINE */
    private static Result result(List<Fingerprint> fingerprints, List<Candidate> candidates, long work, boolean truncated) {
        /* M3-LEAF kind=INVOCATION id=91c2cba848ec8850ded93af2f85de20e134b66baf440a9dcedcdacee1d6e9eb4 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
        candidates.sort(/* M3-LEAF kind=INVOCATION id=00a483d2a9fb586548f4740e669e807f2618e462eb166ea79399fa4881509782 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
/* M3-LEAF kind=INVOCATION id=f924e66d295538814deab51573ab068014a0ed02e6a48461f87e1378f6ae6151 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
/* M3-LEAF kind=INVOCATION id=0234304e48a302d5e8b75c6afba183795749a4521e76be7dee591364556c5078 contract=INTERNAL_OR_PACKAGE patterns=INVOCATION */
Comparator.comparing(Candidate::left, ID_ORDER).thenComparing(Candidate::right, ID_ORDER)
                .thenComparing(Candidate::kind));
        /* M3-LEAF kind=RETURN id=a53054876405f4c07342161c74f19107ef0d6d9688b4b8f2d5d6a15096c26369 contract=INTERNAL_OR_PACKAGE patterns=RETURN */
        return /* M3-LEAF kind=CONSTRUCTION id=78684732ca52500b7d68afd1716598ee6f932a0ee4103dbf80182583b4d8cca3 contract=INTERNAL_OR_PACKAGE patterns=CONSTRUCTION */
 new Result(fingerprints, candidates, work, truncated);
    }

    /* M3-ATOM kind=METHOD id=43862ac4a368f30f40762a98933cdf8abec106141e9b0bd71d2058e9e487b2d5 contract=INTERNAL_OR_PACKAGE patterns=STRAIGHT_LINE */
    private static int _m3Atom_1(long fingerprint, int band) { /* M3-LEAF kind=RETURN id=bfb593a8155371d4dbff64eb3db22afa05f7dff69b515e354e3daba116b5be61 contract=INTERNAL_OR_PACKAGE patterns=RETURN */
 return (band << 16) | (int) ((fingerprint >>> (band * 16)) & 0xffffL); }
}
