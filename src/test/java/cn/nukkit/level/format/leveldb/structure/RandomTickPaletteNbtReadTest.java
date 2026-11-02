package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockCrops;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RandomTickPaletteNbtReadTest {
    @BeforeAll static void initialize() { Block.init(); }
    static Stream<Arguments> inertCases() {
        return Stream.of(-4, 19).flatMap(y -> Stream.of(Block.STONE, Block.AIR).map(id -> Arguments.of(y, id)));
    }

    @ParameterizedTest @MethodSource("inertCases")
    void sequentialInPlaceNbtReloadClearsPreviousPositive(int y, int id) {
        StateBlockStorage data = singleton(Block.GRASS, 0);
        LevelDBChunkSection section = section(y, data);
        assertTrue(section.mayHaveRandomTickBlocks());
        int before = data.getVersion();
        byte[] encoded = encode(singleton(id, 0));
        ByteBuf input = Unpooled.wrappedBuffer(encoded);
        try {
            section.writeLock.lock();
            try { data.readFromStorage(input, mock(ChunkBuilder.class)); }
            finally { section.writeLock.unlock(); }
            assertSame(data, section.getStorages()[0]);
            assertNotEquals(before, data.getVersion());
            assertEquals(0, input.readableBytes());
            assertEquals(id << Block.DATA_BITS, data.get(0));
            assertArrayEquals(encoded, encode(data));
            assertFalse(section.mayHaveRandomTickBlocks());
        } finally { input.release(); }
    }

    @ParameterizedTest @MethodSource("inertCases")
    void probeDuringNbtDecodeCannotRetainTheOldPositiveAfterCompletion(int y, int id) {
        StateBlockStorage data = singleton(Block.GRASS, 0);
        LevelDBChunkSection section = section(y, data);
        assertTrue(section.mayHaveRandomTickBlocks());
        byte[] encoded = encode(singleton(id, 0));
        ByteBuf original = Unpooled.wrappedBuffer(encoded);
        ByteBuf input = spy(original);
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicInteger observedVersion = new AtomicInteger(-1);
        doAnswer(invocation -> {
            if (first.compareAndSet(true, false)) {
                observedVersion.set(data.getVersion());
                assertTrue(section.mayHaveRandomTickBlocks(), "the old grass is still present before palette.clear");
            }
            return invocation.callRealMethod();
        }).when(input).readUnsignedByte();
        try {
            data.readFromStorage(input, mock(ChunkBuilder.class));
            assertSame(data, section.getStorages()[0]);
            assertFalse(first.get());
            assertEquals(0, input.readableBytes());
            assertEquals(id << Block.DATA_BITS, data.get(0));
            assertFalse(data.mayHaveRandomTickBlocks(), "decoded palette is actually inert");
            assertArrayEquals(encoded, encode(data), "storage bytes are unchanged");
            boolean eligible = section.mayHaveRandomTickBlocks();
            AtomicInteger draws = new AtomicInteger();
            if (eligible) section.sampleRandomTickBlocks(3, new Random() {
                @Override public int nextInt() { draws.incrementAndGet(); return 0; }
            });
            System.out.printf("NBT end: y=%d id=%d eligible=%s draws=%d observedVersion=%d finalVersion=%d%n",
                    y, id, eligible, draws.get(), observedVersion.get(), data.getVersion());
            assertFalse(eligible, "a probe during decode must not permanently keep the old grass memo");
            assertEquals(0, draws.get());
            assertNotEquals(observedVersion.get(), data.getVersion());
        } finally { input.release(); if (original.refCnt() > 0) original.release(); }
    }

    @ParameterizedTest @ValueSource(ints = {-4, 19})
    void nbtReloadFromInertToCropKeepsNativeGrowth(int y) {
        StateBlockStorage data = singleton(Block.GRASS, 0);
        LevelDBChunkSection section = section(y, data);
        assertTrue(section.mayHaveRandomTickBlocks());
        read(data, encode(singleton(Block.STONE, 0)));
        assertFalse(section.mayHaveRandomTickBlocks());
        read(data, encode(singleton(Block.WHEAT_BLOCK, 6)));
        assertTrue(section.mayHaveRandomTickBlocks());
        assertEquals((Block.WHEAT_BLOCK << Block.DATA_BITS) | 6, data.get(0));

        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        Level level = mock(Level.class);
        List<BlockGrowEvent> events = new ArrayList<>();
        doAnswer(invocation -> { events.add((BlockGrowEvent) invocation.getArgument(0)); return null; })
                .when(plugins).callEvent(any(Event.class));
        doAnswer(invocation -> {
            Vector3 pos = invocation.getArgument(0);
            Block state = invocation.getArgument(1);
            assertEquals(-32, pos.getFloorX()); assertEquals(y << 4, pos.getFloorY()); assertEquals(-48, pos.getFloorZ());
            assertFalse((boolean) invocation.getArgument(2)); assertTrue((boolean) invocation.getArgument(3));
            data.set(0, BlockStateMapping.get().getState(state.getId(), state.getDamage()));
            return true;
        }).when(level).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
        try (MockedStatic<Server> servers = mockStatic(Server.class)) {
            servers.when(Server::getInstance).thenReturn(server);
            for (int attempts = 0; attempts < 128 && events.isEmpty(); attempts++) {
                Block crop = Block.get(data.get(0), level, -32, y << 4, -48);
                assertEquals(6, ((BlockCrops) crop).getIntValue("growth"));
                int result = crop.onUpdate(Level.BLOCK_UPDATE_RANDOM);
                assertEquals(events.isEmpty() ? Level.BLOCK_UPDATE_RANDOM : 0, result);
            }
        }
        assertEquals(1, events.size(), "bounded attempts must reach the native grow coin branch");
        assertEquals(7, ((BlockCrops) events.get(0).getNewState()).getIntValue("growth"));
        assertEquals((Block.WHEAT_BLOCK << Block.DATA_BITS) | 7, data.get(0));
    }

    @ParameterizedTest @ValueSource(ints = {-4, 19})
    void freshCanonicalLargeInertNbtRetainsConservativeFallback(int y) {
        List<BlockStateSnapshot> palette = new ArrayList<>();
        for (int i = 0; i < 17; i++) palette.add(BlockStateMapping.get().getState(Block.STONE, 0));
        StateBlockStorage source = new StateBlockStorage(BitArrayVersion.V5.createPalette(4096), palette, null, null);
        byte[] encoded = encode(source);
        StateBlockStorage decoded = new StateBlockStorage();
        read(decoded, encoded);
        assertEquals(Block.STONE << Block.DATA_BITS, decoded.get(0));
        assertArrayEquals(encoded, encode(decoded));
        assertTrue(section(y, decoded).mayHaveRandomTickBlocks(), "NBT load alone does not compact or certify large random-inert data");
    }

    private static StateBlockStorage singleton(int id, int meta) {
        return new StateBlockStorage(BitArrayVersion.V0.createPalette(4096),
                new ArrayList<>(List.of(BlockStateMapping.get().getState(id, meta))), null, null);
    }
    private static LevelDBChunkSection section(int y, StateBlockStorage data) {
        return new LevelDBChunkSection(y, new StateBlockStorage[]{data}, false);
    }
    private static byte[] encode(StateBlockStorage data) {
        ByteBuf output = Unpooled.buffer();
        try { data.writeToStorage(output); byte[] bytes = new byte[output.readableBytes()]; output.getBytes(0, bytes); return bytes; }
        finally { output.release(); }
    }
    private static void read(StateBlockStorage data, byte[] encoded) {
        ByteBuf input = Unpooled.wrappedBuffer(encoded);
        try { data.readFromStorage(input, mock(ChunkBuilder.class)); assertEquals(0, input.readableBytes()); }
        finally { input.release(); }
    }
}
