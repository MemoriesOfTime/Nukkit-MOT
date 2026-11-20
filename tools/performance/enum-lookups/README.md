# Enum lookup benchmark

Build the unchanged `b216d2b21` baseline in a separate checkout and this candidate
with the same JDK, Maven compiler settings and dependencies. Each main class tree
contains 4,261 classes. In the candidate checkout:

```sh
mvn -B -ntp test
mvn -B -ntp dependency:build-classpath -Dmdep.outputFile=/tmp/nukkit-enum-lookup-cp.txt
JDK_JAVA_OPTIONS=-Xmx2g python3 tools/performance/enum-lookups/run.py \
  --baseline-classes /path/to/unchanged-baseline/target/classes \
  --candidate-classes target/classes \
  --dependency-classpath /tmp/nukkit-enum-lookup-cp.txt \
  --output /tmp/nukkit-enum-lookup-fresh-run
```

Run one benchmark at a time. The runner creates a fresh output directory, hashes
every baseline/candidate main class and dependency before and after, and compiles
one fresh helper. The selected production classes precede helpers. It accepts a
matching full candidate tree or the four-class overlay produced by compiling
`BitArrayVersion` and `BlockShelf`; unrelated main classes must remain unchanged.
Class origins and class-load logs prove the native targets and fresh helper used
in each JVM. Behavior tests, mocks and synthetic target subclasses are excluded.

Three fresh JVM pairs run before/after, after/before, before/after. Each workload
has eight warmup loops and seven samples. Raw read/write enum lookup, shelf type
and unchanged shelf powered-state control run 1,000,000 operations per loop.
Whole native `PalettedBlockStorage.readFromStorage` uses V0, V1 and V4 wire forms,
8,192 operations per loop. Every sample remains in the output: seven workloads,
294 samples, and 21 paired fork medians.

The whole reader uses 256 native storage instances and 256 prebuilt Netty buffers
per wire form. Buffers contain one valid palette entry with varying runtime IDs;
the header's low bit varies, all word arrays contain zero indices. Each operation
resets the reader index, calls the entire reader, then consumes a varying cell.
Storage instances retain their native bit arrays. Buffer creation/release and
initial storage construction occur outside timing. Independent expected
checksums, exhausted wire cursors and final retained states are checked. Separate
behavior tests cover every bit width, two-entry palettes, all cells, invalid
lookup inputs, metadata and shelf connection callbacks.

Current-thread allocated bytes, current-thread CPU and wall time are separate.
CPU-counter calls stay outside the allocation interval. Child JVMs use
`-Xms256m -Xmx256m`, normal escape analysis and default compiler settings. Report
every workload and unchanged control, including regressions. Private arrays are
never forced to escape; returned enum constants and reader output are consumed.

Two private cached enum arrays move allocation to class initialization and retain
their reference slots. Startup time and retained static storage are excluded
from warm measurements. Public `values()` still returns independent snapshots.
These measurements establish local method behavior and allocation; they do not
establish production call frequency, TPS, latency percentiles or a live-client
result.
