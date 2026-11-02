package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LevelDBChunkSectionConstructorTest {
    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void trimsTrailingNullLayersAndPreservesStorageAndLight(int trailingLayers) {
        StateBlockStorage first = new StateBlockStorage();
        StateBlockStorage second = new StateBlockStorage();
        StateBlockStorage[] input = new StateBlockStorage[2 + trailingLayers];
        input[0] = first;
        input[1] = second;
        byte[] blockLight = new byte[2048];
        byte[] skyLight = new byte[2048];
        byte[] compressed = new byte[]{1, 2, 3};
        Arrays.fill(blockLight, (byte) 0x47);
        Arrays.fill(skyLight, (byte) 0x9c);

        LevelDBChunkSection section = new LevelDBChunkSection(null, 7, input,
                blockLight, skyLight, compressed, true, true);

        assertEquals(7, section.getY());
        assertArrayEquals(new StateBlockStorage[]{first, second}, section.storages);
        assertNotSame(input, section.storages);
        assertSame(blockLight, section.blockLight);
        assertSame(skyLight, section.skyLight);
        assertSame(compressed, section.compressedLight);
        assertTrue(section.hasBlockLight);
        assertTrue(section.hasSkyLight);
        input[0] = null;
        assertSame(first, section.storages[0], "the array remains detached from its caller");
    }

    @Test
    void fillsInteriorHolesBeforeTrimmingOnlyTheEmptyTail() {
        StateBlockStorage first = new StateBlockStorage();
        StateBlockStorage last = new StateBlockStorage();
        StateBlockStorage[] input = {null, first, null, last, null, null};
        LevelDBChunkSection section = new LevelDBChunkSection(3, input);

        assertEquals(4, section.storages.length);
        assertNotNull(section.storages[0]);
        assertSame(first, section.storages[1]);
        assertNotNull(section.storages[2]);
        assertSame(last, section.storages[3]);
        assertNotSame(section.storages[0], section.storages[2]);
        assertNull(input[4]);
        assertNull(input[5]);
    }

    @Test
    void fullyPopulatedLayersStillUseADetachedArrayWithTheSameStorageObjects() {
        StateBlockStorage first = new StateBlockStorage();
        StateBlockStorage second = new StateBlockStorage();
        StateBlockStorage[] input = {first, second};
        LevelDBChunkSection section = new LevelDBChunkSection(2, input);

        assertNotSame(input, section.storages);
        assertArrayEquals(input, section.storages);
        input[0] = null;
        assertSame(first, section.storages[0]);
    }

    @Test
    void absentOrEntirelyEmptyStorageKeepsTheTwoAirLayerFallback() {
        for (StateBlockStorage[] input : new StateBlockStorage[][]{null, {}, {null}, {null, null, null}}) {
            LevelDBChunkSection section = new LevelDBChunkSection(0, input);
            assertEquals(2, section.storages.length);
            assertNotNull(section.storages[0]);
            assertNotNull(section.storages[1]);
            assertEquals(0, section.getBlockId(0, 0, 0, 0));
            assertEquals(0, section.getBlockId(0, 0, 0, 1));
        }
    }
}
