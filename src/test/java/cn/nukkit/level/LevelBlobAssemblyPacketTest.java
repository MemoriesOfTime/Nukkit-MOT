package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.XXHash64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Level 装配层线格式不变量：缓存整列包（blobIds = 逐子区块哈希 + 末位 biomes 哈希、
 * data = border 字节 + 方块实体 NBT）与拉取壳（缓存态仅 biomes 单哈希 + border 字节；
 * raw 态 biomes + 末尾 border 字节）。这些契约此前零覆盖，重构极易悄悄破坏。
 * <p>
 * Wire-format invariants of the Level assembly layer: the cached full-column packet
 * (blobIds = per-sub-chunk hashes with the biomes hash last, data = border byte +
 * block entity NBT) and the pull shell (cache mode: the single biomes hash + border
 * byte; raw mode: biomes + trailing border byte). Previously untested — easy to
 * break silently in a refactor.
 */
class LevelBlobAssemblyPacketTest {

    private ClientCacheBlobStore store;
    private Level level;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.init();
        Server server = Mockito.mock(Server.class);
        this.store = new ClientCacheBlobStore(1 << 20);
        Mockito.lenient().when(server.getClientCacheBlobStore()).thenReturn(this.store);
        Mockito.lenient().when(server.getLogger()).thenReturn(Mockito.mock(MainLogger.class));
        Mockito.lenient().when(server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));
        this.level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        setField(this.level, "server", server);
        Mockito.lenient().doReturn(Level.DIMENSION_OVERWORLD).when(this.level).getDimension();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void blobPacketListsEverySubChunkHashWithBiomesLast() {
        List<byte[]> blobs = Arrays.asList(new byte[]{1}, new byte[]{2, 2}, new byte[]{3, 3, 3});
        byte[] biomes = new byte[64];
        Arrays.fill(biomes, (byte) 5);
        byte[] entities = new byte[]{9, 8, 7};

        LevelChunkPacket pk = this.level.buildBlobLevelChunkPacket(null, 3, -7, 5, blobs, biomes, entities);

        assertEquals(3, pk.chunkX);
        assertEquals(-7, pk.chunkZ);
        assertEquals(Level.DIMENSION_OVERWORLD, pk.dimension);
        assertEquals(5, pk.subChunkCount);
        assertTrue(pk.cacheEnabled);
        assertEquals(blobs.size() + 1, pk.blobIds.length, "blobIds = per-sub-chunk hashes + the biomes hash");
        for (int i = 0; i < blobs.size(); i++) {
            assertEquals(XXHash64.hash(blobs.get(i)), pk.blobIds[i], "sub-chunk blob hash at index " + i);
            assertArrayEquals(blobs.get(i), this.store.get(pk.blobIds[i]), "blob content stored under its hash");
        }
        assertEquals(XXHash64.hash(biomes), pk.blobIds[blobs.size()], "the trailing blob id is the biomes hash");
        assertArrayEquals(biomes, this.store.get(pk.blobIds[blobs.size()]));
        assertEquals(1 + entities.length, pk.data.length, "data = border byte + block entity NBT");
        assertEquals(0, pk.data[0]);
        assertArrayEquals(entities, Arrays.copyOfRange(pk.data, 1, pk.data.length));
    }

    @Test
    void blobPacketTreatsNullEntitiesAsEmpty() {
        List<byte[]> blobs = Arrays.asList(new byte[]{1});
        LevelChunkPacket pk = this.level.buildBlobLevelChunkPacket(null, 0, 0, 1, blobs, new byte[8], null);
        assertEquals(1, pk.data.length);
        assertEquals(0, pk.data[0]);
    }

    @Test
    void pullCacheShellCarriesOnlyTheBiomesHash() {
        long biomeHash = XXHash64.hash(new byte[32]);

        LevelChunkPacket pk = this.level.buildPullLevelChunkPacket(null, 1, 2, 24, true, biomeHash, null);

        assertTrue(pk.requestSubChunks);
        assertEquals(0, pk.subChunkCount, "the shell carries no sub-chunk payload");
        assertEquals(24, pk.subChunkLimit);
        assertTrue(pk.cacheEnabled);
        assertEquals(1, pk.blobIds.length, "cache-mode shell references exactly the biomes blob");
        assertEquals(biomeHash, pk.blobIds[0]);
        assertArrayEquals(new byte[]{0}, pk.data, "data = a single border byte");
    }

    @Test
    void pullRawShellAppendsBorderByteAfterBiomes() {
        byte[] biomes = new byte[32];
        Arrays.fill(biomes, (byte) 3);

        LevelChunkPacket pk = this.level.buildPullLevelChunkPacket(null, 1, 2, 24, false, 0, biomes);

        assertTrue(pk.requestSubChunks);
        assertEquals(0, pk.subChunkCount);
        assertEquals(24, pk.subChunkLimit);
        assertFalse(pk.cacheEnabled);
        assertNull(pk.blobIds);
        assertEquals(biomes.length + 1, pk.data.length);
        assertArrayEquals(biomes, Arrays.copyOfRange(pk.data, 0, biomes.length));
        assertEquals(0, pk.data[biomes.length], "border byte trails the biomes");
    }
}
