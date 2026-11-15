#!/usr/bin/env python3
"""Compare complete explodeB using concrete callbacks in three fresh JVM pairs."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--baseline", type=Path, required=True, help="unmodified compiled main classes/resources")
parser.add_argument("--candidate", type=Path, default=Path("target/classes"))
parser.add_argument("--test-classes", type=Path, default=Path("target/test-classes"))
parser.add_argument("--classpath-file", type=Path, required=True, help="Maven test dependency classpath")
parser.add_argument("--output", type=Path, required=True)
a = parser.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
deps = a.classpath_file.read_text().strip()
agent = next(entry for entry in deps.split(":") if "/mockito-core/" in entry)
source = Path(__file__).with_name("ExplosionUpdateBenchmark.java").resolve()
results = []
with tempfile.TemporaryDirectory(prefix="explosion-update-benchmark-") as temporary:
    common = f"{Path(temporary)}:{a.test_classes.resolve()}:{deps}"
    subprocess.run(["javac", "--release", "17", "-cp", f"{a.baseline.resolve()}:{common}", "-d", temporary, str(source)], check=True)
    for fork in range(3):
        for variant in (["before", "after"] if fork % 2 == 0 else ["after", "before"]):
            classes = a.baseline if variant == "before" else a.candidate
            command = ["java", "-Xms512m", "-Xmx512m", "-XX:+UseSerialGC", f"-javaagent:{agent}",
                       "-cp", f"{classes.resolve()}:{common}", "cn.nukkit.ExplosionUpdateBenchmark"]
            run = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                 timeout=300, check=True)
            (a.output / f"{variant}-{fork + 1}.log").write_text(run.stdout)
            rows = [line for line in run.stdout.splitlines() if line.startswith(("ORDINARY,", "WEAK,", "STRONG,")) and line.count(",") == 5]
            for row in csv.reader(rows):
                route, count, iterations, nanos, allocated, checksum = row
                sample = sum(r["variant"] == variant and r["fork"] == fork + 1
                             and r["route"] == route and r["affected_blocks"] == int(count) for r in results) + 1
                results.append(dict(variant=variant, fork=fork + 1, sample=sample, route=route, affected_blocks=int(count),
                                    iterations=int(iterations), ns_per_op=float(nanos),
                                    bytes_per_op=float(allocated), checksum=int(checksum)))
            if len(rows) != 84 or any(sum(row[0] == route and int(row[1]) == count for row in csv.reader(rows)) != 7
                                      for route in ("ORDINARY", "WEAK", "STRONG") for count in (0, 64, 512, 2048)):
                raise RuntimeError(f"Expected twelve scenarios with seven samples: {variant}/{fork + 1}")
summary = []
for route, count in [(route, count) for route in ("ORDINARY", "WEAK", "STRONG") for count in (0, 64, 512, 2048)]:
    for sample in range(1, 8):
        observed = [r for r in results if r["route"] == route and r["affected_blocks"] == count and r["sample"] == sample]
        if len(observed) != 6 or len({r["checksum"] for r in observed}) != 1:
            raise RuntimeError(f"Observable effects differ: count={count}, sample={sample}")
for route, count in sorted({(row["route"], row["affected_blocks"]) for row in results}):
    row = {"route": route, "affected_blocks": count}
    for variant in ["before", "after"]:
        selected = [r for r in results if r["variant"] == variant and r["route"] == route and r["affected_blocks"] == count]
        row[variant] = {key: statistics.median(r[key] for r in selected)
                        for key in ["ns_per_op", "bytes_per_op"]}
    summary.append(row)
paired = []
for route, count in [(route, count) for route in ("ORDINARY", "WEAK", "STRONG") for count in (0, 64, 512, 2048)]:
    for fork in range(1, 4):
        row = {"route": route, "affected_blocks": count, "fork": fork}
        for variant in ("before", "after"):
            selected = [r for r in results if r["variant"] == variant
                        and r["route"] == route and r["affected_blocks"] == count and r["fork"] == fork]
            row[variant] = {key: statistics.median(r[key] for r in selected)
                            for key in ("ns_per_op", "bytes_per_op")}
        paired.append(row)
report = {"java": subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True),
          "benchmark_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
          "scope": "Complete ordinary, weak and strong explodeB; concrete world/event callbacks, no entities/drops/ignition, spaced obsidian blocks. Mockito only bootstraps registries outside timing. Effect counts verified after every sample. Not TPS.",
          "jvm_flags": ["-Xms512m", "-Xmx512m", "-XX:+UseSerialGC", "Mockito javaagent"],
          "raw": results, "medians": summary, "paired_fork_medians": paired,
          "all_effect_checksums_equal": True}
(a.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(summary, indent=2))
