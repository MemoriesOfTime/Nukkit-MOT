package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockFenceCherry;
import cn.nukkit.block.BlockFencePaleOak;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Fence connection states must survive registry materialization and native storage writes. */
class FenceStateRetentionTest {
    @BeforeAll
    static void initializeRegistry() {
        MockServer.init(); // Real Block.init, block palettes, runtime items and item registry.
        assertEquals(BlockFence.class, Block.list[85]);
        assertEquals(BlockFenceCherry.class, Block.list[787]);
        assertEquals(BlockFencePaleOak.class, Block.list[1246]);
    }

    static Stream<Arguments> fences() {
        // 85 is the ordinary oak control. Mask zero is a positive control for each species.
        return IntStream.of(85, 787, 1246).boxed().flatMap(id -> IntStream.range(0, 16)
                .mapToObj(mask -> Arguments.of(id, mask)));
    }

    @ParameterizedTest(name = "id={0}, mask={1}")
    @MethodSource("fences")
    void registryMaterializationRetainsAllConnectionMasks(int id, int mask) {
        int meta = mask << 4;
        Block block = Block.get(id, meta);
        assertFence(block, id, mask);
        Block independent = Block.get(id, meta);
        assertNotSame(block, independent);
        block.setDamage(meta ^ 0x10);
        assertFence(independent, id, mask);
        assertFence(Block.get(id, meta), id, mask);
        BlockFence fence = assertInstanceOf(BlockFence.class, independent);
        assertFalse(fence.canConnect(Block.get(Block.AIR, 0)));
        assertTrue(fence.canConnect(Block.get(Block.STONE, 0)));
    }

    @ParameterizedTest(name = "id={0}, mask={1}")
    @MethodSource("fences")
    void loadedSectionReadAndNativeWriteRoundTripRetainAllConnectionMasks(int id, int mask) {
        int expected = fullId(id, mask);
        StateBlockStorage seed = new StateBlockStorage();
        // Native mapping seeds legal raw state without first traversing Block.get (the defect).
        seed.set(1, 1, 1, expected);
        seed.set(1, 1, 2, Block.STONE << Block.DATA_BITS);
        StateBlockStorage storage = roundTrip(seed);
        assertEquals(expected, storage.get(1, 1, 1), "storage-only control before materialization");
        assertEquals(id, storage.getBlockState(1, 1, 1).getLegacyId());
        assertEquals(mask << 4, storage.getBlockState(1, 1, 1).getLegacyData());
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{storage, new StateBlockStorage()}, false);
        LevelDBChunk chunk = new ChunkBuilder(0, 0, mock(LevelDBProvider.class))
                .sections(new ChunkSection[]{section}).build();
        assertSame(section, chunk.getSection(0));
        Level level = loadedReader(chunk);
        int version = storage.getVersion();
        long changes = chunk.getChanges();
        Block read = read(level, 1, 1);
        assertEquals(version, storage.getVersion(), "read must not mutate source storage");
        assertEquals(changes, chunk.getChanges(), "read must not dirty loaded chunk");
        assertEquals(Block.AIR, read(level, 0, 1).getId());
        assertEquals(Block.STONE, read(level, 1, 2).getId());
        verify(level, never()).getChunk(anyInt(), anyInt());
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());

        // Explicit write to a different cell; reading alone is not durable corruption.
        Block previous = chunk.getAndSetBlock(2, 1, 1, 0, read);
        assertEquals(Block.AIR, previous.getId());
        StateBlockStorage reopened = roundTrip(storage);
        assertAll("loaded materialization and native chunk write/encode/decode",
                () -> assertFence(read, id, mask),
                () -> assertEquals(expected, reopened.get(2, 1, 1), "destination retains mask"),
                () -> assertEquals(expected, reopened.get(1, 1, 1), "source remains unchanged"),
                () -> assertEquals(Block.AIR, reopened.get(0, 1, 1)),
                () -> assertEquals(Block.STONE << Block.DATA_BITS, reopened.get(1, 1, 2)));
    }

    @ParameterizedTest(name = "id={0}, mask={1}")
    @MethodSource("fences")
    void stableNormalUpdatesDoNotPublishWrites(int id, int mask) {
        Level level = mock(Level.class);
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        for (int bit = 0; bit < faces.length; bit++) {
            BlockFace face = faces[bit];
            when(level.getBlock(1 + face.getXOffset(), 1, 1 + face.getZOffset(), 0))
                    .thenReturn(Block.get((mask & (1 << bit)) != 0 ? Block.STONE : Block.AIR, 0));
        }
        // Four bounded reload/update passes; no scheduler or elapsed-time assertion.
        for (int pass = 0; pass < 4; pass++) {
            BlockFence fence = assertInstanceOf(BlockFence.class, Block.get(id, mask << 4));
            fence.level = level;
            fence.x = 1;
            fence.y = 1;
            fence.z = 1;
            assertEquals(Level.BLOCK_UPDATE_NORMAL, fence.onUpdate(Level.BLOCK_UPDATE_NORMAL));
            assertFence(fence, id, mask);
        }
        verify(level, times(16)).getBlock(anyInt(), anyInt(), anyInt(), anyInt());
        verify(level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean());
    }

    private static int fullId(int id, int mask) {
        return (id << Block.DATA_BITS) | (mask << 4);
    }

    private static void assertFence(Block block, int id, int mask) {
        BlockFence fence = assertInstanceOf(BlockFence.class, block);
        // Expected bits are fixed by the four connection states, not read back from the block.
        assertAll("id=" + id + ", mask=" + mask,
                () -> assertEquals(id, block.getId()),
                () -> assertEquals(mask << 4, block.getDamage()),
                () -> assertEquals(fullId(id, mask), block.getFullId()),
                () -> assertEquals((mask & 1) != 0, fence.isConnectedTo(BlockFace.NORTH)),
                () -> assertEquals((mask & 2) != 0, fence.isConnectedTo(BlockFace.EAST)),
                () -> assertEquals((mask & 4) != 0, fence.isConnectedTo(BlockFace.SOUTH)),
                () -> assertEquals((mask & 8) != 0, fence.isConnectedTo(BlockFace.WEST)));
    }

    private static StateBlockStorage roundTrip(StateBlockStorage source) {
        ByteBuf bytes = Unpooled.buffer();
        try {
            source.writeToStorage(bytes);
            StateBlockStorage decoded = new StateBlockStorage();
            decoded.readFromStorage(bytes, new ChunkBuilder(0, 0, mock(LevelDBProvider.class)));
            assertEquals(0, bytes.readableBytes(), "native decoder must consume complete storage");
            return decoded;
        } finally {
            bytes.release();
        }
    }

    private static Level loadedReader(LevelDBChunk chunk) {
        // Only unrelated server/provider plumbing and the loaded chunk lookup are mocked.
        // Registry, materialization, section, mapping and NBT read/write remain real.
        Level level = mock(Level.class);
        when(level.getMinBlockY()).thenReturn(0);
        when(level.getMaxBlockY()).thenReturn(255);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        doCallRealMethod().when(level).isYInRange(anyInt());
        doCallRealMethod().when(level).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(),
                anyInt(), anyBoolean());
        return level;
    }

    private static Block read(Level level, int x, int z) {
        return level.getBlock((FullChunk) null, x, 1, z, 0, false);
    }
}
