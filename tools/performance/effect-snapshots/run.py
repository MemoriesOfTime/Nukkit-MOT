#!/usr/bin/env python3
"""Three fresh whole native Entity.entityBaseTick JVM pairs using full class trees."""
from pathlib import Path
from urllib.parse import urlparse, unquote
import argparse
import hashlib
import json
import os
import statistics
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--baseline-classes', type=Path, required=True)
parser.add_argument('--candidate-classes', type=Path, required=True)
parser.add_argument('--dependency-classpath', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
assert os.environ.get('JDK_JAVA_OPTIONS') == '-Xmx2g', 'Use scoped JDK_JAVA_OPTIONS=-Xmx2g and run one benchmark at a time'
here = Path(__file__).resolve().parent
baseline, candidate, output = args.baseline_classes.resolve(), args.candidate_classes.resolve(), args.output.resolve()
output.mkdir(parents=True, exist_ok=False)
helpers = output / 'harness-classes'
helpers.mkdir()
deps = args.dependency_classpath.read_text().strip()
counts = [0, 1, 4, 8, 16, 17, 64, 128]
new_state = 'cn/nukkit/entity/Entity$EffectTickSnapshot.class'
entity = 'cn/nukkit/entity/Entity.class'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def tree(path):
    return {str(p.relative_to(path)): sha(p) for p in sorted(path.rglob('*.class'))}


def snapshot():
    return dict(baseline=tree(baseline), candidate=tree(candidate),
                dependencies={p: sha(p) for p in deps.split(os.pathsep)})


before = snapshot()
assert len(before['baseline']) == 4261
assert set(before['candidate']) == set(before['baseline']) | {new_state}
assert {p for p in before['baseline'] if before['candidate'][p] != before['baseline'][p]} == {entity}
assert len(before['candidate']) == 4262
for directory in [baseline, candidate]:
    assert not (directory / 'EffectSnapshotBenchmark.class').exists()
    assert not list(directory.rglob('*EntityEffectSnapshotTest*'))
sources = {str(p): sha(p) for p in [here / 'EffectSnapshotBenchmark.java', Path(__file__), here / 'README.md']}
commands = [['javac', '--release', '17', '-cp', str(baseline) + os.pathsep + deps,
             '-d', str(helpers), str(here / 'EffectSnapshotBenchmark.java')]]
compile_process = subprocess.run(commands[0], capture_output=True, text=True, timeout=60)
(output / 'compile.log').write_text(compile_process.stdout + compile_process.stderr)
compile_process.check_returncode()
helper_before = tree(helpers)
assert set(helper_before) == {'EffectSnapshotBenchmark.class', 'EffectSnapshotBenchmark$FixtureEntity.class'}
rows = []
expected_types = ['cn.nukkit.entity.Entity', 'cn.nukkit.potion.Effect', 'cn.nukkit.level.Level',
                  'cn.nukkit.level.format.anvil.Anvil', 'cn.nukkit.level.format.generic.BaseLevelProvider',
                  'EffectSnapshotBenchmark$FixtureEntity', 'EffectSnapshotBenchmark']
for fork in range(3):
    variants = [('before', baseline), ('after', candidate)]
    if fork % 2:
        variants.reverse()
    for label, selected in variants:
        classlog = output / f'{label}-{fork}-classload.log'
        command = ['java', '-Xms256m', '-Xmx256m', '-Xlog:class+load=info:file=' + str(classlog),
                   '-cp', os.pathsep.join([str(selected), str(helpers), deps]), 'EffectSnapshotBenchmark']
        commands.append(command)
        process = subprocess.run(command, capture_output=True, text=True, timeout=120)
        (output / f'{label}-{fork}.log').write_text(process.stdout + process.stderr)
        process.check_returncode()
        origins = dict(line.removeprefix('origin=').split('\t', 1)
                       for line in process.stdout.splitlines() if line.startswith('origin='))
        assert set(origins) == set(expected_types)
        loaded = classlog.read_text()
        assert all(term not in loaded for term in ['org.mockito.', '$MockitoMock', 'EntityEffectSnapshotTest'])
        for name in expected_types:
            expected = helpers if name.startswith('EffectSnapshotBenchmark') else selected
            assert Path(unquote(urlparse(origins[name]).path)).resolve() == expected
            assert name + ' source: ' + origins[name] in loaded
        if label == 'after':
            assert 'cn.nukkit.entity.Entity$EffectTickSnapshot source: ' + origins['cn.nukkit.entity.Entity'] in loaded
        else:
            assert 'cn.nukkit.entity.Entity$EffectTickSnapshot source:' not in loaded
        samples = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
        assert len(samples) == 56
        for count in counts:
            group = [r for r in samples if r['effects'] == count]
            assert [r['sample'] for r in group] == list(range(7))
            assert {r['iterations'] for r in group} == {200000}
        rows.extend(dict(r, variant=label, fork=fork) for r in samples)
        (output / 'raw-partial.json').write_text(json.dumps(rows, indent=2) + '\n')
assert len(rows) == 336
paired = []
for count in counts:
    for fork in range(3):
        groups = {label: [r for r in rows if (r['effects'], r['fork'], r['variant']) == (count, fork, label)]
                  for label in ['before', 'after']}
        assert [(r['sample'], r['iterations'], r['checksum']) for r in groups['before']] == [
            (r['sample'], r['iterations'], r['checksum']) for r in groups['after']]
        medians = {label: {key: statistics.median(r[key] for r in groups[label])
                           for key in ['bytes_per_op', 'cpu_ns_per_op', 'wall_ns_per_op']}
                   for label in ['before', 'after']}
        paired.append(dict(effects=count, fork=fork, samples_per_variant=7, **medians))
after, helper_after = snapshot(), tree(helpers)
assert before == after and helper_before == helper_after
assert sources == {p: sha(p) for p in sources}
metadata = dict(commands=commands, scoped_java_options=os.environ['JDK_JAVA_OPTIONS'], child_heap='-Xms256m -Xmx256m',
                baseline_classes=4261, candidate_classes=4262, candidate_mode='actual-full-main-tree',
                sources_sha256=sources, class_hashes_before=before, class_hashes_after=after,
                helper_hashes_before=helper_before, helper_hashes_after=helper_after,
                java_version=subprocess.check_output(['java', '-version'], text=True, stderr=subprocess.STDOUT),
                all_files_stable=True, native_class_origins_verified=True, main_before_helpers=True,
                no_test_witnesses_or_mockito_loaded=True, paired_checksum_parity=True,
                workloads=8, rows=336, paired_fork_rows=24, warmup_loops=8, samples_per_variant_per_fork=7,
                operations_per_sample=200000, scope='Whole steady native entityBaseTick. Allocation, current-thread CPU and wall time; no production frequency, TPS, latency percentile or universal speed claim.')
(output / 'raw.json').write_text(json.dumps(rows, indent=2) + '\n')
(output / 'paired-fork-medians.json').write_text(json.dumps(paired, indent=2) + '\n')
(output / 'run-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps(dict(workloads=8, rows=336, paired_fork_rows=24, all_files_stable=True, paired_checksum_parity=True)))
