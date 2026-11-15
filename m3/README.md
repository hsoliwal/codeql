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
