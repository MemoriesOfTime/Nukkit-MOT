package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class StateBlockStorageNetworkTest {
    private boolean originalHashedIds;

    @BeforeEach
    void saveNetworkIdSetting() {
        originalHashedIds = GlobalBlockPalette.useHashedBlockNetworkIds();
    }

    @AfterEach
    void restoreNetworkIdSetting() {
        GlobalBlockPalette.setUseHashedBlockNetworkIds(originalHashedIds);
    }

    static Stream<GameVersion> protocols() {
        return Stream.of(GameVersion.V1_12_0, GameVersion.V1_16_0, GameVersion.V1_16_100,
                GameVersion.V1_19_80, GameVersion.V1_21_50_NETEASE, GameVersion.getLastVersion());
    }

    @ParameterizedTest
    @MethodSource("protocols")
    void bytesMatchPerBlockLookupWithAndWithoutAntiXrayAndHashedIds(GameVersion protocol) {
        StateBlockStorage storage = new StateBlockStorage();
        int[][] blocks = {{Block.AIR, 0}, {Block.STONE, 0}, {Block.PLANKS, 2},
                {Block.WOOL, 14}, {Block.DIAMOND_ORE, 0}, {Block.LOG, 8}};
        for (int i = 0; i < 4096; i++) {
            int[] block = blocks[i % blocks.length];
            storage.set(i, BlockStateMapping.get().getState(block[0], block[1]));
        }

        for (boolean hashed : new boolean[]{false, true}) {
            GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
            for (boolean antiXray : new boolean[]{false, true}) {
                BinaryStream actual = new BinaryStream();
                storage.writeTo(protocol, actual, antiXray);
                assertArrayEquals(serializeUsingPerBlockLookup(storage, protocol, antiXray), actual.getBuffer(),
                        protocol + ", hashed=" + hashed + ", antiXray=" + antiXray);
            }
        }
    }

    /** The previous wire-format algorithm is an independent reference for both palette paths. */
    private byte[] serializeUsingPerBlockLookup(StateBlockStorage storage, GameVersion protocol, boolean antiXray) {
        PalettedBlockStorage output = PalettedBlockStorage.createFromBlockPalette(protocol);
        for (int i = 0; i < 4096; i++) {
            int fullId = storage.get(i);
            int id = fullId >> Block.DATA_BITS;
            int meta = fullId & Block.DATA_MASK;
            if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
                id = Block.STONE;
                meta = 0;
            }
            output.setBlock(i, GlobalBlockPalette.getOrCreateRuntimeId(protocol, id, meta));
        }
        BinaryStream expected = new BinaryStream();
        output.writeTo(expected);
        return expected.getBuffer();
    }
}
