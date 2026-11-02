package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import cn.nukkit.GameVersion;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class StateBlockStorageLazyAllocationTest {
    @BeforeAll
    static void init() { MockServer.init(); }

    @Test
    void freshAirLayerHasNoCellWordsAndSerializesExactlyAsBefore() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        assertEquals(0, bits(storage).getWords().length);
        assertEquals(BitArrayVersion.V0, bits(storage).getVersion());
        for (int cell = 0; cell < 4096; cell++) assertEquals(BlockID.AIR, storage.getBlockState(cell).getLegacyId());
        assertArrayEquals(serialized(new StateBlockStorage(BitArrayVersion.V2)), serialized(storage));
    }

    @Test
    void firstMutationAndCopyLeaveAllOtherCellsAndOriginalIndependent() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        StateBlockStorage copy = storage.copy();
        var stone = BlockStateMapping.get().getState(BlockID.STONE, 0);
        var water = BlockStateMapping.get().getState(BlockID.WATER, 0);
        storage.set(2048, stone);
        assertEquals(BitArrayVersion.V2, bits(storage).getVersion(), "skip an intermediate V1 and zero-index scan");
        copy.set(4095, water);
        for (int cell = 0; cell < 4096; cell++) {
            assertEquals(cell == 2048 ? BlockID.STONE : BlockID.AIR, storage.getBlockState(cell).getLegacyId());
            assertEquals(cell == 4095 ? BlockID.WATER : BlockID.AIR, copy.getBlockState(cell).getLegacyId());
        }
    }

    @Test
    void mutationsRoundTripThroughTheExistingDiskReader() {
        StateBlockStorage storage = new StateBlockStorage();
        int[] ids = {BlockID.STONE, BlockID.DIRT, BlockID.WATER, BlockID.GLASS, BlockID.SAND};
        for (int cell = 0; cell < 4096; cell += 7) storage.set(cell, BlockStateMapping.get().getState(ids[cell % ids.length], 0));
        ByteBuf bytes = Unpooled.wrappedBuffer(serialized(storage));
        try {
            StateBlockStorage restored = new StateBlockStorage();
            restored.readFromStorage(bytes, null);
            for (int cell = 0; cell < 4096; cell++) assertSame(storage.getBlockState(cell), restored.getBlockState(cell));
        } finally { bytes.release(); }
    }

    @Test
    void customNonzeroSingletonStillCopiesIndicesWhenGrown() throws Exception {
        BitArray bits = BitArrayVersion.V0.createPalette(4096);
        bits.set(0, 1);
        StateBlockStorage storage = new StateBlockStorage(bits, new java.util.ArrayList<>(java.util.List.of(
                BlockStateMapping.get().getState(BlockID.AIR, 0),
                BlockStateMapping.get().getState(BlockID.STONE, 0))), null, null);
        var grow = StateBlockStorage.class.getDeclaredMethod("grow", BitArrayVersion.class);
        grow.setAccessible(true);
        grow.invoke(storage, BitArrayVersion.V2);
        for (int cell = 0; cell < 4096; cell++) assertEquals(BlockID.STONE, storage.getBlockState(cell).getLegacyId());
    }

    @Test
    void everySupportedProtocolKeepsThePreviousV2NetworkBytes() {
        var versions = java.util.Arrays.stream(GameVersion.getValues())
                .filter(v -> v.getProtocol() >= 589 && v.getProtocol() <= 2193).toList();
        assertTrue(versions.stream().anyMatch(v -> v.getProtocol() == 589));
        assertTrue(versions.stream().anyMatch(v -> v.getProtocol() == 2193));
        boolean originalHashedIds = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (int scenario = 0; scenario < 4; scenario++) {
                StateBlockStorage before = new StateBlockStorage(BitArrayVersion.V2);
                StateBlockStorage after = new StateBlockStorage();
                int[] ids = {BlockID.STONE, BlockID.DIRT, BlockID.DIAMOND_ORE, BlockID.WATER};
                int writes = switch (scenario) { case 0 -> 0; case 1 -> 1; case 2 -> 2; default -> 4096; };
                for (int cell = 0; cell < writes; cell++) {
                    var state = BlockStateMapping.get().getState(ids[cell % ids.length], 0);
                    before.set(cell, state);
                    after.set(cell, state);
                }
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

    private static BitArray bits(StateBlockStorage storage) throws Exception {
        Field f = StateBlockStorage.class.getDeclaredField("bitArray"); f.setAccessible(true);
        return (BitArray) f.get(storage);
    }

    private static byte[] serialized(StateBlockStorage storage) {
        ByteBuf bytes = Unpooled.buffer();
        try { storage.writeToStorage(bytes); byte[] result = new byte[bytes.readableBytes()]; bytes.readBytes(result); return result; }
        finally { bytes.release(); }
    }
}
