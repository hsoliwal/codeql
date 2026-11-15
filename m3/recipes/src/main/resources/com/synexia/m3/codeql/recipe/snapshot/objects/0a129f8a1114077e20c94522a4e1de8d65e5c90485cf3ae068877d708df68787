# M3 Java semantic evidence

This opt-in CodeQL pack exports facts for the M3 Java 21 coordinator. It uses the
fork's public Java library predicates and changes no upstream queries, extractors,
or APIs. Run the exports against a database extracted from the exact source
snapshot that the coordinator will hash and verify.

## Tuple schema

All three exports use exactly these ten columns:

| Column | Type | Meaning |
| --- | --- | --- |
| `path` | string | Path relative to the CodeQL source root |
| `package` | string | Declaring Java package; empty for the default package |
| `owner` | string | Declaring type's qualified name |
| `signature` | string | Callable name and fully qualified parameter types |
| `kind` | string | Evidence category listed below |
| `subject` | string | Category-specific value |
| `startLine` | integer | CodeQL source-anchor start line, one-based and inclusive |
| `startColumn` | integer | CodeQL source-anchor start column, one-based and inclusive |
| `endLine` | integer | CodeQL source-anchor end line, one-based and inclusive |
| `endColumn` | integer | CodeQL source-anchor end column, one-based and inclusive |

Every row carries the **enclosing callable's CodeQL source anchor**, not the call
or effect site. This anchor often covers only the declaration's name token; it
is not the full method body or a source slice suitable for proving equivalent
logic. The coordinator binds facts to the full source-file hash and hashes the
canonical evidence tuples, without interpreting the anchor as a body boundary.
Rows form sets: repeated calls to the same target within one callable are one
evidence row. Source-associated implicit constructors and lambda methods can be
present. Library-only declarations are excluded from the callable inventory,
but may appear as subjects of call edges. CodeQL's synthetic lambda types can
have an anonymous owner and the implemented interface's package. The exports
retain those extracted values. Qualified names of anonymous classes may be
shared; the source path and range remain part of a row's identity.

| Export | Kind | Subject |
| --- | --- | --- |
| `Inventory.ql` | `callable` | JVM method descriptor |
| | `contract/visibility` | `public`, `protected`, `private`, or `package` |
| | `contract/return` | JVM return-type descriptor |
| | `contract/parameter` | Zero-based index, colon, JVM type descriptor |
| | `contract/modifier` | A positive static/final/abstract/native/synchronized/varargs fact |
| | `contract/throws` | Declared thrown type's qualified name |
| `Calls.ql` | `call/static` | Declared target owner, `#`, target signature |
| `Effects.ql` | `effect/read-field`, `effect/write-field` | Field owner, `#`, field name |
| | `effect/write-array` | JVM array-type descriptor |
| | `effect/throw` | JVM descriptor of the explicitly thrown expression's type |
| | `effect/monitor` | `method` or `block` |
| | `effect/native` | Declaring owner, `#`, callable signature |

Here `call/static` means statically bound **declared target**, including instance
methods; it does not mean that every target has Java's `static` modifier. The pack
does not expand runtime dispatch or reflective targets. Effect rows are direct,
local hints. An empty effect result never proves purity, contract preservation,
or freedom from indirect, aliased, reflective, native, or library effects.
Declared contract rows are a partial inventory, not an ABI compatibility verdict.

## Run

Use a separately installed CodeQL CLI compatible with this fork's library packs.
The local workspace contains the two M3 packs. The `--search-path=.` option
resolves the fork's `java/ql/lib` and `shared` dependencies through the existing
root workspace. From the repository root:

```sh
codeql query compile --search-path=. m3/ql/java/Inventory.ql m3/ql/java/Calls.ql m3/ql/java/Effects.ql
codeql test run --search-path=. --check-databases m3/ql/java-tests
codeql query run --search-path=. --database=/path/to/database --output=Inventory.bqrs m3/ql/java/Inventory.ql
codeql bqrs decode --format=csv --result-set='#select' --output=Inventory.csv Inventory.bqrs
```

Repeat query execution and CSV decoding for `Calls` and `Effects`. Preserve the
database, source snapshot, query/library revision, and CLI identity alongside the
export. The Java coordinator sorts/deduplicates rows and binds evidence to exact
source content; CodeQL's incidental output order is not an identity.

The fixtures cover overloaded calls, constructor inventory, field and array
updates (including compound and unary updates), monitors, native declarations,
throws, lambdas, nested types, and code-looking comments/string literals. The
assertion query must produce no rows. Generate expected files with
`codeql test run --learn --search-path=. m3/ql/java-tests`, inspect them, and rerun
without `--learn`. Do not hand-edit `.expected` files.

The CodeQL CLI/engine is distributed separately from this MIT-licensed repository
and retains its own license. This pack does not reimplement that engine.
