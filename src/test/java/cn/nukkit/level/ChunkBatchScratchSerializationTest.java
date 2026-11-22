package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.SnappyCompression;
import cn.nukkit.utils.ThreadCache;
import cn.nukkit.utils.Zlib;
import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ChunkBatchScratchSerializationTest {

    private static final DimensionData OVERWORLD = new DimensionData(Level.DIMENSION_OVERWORLD, -64, 319);

    @BeforeAll
    static void initServer() {
        MockServer.init();
        Zlib.setProvider(2);
    }

    @AfterAll
    static void resetServer() {
        MockServer.reset();
    }

    @Test
    void chunkCacheBatchMatchesReferenceWireBytesAcrossProtocolsAndModes() throws Exception {
        byte[] payload = new byte[98_304];
        new Random(42L).nextBytes(payload);

        GameVersion[] versions = {
                GameVersion.V1_12_0,
                GameVersion.V1_16_0,
                GameVersion.V1_20_0,
                GameVersion.V1_21_80,
                GameVersion.V1_26_40,
                GameVersion.V1_26_50,
                GameVersion.V1_26_60
        };

        Server server = Server.getInstance();
        boolean prevSnappy = server.useSnappy;
        int prevLevel = server.networkCompressionLevel;
        server.networkCompressionLevel = 7;
        try {
            for (boolean snappy : new boolean[]{false, true}) {
                server.useSnappy = snappy;
                for (GameVersion gv : versions) {
                    BatchPacket actual = Player.getChunkCacheFromData(gv, -17, 29, 20, payload, Level.DIMENSION_OVERWORLD);
                    byte[] expected = referenceChunkBatch(server, gv, -17, 29, 20, payload, Level.DIMENSION_OVERWORLD);
                    assertArrayEquals(expected, actual.payload,
                            "Wire batch mismatch for " + gv + " (snappy=" + snappy + ")");
                }
            }
        } finally {
            server.useSnappy = prevSnappy;
            server.networkCompressionLevel = prevLevel;
        }
    }

    @Test
    void levelChunkPacketPreSizesInOneAllocationAndPreservesBytes() {
        byte[] payload = new byte[131_072];
        Arrays.fill(payload, (byte) 0x3C);

        LevelChunkPacket packet = new LevelChunkPacket();
        packet.chunkX = 12;
        packet.chunkZ = -34;
        packet.dimension = Level.DIMENSION_NETHER;
        packet.subChunkCount = 8;
        packet.cacheEnabled = true;
        packet.blobIds = new long[]{11L, 22L, 33L, 44L};
        packet.data = payload;
        packet.clientBiomeUpdate = true;
        packet.gameVersion = GameVersion.V1_26_60;
        packet.protocol = GameVersion.V1_26_60.getProtocol();

        packet.encode();
        assertTrue(packet.getBufferUnsafe().length >= packet.getCount());
        assertTrue(packet.getBufferUnsafe().length <= packet.getCount() + 32,
                "Pre-sized buffer should avoid geometric over-allocation: cap=" + packet.getBufferUnsafe().length + " count=" + packet.getCount());
    }

    @Test
    void biomeStreamingDoesNotClobberActiveStreamAndMatches2DAnd3DBiomes() {
        Level level = mock(Level.class);
        doReturn(OVERWORLD).when(level).getDimensionData();

        LevelDBProvider ldbProvider = mock(LevelDBProvider.class);
        doReturn(level).when(ldbProvider).getLevel();
        doReturn(-64).when(ldbProvider).getMinBlockY();
        LevelDBChunk chunk3d = LevelDBChunk.getEmptyChunk(3, -5, ldbProvider);
        assertNotNull(chunk3d);
        chunk3d.setBlock(1, 2, 3, Block.STONE);
        chunk3d.setBiomeId(0, 0, 4);
        chunk3d.setBiomeId(7, 9, 12);

        Anvil anvilProvider = mock(Anvil.class);
        doReturn(level).when(anvilProvider).getLevel();
        doReturn(0).when(anvilProvider).getMinBlockY();
        Chunk chunk2d = Chunk.getEmptyChunk(3, -5, anvilProvider);
        assertNotNull(chunk2d);
        chunk2d.setBlock(2, 4, 6, Block.DIRT);
        chunk2d.setBiomeId(0, 0, 1);
        chunk2d.setBiomeId(5, 5, 6);
        chunk2d.setBiomeId(15, 15, 2);

        for (BaseChunk chunk : new BaseChunk[]{chunk3d, chunk2d}) {
            for (GameVersion gv : new GameVersion[]{GameVersion.V1_18_0, GameVersion.V1_20_0, GameVersion.V1_26_60}) {
                List<byte[]> payloads = new ArrayList<>();
                List<byte[]> biomes = new ArrayList<>();
                for (boolean capture : new boolean[]{true, false}) {
                    ObjectOpenHashSet<GameVersion> set = new ObjectOpenHashSet<>();
                    set.add(gv);
                    NetworkChunkSerializer.serialize(set, chunk, cb -> {
                        payloads.add(cb.getStream().getBuffer());
                        biomes.add(cb.getBiomes());
                    }, false, OVERWORLD, capture);
                }
                assertArrayEquals(payloads.get(0), payloads.get(1), "Full payload must match for " + gv);
                assertNotNull(biomes.get(0));
                assertTrue(biomes.get(0).length > 0);
                assertNull(biomes.get(1), "Uncaptured column should not allocate a separate biome copy");
            }
        }
    }

    @Test
    void cachedBinaryStreamUsesDirectPrimitiveWritesAndStillDetachesOversizedBuffers() throws Exception {
        if (isBinaryStreamInstrumentedByAgent()) {
            runCleanChildProbe();
        } else {
            verifyDirectCachedBinaryStreamWrites();
        }

        ThreadCache.binaryStream.remove();
        BinaryStream cached = ThreadCache.binaryStream.get();
        cached.reserve(2 * 1024 * 1024);
        assertNotSame(cached, ThreadCache.binaryStream.get(), "Oversized reserve must still detach ThreadCache.binaryStream");
        ThreadCache.binaryStream.remove();
    }

    public static void main(String[] args) {
        verifyDirectCachedBinaryStreamWrites();
    }

    private static boolean isBinaryStreamInstrumentedByAgent() {
        BinaryStream probe = new BinaryStream(64);
        for (int i = 0; i < 64; i++) {
            probe.reset();
            probe.putByte((byte) i);
        }
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 64; i++) {
            probe.reset();
            probe.putByte((byte) i);
        }
        return mx.getThreadAllocatedBytes(tid) - before > 256L;
    }

    private static void runCleanChildProbe() throws Exception {
        String javaBin = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process proc = new ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"),
                ChunkBatchScratchSerializationTest.class.getName())
                .redirectErrorStream(true)
                .start();
        String output;
        try (var in = proc.getInputStream()) {
            output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "Child allocation probe timed out");
        assertEquals(0, proc.exitValue(), () -> "Child allocation probe failed:\n" + output);
    }

    private static void verifyDirectCachedBinaryStreamWrites() {
        ThreadCache.binaryStream.remove();
        BinaryStream cached = ThreadCache.binaryStream.get();
        cached.reset();
        cached.reserve(4096);

        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        for (int i = 0; i < 64; i++) {
            cached.reset();
            for (int w = 0; w < 512; w++) {
                cached.putLInt(w);
            }
            cached.putByte((byte) 7);
        }
        long before = mx.getThreadAllocatedBytes(tid);
        for (int r = 0; r < 50; r++) {
            cached.reset();
            for (int w = 0; w < 512; w++) {
                cached.putLInt(w ^ r);
            }
            cached.putByte((byte) r);
        }
        long allocated = mx.getThreadAllocatedBytes(tid) - before;
        // Without directByteWrite on CachedBinaryStream, 50 * (512 * 24 + 24) = 615,600 B of byte[] wrappers were allocated.
        assertTrue(allocated < 1024L,
                "ThreadCache.binaryStream direct writes must not allocate temporary byte[] wrappers, got " + allocated + " B");
        ThreadCache.binaryStream.remove();
    }

    @Test
    void chunkCacheBatchEliminatesStagingBufferChurn() {
        byte[] payload = new byte[120_000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31 + (i >>> 8));
        }

        Server server = Server.getInstance();
        boolean prevSnappy = server.useSnappy;
        int prevLevel = server.networkCompressionLevel;
        server.useSnappy = false;
        server.networkCompressionLevel = 7;
        try {
            // Warm up thread-local packet, scratch stream and deflater
            for (int i = 0; i < 5; i++) {
                Player.getChunkCacheFromData(GameVersion.V1_26_60, i, -i, 16, payload, 0);
            }

            ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            long tid = Thread.currentThread().getId();
            long before = mx.getThreadAllocatedBytes(tid);
            int rounds = 20;
            int compressedSum = 0;
            for (int i = 0; i < rounds; i++) {
                BatchPacket bp = Player.getChunkCacheFromData(GameVersion.V1_26_60, i, -i, 16, payload, 0);
                compressedSum += bp.payload.length;
            }
            long allocatedPerCall = (mx.getThreadAllocatedBytes(tid) - before) / rounds;
            int avgCompressed = compressedSum / rounds;

            // Without leased scratch buffers, each call allocated >360 KB (grown packet + getBuffer + exact batch + compressed).
            // Now only the compressed output array + BatchPacket wrapper (~avgCompressed + <2 KB) is allocated.
            assertTrue(allocatedPerCall < avgCompressed + 8192L,
                    "Expected near-zero staging allocations, got " + allocatedPerCall + " B vs compressed " + avgCompressed + " B");
        } finally {
            server.useSnappy = prevSnappy;
            server.networkCompressionLevel = prevLevel;
        }
    }

    private static byte[] referenceChunkBatch(Server server, GameVersion gameVersion, int chunkX, int chunkZ,
                                              int subChunkCount, byte[] payload, int dimension) throws Exception {
        int protocol = gameVersion.getProtocol();
        LevelChunkPacket pk = new LevelChunkPacket();
        pk.chunkX = chunkX;
        pk.chunkZ = chunkZ;
        pk.dimension = dimension;
        pk.subChunkCount = subChunkCount;
        pk.data = payload;
        pk.protocol = protocol;
        pk.gameVersion = gameVersion;
        pk.tryEncode();

        byte[] buf = pk.getBuffer();
        BinaryStream batched = ThreadCache.acquireBinaryStream();
        try {
            batched.reset();
            batched.putUnsignedVarInt(buf.length);
            batched.put(buf);
            byte[] bytes = batched.getBuffer();
            if (server.useSnappy && protocol >= ProtocolInfo.v1_19_30_23) {
                return SnappyCompression.compress(bytes);
            } else if (protocol >= ProtocolInfo.v1_16_0) {
                return Zlib.deflateRaw(bytes, server.networkCompressionLevel);
            } else {
                return Zlib.deflatePre16Packet(bytes, server.networkCompressionLevel);
            }
        } finally {
            ThreadCache.releaseBinaryStream(batched);
        }
    }
}
