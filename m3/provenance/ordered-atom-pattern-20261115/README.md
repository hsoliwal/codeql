<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: MIT -->
# Ordered atom and pattern delivery

This directory holds passive receiving evidence for the CodeQL M3 product.
The reusable recipes and qualification harness remain in canonical Synexia.
This document explains the checks; it is not an execution or promotion receipt.

## Two verification checks

The original bundled snapshot under `m3/recipes` remains frozen. The workflow
applies it to a fresh temporary directory, verifies the resulting bytes and
modes, and requires a second application to generate zero files. The existing
recipe tests retain their content, path, mode, tampering and drift refusals.

The current product has a separate versioned receipt: `manifest.tsv` records
paths, SHA-256 hashes, lengths and modes; `manifest.sha256` identifies that
manifest. Its paths must exactly cover tracked `m3` product files and the M3
workflow, excluding the preserved legacy `m3/recipes` subtree and these two
receipt files themselves. This README is included in that coverage.

The existing snapshot tool captures those explicit current paths into temporary
resources. The workflow compares the complete captured manifest and digest with
the committed passive receipt before verifying the live files against those
resources. Missing, additional or changed receipt paths fail; the existing
verifier retains its byte, mode and symlink checks. Later accepted output does
not silently replace the original release snapshot.

## Finite ordered qualification

The declared source target is the private bucket calculation in
`EvidenceFingerprints`. A qualifying donation must execute Atomize, compiler,
behavior, public/protected contract and explicit host gates, then Patternize and
the same gates, continuing to a complete unchanged round. The source, ordered
passes and fresh replay results are bound by their actual receipts.

The CodeQL fixture requires actual HotSpot C1 and C2 observations for
`EvidenceFingerprints::bucket`, with 512 repetitions of its frozen public API
oracle per mode at each checkpoint. These are finite observations over the
declared fixture, not universal equivalence, private-reflection or stack-layout
proof, or a measured performance improvement.

Existing CodeQL packs, both M3 query/tool surfaces, public contracts and earlier
accepted additions remain receiving requirements. Model advice is optional
candidate evidence and cannot satisfy an acceptance gate. This delivery makes
no Chrome-managed, GPU or general native-runtime qualification claim. Consult
the execution receipts and pull-request checks for actual outcomes.
