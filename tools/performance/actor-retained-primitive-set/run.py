#!/usr/bin/env python3
"""Compare actual frozen baseline/candidate classes, three independent JVM forks each."""
import argparse
import json
from pathlib import Path
import statistics
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--baseline-classes', type=Path, required=True)
p.add_argument('--candidate-classes', type=Path, default=Path('target/classes'))
p.add_argument('--dependency-classpath', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
here = Path(__file__).resolve().parent
build = Path('target/actor-retained-benchmark').resolve()
build.mkdir(parents=True, exist_ok=True)
a.output.mkdir(parents=True, exist_ok=True)
deps = a.dependency_classpath.read_text().strip()
subprocess.run(['javac', '--release', '17', '-cp', str(a.candidate_classes.resolve()) + ':' + deps,
                '-d', str(build), str(here / 'ActorRetentionBenchmark.java')], check=True)
rows = []
for fork in range(3):
    # Alternate order to reduce systematic warm-machine bias.
    variants = [('before', a.baseline_classes), ('after', a.candidate_classes)]
    if fork % 2:
        variants.reverse()
    for label, classes in variants:
        command = ['java', '-Xms256m', '-Xmx256m', '-cp',
                   str(build) + ':' + str(classes.resolve()) + ':' + deps, 'cn.nukkit.level.format.leveldb.serializer.ActorRetentionBenchmark']
        result = subprocess.run(command, text=True, capture_output=True, check=True, timeout=180)
        (a.output / f'{label}-{fork}.log').write_text(result.stdout + result.stderr)
        for line in result.stdout.splitlines():
            if line.startswith('{'):
                rows.append(dict(json.loads(line), variant=label, fork=fork))
(a.output / 'raw.json').write_text(json.dumps(rows, indent=2) + '\n')
summary = {}
for workload in sorted({row['workload'] for row in rows}):
    summary[workload] = {}
    for label in ('before', 'after'):
        matching = [row for row in rows if row['workload'] == workload and row['variant'] == label]
        summary[workload][label] = {key: statistics.median(row[key] for row in matching)
                                    for key in ('ns_op', 'bytes_op')}
(a.output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary, indent=2))
