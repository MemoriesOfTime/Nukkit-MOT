package cn.nukkit.level.format.anvil.util;

import cn.nukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

public class BlockStorageTest {

    private BlockStorage storage;

    @BeforeEach
    void setup() {
        storage = new BlockStorage();
    }

    @Test
    void testInvalidIndex() {
        assertThrows(IllegalArgumentException.class, () -> storage.getFullBlock(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.getFullBlock(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> {
            storage.setFullBlock(0, 0, 0, Block.FULL_SIZE); // just out of bound
        });
    }

    @Test
    void testFullBlockMaxBoundary() {
        int max = Block.FULL_SIZE - 1;
        storage.setFullBlock(0, 0, 0, max);
        assertEquals(max, storage.getFullBlock(0, 0, 0));
    }

    @Test
    void testSetGetBlockId() {
        storage.setBlockId(1, 2, 3, 255);
        assertEquals(255, storage.getBlockId(1, 2, 3));
        assertTrue(storage.hasBlockIds());

        storage.setBlockId(1, 2, 3, 300); // test with extra byte
        assertEquals(300, storage.getBlockId(1, 2, 3));
        assertTrue(storage.hasBlockIdExtras());
    }

    @Test
    void testSetGetBlockData() {
        int data = 0xFEDCB; // includes bits for base, extra, hyperA, hyperB
        storage.setBlockData(1, 2, 3, data);
        int read = storage.getBlockData(1, 2, 3);
        assertEquals(data & Block.DATA_MASK, read);
        assertTrue(storage.hasBlockDataExtras());
        assertTrue(storage.hasBlockDataHyperA());
        if (Block.DATA_BITS > 16) { //仅数据长度大于16时才会使用
            assertTrue(storage.hasBlockDataHyperB());
        }
    }

    @Test
    void testFullBlockSetGet() {
        int fullBlock = (0x01 << (Block.DATA_BITS + 8)) | (0xAB << Block.DATA_BITS) | 0x12345;
        storage.setFullBlock(1, 2, 3, fullBlock);
        assertEquals(fullBlock, storage.getFullBlock(1, 2, 3));
    }

    @Test
    void testGetAndSetFullBlock() {
        int last = 0;
        for (int i = 0; i < 16; i++) {
            int nextInt = ThreadLocalRandom.current().nextInt(Block.FULL_SIZE);

            int old = storage.getAndSetFullBlock(1, 2, 3, nextInt);
            assertEquals(last, old);

            int newVal = storage.getFullBlock(1, 2, 3);
            assertEquals(nextInt & (Block.FULL_SIZE - 1), newVal);

            last = newVal;
        }
    }

    @Test
    void testBlockStateSetGet() {
        storage.setBlock(1, 2, 3, 123, 456);
        int[] state = storage.getBlockState(1, 2, 3);
        assertEquals(123, state[0]);
        assertEquals(456 & Block.DATA_MASK, state[1]);
    }

    @Test
    void testHighBitsBlock() {
        int id = 0x1FF; // 9 bits
        int data = 0xFFFFF; // 20 bits
        storage.setBlock(2, 2, 2, id, data);
        assertEquals(id, storage.getBlockId(2, 2, 2));
        assertEquals(data & Block.DATA_MASK, storage.getBlockData(2, 2, 2));
    }

    @Test
    void testAllEdgeCoordinates() {
        for (int x = 0; x <= 15; x++) {
            for (int y = 0; y <= 15; y++) {
                for (int z = 0; z <= 15; z++) {
                    storage.setBlockId(x, y, z, 42);
                    assertEquals(42, storage.getBlockId(x, y, z));
                }
            }
        }
    }

    @Test
    void testMultipleUpdates() {
        storage.setBlock(5, 5, 5, 100, 200);
        storage.setBlock(5, 5, 5, 101, 201);
        int[] state = storage.getBlockState(5, 5, 5);
        assertEquals(101, state[0]);
        assertEquals(201 & Block.DATA_MASK, state[1]);
    }

    @Test
    void testCopyIndependence() {
        storage.setBlock(1, 2, 3, 42, 99);
        BlockStorage copy = storage.copy();
        assertEquals(42, copy.getBlockId(1, 2, 3));
        assertEquals(99 & Block.DATA_MASK, copy.getBlockData(1, 2, 3));

        // Change original
        storage.setBlock(1, 2, 3, 0, 0);
        assertNotEquals(storage.getBlockId(1, 2, 3), copy.getBlockId(1, 2, 3));
    }

    @Test
    void testRecheckFlags() {
        storage.setFullBlock(1, 1, 1, Block.FULL_SIZE - 1);
        storage.recheckBlocks();

        assertTrue(storage.hasBlockIds());
        assertTrue(storage.hasBlockIdExtras());
        assertTrue(storage.hasBlockDataExtras());
        assertTrue(storage.hasBlockDataHyperA());
        if (Block.DATA_BITS > 16) {
            assertTrue(storage.hasBlockDataHyperB());
        }
    }

    @Test
    void testZeroState() {
        assertEquals(0, storage.getBlockId(1, 1, 1));
        assertEquals(0, storage.getBlockData(1, 1, 1));
        assertEquals(0, storage.getHyperDataA(1, 1, 1));
        assertEquals(0, storage.getHyperDataB(1, 1, 1));
    }
}
