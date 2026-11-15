# M3 CodeQL

M3 CodeQL adds a Java 21 evidence coordinator, semantic queries, and a repeatable
OpenRewrite installation recipe to this CodeQL fork. Existing CodeQL libraries,
queries, extractors, interfaces, and behavior remain available. The M3 commands
are opt-in and live under `m3/`.

The implementation follows the supplied M3Scale rules: inventory before action,
exact source identity, deterministic fan-in, explicit unresolved evidence,
contract preservation, and honest verification receipts. It contains no model
call in the analysis path.

## What runs

| Component | Executable behavior |
| --- | --- |
| `runtime` | Inventory and incremental change classification; fresh CodeQL extraction and queries; deterministic evidence and fingerprint reports |
| `ql/java` | Callable inventory, declared contract facts, call edges, and direct effect evidence from CodeQL's Java AST |
| `recipes` | An actual OpenRewrite recipe that reproduces the additive files from a SHA-256-pinned resource snapshot |

The coordinator owns evidence in primitive column arrays with a shared, VM-local
dictionary and creates row objects on demand. This is an in-process ownership
model. It does not claim that ordinary Java strings are backed by the M3 OS-shared
lexicon.

## Build

Use Java 21 and Maven 3.9 or later from this repository's root:

```sh
mvn -B -ntp -f m3/pom.xml verify
```

The runtime JAR is `m3/runtime/target/m3-codeql-runtime-0.1.0-SNAPSHOT.jar`.
Maven builds the M3 modules without replacing the fork's existing build systems.

CodeQL CLI is a separate prerequisite for semantic analysis and QL tests. The
qualification version is **2.27.4**. The workflow downloads that exact version and
verifies its archive digest; the CLI binary is not committed to this repository.
The source and provider identities are recorded in [provenance.json](provenance.json).

## Inventory and incremental changes

Choose a new output directory outside the inspected source tree:

```sh
java -jar m3/runtime/target/m3-codeql-runtime-0.1.0-SNAPSHOT.jar \
  inventory --source /work/project --output /work/reports/inventory-1 --workers 4

java -jar m3/runtime/target/m3-codeql-runtime-0.1.0-SNAPSHOT.jar \
  inventory --source /work/project --output /work/reports/inventory-2 --workers 4 \
  --previous /work/reports/inventory-1/INVENTORY.tsv
```

SHA-256 identifies file contents. Sorted paths and deterministic fan-in make
worker completion order irrelevant to identity. File additions, changes, and
deletions are visible in the incremental comparison. A matching individual file
hash is insufficient to reuse cross-file semantic analysis: other declarations,
dependencies, and call targets can change its meaning.

## Run semantic analysis

Run from a complete checkout of this fork, with a separately installed CodeQL CLI:

```sh
java -jar m3/runtime/target/m3-codeql-runtime-0.1.0-SNAPSHOT.jar \
  analyze --source /work/project --output /work/reports/analysis-1 \
  --queries "$PWD/m3/ql/java" --codeql /opt/codeql/codeql --workers 2
```

The coordinator copies the inventoried inputs, verifies the copy's complete
content digest, and creates a fresh Java database from that copy using
`--build-mode=none`. It runs the M3 queries, decodes their BQRS output, validates
and canonicalizes the rows, and verifies both the analyzed copy and the original
input boundary. It does not supply a project build command. Source mutation,
malformed evidence, an unavailable provider, timeouts, and nonzero tool exits
block successful analysis. Local query-pack and Java/shared library hashes are
recorded separately; generated `.cache` directories are excluded from these
query-input hashes.

This release's semantic pack covers Java. Upstream CodeQL's other language packs
continue to be available through their existing interfaces. Java no-build
extraction is not a Kotlin build or a successful project compilation; unresolved
external symbols and behavior remain explicit limitations. Static facts are
evidence for a candidate, not permission to change source code.

See [the query schema and categories](ql/README.md) for each exported field and
its precise meaning. The `call/static` category records the target resolved by
static extraction, including instance calls; it does not assert that the Java
method is declared `static` or enumerate every runtime target.

## Contracts and hashes

The integration preserves the distinction between different kinds of evidence:

| Evidence | Interpretation |
| --- | --- |
| File SHA-256 | Exact identity of bytes read from a file |
| Canonical evidence digest | Identity of the exported, ordered facts within the declared query scope |
| Exact-content cluster | Files sharing a content digest, offered for review |
| Fact-pattern SHA-256 | Identity of sorted exported `(kind, subject)` facts for a callable, with grouping names and anchors omitted |
| SimHash64 candidate | Approximate similarity of those exported facts, offered for review |
| Declared contract row | A partial signature/modifier/throws fact |
| Direct effect row | An observed local read, write, throw, monitor, or native declaration |
| Typed residue | Work that has not been proven or covered by this analysis |

A hash of signatures or effect rows is not the M3 `LogicHash` of a normalized
operation DAG. This integration does not claim full behavioral equivalence,
purity, complete runtime dispatch, whole-program effect closure, or compatibility
across every public contract dimension. Those obligations remain unresolved;
they do not become successful verification merely because a query returns no
rows. The coordinator performs no source rewrite or canonical promotion.

The candidate search groups exact fact patterns and uses four 16-bit SimHash
bands to find representative pairs at Hamming distance at most three. The
default bounds are one million candidate comparisons and ten thousand emitted
pairs. `SUMMARY.tsv` and `RESIDUE.tsv` explicitly report truncation. The grouping
omits method bodies: two different implementations can export the same facts.
Identifiers inside an exported fact's subject remain significant.

## Run artifacts

| Files | Purpose |
| --- | --- |
| `INVENTORY.tsv` | Complete file manifest, content hashes, metadata, and incremental status |
| `EVIDENCE.tsv`, `FINGERPRINTS.tsv` | Canonical source-bound facts and per-callable fact fingerprints |
| `CLUSTERS_EXACT.tsv`, `CLUSTERS_NORMALIZED.tsv`, `ACTION_QUEUE.tsv` | Exact-content and exported-fact review candidates |
| `TASK_PACKET.tsv`, `RUN_CONTEXT.tsv`, `PROVENANCE.tsv` | Declared scope, invariant locks, source/provider identities, and input hashes |
| `STATUS.tsv`, `PROOF_RECEIPTS.tsv`, `VERIFY_CONTRACT.tsv` | Run outcome, actual provider process exits, and separate verification gates |
| `RESIDUE.tsv`, `OUTPUT_CONTRACT.tsv`, `SUMMARY.tsv`, `FINAL_REPORT.md` | Unproved dimensions, output completeness, counts, and the run report |

All source inputs are inventoried, including build descriptors and version-control
metadata. Source symlinks are rejected. Output directories must be new and must
not overlap source or query/library inputs. The source copy requires additional
disk space. For a small fixture in a constrained environment, the verified
settings are a `-Xmx128m` coordinator with `--workers 1 --ram-mb 1024 --codeql-heap-mb 512`; CodeQL may round its
import/evaluator memory hint upward independently of the configured JVM heap.
Use a full JDK through `JAVA_HOME` and `PATH` for Java extraction.

## Reproduce and verify the integration

The recipe module is the owner of the generated implementation snapshot. Fix the
recipe inputs, regenerate, and verify a replay before publishing changes. Its
manifest records every destination and content hash. It admits missing files,
accepts an already exact match, and fails on conflicting existing content or
invalid paths. See [the recipe instructions](recipes/README.md).

Query checks use the real CodeQL engine:

```sh
codeql query compile --search-path=. --threads=2 --ram=3072 \
  m3/ql/java/Inventory.ql m3/ql/java/Calls.ql m3/ql/java/Effects.ql
codeql test run --search-path=. --threads=2 --ram=3072 \
  --check-databases m3/ql/java-tests
```

The tests include overloaded calls, constructors, field and array updates,
throws, monitors, native declarations, lambdas and nested classes, and apparent
code inside strings/comments. `.expected` files are generated by CodeQL tests
and are never hand-authored.

The dedicated [GitHub Actions workflow](../.github/workflows/m3-codeql.yml) checks
the Java modules, the recipe snapshot, the QL fixture tests, and a fresh
end-to-end analysis. Local qualification results and exact commands are recorded
in [VALIDATION.md](VALIDATION.md). A queued or unexecuted hosted check is not a
passing check.

## Licensing and source authority

The fork's MIT license continues to cover repository code. OpenRewrite is a
separate Apache-2.0 dependency. CodeQL CLI and its evaluator are distributed and
licensed separately; this repository does not relicense or reimplement them.

The supplied locked M3Scale document, expanded copy, and derived coding-agent
design are identified separately by hash in `provenance.json`. The expanded copy
does not silently replace the locked canon. The source documents themselves are
not required at runtime.
