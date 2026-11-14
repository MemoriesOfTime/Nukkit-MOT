# Block name literal table benchmark

The harness calls the actual public `getName()` and whole `toItem()` methods on
256 prebuilt native instances of each of 24 block classes and two cached controls
(`BlockFlower`, `BlockLeaves2`). Metadata varies across 0–15; coral fan uses valid
types 0–4. Invalid indices, changing metadata, virtual callback order, exceptions,
literal identity and reentrant calls are covered separately by behavior tests.

Build the unchanged `b216d2b21` baseline in a separate checkout and this candidate
with the same JDK, Maven compiler settings and dependencies. Each complete main
class tree contains 4,261 classes. Build the dependency classpath in the candidate
checkout (it includes the test fixture's Mockito dependency):

```sh
mvn -B -ntp test
mvn -B -ntp dependency:build-classpath -Dmdep.outputFile=/tmp/nukkit-block-names-cp.txt
JDK_JAVA_OPTIONS=-Xmx2g python3 tools/performance/block-name-tables/run.py \
  --baseline-classes /path/to/unchanged-baseline/target/classes \
  --candidate-classes target/classes \
  --dependency-classpath /tmp/nukkit-block-names-cp.txt \
  --output /tmp/nukkit-block-names-fresh-run
```

Run one benchmark at a time on an otherwise idle machine. The candidate may also
be an overlay containing the 24 affected classes and two existing enum-switch
companions; the runner verifies the same complete effective class tree. A full
candidate tree must have the same class set as the baseline, with differences
limited to that family. Both cached controls must remain byte-identical.

The output directory must be new. The runner builds the existing `MockServer`
registry fixture into an isolated support directory and initializes it before
timing. Timed calls use native constructors and public methods, with no timed
mocks or reflection. Behavior-test classes and their synthetic target subclasses
are excluded from the benchmark classpath. Class-load logs and actual source
locations verify the selected native classes and fresh helper.

Three fresh JVM pairs run in alternating order (before/after, after/before,
before/after). Every workload has eight warmup loops and seven measured loops of
200,000 operations. The returned strings/items escape into a retained ring and
are consumed with names, IDs, damage and counts. The private name arrays are never
forced to escape. Expected checksums are verified within each JVM and between
every paired variant. All 52 workloads and 2,184 samples remain in the output.

Current-thread allocated bytes, current-thread CPU and wall elapsed time are
reported separately. CPU-counter calls stay outside the allocation interval.
Each paired median uses exactly seven samples from its own fork. All 4,261 baseline classes, the selected candidate tree or overlay, fresh helpers,
support classes and dependency files must remain unchanged. Selected production classes precede all helpers on every classpath.

The candidate caches only literal arrays. Dynamic name concatenation remains and
must still return fresh strings; metadata, callback order, drops and `toItem()`
logic remain unchanged. Baseline allocation alone cannot establish the saving of
the table in a concatenating method; use the paired whole-method result. Report
every class and both cached controls, including regressions. These local
measurements do not establish production call frequency, TPS, latency percentiles
or behavior on a live client.

The 24 private tables add one retained array per affected loaded class, including
its literal-reference slots. This moves allocation to class initialization;
startup time and retained static-table cost are excluded from these warm
measurements. String literals retain their existing identities, and dynamic
concatenation results are not cached.
