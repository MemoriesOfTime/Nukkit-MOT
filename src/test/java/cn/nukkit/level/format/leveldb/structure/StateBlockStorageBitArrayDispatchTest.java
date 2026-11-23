package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateBlockStorageBitArrayDispatchTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void pow2PaddedSingletonAndCustomBitArraysPreserveStateAndPackedReads() {
        BlockStateSnapshot air = BlockStateMapping.get().getState(BlockID.AIR, 0);
        BlockStateSnapshot stone = BlockStateMapping.get().getState(BlockID.STONE, 0);
        BlockStateSnapshot granite = BlockStateMapping.get().getState(BlockID.STONE, 1);
        int expectedStonePacked = (BlockID.STONE << Block.DATA_BITS);
        int expectedGranitePacked = (BlockID.STONE << Block.DATA_BITS) | 1;

        for (BitArrayVersion version : new BitArrayVersion[]{
                BitArrayVersion.V0,
                BitArrayVersion.V1,
                BitArrayVersion.V2,
                BitArrayVersion.V3,
                BitArrayVersion.V4,
                BitArrayVersion.V5,
                BitArrayVersion.V6,
                BitArrayVersion.V8,
                BitArrayVersion.V16
        }) {
            StateBlockStorage storage = new StateBlockStorage(version);
            storage.set(0, 0, 0, stone);
            storage.set(1, 2, 3, granite);

            int idx0 = (0 << 8) | (0 << 4) | 0;
            int idx1 = (1 << 8) | (3 << 4) | 2;
            int idxEmpty = (2 << 8) | (2 << 4) | 2;

            assertEquals(stone, storage.getBlockState(idx0));
            assertEquals(granite, storage.getBlockState(idx1));
            assertEquals(air, storage.getBlockState(idxEmpty));
            assertEquals(expectedStonePacked, storage.get(idx0));
            assertEquals(expectedGranitePacked, storage.get(idx1));
            assertEquals(0, storage.get(idxEmpty));
        }

        AtomicInteger customGets = new AtomicInteger();
        BitArray custom = new BitArray() {
            @Override
            public void set(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int get(int index) {
                customGets.incrementAndGet();
                return index == 7 ? 1 : 0;
            }

            @Override
            public int size() {
                return 4096;
            }

            @Override
            public int[] getWords() {
                return new int[0];
            }

            @Override
            public BitArrayVersion getVersion() {
                return BitArrayVersion.V1;
            }

            @Override
            public BitArray copy() {
                return this;
            }
        };

        StateBlockStorage customStorage = new StateBlockStorage(custom, List.of(air, granite), null, null) {};
        assertEquals(granite, customStorage.getBlockState(7));
        assertEquals(expectedGranitePacked, customStorage.get(7));
        assertEquals(air, customStorage.getBlockState(8));
        assertEquals(0, customStorage.get(8));
        assertTrue(customGets.get() >= 4);
    }
}
