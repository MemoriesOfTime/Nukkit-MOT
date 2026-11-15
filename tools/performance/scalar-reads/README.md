# Fixed-width scalar read benchmark

Build the unchanged base and candidate with the same JDK and Maven dependencies.
Keep both complete `target/classes` trees, including resources, in separate paths.
Generate the dependency classpath once with Maven's dependency plugin. Use the same
classpath file for both variants. Run without concurrent builds or benchmarks:

```sh
python3 tools/performance/run-scalar-read.py --baseline /path/to/base/classes \
  --candidate target/classes --classpath-file /path/to/dependency-classpath.txt \
  --harness stream --output /tmp/scalar-stream.json
python3 tools/performance/run-scalar-read.py --baseline /path/to/base/classes \
  --candidate target/classes --classpath-file /path/to/dependency-classpath.txt \
  --harness packet-read --output /tmp/scalar-packet.json
python3 tools/performance/run-scalar-read.py --baseline /path/to/base/classes \
  --candidate target/classes --classpath-file /path/to/dependency-classpath.txt \
  --harness packet-constructor --output /tmp/scalar-constructor.json
python3 tools/performance/run-scalar-read.py --baseline /path/to/base/classes \
  --candidate target/classes --classpath-file /path/to/dependency-classpath.txt \
  --harness blob-decode --output /tmp/scalar-blob-decode.json
```

The runner compiles only the selected Java harness, rejects helper classes that
could shadow core classes, puts the selected production tree first, and hashes
both complete main class trees before and after measurement. Three fresh JVM
pairs use AB/BA/AB order, `-Xms256m -Xmx256m`, eight warmup batches and seven measured
samples. Stream, raw packet-reader and constructor modes use one million operations
per batch; whole blob decoding uses 131,072 operations, or 8,192 for 256 hashes.
Consume every checksum and retain every fork.
`ThreadMXBean` allocated bytes are current-thread Java heap bytes, not native
memory or whole-process allocation. Timings use `System.nanoTime`.

`stream` uses 256 varied prebuilt BinaryStream inputs. Its mixed operation is eight
reads; width modes are one read; the little-endian control is two unchanged reads.
`packet-read` runs the same mixed and unchanged control on actual MovePlayerPacket
instances, including the warmed subclass capability lookup. `packet-constructor`
retains actual newly created packets in a 256-slot ring. The stream constructor
control similarly retains instances. Cold class loading and first-subclass
capability reflection are outside warmed measurement.

Baseline b216d2b21 on JDK 26.0.2/macOS aarch64: stream and packet mixed reads allocate
192 B/op before and 0 B/op after in all three pairs. Individual widths allocate
24 B/read before and 0 after. Native unchanged little-endian timings regress in
all three pairs; native constructor timings regress in two pairs and actual packet
constructor timings regress in one. Both constructor allocations stay unchanged.
Do not infer universal CPU, production frequency, TPS or latency percentiles.
The selector and class/helper diagnostics were added after the initial raw-reader and
constructor measurements and used for the additional whole-packet run. The original
measured Java loops, production source and behavior tests are unchanged.

`blob-decode` measures the actual ClientCacheBlobStatusPacket.decode implementation
with legacy/modern wire layouts and 0/1/16/256 total hashes. Each JVM handles one
case with 256 prebuilt packets; setup checks every expected hash array and cursor.
Eight warmup and seven measured batches use 131,072 operations, or 8,192 for 256
hashes. Every resulting array is consumed and retained through packet fields.
All three pairs allocate 64→40 B for one hash, 544→160 B for 16 and 8,224→2,080 B
for 256; empty controls remain 32 B. Whole-packet timing is mixed, with both
target and empty-control regressions retained in the PR table. The MovePlayerPacket
raw-reader workload exercises inherited methods, not its decode implementation.
