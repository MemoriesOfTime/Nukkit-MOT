# PGZIP single-byte writes

Compare actual `PGZIPOutputStream.write(int)` on unchanged and candidate main classes using three fresh JVM pairs, alternating process order. Each JVM warms eight 1 MiB streams, then records eight ABBA samples for each mode. The unchanged control calls `write(byte[], 0, 1)` with a caller-owned reusable array. Every gzip result is consumed after timing and all compressed lengths/checksums must match across modes and processes.

The synthetic input repeats a 256-byte pattern and compresses well. Timed write loops include block submission, compressed output emission and any waits for worker/native compression before the loop ends. Construction and close are outside timing. ThreadMXBean counts allocations on the calling thread only; worker allocation and native Deflater memory are excluded. Results do not establish server TPS or production frequency.

Compile the unchanged base in a separate checkout, preserve its `target/classes`, then compile this branch with `mvn -B -ntp test`. Generate the test dependency classpath with `mvn -B -ntp dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=/tmp/nukkit-test-classpath.txt`.

```sh
python3 tools/performance/pgzip-single-byte/run.py \
  --baseline /path/to/unchanged/classes \
  --candidate target/classes \
  --classpath-file /tmp/nukkit-test-classpath.txt \
  --output /tmp/pgzip-single-byte-results
```

Behavioral coverage checks exact gzip bytes, decompression/CRC/trailer, full-block boundaries, mixed bulk/single writes and flushes, int truncation, subclass array/slice overrides with retention/reentry/failure, rejected-submission retry and existing post-close behavior.
