package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.format.anvil.util.NibbleArray;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import com.sun.management.ThreadMXBean;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkSendingCowTest {
    private static final DimensionData DIMENSION = new DimensionData(Level.DIMENSION_OVERWORLD, -64, 319);
    private static volatile Object allocationSink;

    @BeforeAll
    static void init() { MockServer.init(); }

    @AfterAll
    static void cleanup() { MockServer.reset(); }

    @ParameterizedTest
    @EnumSource(BitArrayVersion.class)
    void stateWritersKeepSnapshotsAndOldLogicEquivalent(BitArrayVersion width) {
        List<Consumer<StateBlockStorage>> writers = List.of(
                storage -> storage.set(0, state(Block.DIAMOND_ORE)),
                storage -> storage.set(0, 0, 0, state(Block.DIAMOND_ORE)),
                storage -> storage.set(0, 0, 0, Block.DIAMOND_ORE << Block.DATA_BITS),
                storage -> storage.set(new BlockVector3(), Block.DIAMOND_ORE << Block.DATA_BITS),
                storage -> {
                    for (int i = 0; i < 16; i++) storage.set(i, BlockStateMapping.get().getState(Block.WOOL, i));
                },
                storage -> read(storage, disk(new StateBlockStorage())),
                storage -> assertTrue(storage.compress())
        );
        for (Consumer<StateBlockStorage> writer : writers) {
            var live = populated(width);
            var eager = eager(live);
            var first = live.copy();
            var second = first.copy();
            byte[] before = disk(first);
            writer.accept(live);
            writer.accept(eager);
            assertEquivalent(eager, live);
            assertArrayEquals(before, disk(first));
            assertArrayEquals(before, disk(second));
            assertFalse(Arrays.equals(before, disk(live)));
            var third = live.copy();
            byte[] after = disk(third);
            first.set(2048, state(Block.GOLD_ORE));
            assertEquals(Block.GOLD_ORE << Block.DATA_BITS, first.get(2048));
            assertArrayEquals(before, disk(second));
            assertArrayEquals(after, disk(live));
            assertArrayEquals(after, disk(third));
        }
    }

    @Test
    void firstWriteDetachesOnceAndLegacyAndExternalArraysRemainPrivate() {
        var live = populated(BitArrayVersion.V3);
        live.getBlockIds();
        byte[] legacy = live.getBlockData();
        var first = live.copy();
        assertSame(field(live, "bitArray"), field(first, "bitArray"));
        assertSame(field(live, "palette"), field(first, "palette"));
        byte[] before = disk(first), oldLegacy = first.getBlockData().clone();
        legacy[0] ^= 15;
        assertArrayEquals(oldLegacy, first.getBlockData());
        live.set(0, state(Block.DIRT));
        Object detached = field(live, "bitArray"), palette = field(live, "palette");
        assertNotSame(detached, field(first, "bitArray"));
        assertNotSame(palette, field(first, "palette"));
        live.set(1, state(Block.DIRT));
        assertSame(detached, field(live, "bitArray"));
        assertSame(palette, field(live, "palette"));
        var second = live.copy();
        live.set(2, state(Block.DIRT));
        assertNotSame(detached, field(live, "bitArray"));
        assertSame(detached, field(second, "bitArray"));
        assertArrayEquals(before, disk(first));

        BitArray bits = BitArrayVersion.V2.createPalette();
        var states = new ObjectArrayList<>(List.of(state(Block.AIR), state(Block.STONE)));
        var external = new StateBlockStorage(bits, states, null, null);
        var snapshot = external.copy();
        bits.getWords()[0] = 1;
        states.set(0, state(Block.DIRT));
        assertEquals(Block.STONE << Block.DATA_BITS, external.get(0));
        assertEquals(Block.DIRT << Block.DATA_BITS, external.get(1));
        assertEquals(0, snapshot.get(0));
        assertEquals(0, snapshot.get(1));
    }

    @Test
    void singletonAirCompactionAndFailedWritesPreserveSnapshots() {
        var live = new StateBlockStorage();
        var singleton = live.copy();
        live.set(0, state(Block.STONE));
        assertEquals(0, singleton.get(0));
        assertEquals(Block.STONE << Block.DATA_BITS, live.get(0));
        live.set(0, state(Block.AIR));
        var air = live.copy();
        byte[] before = disk(air);
        assertTrue(live.compress());
        assertArrayEquals(before, disk(air));
        assertNotSame(field(live, "bitArray"), field(air, "bitArray"));
        var allAir = new StateBlockStorage();
        allAir.set(0, state(Block.AIR).toBuilder().legacyData(1).build());
        var airSnapshot = allAir.copy();
        byte[] airBefore = disk(airSnapshot);
        assertTrue(allAir.compress());
        assertArrayEquals(airBefore, disk(airSnapshot));
        assertFalse(Arrays.equals(airBefore, disk(allAir)));
        for (byte[] malformed : List.of(new byte[0], new byte[]{15}, new byte[]{4, 0})) {
            var source = populated(BitArrayVersion.V2);
            var copy = source.copy();
            byte[] original = disk(source);
            assertThrows(RuntimeException.class, () -> read(copy, malformed));
            assertArrayEquals(original, disk(source));
        }
        var copy = air.copy();
        assertThrows(IndexOutOfBoundsException.class, () -> copy.set(-1, state(Block.DIRT)));
        assertArrayEquals(before, disk(air));
    }

    @Test
    void sectionSettersLightAndBulkBiomeWritersKeepChunkSnapshotStable() throws Exception {
        List<Consumer<LevelDBChunkSection>> writers = List.of(
                section -> section.setBlockId(0, 0, 0, Block.DIRT),
                section -> section.setBlockId(0, 0, 0, 1, Block.DIRT),
                section -> section.setBlockData(0, 0, 0, 1),
                section -> section.setBlockData(0, 0, 0, 1, 1),
                section -> section.setFullBlockId(0, 0, 0, Block.DIRT << Block.DATA_BITS),
                section -> section.setFullBlockId(0, 0, 0, 1, Block.DIRT << Block.DATA_BITS),
                section -> section.setBlock(0, 0, 0, Block.DIRT),
                section -> section.setBlock(0, 0, 0, Block.DIRT, 0),
                section -> section.setBlock(0, 0, 0, 1, Block.DIRT, 0),
                section -> section.setBlockAtLayer(0, 0, 0, 1, Block.DIRT),
                section -> section.setBlockAtLayer(0, 0, 0, 1, Block.DIRT, 0),
                section -> section.getAndSetBlock(0, 0, 0, Block.get(Block.DIRT)),
                section -> section.getAndSetBlock(0, 0, 0, 1, Block.get(Block.DIRT)),
                section -> section.getStorages()[0].set(0, state(Block.DIRT))
        );
        for (Consumer<LevelDBChunkSection> writer : writers) {
            var chunk = chunk(false);
            var live = (LevelDBChunkSection) chunk.getSection(-4);
            var snapshot = (LevelDBChunkSection) chunk.cloneForChunkSending().getSection(-4);
            byte[] before = sectionBytes(snapshot);
            writer.accept(live);
            assertArrayEquals(before, sectionBytes(snapshot));
            assertFalse(Arrays.equals(before, sectionBytes(live)));
        }
        var chunk = chunk(false);
        var live = (LevelDBChunkSection) chunk.getSection(-4);
        live.setBlockLight(0, 0, 0, 2);
        live.setBlockLight(1, 0, 0, 4);
        live.setBlockSkyLight(0, 0, 0, 3);
        live.setBlockSkyLight(1, 0, 0, 5);
        var copy = chunk.cloneForChunkSending();
        var snapshot = (LevelDBChunkSection) copy.getSection(-4);
        live.setBlockLight(0, 0, 0, 9);
        live.setBlockLight(1, 0, 0, 11);
        live.setBlockSkyLight(0, 0, 0, 10);
        live.setBlockSkyLight(1, 0, 0, 12);
        live.getLightArray()[1] = 42;
        assertEquals(2, snapshot.getBlockLight(0, 0, 0));
        assertEquals(3, snapshot.getBlockSkyLight(0, 0, 0));
        assertEquals(4, snapshot.getBlockLight(1, 0, 0));
        assertEquals(5, snapshot.getBlockSkyLight(1, 0, 0));
        assertNotEquals(42, snapshot.getLightArray()[1]);
        assertEquals(9, live.getBlockLight(0, 0, 0));
        assertEquals(10, live.getBlockSkyLight(0, 0, 0));
        byte[] biomes = biomeBytes(copy);
        chunk.setBiomeId(0, -64, 0, 71);
        chunk.setBiomeId(1, 1, 72);
        chunk.setBiomeIdArray(new byte[256]);
        chunk.getBiomeIdArray()[0] = 73;
        assertArrayEquals(biomes, biomeBytes(copy));
        assertNotEquals(chunk.getBiomeIdArray()[0], copy.getBiomeIdArray()[0]);
        assertFalse(Arrays.equals(biomes, biomeBytes(chunk)));

    }

    @Test
    void compressedLightSnapshotsKeepAliasesInflationAndRecompressionPrivate() throws Exception {
        List<Consumer<LevelDBChunkSection>> writers = List.of(
                section -> section.compressedLight[0] ^= 1,
                section -> section.getLightArray(),
                section -> section.getSkyLightArray(),
                section -> section.setBlockLight(0, 0, 0, 12),
                section -> section.setBlockSkyLight(0, 0, 0, 13),
                section -> {
                    section.setBlockLight(0, 0, 0, 12);
                    assertTrue(section.compress());
                    assertNotNull(section.compressedLight);
                }
        );
        var executor = Executors.newSingleThreadExecutor();
        try {
            for (int length : new int[]{0, 2048, 4096}) {
                byte[] raw = new byte[length];
                if (length != 0) Arrays.fill(raw, 0, 2048, (byte) 0x21);
                if (length == 4096) Arrays.fill(raw, 2048, 4096, (byte) 0x43);
                byte[] compressed = length == 0 ? new byte[0] : Zlib.deflate(raw, 1);
                byte[] blocks = Arrays.copyOf(raw, 2048), sky = new byte[2048];
                Arrays.fill(sky, (byte) (length == 4096 ? 0x43 : 0xFF));
                for (int operation = 0; operation < writers.size(); operation++) {
                    if (length == 0 && operation == 0) continue;
                    var chunk = chunk(false);
                    byte[] external = compressed.clone();
                    var live = new LevelDBChunkSection(chunk, -4,
                            new StateBlockStorage[]{populated(BitArrayVersion.V3)}, null, null, external, true, true);
                    chunk.getSections()[0] = live;
                    var firstChunk = chunk.cloneForChunkSending();
                    var first = (LevelDBChunkSection) firstChunk.getSections()[0];
                    var second = (LevelDBChunkSection) firstChunk.cloneForChunkSending().getSections()[0];
                    assertSame(external, live.compressedLight);
                    assertNotSame(external, first.compressedLight);
                    assertNotSame(first.compressedLight, second.compressedLight);
                    writers.get(operation).accept(live);
                    if (operation == 0) assertFalse(Arrays.equals(external, compressed));
                    if (operation == 3 || operation == 5) assertEquals(12, live.getBlockLight(0, 0, 0));
                    if (operation == 4) assertEquals(13, live.getBlockSkyLight(0, 0, 0));
                    if (operation == 1 || operation == 2) {
                        assertNotNull(live.blockLight);
                        assertNotNull(live.skyLight);
                    }
                    assertArrayEquals(compressed, first.compressedLight);
                    assertArrayEquals(compressed, second.compressedLight);
                    executor.submit(() -> {
                        assertNull(first.blockLight);
                        assertNull(first.skyLight);
                        assertArrayEquals(blocks, first.getLightArray());
                        assertArrayEquals(sky, second.getSkyLightArray());
                        assertArrayEquals(sky, first.getSkyLightArray());
                        assertArrayEquals(blocks, second.getLightArray());
                        assertNotSame(first.blockLight, second.blockLight);
                        assertNotSame(first.skyLight, second.skyLight);
                        first.setBlockLight(0, 0, 0, 14);
                        assertEquals(blocks[0] & 0x0F, second.getBlockLight(0, 0, 0));
                    }).get(10, TimeUnit.SECONDS);
                }
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void allBiomeSettersAndRetainedBulkArraysKeepSnapshotStable() {
        List<Consumer<LevelDBChunk>> writers = List.of(
                chunk -> chunk.setBiomeId(0, -64, 0, 2),
                chunk -> chunk.setBiomeId(0, -64, 0, (byte) 2),
                chunk -> chunk.setBiomeId(0, 0, 2),
                chunk -> chunk.setBiomeId(0, 0, (byte) 2),
                chunk -> chunk.setBiome(0, -64, 0, Biome.getBiome(2)),
                chunk -> chunk.setBiome(0, 0, Biome.getBiome(2)),
                chunk -> chunk.setBiomeIdArray(new byte[256]),
                chunk -> chunk.setBiomes3d(-4, PalettedBlockStorage.createWithDefaultState(2)),
                chunk -> {
                    var replacement = chunk.biomes3d.clone();
                    replacement[0] = PalettedBlockStorage.createWithDefaultState(2);
                    chunk.setBiomes3d(replacement);
                },
                chunk -> chunk.biomes3d[0] = PalettedBlockStorage.createWithDefaultState(2)
        );
        for (Consumer<LevelDBChunk> writer : writers) {
            var live = chunk(false);
            var snapshot = live.cloneForChunkSending();
            byte[] before = biomeBytes(snapshot);
            writer.accept(live);
            assertArrayEquals(before, biomeBytes(snapshot));
            assertFalse(Arrays.equals(before, biomeBytes(live)));
        }
        var legacy = chunk(false);
        legacy.setBiomes3d((PalettedBlockStorage[]) null);
        byte[] retained = new byte[256];
        retained[0] = 1;
        legacy.setBiomeIdArray(retained);
        assertFalse(legacy.has3dBiomes());
        var snapshot = legacy.cloneForChunkSending();
        retained[0] = 2;
        assertEquals(1, snapshot.getBiomeId(0, 0));
        assertEquals(2, legacy.getBiomeId(0, 0));
        legacy.getBiomeIdArray()[1] = 3;
        assertEquals(1, snapshot.getBiomeId(0, 0));
        assertEquals(0, snapshot.getBiomeId(0, 1));
        assertEquals(3, legacy.getBiomeId(0, 1));
    }

    @Test
    void workerSerializationMatchesEagerSnapshotForEverySupportedVersion() throws Exception {
        boolean previous = GlobalBlockPalette.useHashedBlockNetworkIds();
        var executor = Executors.newSingleThreadExecutor();
        try {
            for (boolean hashed : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                var live = chunk(false);
                var eager = eagerChunk(live);
                var snapshot = live.cloneForChunkSending();
                var start = new CountDownLatch(1);
                var worker = executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (GameVersion version : GameVersion.values()) {
                        if (version.isNetEase() || version.getProtocol() < GameVersion.V1_20_0.getProtocol()
                                || version.getProtocol() > GameVersion.V1_26_50.getProtocol()) continue;
                        for (boolean antiXray : new boolean[]{false, true}) {
                            assertArrayEquals(serialize(eager, version, antiXray), serialize(snapshot, version, antiXray),
                                    version + " hash=" + hashed + " antiXray=" + antiXray);
                        }
                    }
                    return null;
                });
                start.countDown();
                for (int i = 0; i < 4096; i++) {
                    ((LevelDBChunkSection) live.getSection(-4)).getStorages()[0].set(i, state(Block.DIRT));
                    live.getBiomeStorage(-4).setBlock(i, 71);
                }
                worker.get(60, TimeUnit.SECONDS);
                assertFalse(Arrays.equals(serialize(snapshot, GameVersion.V1_26_50, false),
                        serialize(live, GameVersion.V1_26_50, false)));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            GlobalBlockPalette.setUseHashedBlockNetworkIds(previous);
        }
    }

    @Test
    void workerCapturedSnapshotsMatchEagerCopiesIncludingNetEase() throws Exception {
        boolean previous = GlobalBlockPalette.useHashedBlockNetworkIds();
        var executor = Executors.newSingleThreadExecutor();
        try {
            for (boolean hashed : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                var live = chunk(false);
                var eager = eagerChunk(live);
                var snapshot = live.cloneForChunkSending();
                var section = (LevelDBChunkSection) live.getSection(-4);
                section.getStorages()[0].set(0, state(Block.DIRT));
                section.getStorages()[1].set(0, state(Block.GOLD_ORE));
                for (var biome : live.biomes3d) biome.setBlock(0, 71);
                var worker = executor.submit(() -> {
                    for (GameVersion version : GameVersion.values()) {
                        if (version.getProtocol() < GameVersion.V1_20_0.getProtocol()
                                || version.getProtocol() > GameVersion.V1_26_50.getProtocol()) continue;
                        for (boolean antiXray : new boolean[]{false, true}) {
                            var plain = column(eager, version, antiXray, false);
                            for (boolean capture : new boolean[]{false, true}) {
                                var expected = column(eager, version, antiXray, capture);
                                var actual = column(snapshot, version, antiXray, capture);
                                assertEquals(expected.count, actual.count, version.toString());
                                assertArrayEquals(expected.payload, actual.payload, version.toString());
                                assertArrayEquals(expected.biomes, actual.biomes, version.toString());
                                assertArrayEquals(expected.entities, actual.entities, version.toString());
                                assertArrayEquals(plain.payload, actual.payload, "capture preserves full column");
                                if (capture) {
                                    assertEquals(expected.count, actual.blobs.size());
                                    assertEquals(expected.blobs.size(), actual.blobs.size());
                                    for (int i = 0; i < expected.blobs.size(); i++) {
                                        assertArrayEquals(expected.blobs.get(i), actual.blobs.get(i), version.toString());
                                    }
                                } else {
                                    assertNull(actual.blobs);
                                }
                            }
                        }
                    }
                    return null;
                });
                worker.get(60, TimeUnit.SECONDS);
                assertFalse(Arrays.equals(serialize(eager, GameVersion.V1_26_50, false),
                        serialize(live, GameVersion.V1_26_50, false)));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            GlobalBlockPalette.setUseHashedBlockNetworkIds(previous);
        }
    }

    @Test
    void chunkSnapshotsAvoidBackingArrayAllocation() {
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported(), "Allocation counter required for COW proof");
        bean.setThreadAllocatedMemoryEnabled(true);
        var live = chunk(true);
        assertEquals(24, live.getSections().length);
        // Dimension and all 24 sections are already captured. Exclude Mockito's per-call
        // provider/level invocation records from the measured production copy path.
        live.setProvider(null);
        for (int i = 0; i < 300; i++) allocationSink = live.cloneForChunkSending();
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 100; i++) allocationSink = live.cloneForChunkSending();
        long actual = (bean.getCurrentThreadAllocatedBytes() - before) / 100;
        System.out.println("ChunkSendingCowTest clone bytes: " + actual);
        // Covers wrappers, locks and the legacy 256-byte biome copy, but no section cell arrays.
        assertTrue(actual < 32 * 1024, "Chunk snapshot allocated " + actual + " bytes");
    }

    @Test
    void firstWriteDetachesOnceAndLaterWritesAvoidBackingCopies() {
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var blocks = populated(BitArrayVersion.V8);
        var biomes = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V8, 1);
        for (int i = 0; i < 4096; i++) biomes.setBlock(i, i % 64 + 1);
        var stone = state(Block.STONE);
        var dirt = state(Block.DIRT);
        for (int i = 0; i < 500; i++) {
            allocationSink = blocks.copy();
            allocationSink = biomes.copy();
            blocks.set(0, (i & 1) == 0 ? stone : dirt);
            biomes.setBlock(0, (i & 1) + 1);
        }
        bean.getCurrentThreadAllocatedBytes();
        long firstWriteBytes = 0;
        for (int i = 0; i < 100; i++) {
            int previousBlock = blocks.get(0), previousBiome = biomes.getBlock(0);
            var blockSnapshot = blocks.copy();
            var biomeSnapshot = biomes.copy();
            long before = bean.getCurrentThreadAllocatedBytes();
            blocks.set(0, (i & 1) == 0 ? stone : dirt);
            biomes.setBlock(0, (i & 1) + 1);
            firstWriteBytes += bean.getCurrentThreadAllocatedBytes() - before;
            assertEquals(previousBlock, blockSnapshot.get(0));
            assertEquals(previousBiome, biomeSnapshot.getBlock(0));
        }
        var controlBlocks = eager(blocks);
        var controlBiomes = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V8, 1);
        for (int i = 0; i < 4096; i++) controlBiomes.setBlock(i, i % 64 + 1);
        writePairs(blocks, biomes, stone, dirt, 10000);
        writePairs(controlBlocks, controlBiomes, stone, dirt, 10000);
        long before = bean.getCurrentThreadAllocatedBytes();
        writePairs(controlBlocks, controlBiomes, stone, dirt, 1000);
        long controlWriteBytes = bean.getCurrentThreadAllocatedBytes() - before;
        before = bean.getCurrentThreadAllocatedBytes();
        writePairs(blocks, biomes, stone, dirt, 1000);
        long repeatedWriteBytes = bean.getCurrentThreadAllocatedBytes() - before;
        long firstPair = firstWriteBytes / 100;
        System.out.println("COW mutation bytes: firstPair=" + firstPair + ", repeated1000Pairs=" + repeatedWriteBytes
                + ", control1000Pairs=" + controlWriteBytes);
        // Budget includes detaching both V8 word arrays and their small palettes.
        assertTrue(firstPair < 16 * 1024, "First write pair allocated " + firstPair);
        assertTrue(repeatedWriteBytes <= controlWriteBytes + 256, "Later writes exceeded unshared control: " + repeatedWriteBytes);
    }

    private static void writePairs(StateBlockStorage blocks, PalettedBlockStorage biomes,
                                   BlockStateSnapshot stone, BlockStateSnapshot dirt, int count) {
        for (int i = 0; i < count; i++) {
            blocks.set(0, (i & 1) == 0 ? stone : dirt);
            biomes.setBlock(0, (i & 1) + 1);
        }
    }

    @Test
    void snapshotWriteSnapshotAllocationScalesWithChangedSections() {
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var stone = state(Block.STONE);
        var dirt = state(Block.DIRT);
        long unchangedBytes = 0;
        for (int changed : new int[]{0, 6, 12, 24}) {
            var live = chunk(true);
            live.setProvider(null);
            for (int i = 0; i < 300; i++) {
                allocationSink = snapshotWriteSnapshot(live, changed, (i & 1) == 0 ? dirt : stone, (i & 1) == 0 ? 2 : 1);
            }
            long before = bean.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < 100; i++) {
                allocationSink = snapshotWriteSnapshot(live, changed, (i & 1) == 0 ? dirt : stone, (i & 1) == 0 ? 2 : 1);
            }
            long actual = (bean.getCurrentThreadAllocatedBytes() - before) / 100;
            System.out.println("COW cycle bytes: changedSections=" + changed + "/24, bytes=" + actual);
            if (changed == 0) {
                unchangedBytes = actual;
                assertTrue(actual < 64 * 1024, "Unchanged snapshot pair allocated " + actual);
            }
            // Two snapshots plus one detachment of both block layers and the biome storage per changed section.
            assertTrue(actual <= unchangedBytes + (long) changed * 16 * 1024 + 4096,
                    "Snapshot/write/snapshot cycle allocated " + actual + " bytes for " + changed + " sections");
            var first = snapshotWriteSnapshot(live, changed, dirt, 2);
            var second = (LevelDBChunk) allocationSink;
            for (int i = 0; i < 24; i++) {
                var original = ((LevelDBChunkSection) first.getSections()[i]).getStorages();
                var current = ((LevelDBChunkSection) live.getSections()[i]).getStorages();
                var latest = ((LevelDBChunkSection) second.getSections()[i]).getStorages();
                for (int layer = 0; layer < 2; layer++) {
                    assertEquals(Block.STONE << Block.DATA_BITS, original[layer].get(0));
                    assertEquals((i < changed ? Block.DIRT : Block.STONE) << Block.DATA_BITS, current[layer].get(0));
                    assertEquals(i >= changed, field(original[layer], "bitArray") == field(current[layer], "bitArray"));
                    assertSame(field(current[layer], "bitArray"), field(latest[layer], "bitArray"));
                }
                assertEquals(1, first.biomes3d[i].getBlock(0));
                assertEquals(i < changed ? 2 : 1, live.biomes3d[i].getBlock(0));
                assertEquals(i >= changed, field(first.biomes3d[i], "bitArray") == field(live.biomes3d[i], "bitArray"));
                assertSame(field(live.biomes3d[i], "bitArray"), field(second.biomes3d[i], "bitArray"));
            }
        }
    }

    private static LevelDBChunk snapshotWriteSnapshot(LevelDBChunk live, int changed,
                                                     BlockStateSnapshot block, int biome) {
        var first = live.cloneForChunkSending();
        for (int i = 0; i < changed; i++) {
            var storages = ((LevelDBChunkSection) live.getSections()[i]).getStorages();
            storages[0].set(0, block);
            storages[1].set(0, block);
            live.biomes3d[i].setBlock(0, biome);
        }
        allocationSink = live.cloneForChunkSending();
        return first;
    }

    private static StateBlockStorage populated(BitArrayVersion width) {
        var storage = new StateBlockStorage(width);
        int[] ids = {Block.STONE, Block.DIRT, Block.WOOL, Block.LOG, Block.PLANKS};
        for (int i = 0; i < 4096; i++) storage.set(i, state(ids[i % ids.length]));
        storage.set(4095, state(Block.GOLD_ORE));
        storage.set(4095, state(ids[4095 % ids.length]));
        return storage;
    }

    private static LevelDBChunk chunk(boolean full) {
        var provider = mock(LevelDBProvider.class);
        var level = mock(Level.class);
        when(provider.getLevel()).thenReturn(level);
        when(level.getDimensionData()).thenReturn(DIMENSION);
        var chunk = LevelDBChunk.getEmptyChunk(3, 5, provider);
        var biomes = new PalettedBlockStorage[chunk.getSections().length];
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V8, 1);
            if (!full && i != 0) continue;
            chunk.getSections()[i] = new LevelDBChunkSection(chunk, i - chunk.getSectionOffset(),
                    new StateBlockStorage[]{populated(BitArrayVersion.V8), populated(BitArrayVersion.V8)},
                    null, null, null, false, true);
            for (int cell = 0; cell < 4096; cell++) biomes[i].setBlock(cell, cell % 64 + 1);
        }
        chunk.setBiomes3d(biomes);
        return chunk;
    }

    // Reconstruct the old eager-copy fields; never use copy() for the reference backing arrays.
    @SuppressWarnings("unchecked")
    private static StateBlockStorage eager(StateBlockStorage source) {
        byte[] ids = (byte[]) field(source, "blockIds");
        var data = (NibbleArray) field(source, "blockData");
        return new StateBlockStorage(((BitArray) field(source, "bitArray")).copy(),
                new ObjectArrayList<>((List<BlockStateSnapshot>) field(source, "palette")),
                ids == null ? null : ids.clone(), data == null ? null : data.copy());
    }

    private static LevelDBChunk eagerChunk(LevelDBChunk source) {
        var copy = source.cloneForChunkSending();
        for (int i = 0; i < source.getSections().length; i++) {
            if (!(source.getSections()[i] instanceof LevelDBChunkSection section)) continue;
            var storages = Arrays.stream(section.getStorages()).map(ChunkSendingCowTest::eager)
                    .toArray(StateBlockStorage[]::new);
            copy.getSections()[i] = new LevelDBChunkSection(copy, section.y, storages,
                    section.blockLight == null ? null : section.blockLight.clone(),
                    section.skyLight == null ? null : section.skyLight.clone(),
                    section.compressedLight == null ? null : section.compressedLight.clone(),
                    section.hasBlockLight, section.hasSkyLight);
        }
        for (int i = 0; i < source.biomes3d.length; i++) {
            try {
                var constructor = PalettedBlockStorage.class.getDeclaredConstructor(BitArray.class, IntList.class);
                constructor.setAccessible(true);
                copy.biomes3d[i] = constructor.newInstance(((BitArray) field(source.biomes3d[i], "bitArray")).copy(),
                        new IntArrayList((IntList) field(source.biomes3d[i], "palette")));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        return copy;
    }

    private static byte[] serialize(LevelDBChunk chunk, GameVersion version, boolean antiXray) {
        var versions = new ObjectOpenHashSet<GameVersion>();
        versions.add(version);
        var output = new ArrayList<byte[]>();
        NetworkChunkSerializer.serialize(versions, chunk, value -> output.add(value.getStream().getBuffer()),
                antiXray, DIMENSION);
        assertEquals(1, output.size());
        return output.get(0);
    }

    private static byte[] biomeBytes(LevelDBChunk chunk) {
        var stream = new BinaryStream();
        for (var biome : chunk.biomes3d) biome.writeTo(stream);
        return stream.getBuffer();
    }

    private static CapturedColumn column(LevelDBChunk chunk, GameVersion version, boolean antiXray, boolean capture) {
        var versions = new ObjectOpenHashSet<GameVersion>();
        versions.add(version);
        var output = new ArrayList<CapturedColumn>();
        NetworkChunkSerializer.serialize(versions, chunk, value -> output.add(new CapturedColumn(value.getSubchunks(),
                value.getStream().getBuffer(), value.getSubChunkBlobs(), value.getBiomes(), value.getBlockEntities())),
                antiXray, DIMENSION, capture);
        assertEquals(1, output.size());
        return output.get(0);
    }

    private record CapturedColumn(int count, byte[] payload, List<byte[]> blobs, byte[] biomes, byte[] entities) { }

    private static byte[] sectionBytes(LevelDBChunkSection section) {
        var stream = new BinaryStream();
        section.writeTo(GameVersion.V1_26_50, stream, false);
        return stream.getBuffer();
    }

    private static BlockStateSnapshot state(int id) { return BlockStateMapping.get().getState(id, 0); }

    private static byte[] disk(StateBlockStorage storage) {
        var buffer = Unpooled.buffer();
        try {
            storage.writeToStorage(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static void read(StateBlockStorage storage, byte[] bytes) {
        var buffer = Unpooled.wrappedBuffer(bytes);
        try {
            storage.readFromStorage(buffer, new ChunkBuilder(0, 0, mock(LevelDBProvider.class)));
        } finally {
            buffer.release();
        }
    }

    private static void assertEquivalent(StateBlockStorage expected, StateBlockStorage actual) {
        for (int i = 0; i < 4096; i++) assertEquals(expected.get(i), actual.get(i));
        assertEquals(expected.isEmpty(), actual.isEmpty());
        assertArrayEquals(disk(expected), disk(actual));
    }

    private static Object field(Object storage, String name) {
        try {
            Class<?> type = storage instanceof StateBlockStorage ? StateBlockStorage.class : PalettedBlockStorage.class;
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(storage);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
