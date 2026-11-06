# actor-retained-primitive-set

This microbenchmark calls actual MOT actor storage methods from separately compiled baseline/candidate class directories. Baseline: `b216d2b21fe9f25d242ec3832234a6a3092ab029`. It measures generated keys, eager digest reads with missing records, and full preserved-actor snapshot plus stale cleanup. Read/snapshot workloads use 0/1/16/64/256 retained actors plus two stale digest entries; preserved input adds a duplicate every fourth actor. No disk/JNI latency is measured.

Every generated key, read result and cleanup closure escapes into a retained ring. The in-memory DB and batch also retain observed key/value arrays, matching their production escape behavior. Batch outputs feed a consumed checksum. Setup creates a real LevelDBChunk; a constructor-free Level fixture supplies only dimension getters, and a LevelProvider proxy supplies that Level. No Mockito calls run inside timed loops. Eight warmup batches precede five measured batches in each of three independent JVM forks per variant; order alternates. ThreadMXBean reports B/op, System.nanoTime reports ns/op. Results include class-binding directories, raw logs/samples and medians.

Build/test baseline and candidate separately; freeze baseline classes. In candidate checkout:

```sh
mvn -B -ntp test
mvn -B -ntp dependency:build-classpath -Dmdep.outputFile=target/benchmark-classpath.txt
python3 tools/performance/actor-retained-primitive-set/run.py \
  --baseline-classes /path/to/baseline/target/classes \
  --dependency-classpath target/benchmark-classpath.txt \
  --output target/actor-retained-primitive-set-results
```

JDK 17+; benchmark compiler targets release 17 and JVM forks use -Xms256m -Xmx256m. Allocation is primary evidence under unrelated host load. These synthetic workloads establish per-operation cost, not current production frequency or TPS/MSPT/p99 improvement.

## Results

OpenJDK 26.0.2, macOS aarch64; baseline versus candidate, B/op ranges across the three fork medians:

| Snapshot + stale cleanup | Baseline B/op | Candidate B/op |
|---|---:|---:|
| 0 retained actors | 280 | 280 |
| 1 retained actor | 904–968 | 624 |
| 16 retained actors | 4,168–4,256 | 2,600 |
| 64 retained actors | 15,376–15,464 | 9,120 |
| 256 retained actors | 60,896–60,984 | 35,840 |

Generated-key allocation remains 24 B/op. Eager reads with missing actor records remain 288/352/1,496/5,248/21,136 B/op for the five fixture sizes in every fork. The zero-actor fixture still contains two stale digest entries. CPU timings, including unchanged controls, vary under unrelated host load; these results support allocation reduction without a whole-server speedup claim.

Full `mvn -B -ntp test`: 26,286 tests, no failures/errors, the original seven skips. All 459 baseline suites preserve their counts; the added eight invocations also pass unchanged against baseline classes.

## Regression coverage

Added 8 test invocations, also executed unchanged against frozen baseline classes. Existing native/map LevelDB read and delayed durable-save tests remain in full Maven suite; original seven skipped testcase identities must match baseline.

## Compared with

PowerNukkitX beta `17a858adbec9` has modern actor codec/digest support and a separate boxed pending-deletion lifecycle. Its stricter malformed-digest validation and different canonical ID mapping are not transplanted. Lumi stable/dev `915d27295eb1`, PetteriM1 release `865f4b1cdfd4`, and CloudburstMC master `48419471008e` use legacy concatenated entity NBT. EaseCation master `cad5792a8415` recognizes digp keys but reads/writes legacy entity NBT. None supplies a directly equivalent detached MOT retained-ID snapshot.

No donor implementation copied; use the existing fastutil dependency for a private membership representation. Keep live/preserved digest order, duplicates and stale cleanup order. Empty/singleton snapshots use immutable primitive sets; larger cleanup sets are privately owned and never mutated after capture.
