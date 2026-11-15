# M3 CodeQL provenance and notices

The original repository remains governed by its root MIT LICENSE, including GitHub's copyright notice. Existing source and attribution are retained.

New public M3 CodeQL queries, Java runtime tools, product tests and documentation are contributed under the MIT License by Hitesh Soliwal and Contributors to the Synexia Project (2026). The private recipe adapter and qualification harness use Apache-2.0 and are not distributed in this target.

The query pack imports the CodeQL Java standard library from this fork, pinned during qualification to `2e2b72797673638eaf94772632721d2fa5c42219`. It reuses the upstream semantic APIs rather than copying a replacement compiler or extractor. Public file references and exact output hashes are recorded with the delivery evidence.

The CodeQL CLI and engine are distributed and licensed separately by GitHub. The M3 Java tool invokes an installed CLI; it does not embed, relicense or redistribute that engine. The CI workflow downloads the official CodeQL 2.27.4 Linux archive and verifies its published SHA-256:

`393bf92b66596141ec93baf9b483f4a350f3749916c922fed0dd63c67706047d`

Maven and JUnit are build/test tools; the product JAR uses Java 21's standard library. No LeetCode, HackerRank or GeeksforGeeks solution/editorial source is incorporated in this delivery.
