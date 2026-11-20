#!/usr/bin/env python3
"""Compare actual PGZIP write(int) in three fresh JVM pairs with an unchanged bulk control."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import tempfile

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline',type=Path,required=True)
p.add_argument('--candidate',type=Path,required=True)
p.add_argument('--classpath-file',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True)
deps=a.classpath_file.read_text().strip()
source=Path(__file__).with_name('PgzipSingleByteBenchmark.java')
raw=[]
with tempfile.TemporaryDirectory(prefix='pgzip-single-byte-') as temporary:
    subprocess.run(['javac','--release','17','-cp',f'{a.baseline.resolve()}:{deps}',
                    '-d',temporary,str(source)],check=True,timeout=120)
    for fork in range(1,4):
        for variant in (['before','after'] if fork%2 else ['after','before']):
            classes=a.baseline if variant=='before' else a.candidate
            result=subprocess.run(['java','-Xms64m','-Xmx512m','-cp',
                                   f'{temporary}:{classes.resolve()}:{deps}',
                                   'PgzipSingleByteBenchmark'],text=True,stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT,check=True,timeout=180)
            (a.output/f'{variant}-{fork}.log').write_text(result.stdout)
            rows=[json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
            assert len(rows)==16
            assert all(sum(r['mode']==mode for r in rows)==8 for mode in ('write-int','bulk-control'))
            raw.extend(dict(row,variant=variant,fork=fork) for row in rows)
assert len({(row['gzip_bytes'],row['checksum']) for row in raw})==1,'Gzip bytes/checksum differ'
paired=[]
for mode in ('write-int','bulk-control'):
    for fork in range(1,4):
        row=dict(mode=mode,fork=fork)
        for variant in ('before','after'):
            selected=[r for r in raw if r['mode']==mode and r['fork']==fork and r['variant']==variant]
            row[variant]={key:statistics.median(r[key] for r in selected)
                          for key in ('ns_per_byte','main_bytes_per_byte')}
        paired.append(row)
report=dict(java=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True),
            source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
            scope='Actual identical write(int) loops over 1 MiB per stream, plus unchanged caller-owned reusable bulk-array control. Main-thread allocations and wall write-loop time including submission/emission and worker compression waits; excludes construction/close, worker allocations and native Deflater memory. Synthetic compressible 256-byte repeating input. Gzip result consumed outside timing. No TPS claim.',
            all_gzip_checksums_equal=True,raw=raw,paired_fork_medians=paired)
(a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(paired,indent=2))
