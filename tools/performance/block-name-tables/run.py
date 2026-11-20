#!/usr/bin/env python3
"""Three fresh alternating JVM pairs using frozen baseline and a narrow class overlay."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
from urllib.parse import unquote, urlparse

p = argparse.ArgumentParser()
p.add_argument('--baseline-classes', required=True, type=Path)
p.add_argument('--candidate-classes', required=True, type=Path)
p.add_argument('--dependency-classpath', required=True, type=Path)
p.add_argument('--output', required=True, type=Path)
a = p.parse_args()
assert os.environ.get('JDK_JAVA_OPTIONS') == '-Xmx2g', 'Set JDK_JAVA_OPTIONS=-Xmx2g and run one benchmark at a time'
here = Path(__file__).resolve().parent
repo = here.parents[2]
baseline = a.baseline_classes.resolve()
candidate = a.candidate_classes.resolve()
output = a.output.resolve()
output.mkdir(parents=True, exist_ok=False)
helpers = output / 'harness-classes'
support = output / 'test-support-classes'
helpers.mkdir()
support.mkdir()
deps = a.dependency_classpath.read_text().strip()

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def tree(path):
    return {str(file.relative_to(path)): sha(file) for file in sorted(path.rglob('*.class'))}

def dep_hashes():
    return {path: sha(path) for path in deps.split(os.pathsep)}

before = dict(baseline=tree(baseline), candidate=tree(candidate), dependencies=dep_hashes())
assert len(before['baseline']) == 4261
baseline_keys, candidate_keys = set(before['baseline']), set(before['candidate'])
if len(candidate_keys) == 26 and candidate_keys <= baseline_keys:
    candidate_mode = 'narrow-overlay'
elif candidate_keys == baseline_keys:
    candidate_mode = 'full-tree'
else:
    raise AssertionError('Candidate must be the 26-class overlay or a complete matching baseline class tree')
for name in ('BlockNameBenchmark.class', 'cn/nukkit/MockServer.class'):
    assert not (baseline / name).exists() and not (candidate / name).exists()
source = here / 'BlockNameBenchmark.java'
fixture_source = repo / 'src/test/java/cn/nukkit/MockServer.java'
source_before = {str(path): sha(path) for path in (source, Path(__file__), here / 'README.md', fixture_source)}
type_block = re.search(r'String\[\] TYPES = \{([\s\S]*?)\};', source.read_text()).group(1)
types = re.findall(r'"([^"]+)"', type_block)
assert len(types) == len(set(types)) == 26
affected_types = set(types) - {'BlockFlower', 'BlockLeaves2'}
allowed_changes = {path for path in before['baseline']
                   if any(Path(path).stem == name or Path(path).name.startswith(name + '$') for name in affected_types)}
assert len(allowed_changes) == 26
if candidate_mode == 'narrow-overlay':
    assert candidate_keys == allowed_changes
else:
    changed = {path for path in before['baseline'] if before['baseline'][path] != before['candidate'][path]}
    assert changed <= allowed_changes, 'Unrelated class files changed; use matching baseline/candidate builds'
commands = []
for command in (
    ['javac', '--release', '17', '-cp', str(baseline) + ':' + deps, '-d', str(support), str(fixture_source)],
    ['javac', '--release', '17', '-cp', str(baseline) + ':' + str(support) + ':' + deps,
     '-d', str(helpers), str(source)],
):
    commands.append(command)
    subprocess.run(command, check=True, timeout=90)
helper_before = dict(harness=tree(helpers), support=tree(support))
assert list(helper_before['harness']) == ['BlockNameBenchmark.class']
assert sorted(helper_before['support']) == ['cn/nukkit/MockServer$1.class', 'cn/nukkit/MockServer.class']
rows = []
for fork in range(3):
    variants = [('before', baseline), ('after', candidate)]
    if fork % 2:
        variants.reverse()
    for label, selected in variants:
        class_log = output / f'{label}-{fork}-classload.log'
        command = ['java', '-Xms256m', '-Xlog:class+load=info:file=' + str(class_log), '-cp',
                   ':'.join((str(selected), str(baseline), str(helpers), str(support), deps)),
                   'BlockNameBenchmark']
        commands.append(command)
        process = subprocess.run(command, text=True, capture_output=True, timeout=180)
        (output / f'{label}-{fork}.log').write_text(process.stdout + process.stderr)
        process.check_returncode()
        origins = [json.loads(line.removeprefix('origin=')) for line in process.stdout.splitlines()
                   if line.startswith('origin=')]
        assert {row['class'] for row in origins} == set(types) and len(origins) == 26
        for origin in origins:
            relative = 'cn/nukkit/block/' + origin['class'] + '.class'
            expected = selected if (selected / relative).is_file() else baseline
            assert Path(unquote(urlparse(origin['url']).path)).resolve() == expected
        helper_origins = [line.removeprefix('helper=') for line in process.stdout.splitlines() if line.startswith('helper=')]
        assert len(helper_origins) == 1
        assert Path(unquote(urlparse(helper_origins[0]).path)).resolve() == helpers
        loaded = class_log.read_text()
        assert not any(name in loaded for name in ('BlockNameTableTest', 'LeafNameBaselineProbe', 'BlockNameFamilyBaselineProbe'))
        assert not re.search(r'(?:' + '|'.join(types) + r')\$MockitoMock', loaded)
        samples = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
        assert len(samples) == 26 * 2 * 7 == 364
        for name in types:
            for items in (False, True):
                matching = [row for row in samples if row['class'] == name and row['items'] == items]
                assert [row['sample'] for row in matching] == list(range(7))
                assert len({(row['iterations'], row['checksum']) for row in matching}) == 1
        rows.extend(dict(row, variant=label, fork=fork) for row in samples)
        (output / 'raw-partial.json').write_text(json.dumps(rows, indent=2) + '\n')
assert len(rows) == 2184
paired = []
for name in types:
    for items in (False, True):
        for fork in range(3):
            groups = {label: [row for row in rows if row['class'] == name and row['items'] == items
                              and row['fork'] == fork and row['variant'] == label]
                      for label in ('before', 'after')}
            assert [(row['iterations'], row['checksum']) for row in groups['before']] == [
                (row['iterations'], row['checksum']) for row in groups['after']]
            medians = {label: {key: statistics.median(row[key] for row in groups[label])
                               for key in ('ns_per_op', 'cpu_ns_per_op', 'bytes_per_op')}
                       for label in ('before', 'after')}
            paired.append(dict(name=name, items=items, fork=fork, samples_per_variant=7, **medians))
after = dict(baseline=tree(baseline), candidate=tree(candidate), dependencies=dep_hashes())
helper_after = dict(harness=tree(helpers), support=tree(support))
assert before == after and helper_before == helper_after
assert source_before == {path: sha(path) for path in source_before}
(output / 'raw.json').write_text(json.dumps(rows, indent=2) + '\n')
(output / 'paired-fork-medians.json').write_text(json.dumps(paired, indent=2) + '\n')
effective_candidate = dict(before['baseline'], **before['candidate'])
metadata = dict(commands=commands, scoped_java_options=os.environ['JDK_JAVA_OPTIONS'],
                candidate_mode=candidate_mode,
                java_version=subprocess.check_output(['java', '-version'], text=True, stderr=subprocess.STDOUT),
                source_sha256=source_before, class_hashes_before=before, class_hashes_after=after,
                helper_hashes_before=helper_before, helper_hashes_after=helper_after,
                effective_candidate_classes=len(effective_candidate),
                changed_class_files=[name for name in effective_candidate if effective_candidate[name] != before['baseline'][name]],
                selected_production_before_helpers=True, native_class_origins_verified=True,
                behavior_test_and_synthetic_target_subclasses_absent_from_timed_jvms=True,
                all_files_stable=True, paired_output_parity=True, workloads=52, rows=2184,
                forks=3, samples_per_workload_per_variant_per_fork=7,
                scope='Native getName and whole toItem; real registry setup untimed. Current-thread allocated bytes and CPU; wall time separate. No production frequency, TPS, latency percentile or client-runtime claim.')
(output / 'run-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps(dict(workloads=52, rows=2184, forks=3, paired_output_parity=True, all_files_stable=True)))
