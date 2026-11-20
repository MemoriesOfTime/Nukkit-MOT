#!/usr/bin/env python3
"""Three paired fresh JVMs; actual private leaf search on identical prebuilt concrete graphs."""
import argparse,hashlib,json,statistics,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True)
p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--check',action='store_true');a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def tree(p):return {str(f.relative_to(p)):sha(f) for f in sorted(p.rglob('*.class'))}
source=Path(__file__).with_name('LeafSearchBenchmark.java');deps=a.classpath_file.read_text().strip()
initial={'baseline':tree(a.baseline),'candidate':tree(a.candidate),'dependencies':{name:sha(Path(name)) for name in deps.split(':')}}
assert len(initial['baseline'])==len(initial['candidate'])==4261
changed=[p for p in initial['baseline'] if initial['baseline'][p]!=initial['candidate'][p]]
assert changed==['cn/nukkit/block/BlockLeaves.class'],changed
scenarios=['immediate-null','immediate-log','isolated','small-chain','dense-no-support','dense-log-distance5'];raw=[]
with tempfile.TemporaryDirectory(prefix='leaf-search-benchmark-') as directory:
 subprocess.run(['javac','--release','17','-cp',f'{a.baseline.resolve()}:{deps}','-d',directory,str(source)],check=True,timeout=120)
 helper_tree=tree(Path(directory))
 for fork in range(1,2 if a.check else 4):
  for variant in (['before','after'] if fork%2 else ['after','before']):
   classes=a.baseline if variant=='before' else a.candidate
   command=['java','-Xms128m','-Xmx512m','-cp',f'{classes.resolve()}:{directory}:{deps}','cn.nukkit.block.LeafSearchBenchmark']
   if a.check:command.append('--check')
   result=subprocess.run(command,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True,timeout=240)
   (a.output/f'{variant}-{fork}.log').write_text(result.stdout)
   rows=[json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
   assert len(rows)==(6 if a.check else 42)
   assert set(row['scenario'] for row in rows)==set(scenarios)
   if not a.check:
    assert all(sorted(r['sample'] for r in rows if r['scenario']==s)==list(range(7)) for s in scenarios)
   raw.extend(dict(row,fork=fork,variant=variant) for row in rows)
 for scenario in scenarios:assert len({r['checksum'] for r in raw if r['scenario']==scenario})==1,scenario
 assert helper_tree==tree(Path(directory))
assert initial=={'baseline':tree(a.baseline),'candidate':tree(a.candidate),'dependencies':{name:sha(Path(name)) for name in deps.split(':')}}
paired=[]
if not a.check:
 for scenario in scenarios:
  for fork in range(1,4):
   row=dict(scenario=scenario,fork=fork)
   for variant in ('before','after'):
    selected=[r for r in raw if r['scenario']==scenario and r['fork']==fork and r['variant']==variant]
    row[variant]={key:statistics.median(r[key] for r in selected) for key in ('ns_per_search','main_cpu_ns_per_search','main_bytes_per_search')}
   paired.append(row)
report={'check_only':a.check,'source_sha256':sha(source),'runner_sha256':sha(Path(__file__)), 'java':subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True),
        'main_class_trees':initial,'helper_classes':helper_tree,'all_class_and_dependency_hashes_stable':True,'only_changed_class':changed,'raw':raw,'paired_fork_medians':paired,
        'scope':'Actual private findLog through cached MethodHandle; concrete prebuilt graph16root ring per scenario, native hash/equals/predicates; fixed neighbor accessor replaces world lookup. Includes real collection/enum traversal, checksum loop overhead and GC; excludes setup/reflection/classinit. ThreadMXBean MAIN allocatedbytes and CPU plus walltime; no Mockito, providerIO, production frequency or TPS claim.'}
(a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(paired if not a.check else {'checksums_equal':True,'rows':len(raw)},indent=2))
