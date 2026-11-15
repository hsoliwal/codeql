# Replaying the M3 CodeQL integration

`com.synexia.m3.codeql.recipe.M3CodeqlSnapshotRecipe` is an actual OpenRewrite
`ScanningRecipe`. It scans existing source paths, refuses conflicting destinations,
and generates reviewed new files through OpenRewrite's generation lifecycle.
It never rewrites or deletes an existing source file. An exact existing postimage
is an idempotent fixed point.

The recipe's reviewed inputs live under
`src/main/resources/com/synexia/m3/codeql/recipe/snapshot/`. `manifest.tsv` records
each repository-relative path, SHA-256 digest, byte length, and Git executable
mode. `manifest.sha256` pins the complete sorted manifest. Content-addressed
`objects/` contain the exact UTF-8 postimages. These hashes establish byte identity;
they do not establish behavioral equivalence or authorize a merge.

## Build and verify

Run from the repository root with Java 21 and Maven:

```sh
mvn -f m3/recipes/pom.xml test
mvn -f m3/recipes/pom.xml exec:java -Dexec.args="verify ."
```

The second command checks that the working-tree files exactly match the packaged
recipe resources, including executable bits on POSIX filesystems. Run `mvn process-resources`
again after changing the resource snapshot so that `target/classes` contains the
current manifest.

## Apply to a clean checkout

Build this recipe module, then point its execution driver at a separate clean
checkout of the inspected CodeQL base:

```sh
mvn -f m3/recipes/pom.xml exec:java \
  -Dexec.args="apply /absolute/path/to/clean-codeql"
mvn -f m3/recipes/pom.xml exec:java \
  -Dexec.args="verify /absolute/path/to/clean-codeql"
```

The driver performs full preflight validation, runs the real recipe in memory,
checks every generated digest, and reruns the recipe to require zero changes.
Only then does it create missing files with `CREATE_NEW`. Existing files are
never overwritten. On a write failure it attempts to remove its own matching
outputs, and the command fails. Filesystem writes spanning multiple paths are not
one atomic transaction; perform replay in a dedicated checkout.

The driver reads and writes only manifest-owned paths. Upstream files do not need
to be parsed or copied into memory. Running `apply` again must report zero new
files. This generator installs the reviewed additions; later changes to existing
M3 files require a separately reviewed change, not an implicit overwrite.

## Capture a reviewed candidate

Capture is a maintainer operation after the candidate has passed its compiler,
CodeQL, and test checks. Pass explicit files or directories, for example:

```sh
mvn -f m3/recipes/pom.xml exec:java \
  -Dexec.args="capture . m3/recipes/src/main/resources/com/synexia/m3/codeql/recipe/snapshot m3/runtime m3/ql m3/pom.xml m3/README.md .github/workflows/m3-codeql.yml"
mvn -f m3/recipes/pom.xml process-resources
mvn -f m3/recipes/pom.xml exec:java -Dexec.args="verify ."
```

Append every other reviewed M3 documentation/script path that belongs in the
integration. Capturing overlapping include paths is an error. Capture may update
the recipe's resource directory; `apply` only creates absent destination files.
Both `verify` and `apply` accept an optional third argument naming a resource
directory instead of using the packaged bundle.

The destination allowlist is `m3/**`, excluding this `m3/recipes/**` module itself,
and the exact workflow `.github/workflows/m3-codeql.yml`. This avoids recursive
snapshotting and keeps upstream CodeQL implementation files outside the recipe's
write scope. Source inputs must be regular UTF-8 files without a BOM or NUL;
symbolic links and non-canonical paths are rejected. `target`, `*.testproj`,
`.cache`, `.codeql`, `.git`, and `__pycache__` directories and generated
`.bqrs`, `.qlx`, `.qlx.lock`, `.actual`, `.log`, and `.pyc` files are excluded. CodeQL-generated
`.expected` files are included as exact reviewed data; this tool does not author
or repair their expected results.

## What the tests establish

Tests execute OpenRewrite's `Recipe.run` lifecycle, checking generation, mixed
existing/missing destinations, zero-change replay, deterministic input order,
and refusal on content conflicts, duplicate paths, path aliases, malformed text,
resource tampering, symlinks, and executable-mode drift. Filesystem tests capture,
reload, replay, and verify actual files, including Unicode and CRLF bytes, while
checking that unrelated upstream files remain unchanged.

The recipe uses `PlainText` for exact, reviewed whole-file generation. It does
not perform a Java AST rewrite, optimize QL predicates, or claim that source
checksums prove contracts. Runtime and query tests remain separate evidence.
