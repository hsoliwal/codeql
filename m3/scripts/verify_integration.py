#!/usr/bin/env python3
"""Exercise the packaged Java coordinator with an actual CodeQL installation."""

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def rows(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--codeql", type=Path, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--work", type=Path)
    options = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    work = options.work.resolve() if options.work else Path(tempfile.mkdtemp(prefix="m3-codeql-proof-"))
    work.mkdir(parents=True, exist_ok=True)
    require(not work.is_relative_to(root), "Proof directory must be outside the checkout")
    source = work / "source"
    source.mkdir()
    fixture = root / "m3/ql/java-tests/evidence/Fixture.java"
    shutil.copyfile(fixture, source / "Fixture.java")
    original = hashlib.sha256((source / "Fixture.java").read_bytes()).hexdigest()
    jar = root / "m3/runtime/target/m3-codeql-runtime-0.1.0-SNAPSHOT.jar"
    require(jar.is_file(), f"Build the runtime first: {jar}")
    commands = []
    environment = os.environ.copy()
    java_executable = shutil.which(options.java)
    require(java_executable is not None, "Java executable was not found")
    java_home = Path(java_executable).resolve().parent.parent
    if (java_home / "release").is_file():
        environment["JAVA_HOME"] = str(java_home)
        environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")

    def execute(name, arguments):
        command = [options.java, "-Xmx128m", "-jar", str(jar), *arguments]
        with (work / f"{name}.log").open("w", encoding="utf-8") as log:
            completed = subprocess.run(command, env=environment, stdout=log, stderr=subprocess.STDOUT, check=False, timeout=1800)
        commands.append({"name": name, "arguments": command, "exitCode": completed.returncode})
        (work / "commands.json").write_text(json.dumps(commands, indent=2) + "\n", encoding="utf-8")
        require(completed.returncode == 0, f"{name} failed; see {work / (name + '.log')}")

    first, second, delta, analysis = (work / name for name in ("inventory-1", "inventory-4", "incremental", "analysis"))
    execute("inventory-1", ["inventory", "--source", str(source), "--output", str(first), "--workers", "1"])
    execute("inventory-4", ["inventory", "--source", str(source), "--output", str(second), "--workers", "4"])
    require((first / "INVENTORY.tsv").read_bytes() == (second / "INVENTORY.tsv").read_bytes(),
            "Inventory changed with worker count")
    execute("incremental", ["inventory", "--source", str(source), "--output", str(delta), "--workers", "2",
                            "--previous", str(first / "INVENTORY.tsv")])
    require([row["change"] for row in rows(delta / "INVENTORY.tsv")] == ["UNCHANGED"],
            "Identical fixture did not classify as unchanged")
    execute("analysis", ["analyze", "--source", str(source), "--output", str(analysis), "--workers", "1",
                         "--codeql", str(options.codeql.resolve()), "--queries", str(root / "m3/ql/java"),
                         "--timeout-seconds", "900", "--ram-mb", "1024", "--codeql-heap-mb", "512"])
    require(hashlib.sha256((source / "Fixture.java").read_bytes()).hexdigest() == original,
            "Analysis changed source bytes")
    required = ["STATUS.tsv", "FINAL_REPORT.md", "RUN_CONTEXT.tsv", "PROVENANCE.tsv", "VERIFY_CONTRACT.tsv",
                "OUTPUT_CONTRACT.tsv", "INVENTORY.tsv", "CLUSTERS_EXACT.tsv", "CLUSTERS_NORMALIZED.tsv",
                "ACTION_QUEUE.tsv", "SUMMARY.tsv", "EVIDENCE.tsv", "FINGERPRINTS.tsv", "RESIDUE.tsv", "PROOF_RECEIPTS.tsv", "TASK_PACKET.tsv"]
    for name in required:
        require((analysis / name).is_file(), f"Missing required report: {name}")
    require(all(row["status"] == "PRESENT" for row in rows(analysis / "OUTPUT_CONTRACT.tsv")),
            "Output contract contains a missing artifact")
    require(all(row["status"] == "COMPLETED" for row in rows(analysis / "STATUS.tsv")),
            "Analysis status is not completed")
    evidence = rows(analysis / "EVIDENCE.tsv")
    require(evidence, "Real CodeQL analysis produced no fixture evidence")
    kinds = {row["kind"] for row in evidence}
    require({"callable", "call/static", "effect/write-field", "effect/write-array", "effect/monitor", "effect/native"} <= kinds,
            f"Missing expected evidence families: {sorted(kinds)}")
    require(all(row["sourceSha256"] == original and row["freshness"] == "BOUND_TO_SNAPSHOT" for row in evidence),
            "Evidence is not bound to the source digest")
    fingerprints = rows(analysis / "FINGERPRINTS.tsv")
    candidates = rows(analysis / "CLUSTERS_NORMALIZED.tsv")
    require(fingerprints and all(len(row["simHash64"]) == 16 and len(row["factPatternSha256"]) == 64
                                for row in fingerprints), "Missing callable fact fingerprints")
    identifiers = {row["callableId"] for row in fingerprints}
    require(candidates and all(row["leftCallableId"] in identifiers and row["rightCallableId"] in identifiers
                              and int(row["hammingDistance"]) <= 3 for row in candidates),
            "Missing or unbound fact-pattern review candidates")
    gates = {row["gate"]: row["status"] for row in rows(analysis / "VERIFY_CONTRACT.tsv")}
    require(all(gates.get(gate) == "PASS" for gate in ("original-input-boundary", "analyzed-copy-boundary", "evidence-schema", "query-compile-evaluate")),
            "Evidence verification gates are incomplete")
    receipts = rows(analysis / "PROOF_RECEIPTS.tsv")
    require(len(receipts) >= 8 and all(row["exitCode"] == "0" for row in receipts),
            "Incomplete or failed real CodeQL process receipts")
    summary = {"status": "PASS", "provider": "actual CodeQL CLI", "sourceSha256": original,
               "inventoryWorkerCounts": [1, 4], "incrementalUnchanged": True,
               "evidenceRows": len(evidence), "evidenceKinds": sorted(kinds),
               "callableFingerprints": len(fingerprints), "factPatternCandidates": len(candidates),
               "providerCommands": len(receipts), "sourceUnchanged": True,
               "workDirectory": str(work)}
    (work / "integration-result.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
