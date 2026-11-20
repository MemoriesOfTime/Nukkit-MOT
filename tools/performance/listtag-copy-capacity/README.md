# Native NBT list copy capacity

This harness calls actual `ListTag.copy()` from separately compiled baseline and candidate class directories. It does not compare reimplemented algorithms. Each copy escapes into a retained 64-element ring; the ring is consumed after every batch. Eight warmup batches precede five measured batches. Three independent JVM forks per variant alternate before/after order. Allocation uses `com.sun.management.ThreadMXBean`; nanoseconds use `System.nanoTime`.

Build/test both revisions separately. Baseline revision: `b216d2b21fe9f25d242ec3832234a6a3092ab029`. Keep its compiled classes frozen. In the candidate checkout:

```sh
mvn -B -ntp test
mvn -B -ntp dependency:build-classpath -Dmdep.outputFile=target/benchmark-classpath.txt
python3 tools/performance/listtag-copy-capacity/run.py \
  --baseline-classes /path/to/baseline/target/classes \
  --dependency-classpath target/benchmark-classpath.txt \
  --output target/listtag-copy-results
```

JDK 17+ required. The benchmark compiler targets Java 17; measured runs use `-Xms256m -Xmx256m`. Each raw log prints the loaded ListTag class directory. Results include raw samples and medians across 15 measurements per variant/workload. No current production hot-path share or server TPS/MSPT improvement is inferred. Timing remains sensitive to unrelated host load; allocated bytes per operation are the primary result.

Regression coverage verifies nested child ownership, 0/1/10/11/36/256/4096 entries, names and list type, original name/type/child callback order, custom lists and ArrayList subclasses, callback mutation and null failures. The same added regression class is also run against frozen baseline classes.

## Results

Measured on JDK 26.0.2, macOS aarch64, three forks per variant. Allocation savings were identical in every fork: 448 B at 36 children, 3,480 B at 256, 33,816 B at 4,096; small 0/1/10-entry lists unchanged.

| Workload | B/op before → after | ns/op before → after |
|---|---:|---:|
| compound-36 | 15920.0 → 15472.0 | 56884.5 → 29182.9 |
| int-0 | 48.0 → 48.0 | 44.0 → 21.0 |
| int-1 | 128.0 → 128.0 | 204.1 → 185.2 |
| int-10 | 344.0 → 344.0 | 544.6 → 443.9 |
| int-11 | 448.0 → 376.0 | 848.9 → 804.6 |
| int-256 | 10712.0 → 7232.0 | 34277.2 → 17021.6 |
| int-36 | 1520.0 → 1072.0 | 1312.8 → 1770.1 |
| int-4096 | 148568.0 → 114752.0 | 708090.3 → 590809.5 |

Timing was noisy and mixed under shared host load. For int-36, per-fork medians before were 846.8 / 6,475.9 / 875.4 ns and after 1,104.2 / 3,915.4 / 3,602.0 ns. Even unchanged small-copy controls varied substantially. No CPU speedup or server-wide improvement is claimed; allocation reduction is the repeatable result.

## Peer comparison

PowerNukkitX beta `17a858adbec9`, Lumi stable/dev `915d27295eb1`, PetteriM1 release `865f4b1cdfd4` and CloudburstMC master `48419471008e` use default-capacity child-copy loops. EaseCation master `cad5792a8415` pre-sizes copies (`src/main/java/cn/nukkit/nbt/tag/ListTag.java:518-525`, GPL-3.0). This change uses that capacity idea with the local exact-native-list guard and existing ensureCapacity call; no donor code copied or public constructor added.

Full `mvn -B -ntp test`: 26,290 tests, no failures/errors, the original seven skips. All 459 baseline suites preserve their counts; the added 12 invocations also pass unchanged against baseline classes.
