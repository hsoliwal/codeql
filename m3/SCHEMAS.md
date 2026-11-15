# M3 CodeQL evidence schemas

The three semantic exports have schema version 1. All columns are primitive values with explicit names; no database-internal entity IDs are used as external identities. CSV is emitted by the official `codeql bqrs decode` command and must be read with a CSV parser, including quoted fields and embedded delimiters.

## CallableInventory

| Column | Meaning |
| --- | --- |
| `callable_id` | Canonical source declaration identity within this source snapshot |
| `kind` | Method or constructor |
| `declaring_type` | Qualified declaring type |
| `signature` | CodeQL callable signature with qualified parameter type names |
| `return_type` | CodeQL return-type display name; `void` for constructors |
| `visibility` | Public or protected |
| `source_path` | Relative source path |
| `start_line`, `start_column` | Start of the source span |
| `end_line`, `end_column` | End of the source span |

This export selects explicit source methods and constructors. It excludes compiler-generated callables, initializer holders, functional-expression bodies and implicit constructors. It is a useful callable inventory, not a complete binary/public API compatibility fingerprint: fields, exported modules, type hierarchy, generic bounds, annotations, declared exceptions and runtime effects need additional contract evidence.

## CallDependencies

Columns, in order:

`caller_id`, `caller_declaring_type`, `caller_signature`, `callee_id`, `callee_declaring_type`, `callee_signature`, `call_kind`, `source_path`, `start_line`, `start_column`, `end_line`, `end_column`.

Each row is a source call site and its statically resolved canonical target declaration. Virtual dispatch is not expanded into all possible runtime targets. Calls from constructors are included where the query's explicit-callable scope admits them. Library callees are retained even when they do not have a source location. See the QL query and its help for the exact scope.

## NativeBoundaryInventory

Columns, in order:

`callable_id`, `declaring_type`, `signature`, `return_type`, `visibility`, `is_static`, `source_path`, `start_line`, `start_column`, `end_line`, `end_column`.

Rows describe source Java methods declared `native`, including non-public declarations. This does not inspect a compiled native library or recognize dynamic registration through `RegisterNatives`.

## Source identity and ordering

Callable identity combines the qualified declaring type, callable kind and signature. Source-backed declarations also include their relative source path, start line and start column. The identity deliberately changes when those source coordinates change; it is not a persisted JVM/JNI ABI identifier or a rename-stable identity.

Each semantic query orders rows by source path and span, followed by the relevant callable identity. Results are deterministic for the same database, library/query bytes and CLI semantics. Different extractor versions or database contents may legitimately change rows.

## Policy output

The policy suite contains only problem queries and is suitable for SARIF. Inventory queries are exported separately as tabular BQRS/CSV and are excluded from that suite. Every policy finding requests review; the runner does not propose an automatic equivalent replacement.

## Runner TSV evidence

Runner outputs include a schema marker and escape backslash, tab, carriage return and newline as literal escape sequences so a source path or log message cannot create extra TSV rows or columns. The tool's own schemas and headers are emitted in the files. Keep the schema version with imported records.

`PLAN.tsv` records one command argument per indexed row, preserving argument boundaries without shell quoting. `STATUS.tsv` and `VERIFY_CONTRACT.tsv` distinguish local preparation, actual processes and stages that did not run. `PROVENANCE.tsv` binds input source paths and SHA-256 values. `OUTPUT_CONTRACT.tsv` records required artifacts and whether they exist. A missing artifact or unexecuted stage does not become a pass merely because a report file was created.

Query catalogue duplicate checks are lexical metadata checks, separate from QL semantic validation. Exact duplicate clusters are byte identities for review. Normalized/semantic duplicate clustering is not implemented in this version.
