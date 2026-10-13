package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Batch framing and ownership regressions that restore Server/Zlib globals. */
public final class PlayerChunkCacheOwnedBatchTest {
    static final int X = -1_234_567;
    static final int Z = 7_654_321;
    static final int SUB_CHUNKS = 24;
    static final int DIMENSION = -1;
    static final int LEVEL = 7;
    private static final int[] BOUNDARIES = {
            126, 127, 128, 129, 16_382, 16_383, 16_384, 16_385,
            2_097_150, 2_097_151, 2_097_152, 2_097_153
    };

    static final class Fixture implements AutoCloseable {
        private final Field serverField;
        private final Field providerField;
        private final Object previousServer;
        private final Object previousProvider;
        final Server server;

        Fixture() throws Exception {
            serverField = Server.class.getDeclaredField("instance");
            serverField.setAccessible(true);
            providerField = Zlib.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            previousServer = serverField.get(null);
            previousProvider = providerField.get(null);
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field holder = unsafeClass.getDeclaredField("theUnsafe");
            holder.setAccessible(true);
            Object unsafe = holder.get(null);
            server = (Server) unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, Server.class);
            server.networkCompressionLevel = LEVEL;
            server.networkCompressionThreshold = 256;
            serverField.set(null, server);
            Zlib.setProvider(2);
        }

        void provider(ZlibProvider provider) throws Exception {
            providerField.set(null, provider);
        }

        @Override
        public void close() throws IllegalAccessException {
            providerField.set(null, previousProvider);
            serverField.set(null, previousServer);
        }
    }

    static byte[] payload(int length, boolean random) {
        byte[] data = new byte[length];
        int state = 0x67_42_a9_13;
        for (int i = 0; i < length; i++) {
            if (random) {
                state ^= state << 13;
                state ^= state >>> 17;
                state ^= state << 5;
                data[i] = (byte) state;
            } else {
                data[i] = (byte) ((i / 97) & 3);
            }
        }
        return data;
    }

    static int prefixWidth(int length) {
        int width = 1;
        while ((length >>>= 7) != 0) width++;
        return width;
    }

    static byte[] packet(GameVersion version, byte[] payload) {
        LevelChunkPacket packet = new LevelChunkPacket();
        packet.chunkX = X;
        packet.chunkZ = Z;
        packet.dimension = DIMENSION;
        packet.subChunkCount = SUB_CHUNKS;
        packet.data = payload;
        packet.protocol = version.getProtocol();
        packet.gameVersion = version;
        packet.tryEncode();
        return packet.getBuffer();
    }

    static byte[] plainBatch(GameVersion version, byte[] payload) {
        byte[] encoded = packet(version, payload);
        byte[] batch = new byte[prefixWidth(encoded.length) + encoded.length];
        int remaining = encoded.length;
        int offset = 0;
        do {
            int next = remaining & 127;
            remaining >>>= 7;
            batch[offset++] = (byte) (remaining == 0 ? next : next | 128);
        } while (remaining != 0);
        System.arraycopy(encoded, 0, batch, offset, encoded.length);
        return batch;
    }

    static BatchPacket actual(GameVersion version, byte[] payload) {
        return Player.getChunkCacheFromData(version, X, Z, SUB_CHUNKS, payload, DIMENSION);
    }

    private static byte[] inflateRaw(byte[] data) throws Exception {
        return inflate(data, true);
    }

    private static byte[] inflate(byte[] data, boolean raw) throws Exception {
        Inflater inflater = new Inflater(raw);
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                check(count != 0, "raw inflater stalled");
                out.write(buffer, 0, count);
            }
            check(inflater.getRemaining() == 0, "extra bytes after raw zlib stream");
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static byte[] deflateRaw(byte[] data, int level) {
        return deflate(data, level, true);
    }

    private static byte[] deflate(byte[] data, int level, boolean raw) {
        Deflater deflater = new Deflater(level, raw);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                check(count != 0, "raw deflater stalled");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static void equal(byte[] expected, byte[] actual, String description) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(description + ": expected length=" + expected.length
                    + ", actual length=" + actual.length + ", first mismatch="
                    + Arrays.mismatch(expected, actual));
        }
    }

    private static List<GameVersion> wireVersions() {
        Map<Integer, GameVersion> protocols = new LinkedHashMap<>();
        List<GameVersion> special = new ArrayList<>();
        for (GameVersion version : GameVersion.values()) {
            if (version.getProtocol() < 589 || version.getProtocol() > 2193) continue;
            if (version.isNetEase() || protocols.containsKey(version.getProtocol())) {
                special.add(version);
            } else {
                protocols.put(version.getProtocol(), version);
            }
        }
        check(protocols.size() == 39, "supported window changed: " + protocols.size());
        List<GameVersion> result = new ArrayList<>(protocols.values());
        result.addAll(special);
        check(result.size() == 46, "alias/NetEase coverage changed: " + result.size());
        return result;
    }

    private static int payloadForPacketLength(GameVersion version, int encodedLength) {
        int fixed = packet(version, new byte[0]).length - 1;
        for (int width = 1; width <= 5; width++) {
            int length = encodedLength - fixed - width;
            if (length >= 0 && prefixWidth(length) == width) return length;
        }
        throw new AssertionError("unreachable encoded length " + encodedLength);
    }

    private interface CaseConsumer {
        void accept(String key, GameVersion version, boolean snappy, int threshold,
                byte[] input, byte[] expectedPlain, byte[] compressed) throws Exception;
    }

    private static int runCases(boolean boundaries, CaseConsumer consumer) throws Exception {
        int count = 0;
        try (Fixture fixture = new Fixture()) {
            List<GameVersion> versions = boundaries
                    ? List.of(GameVersion.V1_20_0, GameVersion.V1_20_60, GameVersion.V1_26_50)
                    : wireVersions();
            for (GameVersion version : versions) {
                int[] sizes = boundaries ? BOUNDARIES : new int[] {256};
                for (int target : sizes) {
                    int inputLength = boundaries ? payloadForPacketLength(version, target) : target;
                    for (boolean random : boundaries ? new boolean[] {false} : new boolean[] {false, true}) {
                        byte[] input = payload(inputLength, random);
                        byte[] expected = plainBatch(version, input);
                        int encodedLength = expected.length - prefixWidth(packet(version, input).length);
                        check(!boundaries || encodedLength == target,
                                "packet overhead must be included in VarUInt boundary");
                        for (boolean snappy : new boolean[] {false, true}) {
                            fixture.server.useSnappy = snappy;
                            for (int delta = -1; delta <= 1; delta++) {
                                fixture.server.networkCompressionThreshold = expected.length + delta;
                                String key = (boundaries ? "boundary:" : "window:") + version.name()
                                        + ':' + target + ':' + random + ':' + snappy + ':' + delta;
                                byte[] before = input.clone();
                                byte[] compressed = actual(version, input).payload;
                                equal(before, input, key + " caller input changed during compression");
                                byte[] decoded = snappy
                                        ? SnappyCompression.decompress(compressed, expected.length + 1)
                                        : inflateRaw(compressed);
                                equal(expected, decoded, key + " independent batch framing");
                                if (!snappy) {
                                    equal(deflateRaw(expected, delta > 0 ? 0 : LEVEL), compressed,
                                            key + " raw threshold exact bytes");
                                }
                                consumer.accept(key, version, snappy,
                                        fixture.server.networkCompressionThreshold, input, expected, compressed);
                                count++;
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    @Test
    void supportedWindowAndNetEaseMatchIndependentFraming() throws Exception {
        check(runCases(false, (key, version, snappy, threshold, input, plain, compressed) -> {}) == 552,
                "window case count");
    }

    @Test
    void encodedPacketVarUIntBoundariesIncludePacketOverhead() throws Exception {
        check(runCases(true, (key, version, snappy, threshold, input, plain, compressed) -> {}) == 216,
                "boundary case count");
    }

    @Test
    void thresholdUsesExactBatchLengthAndSnappyIgnoresThreshold() throws Exception {
        try (Fixture fixture = new Fixture()) {
            byte[] input = payload(4096, false);
            GameVersion version = GameVersion.V1_26_50;
            int length = plainBatch(version, input).length;
            for (boolean snappy : new boolean[] {false, true}) {
                fixture.server.useSnappy = snappy;
                fixture.server.networkCompressionThreshold = length - 1;
                byte[] below = actual(version, input).payload;
                fixture.server.networkCompressionThreshold = length;
                byte[] equal = actual(version, input).payload;
                fixture.server.networkCompressionThreshold = length + 1;
                byte[] above = actual(version, input).payload;
                equal(below, equal, "equal threshold must use configured compression level");
                if (snappy) equal(below, above, "Snappy threshold invariance");
                else check(above.length > below.length * 10, "above threshold must use raw level0");
            }
        }
    }

    private static class CaptureProvider implements ZlibProvider {
        final List<byte[]> captured = new ArrayList<>();
        IOException failure;

        @Override
        public byte[] deflateRaw(byte[] data, int level) throws IOException {
            if (failure != null) throw failure;
            captured.add(data);
            return new byte[] {11, 12, 13};
        }

        @Override public byte[] deflate(byte[] data, int level) { throw new AssertionError("wrapped zlib branch"); }
        @Override public byte[] deflate(byte[][] data, int level) { throw new AssertionError("multi-array branch"); }
        @Override public byte[] deflateRaw(byte[][] data, int level) { throw new AssertionError("multi-array branch"); }
        @Override public byte[] inflate(byte[] data, int maxSize) { throw new AssertionError("unexpected inflate"); }
        @Override public byte[] inflateRaw(byte[] data, int maxSize) { throw new AssertionError("unexpected inflateRaw"); }
    }

    @Test
    void byteArrayCompressorReceivesIndependentExactOwnedInput() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CaptureProvider provider = new CaptureProvider();
            fixture.provider(provider);
            IdentityHashMap<byte[], Boolean> identities = new IdentityHashMap<>();
            List<byte[]> expected = new ArrayList<>();
            byte[] input = payload(4096, true);
            GameVersion version = GameVersion.V1_26_50;
            for (int i = 0; i < 16; i++) {
                input[i] ^= (byte) (i + 1);
                expected.add(plainBatch(version, input));
                equal(new byte[] {11, 12, 13}, actual(version, input).payload, "compressor return bytes");
                byte[] captured = provider.captured.get(i);
                check(captured != input, "compressor aliased caller-owned payload");
                check(identities.put(captured, Boolean.TRUE) == null, "compressor input reused across calls");
            }
            Arrays.fill(input, (byte) 99);
            for (int i = 0; i < expected.size(); i++) {
                equal(expected.get(i), provider.captured.get(i), "owned batch immutable after later calls " + i);
            }
        }
    }

    @Test
    void compressorExceptionKeepsExactCauseAndRuntimeWrapper() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CaptureProvider provider = new CaptureProvider();
            provider.failure = new IOException("C126 sentinel");
            fixture.provider(provider);
            try {
                actual(GameVersion.V1_26_50, payload(256, false));
                throw new AssertionError("compressor failure swallowed");
            } catch (RuntimeException failure) {
                check(failure.getClass() == RuntimeException.class, "wrapper class changed");
                check(failure.getCause() == provider.failure, "compressor cause identity changed");
            }
        }
    }

    @Test
    void ownedInputAndReturnedBatchStayImmutableAfterAsyncPublication() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = new Fixture()) {
            CaptureProvider provider = new CaptureProvider();
            fixture.provider(provider);
            byte[] input = payload(4096, true);
            byte[] expected = plainBatch(GameVersion.V1_26_50, input);
            actual(GameVersion.V1_26_50, input);
            byte[] published = provider.captured.get(0);
            CompletableFuture<byte[]> observed = CompletableFuture.supplyAsync(() -> {
                try {
                    check(release.await(10, TimeUnit.SECONDS), "owned input reader release timeout");
                    return published.clone();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(failure);
                }
            }, executor);
            Arrays.fill(input, (byte) 51);
            for (int i = 0; i < 64; i++) actual(GameVersion.V1_26_50, input);
            release.countDown();
            equal(expected, observed.get(10, TimeUnit.SECONDS), "async owned compressor input changed");
            Zlib.setProvider(2);
            for (boolean snappy : new boolean[] {false, true}) {
                fixture.server.useSnappy = snappy;
                byte[] callerInput = payload(4096, true);
                BatchPacket batch = actual(GameVersion.V1_26_50, callerInput);
                byte[] frozen = batch.payload.clone();
                CompletableFuture<byte[]> after = CompletableFuture.supplyAsync(() -> batch.payload, executor);
                Arrays.fill(callerInput, (byte) 22);
                for (int i = 0; i < 64; i++) actual(GameVersion.V1_26_50, callerInput);
                equal(frozen, after.get(10, TimeUnit.SECONDS), "published BatchPacket payload changed");
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
            check(executor.awaitTermination(10, TimeUnit.SECONDS), "reader executor did not stop");
        }
    }

    private static int representativeProviderCases(CaseConsumer consumer) throws Exception {
        int count = 0;
        try (Fixture fixture = new Fixture()) {
            for (int provider : new int[] {0, 1, 2}) {
                Zlib.setProvider(provider);
                List<GameVersion> versions = provider == 2
                        ? List.of(GameVersion.V1_14_60)
                        : List.of(GameVersion.V1_14_60, GameVersion.V1_20_0,
                                GameVersion.V1_20_60, GameVersion.V1_26_50);
                for (GameVersion version : versions) {
                    boolean raw = version.getProtocol() >= ProtocolInfo.v1_16_0;
                    byte[] input = payload(4096, false);
                    byte[] plain = plainBatch(version, input);
                    for (boolean snappyFlag : raw ? new boolean[] {false} : new boolean[] {false, true}) {
                        fixture.server.useSnappy = snappyFlag;
                        for (int delta = -1; delta <= 1; delta++) {
                            fixture.server.networkCompressionThreshold = plain.length + delta;
                            String key = "provider:" + provider + ':' + version.name() + ':' + snappyFlag + ':' + delta;
                            byte[] compressed = actual(version, input).payload;
                            equal(plain, inflate(compressed, raw), key + " independent framing");
                            // Provider1 ignores supplied levels; provider0 raw ignores threshold.
                            int level = !raw && provider != 1 && delta > 0 ? 0 : LEVEL;
                            equal(deflate(plain, level, raw), compressed, key + " existing level/threshold bytes");
                            consumer.accept(key, version, snappyFlag,
                                    fixture.server.networkCompressionThreshold, input, plain, compressed);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    @Test
    void representativeProvidersAndPre16PreserveTheirExistingThresholdBehavior() throws Exception {
        check(representativeProviderCases((key, version, snappy, threshold, input, plain, compressed) -> {}) == 36,
                "representative provider case count");
    }

    @Test
    void identityReturningMutatingAndReentrantCompressorsKeepOwnership() throws Exception {
        try (Fixture fixture = new Fixture()) {
            for (int kind = 0; kind < 3; kind++) {
                final int mode = kind;
                CaptureProvider provider = new CaptureProvider() {
                    private boolean nested;

                    @Override
                    public byte[] deflateRaw(byte[] data, int level) {
                        captured.add(data);
                        if (mode > 0) data[0] ^= 68;
                        byte[] expected = data.clone();
                        if (mode == 2 && !nested) {
                            nested = true;
                            try {
                                actual(GameVersion.V1_26_50, payload(257, true));
                            } finally {
                                nested = false;
                            }
                            equal(expected, data, "reentrant compression overwrote outer owned batch");
                        }
                        return data;
                    }
                };
                fixture.provider(provider);
                byte[] input = payload(4096, true);
                byte[] before = input.clone();
                byte[] expected = plainBatch(GameVersion.V1_26_50, input);
                if (mode > 0) expected[0] ^= 68;
                BatchPacket batch = actual(GameVersion.V1_26_50, input);
                check(batch.payload == provider.captured.get(0), "identity compressor return was copied or replaced");
                check(batch.payload != input, "identity compressor exposed caller-owned input");
                equal(before, input, "mutating compressor changed caller input");
                equal(expected, batch.payload, "mutating compressor return contract changed");
                if (mode == 2) check(provider.captured.get(0) != provider.captured.get(1),
                        "reentrant compression reused the same owned batch");
                Arrays.fill(input, (byte) 81);
                actual(GameVersion.V1_26_50, input);
                equal(expected, batch.payload, "later compression mutated already returned owned batch");
            }
        }
    }

    @Test
    void widthFiveWriterAndSizingMathAgreeWithoutHugePayloadAllocation() {
        int[] lengths = {268_435_455, 268_435_456, Integer.MAX_VALUE};
        byte[][] expected = {
                {(byte) 255, (byte) 255, (byte) 255, 127},
                {(byte) 128, (byte) 128, (byte) 128, (byte) 128, 1},
                {(byte) 255, (byte) 255, (byte) 255, (byte) 255, 7}
        };
        for (int i = 0; i < lengths.length; i++) {
            BinaryStream stream = new BinaryStream(new byte[5]).reset();
            stream.putUnsignedVarInt(lengths[i]);
            equal(expected[i], stream.getBuffer(), "width4/5 writer edge " + lengths[i]);
            int widthFromBits = (32 - Integer.numberOfLeadingZeros(lengths[i]) + 6) / 7;
            check(prefixWidth(lengths[i]) == widthFromBits && stream.getCount() == widthFromBits,
                    "unsigned shift width math disagrees with writer");
        }
    }

}
