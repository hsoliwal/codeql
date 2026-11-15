# M3 CodeQL

M3 CodeQL extends this fork with a Java semantic evidence pack and a Java 21 command-line tool. The existing CodeQL queries, libraries, extractors, schemas and default suites remain the upstream implementation. The M3 suite is explicitly selected alongside any security suite you already use.

## What is included

| Capability | Product owner | Result |
| --- | --- | --- |
| Callable contracts | `ql/src/inventory/CallableInventory.ql` | Public/protected source methods and constructors, signatures, visibility and source spans |
| Call dependencies | `ql/src/inventory/CallDependencies.ql` | Statically resolved source call sites and their caller/callee declarations |
| Native boundary inventory | `ql/src/inventory/NativeBoundaryInventory.ql` | Java `native` declarations and source spans |
| String interning review | `ql/src/policy/StringIntern.ql` | Semantically resolved `java.lang.String.intern()` calls for bounded-pool review |
| Loop allocation review | `ql/src/policy/AllocationInLoop.ql` | Explicit object/array allocation candidates syntactically associated with loops |
| Reproducible analysis | `tools/` | Query catalogue, deterministic sharding, CodeQL process plans, stage logs and proof reports |

Inventory rows are evidence for a proposed refinement. They do not establish behavioral equivalence or authorize source mutation. The call export records static resolution, not a complete runtime dispatch graph. Native declarations do not establish exported JNI symbols, native implementation coverage or ABI equivalence. A loop allocation finding is a review candidate; loop hotness, lifetime, escape behavior and safe replacement still require measurement and contract tests.

The query library uses CodeQL's Java/Kotlin semantic model. This release qualifies Java fixtures; broad Kotlin coverage is not claimed.

## Build the Java tool

Use JDK 21 and Maven 3.9 or later from the repository root:

```sh
mvn -B -ntp -f m3/tools/pom.xml verify
java -jar m3/tools/target/m3-codeql-tools-0.1.0.jar --help
```

The runtime tool uses only the JDK. Maven downloads test/build dependencies in a normal connected build. It does not bundle the CodeQL engine.

## Inventory and plan

Choose a fresh output directory outside the source root:

```sh
java -jar m3/tools/target/m3-codeql-tools-0.1.0.jar inventory \
  --root m3/ql/src --output /tmp/m3-query-catalogue

java -jar m3/tools/target/m3-codeql-tools-0.1.0.jar verify \
  --root . --output /tmp/m3-codeql-plan --codeql /path/to/codeql
```

`verify` and `analyze` plan commands without invoking CodeQL by default. Add `--execute` to run the recorded commands. Use a different output directory for each run so earlier evidence is retained. Source and output roots may not overlap. The `inventory` command immediately performs its read-only source scan and writes its catalogue.

The catalogue is sorted by relative query path before sharding. `--shards N --shard I` selects one zero-based shard; the union of all shards covers the query catalogue. SHA-256 identifies exact source bytes. Repeated content is a review signal, and no files are automatically merged or deleted. Normalized/semantic duplicate detection is outside this version's inventory implementation and is explicitly reported as unimplemented.

Use `--root m3/ql/src` for the M3 pack's strict metadata-ID check. A catalogue of the entire upstream checkout also includes test queries and wrappers that intentionally repeat IDs; these appear as review findings and make strict inventory return a nonzero status while retaining its reports.

## Execute verification

Install the separately distributed [CodeQL CLI](https://github.com/github/codeql-cli-binaries/releases). The workflow runs Linux and native Windows lanes, pins version 2.27.4, and verifies the published SHA-256 for each platform's archive.

```sh
java -jar m3/tools/target/m3-codeql-tools-0.1.0.jar verify \
  --root . --output /tmp/m3-codeql-verify \
  --codeql /path/to/codeql --threads 2 --ram 3072 \
  --timeout-seconds 600 --execute
```

The tool stages a separate M3 query/test copy for CodeQL's working files and invokes the actual formatter check, query compiler and semantic fixture tests. It passes arguments as a list, keeps process output in stage logs, and stops after a failed, missing, interrupted or timed-out process. Later stages remain unrun. `--ram` is a CodeQL memory-budget hint, not an operating-system memory ceiling; CodeQL may enforce its own minimum. The timeout is per process.

It copies and hashes the Java library and shared QL packs into that working directory as well. Symbolic-link privileges are not required. On Windows, use native paths, for example:

```powershell
java -jar m3/tools/target/m3-codeql-tools-0.1.0.jar verify --root . --output C:\m3-results\verify-001 --codeql C:\tools\codeql\codeql.exe --execute
```

Java build/tests and the reviewed source diff remain separate steps before QL verification. A passing process test with a synthetic executable establishes process handling only. It is not evidence that CodeQL queries compiled or ran.

## Analyze an existing database

Create a Java CodeQL database using your project's normal build and the official CLI, then run:

```sh
java -jar m3/tools/target/m3-codeql-tools-0.1.0.jar analyze \
  --root . --database /path/to/java-database \
  --output /tmp/m3-codeql-analysis --codeql /path/to/codeql --execute
```

The M3 policy suite produces SARIF. The three inventory queries produce BQRS and CSV files with named, versioned columns documented in [SCHEMAS.md](SCHEMAS.md). They retain the semantics of the underlying database and extractor; the tool does not silently create or rebuild a database.

CodeQL can write analysis results and caches into the supplied database. Use a separate database copy when its original working state must also be preserved.

Use the standard upstream security suites as well when security findings are required. The M3 policy suite contains optimization/engineering review checks and does not imply complete security coverage.

## Evidence and compatibility

Each run records its context, provenance, planned commands, stage status, required outputs and final report. A plan, missing binary, skipped process or absent hosted runner is never recorded as an executed pass. Exact source hashes bind a run to its inputs; hashes do not prove semantic equivalence. Source remains unchanged by the M3 runner.

Compatibility is one-way: this fork retains the original CodeQL capabilities while adding namespaced M3 capabilities. Ordinary upstream CodeQL is not required to know M3-specific packs or report schemas. Existing IDs, default suites and analysis fixtures are preserved.

Reusable source-changing recipes and their qualification remain in the private Synexia repository. This public target receives executed product outputs, product tests and passive provenance. See [M3-SYNEXIA-NOTICE.md](M3-SYNEXIA-NOTICE.md).

## Current boundaries

This delivery does not replace the separately distributed CodeQL engine, implement new extractors, automatically rewrite findings, establish complete API/ABI compatibility for analyzed applications, or claim performance improvements without benchmarks. JNI acceleration is not introduced: this query repository does not expose the engine's native execution API.
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
