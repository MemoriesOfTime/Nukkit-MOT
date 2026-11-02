#!/usr/bin/env python3
"""Run every native enum/palette workload in three fresh alternating JVM pairs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import statistics
import subprocess
from urllib.parse import unquote, urlparse

p = argparse.ArgumentParser()
p.add_argument('--baseline-classes', required=True, type=Path)
p.add_argument('--candidate-classes', required=True, type=Path)
p.add_argument('--dependency-classpath', required=True, type=Path)
p.add_argument('--output', required=True, type=Path)
a = p.parse_args()
assert os.environ.get('JDK_JAVA_OPTIONS') == '-Xmx2g', 'Set JDK_JAVA_OPTIONS=-Xmx2g; run one benchmark at a time'
here = Path(__file__).resolve().parent
baseline, candidate = a.baseline_classes.resolve(), a.candidate_classes.resolve()
output = a.output.resolve()
output.mkdir(parents=True, exist_ok=False)
helpers = output / 'harness-classes'
helpers.mkdir()
deps = a.dependency_classpath.read_text().strip()
modes = ['enum-read', 'enum-write', 'shelf-type', 'shelf-powered-control',
         'palette-v0', 'palette-v1', 'palette-v4']
allowed = {'cn/nukkit/level/util/BitArrayVersion.class', 'cn/nukkit/block/BlockShelf.class',
           'cn/nukkit/block/BlockShelf$1.class', 'cn/nukkit/block/BlockShelf$PoweredShelfType.class'}
native_types = ['cn.nukkit.level.util.BitArrayVersion', 'cn.nukkit.block.BlockShelf',
                'cn.nukkit.block.BlockShelfOak', 'cn.nukkit.level.util.PalettedBlockStorage']


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def tree(path):
    return {str(file.relative_to(path)): sha(file) for file in sorted(path.rglob('*.class'))}


def snapshot():
    return dict(baseline=tree(baseline), candidate=tree(candidate),
                dependencies={path: sha(path) for path in deps.split(os.pathsep)})


before = snapshot()
assert len(before['baseline']) == 4261
if set(before['candidate']) == allowed:
    candidate_mode = 'narrow-overlay'
elif set(before['candidate']) == set(before['baseline']):
    candidate_mode = 'full-tree'
    assert {name for name in before['baseline']
            if before['baseline'][name] != before['candidate'][name]} <= allowed
else:
    raise AssertionError('Candidate must be the exact four-class overlay or a matching full main class tree')
for path in (baseline, candidate):
    assert not (path / 'EnumLookupBenchmark.class').exists()
sources = {str(path): sha(path) for path in (here / 'EnumLookupBenchmark.java', Path(__file__), here / 'README.md')}
commands = [['javac', '--release', '17', '-cp', str(baseline) + os.pathsep + deps,
             '-d', str(helpers), str(here / 'EnumLookupBenchmark.java')]]
subprocess.run(commands[0], check=True, timeout=60)
helper_before = tree(helpers)
assert set(helper_before) == {'EnumLookupBenchmark.class'}
rows = []
for fork in range(3):
    variants = [('before', baseline), ('after', candidate)]
    if fork % 2:
        variants.reverse()
    for label, selected in variants:
        class_log = output / f'{label}-{fork}-classload.log'
        command = ['java', '-Xms256m', '-Xmx256m', '-Xlog:class+load=info:file=' + str(class_log),
                   '-cp', os.pathsep.join((str(selected), str(baseline), str(helpers), deps)),
                   'EnumLookupBenchmark']
        commands.append(command)
        process = subprocess.run(command, text=True, capture_output=True, timeout=120)
        (output / f'{label}-{fork}.log').write_text(process.stdout + process.stderr)
        process.check_returncode()
        origins = dict(line.removeprefix('origin=').split('\t', 1)
                       for line in process.stdout.splitlines() if line.startswith('origin='))
        assert set(origins) == set(native_types + ['EnumLookupBenchmark'])
        for name in native_types:
            relative = name.replace('.', '/') + '.class'
            expected = selected if (selected / relative).is_file() else baseline
            assert Path(unquote(urlparse(origins[name]).path)).resolve() == expected
        assert Path(unquote(urlparse(origins['EnumLookupBenchmark']).path)).resolve() == helpers
        loaded = class_log.read_text()
        assert not any(name in loaded for name in ('EnumLookupScratchTest', 'EnumLookupBaselineProbe', '$MockitoMock'))
        samples = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
        assert len(samples) == 49
        for mode in modes:
            group = [row for row in samples if row['mode'] == mode]
            assert [row['sample'] for row in group] == list(range(7))
            assert {row['iterations'] for row in group} == {8192 if mode.startswith('palette-') else 1_000_000}
            assert len({row['checksum'] for row in group}) == 1
        rows.extend(dict(row, variant=label, fork=fork) for row in samples)
        (output / 'raw-partial.json').write_text(json.dumps(rows, indent=2) + '\n')
assert len(rows) == 294
paired = []
for mode in modes:
    for fork in range(3):
        groups = {label: [row for row in rows if row['mode'] == mode
                          and row['fork'] == fork and row['variant'] == label]
                  for label in ('before', 'after')}
        assert [(row['iterations'], row['checksum']) for row in groups['before']] == [
            (row['iterations'], row['checksum']) for row in groups['after']]
        medians = {label: {key: statistics.median(row[key] for row in groups[label])
                           for key in ('ns_per_op', 'cpu_ns_per_op', 'bytes_per_op')}
                   for label in ('before', 'after')}
        paired.append(dict(mode=mode, fork=fork, samples_per_variant=7, **medians))
after, helper_after = snapshot(), tree(helpers)
assert before == after and helper_before == helper_after
assert sources == {path: sha(path) for path in sources}
(output / 'raw.json').write_text(json.dumps(rows, indent=2) + '\n')
(output / 'paired-fork-medians.json').write_text(json.dumps(paired, indent=2) + '\n')
metadata = dict(commands=commands, scoped_java_options=os.environ['JDK_JAVA_OPTIONS'],
                child_heap='-Xms256m -Xmx256m', candidate_mode=candidate_mode,
                java_version=subprocess.check_output(['java', '-version'], text=True, stderr=subprocess.STDOUT),
                sources_sha256=sources, class_hashes_before=before, class_hashes_after=after,
                helper_hashes_before=helper_before, helper_hashes_after=helper_after,
                selected_production_before_helpers=True, native_class_origins_verified=True,
                all_files_stable=True, paired_output_parity=True, rows=294, workloads=7,
                forks=3, samples_per_workload_per_variant_per_fork=7,
                scope='Native lookup methods and whole PalettedBlockStorage.readFromStorage; current-thread bytes/CPU and wall time. No production call-frequency, TPS, latency percentile or live-client claim.')
(output / 'run-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps(dict(workloads=7, rows=294, forks=3, paired_output_parity=True, all_files_stable=True)))
