#!/usr/bin/env python3
"""Compare baseline and changed classes; run without concurrent builds or benchmarks.

  python3 tools/performance/run-scalar-read.py --baseline /tmp/baseline-classes \
      --candidate target/classes --classpath-file /tmp/dependency-classpath.txt \
      --output /tmp/scalar-read.json
The mixed unit is eight scalar reads; width units are one scalar read each.
Controls cover two unchanged little-endian reads and one retained default stream construction.
"""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--classpath-file", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--harness", choices=("stream", "packet-read", "packet-constructor", "blob-decode"),
                        default="stream")
    args = parser.parse_args()
    dependencies = args.classpath_file.read_text().strip()
    results = []
    harness_name, operations = {
        "stream": ("ScalarReadBenchmark", ("mixed", "width2", "width3", "width4", "width8",
                                           "little-control", "constructor-control")),
        "packet-read": ("PacketScalarReadBenchmark", ("mixed", "little-control")),
        "packet-constructor": ("PacketConstructorBenchmark", ("constructor",)),
        "blob-decode": ("BlobStatusDecodeBenchmark", tuple(f"{wire}-{count}" for wire in ("legacy", "modern")
                                                           for count in (0, 1, 16, 256))),
    }[args.harness]
    def class_hashes():
        return {variant: {str(path.relative_to(classes)): hashlib.sha256(path.read_bytes()).hexdigest()
                          for path in sorted(classes.rglob("*.class"))}
                for variant in ("baseline", "candidate")
                for classes in (getattr(args, variant).resolve(),)}
    before = class_hashes()
    assert all(before.values()), "empty main class tree"
    with tempfile.TemporaryDirectory(prefix="scalar-read-") as directory:
        harness = Path(directory)
        subprocess.run(["javac", "--release", "17", "-cp", str(args.baseline) + ":" + dependencies,
                        "-d", str(harness), str(Path(__file__).with_name(harness_name + ".java"))],
                       check=True, timeout=60)
        assert sorted(p.name for p in harness.rglob("*.class")) == [harness_name + ".class"], \
            "helper output must not shadow core classes"
        for operation in operations:
            for fork in range(3):
                order = ("baseline", "candidate") if fork % 2 == 0 else ("candidate", "baseline")
                for variant in order:
                    classes = getattr(args, variant).resolve()
                    completed = subprocess.run(
                        ["java", "-Xms256m", "-Xmx256m", "-cp",
                         str(classes) + ":" + str(harness) + ":" + dependencies,
                         harness_name, operation], capture_output=True, text=True,
                        check=True, timeout=120)
                    samples = [json.loads(line) for line in completed.stdout.splitlines() if line.startswith("{")]
                    assert len(samples) == 7, "expected all seven samples"
                    assert len({s["checksum"] for s in samples}) == 1, "checksum differs within JVM"
                    result = {"operation": operation, "variant": variant, "fork": fork + 1,
                              "ns_per_op": statistics.median(s["ns_per_op"] for s in samples),
                              "bytes_per_op": statistics.median(s["bytes_per_op"] for s in samples),
                              "checksum": samples[-1]["checksum"], "samples": samples}
                    results.append(result)
                    print(json.dumps({k: v for k, v in result.items() if k != "samples"}), flush=True)
    summary = {}
    for operation in operations:
        values = [r for r in results if r["operation"] == operation]
        assert len({r["checksum"] for r in values}) == 1, "result checksum differs"
        summary[operation] = {
            variant: {metric: statistics.median(r[metric] for r in values if r["variant"] == variant)
                      for metric in ("ns_per_op", "bytes_per_op")}
            for variant in ("baseline", "candidate")}
    after = class_hashes()
    assert before == after, "main class trees changed during benchmark"
    report = {"harness": harness_name, "main_class_sha256": before,
              "java_version": subprocess.run(["java", "-version"], capture_output=True, text=True,
                                             check=True).stderr.strip(),
              "baseline": str(args.baseline.resolve()), "candidate": str(args.candidate.resolve()),
              "forks": results, "summary": summary}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
