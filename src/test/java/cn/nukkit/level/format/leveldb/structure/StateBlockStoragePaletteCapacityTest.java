package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.BlockID;
import cn.nukkit.MockServer;
import cn.nukkit.GameVersion;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class StateBlockStoragePaletteCapacityTest {
    @BeforeAll
    static void init() { MockServer.init(); }

    @Test
    void compactionDropsUnusedHistoricalCapacityWithoutChangingCurrentCells() throws Exception {
        ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>(10001);
        palette.add(state(BlockID.AIR, 0));
        for (int i = 1; i <= 10000; i++) palette.add(state(BlockID.STONE, i));
        BitArray bits = BitArrayVersion.V16.createPalette(4096);
        bits.set(123, 10000);
        BlockStateSnapshot air = palette.get(0), last = palette.get(10000);
        StateBlockStorage storage = new StateBlockStorage(bits, palette, null, null);
        assertTrue(storage.compress());
        assertEquals(2, palette(storage).size());
        assertEquals(2, ((ObjectArrayList<?>) palette(storage)).elements().length);
        for (int cell = 0; cell < 4096; cell++) assertSame(cell == 123 ? last : air, storage.getBlockState(cell));
        var replacement = state(BlockID.DIRT, 0);
        storage.set(124, replacement);
        assertSame(replacement, storage.getBlockState(124));
        assertSame(last, storage.getBlockState(123));
    }

    @Test
    void allAirSpecialCaseAlsoReleasesHistoricalCapacity() throws Exception {
        ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>(10001);
        for (int i = 0; i <= 10000; i++) palette.add(state(BlockID.AIR, i));
        StateBlockStorage storage = new StateBlockStorage(BitArrayVersion.V16.createPalette(4096), palette, null, null);
        assertTrue(storage.compress());
        assertEquals(1, palette(storage).size());
        assertEquals(1, ((ObjectArrayList<?>) palette(storage)).elements().length);
        storage.set(123, state(BlockID.STONE, 0));
        assertEquals(BlockID.STONE, storage.getBlockState(123).getLegacyId());
        assertEquals(BlockID.AIR, storage.getBlockState(124).getLegacyId());
    }

    @Test
    void everySupportedProtocolKeepsThePreCompactionNetworkBytes() {
        var versions = java.util.Arrays.stream(GameVersion.getValues())
                .filter(v -> v.getProtocol() >= 589 && v.getProtocol() <= 2193).toList();
        assertTrue(versions.stream().anyMatch(v -> v.getProtocol() == 589));
        assertTrue(versions.stream().anyMatch(v -> v.getProtocol() == 2193));
        boolean originalHashedIds = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (int scenario = 0; scenario < 3; scenario++) {
                ObjectArrayList<BlockStateSnapshot> history = new ObjectArrayList<>(257);
                int[] ids = {BlockID.AIR, BlockID.STONE, BlockID.DIAMOND_ORE, BlockID.WATER};
                for (int entry = 0; entry < 257; entry++) {
                    history.add(BlockStateMapping.get().getState(scenario == 0 ? BlockID.AIR : ids[entry % ids.length], 0));
                }
                BitArray bits = BitArrayVersion.V16.createPalette(4096);
                for (int cell = 0; cell < 4096; cell++) bits.set(cell, scenario == 2 ? cell % 257 : 255);
                StateBlockStorage after = new StateBlockStorage(bits, history, null, null);
                StateBlockStorage before = after.copy();
                assertTrue(after.compress());
                for (GameVersion version : versions) {
                    for (boolean hashed : new boolean[]{false, true}) {
                        GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                        for (boolean antiXray : new boolean[]{false, true}) {
                            assertArrayEquals(networkBytes(before, version, antiXray), networkBytes(after, version, antiXray),
                                    version + ", scenario=" + scenario + ", hashed=" + hashed + ", antiXray=" + antiXray);
                        }
                    }
                }
            }
        } finally {
            GlobalBlockPalette.setUseHashedBlockNetworkIds(originalHashedIds);
        }
    }

    private static byte[] networkBytes(StateBlockStorage storage, GameVersion version, boolean antiXray) {
        BinaryStream stream = new BinaryStream();
        storage.writeTo(version, stream, antiXray);
        return stream.getBuffer();
    }

    private static BlockStateSnapshot state(int id, int data) {
        return BlockStateSnapshot.builder().legacyId(id).legacyData(data).build();
    }

    @SuppressWarnings("unchecked")
    private static ObjectArrayList<BlockStateSnapshot> palette(StateBlockStorage storage) throws Exception {
        Field f = StateBlockStorage.class.getDeclaredField("palette"); f.setAccessible(true);
        return (ObjectArrayList<BlockStateSnapshot>) f.get(storage);
    }
}
