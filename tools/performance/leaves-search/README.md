# Leaf support search scratch

The candidate changes only `BlockLeaves.findLog` scratch storage: a private
`ArrayDeque<>(1)` FIFO replaces `LinkedList`, and an unexposed enum-order array
replaces per-node `BlockFace.values()` arrays. HashSet/HashMap, virtual block
predicates/accessors, support on an unloaded neighbor, callback order and the
existing distance limit remain unchanged. A node at distance four still examines
its neighbors; a queued leaf at distance five stops the search before expansion.

`LeafSearchScratchTest` compares the actual private method, through a cached
MethodHandle, with the original b216 method body. It checks null/log at every
face, distance boundaries, cycles/equal aliases/hash collisions, virtual predicates
and metadata, callback exceptions/reentry/mutation, and the native loaded-only
accessor using a concrete constructor-free Level. Its `main` runs the same eight
JUnit methods against separate baseline/candidate main class trees and emits a
consumed observation trace digest. There is no timed Mockito fixture.

The benchmark invokes the **actual private method** on concrete prebuilt graphs.
Each scenario selects from sixteen roots. Blocks retain native coordinate
`hashCode`/`equals` and leaf/log predicates; a fixed neighbor-array accessor
isolates the search from world/provider cost. Immediate unloaded support and log
support, an isolated unsupported leaf, a two-leaf supported chain, and dense
231-leaf graphs with/without support are separate cases. Dense nodes beyond the
processed radius retain the original stopping behavior.

Prepare immutable full baseline and candidate main class trees and a dependency
classpath file using the same JDK/dependencies. The local experiment overlays
only `BlockLeaves.class` on the retained complete b216 baseline (4261 classes).
Then run, from this checkout:

```sh
python3 tools/performance/leaves-search/run.py \
  --baseline /absolute/path/to/baseline-classes \
  --candidate /absolute/path/to/candidate-classes \
  --classpath-file /absolute/path/to/dependency-classpath.txt \
  --output /absolute/path/to/results
```

Add `--check` for a short identical-work/result check before measurements.
The runner compiles only the standalone harness, keeps the selected main tree
first in every runtime classpath, and hashes both entire class trees and every
dependency before/after. Only `BlockLeaves.class` may differ. Three fresh JVM
pairs alternate before/after execution order; each JVM warms all cases and then
reports seven samples per case in alternating scenario order. The runner matches
checksums and pairs medians by exact case/fork/variant. Reflection, graph setup,
class initialization and printing are outside timing. Graph traversals and the
consumed checksum loop are inside timing.

Wall time includes pauses; MAIN thread CPU and allocated bytes are separately
measured with ThreadMXBean. Default escape analysis remains enabled, so removed
source objects do not imply physical allocation savings. Worker CPU, chunk I/O,
whole-world behavior, production invocation frequency and TPS are outside this
synthetic search measurement. Report immediate/small cases and any regressions
alongside dense-search results; do not infer production tail latency from it.
