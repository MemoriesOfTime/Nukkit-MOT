package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.updater.BlockStateUpdaterVanilla;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.*;

/**
 * 1.26.50 前 legacy 连接/角落位的检测：磁盘状态缺 corner/connection 键时必须在区块加载路径
 * 打上 needsLegacyConnectionFix 标志（发送前按邻居重算），已带新键的状态不得误标。
 * <p>
 * Detection of pre-1.26.50 legacy connection/corner bits: a serialized state missing the
 * corner/connection keys must flag its chunk with needsLegacyConnectionFix (recompute from
 * neighbours before sending); states already carrying the new keys must not be flagged.
 */
class LegacyConnectionFixFlagTest {

    private static final int SIZE = 4096;

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void pre12650StatesNeedRecompute() {
        Assertions.assertTrue(BlockStateUpdaterVanilla.needsConnectionRecompute(
                stairs(2, false, null)), "stairs without corner key");
        Assertions.assertTrue(BlockStateUpdaterVanilla.needsConnectionRecompute(
                pane(false)), "pane without connection keys");
        Assertions.assertTrue(BlockStateUpdaterVanilla.needsConnectionRecompute(
                stairs(0, true, null)), "upside-down stairs without corner key");
        Assertions.assertTrue(BlockStateUpdaterVanilla.needsConnectionRecompute(
                tripWire(0, false)), "tripwire without connection keys");
    }

    @Test
    void post12650StatesDoNotNeedRecompute() {
        Assertions.assertFalse(BlockStateUpdaterVanilla.needsConnectionRecompute(
                stairs(2, false, "none")), "stairs with corner key");
        Assertions.assertFalse(BlockStateUpdaterVanilla.needsConnectionRecompute(
                stairs(2, false, "inner_left")), "stairs with corner value");
        Assertions.assertFalse(BlockStateUpdaterVanilla.needsConnectionRecompute(
                pane(true)), "pane with connection keys");
    }

    @Test
    void unrelatedBlocksAreIgnored() {
        Assertions.assertFalse(BlockStateUpdaterVanilla.needsConnectionRecompute(
                NbtMap.builder()
                        .putString("name", "minecraft:stone")
                        .putCompound("states", NbtMap.EMPTY)
                        .putInt("version", stateVersion(STATE_PATCH_VERSION))
                        .build()));
        Assertions.assertFalse(BlockStateUpdaterVanilla.needsConnectionRecompute(
                NbtMap.builder()
                        .putString("name", "minecraft:custom_stairs_like")
                        .putCompound("states", NbtMap.EMPTY)
                        .putInt("version", 1)
                        .build()), "off-list name must not be flagged");
    }

    @Test
    void deserializationFlagsChunkForLegacyStates() {
        StateBlockStorage storage = storage(List.of(
                snapshot(BlockID.AIR, air()),
                snapshot(53, stairs(3, true, null))));
        Assertions.assertTrue(roundTrip(storage), "palette with pre-1.26.50 stairs must flag the chunk");
    }

    @Test
    void deserializationKeepsCurrentStatesUnflagged() {
        StateBlockStorage storage = storage(List.of(
                snapshot(BlockID.AIR, air()),
                snapshot(53, stairs(3, true, "none"))));
        Assertions.assertFalse(roundTrip(storage), "palette whose connection-driven states all carry the new keys must stay unflagged");
    }

    /**
     * 序列化往返后检查 ChunkBuilder 是否被打上 needsLegacyConnectionFix。
     * <p>
     * Serializes and re-reads the storage, checking whether the ChunkBuilder got flagged.
     */
    private boolean roundTrip(StateBlockStorage storage) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            storage.writeToStorage(buffer);
            ChunkBuilder builder = new ChunkBuilder(0, 0, Mockito.mock(LevelDBProvider.class));
            new StateBlockStorage(BitArrayVersion.V2.createPalette(SIZE), new ArrayList<>(), null, null)
                    .readFromStorage(buffer, builder);
            return builder.isNeedsLegacyConnectionFix();
        } finally {
            buffer.release();
        }
    }

    private static StateBlockStorage storage(List<BlockStateSnapshot> palette) {
        BitArray array = BitArrayVersion.V16.createPalette(SIZE);
        for (int i = 0; i < SIZE; i++) {
            array.set(i, Math.min(i, palette.size() - 1));
        }
        return new StateBlockStorage(array, new ArrayList<>(palette), null, null);
    }

    private static BlockStateSnapshot snapshot(int legacyId, NbtMap vanillaState) {
        return BlockStateSnapshot.builder().legacyId(legacyId).legacyData(0).runtimeId(legacyId)
                .vanillaState(vanillaState).build();
    }

    private static int stateVersion(int patch) {
        return (patch << 8) | (STATE_MINOR_VERSION << 16) | (STATE_MAYOR_VERSION << 24);
    }

    private static NbtMap air() {
        return NbtMap.builder()
                .putString("name", "minecraft:air")
                .putCompound("states", NbtMap.EMPTY)
                .putInt("version", stateVersion(STATE_PATCH_VERSION))
                .build();
    }

    private static NbtMap stairs(int weirdoDirection, boolean upsideDown, String corner) {
        NbtMapBuilder states = NbtMap.builder()
                .putInt("weirdo_direction", weirdoDirection)
                .putBoolean("upside_down_bit", upsideDown);
        if (corner != null) {
            states.putString("minecraft:corner", corner);
        }
        return NbtMap.builder()
                .putString("name", "minecraft:oak_stairs")
                .putCompound("states", states.build())
                .putInt("version", stateVersion(corner == null ? 10 : STATE_PATCH_VERSION))
                .build();
    }

    private static NbtMap pane(boolean connectedNorth) {
        NbtMapBuilder states = NbtMap.builder();
        if (connectedNorth) {
            states.putBoolean("minecraft:connection_north", true);
            states.putBoolean("minecraft:connection_east", false);
            states.putBoolean("minecraft:connection_south", false);
            states.putBoolean("minecraft:connection_west", false);
        }
        return NbtMap.builder()
                .putString("name", "minecraft:white_stained_glass_pane")
                .putCompound("states", states.build())
                .putInt("version", stateVersion(connectedNorth ? STATE_PATCH_VERSION : 40))
                .build();
    }

    private static NbtMap tripWire(int powered, boolean attached) {
        return NbtMap.builder()
                .putString("name", "minecraft:trip_wire")
                .putCompound("states", NbtMap.builder()
                        .putInt("powered_bit", powered)
                        .putBoolean("attached_bit", attached)
                        .build())
                .putInt("version", stateVersion(40))
                .build();
    }
}
