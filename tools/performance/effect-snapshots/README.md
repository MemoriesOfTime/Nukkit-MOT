# Native entity effect snapshot benchmark

Build an unchanged `b216d2b21` baseline in a separate checkout, and build this
candidate with the same JDK, Maven settings and dependencies. The baseline main
tree contains 4,261 classes; the candidate contains 4,262, including the private
snapshot-state class. The runner requires complete actual main trees and allows
only the changed `Entity.class` plus that one added private class.

```sh
mvn -B -ntp test
mvn -B -ntp dependency:build-classpath -Dmdep.outputFile=/tmp/nukkit-effect-snapshot-cp.txt
JDK_JAVA_OPTIONS=-Xmx2g python3 tools/performance/effect-snapshots/run.py \
  --baseline-classes /path/to/unchanged-baseline/target/classes \
  --candidate-classes target/classes \
  --dependency-classpath /tmp/nukkit-effect-snapshot-cp.txt \
  --output /tmp/nukkit-effect-snapshot-fresh-run
```

Run one benchmark at a time. The output directory must be new. Three fresh JVM
pairs run in AB/BA/AB order, with eight warmup loops and seven measured loops of
200,000 whole `Entity.entityBaseTick(1)` calls per workload. Child JVMs use
`-Xms256m -Xmx256m` and normal escape analysis. All eight workloads, 336 raw rows
and 24 per-fork paired medians remain in the output, including adverse controls.
Allocated bytes, current-thread CPU and wall time are reported separately;
CPU-counter calls stay outside the allocation interval.

Each workload has 256 prebuilt concrete fixture entities and exactly native
`Effect` instances with positive durations and `canTick()==false`. Effect counts
are 0, 1, 4, 8, 16, 17, 64 and 128. The larger cases exercise complete oversized
capture, including the public map's ability to hold additional integer keys with
repeated native effect IDs. All effects are distinct references. The 0-effect
case is an unchanged empty-guard control. Timed loops consume the native return,
age and tick count; independent checksums and every duration, membership count
and native state are checked after each sample.

The concrete Entity subclass implements only the abstract network ID; the
actual inherited base-tick body runs. Objenesis construction and reflective
setup of final effect/passenger fields occur outside timing. Fixtures are
healthy, stationary at y64, with noClip, empty passengers and no fire, damage or
portal state. Exact native Level and Anvil objects provide uninstrumented
`getMinBlockY`/provider getters; their disk/world constructors are bypassed.
This isolates the steady effect-bearing path and excludes world collision,
active effect callbacks, expiry and server events from the measurements.

Callback mutation, replacement, duplicates, reentry, exceptions, close,
expiration, malformed raw maps and parallel ownership are covered by behavior
tests in a separate source/class directory and JVM. That test source is never
compiled into or referenced by the timed helper. No timed Mockito or synthetic
Effect subclass participates. Actual native class origins, the two fresh helper
classes, complete main trees and dependencies are checked before and after.
The selected full main tree precedes helpers on every timed classpath.

The private ThreadLocal retains one empty 16-reference array per participating
thread. Its first allocation and startup cost are outside these warm samples.
Oversized results are complete independently allocated arrays and are cleared
without being retained. This local benchmark does not establish production call
frequency, TPS, latency percentiles or client behavior. Report every allocation
and CPU/wall result, including unchanged and oversized paths.
