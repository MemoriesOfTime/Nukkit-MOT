package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ScalarSkyLightReadTest {
    private static final class Section extends LevelDBChunkSection {
        Section() { super(0); }
        byte[] backing() { return skyLight; }
        void compressed(byte[] light) { skyLight = null; compressedLight = light; hasSkyLight = true; }
        void uniform(boolean lit) { skyLight = null; compressedLight = null; hasSkyLight = lit; }
    }

    @Test
    void scalarReadsKeepMaterializedStorageAndAllNibbles() {
        Section section = new Section();
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            section.setBlockSkyLight(x, y, z, (x + y + z) & 15);
        }
        byte[] backing = section.backing();
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            assertEquals((x + y + z) & 15, section.getBlockSkyLight(x, y, z));
        }
        assertSame(backing, section.backing(), "scalar queries must not replace live light storage");
        byte[] external = section.getSkyLightArray();
        external[0] = 0;
        assertEquals(1, section.getBlockSkyLight(1, 0, 0));
    }

    @Test
    void compressedLightInflatesOnceAndKeepsBlockLight() throws Exception {
        Section section = new Section();
        byte[] combined = new byte[4096];
        Arrays.fill(combined, 0, 2048, (byte) 0x32);
        Arrays.fill(combined, 2048, 4096, (byte) 0xa7);
        section.compressed(Zlib.deflate(combined));
        assertEquals(7, section.getBlockSkyLight(0, 0, 0));
        byte[] backing = section.backing();
        assertEquals(10, section.getBlockSkyLight(1, 0, 0));
        assertSame(backing, section.backing());
        assertEquals(2, section.getBlockLight(0, 0, 0));
        assertEquals(3, section.getBlockLight(1, 0, 0));
    }

    @Test
    void uniformDefaultsRemainImplicitAndWritesAreVisible() {
        Section section = new Section();
        section.uniform(false);
        assertEquals(0, section.getBlockSkyLight(15, 15, 15));
        assertNull(section.backing());
        section.uniform(true);
        assertEquals(15, section.getBlockSkyLight(15, 15, 15));
        assertNull(section.backing());
        section.setBlockSkyLight(14, 15, 15, 4);
        assertEquals(4, section.getBlockSkyLight(14, 15, 15));
        assertEquals(15, section.getBlockSkyLight(15, 15, 15));
    }
    @Test
    void copyingDetachesMaterializedSkylightBeforeEitherSectionReads() {
        Section source = new Section();
        source.setBlockSkyLight(0, 0, 0, 7);
        source.setBlockSkyLight(1, 0, 0, 11);
        LevelDBChunkSection copy = (LevelDBChunkSection) source.copy();
        source.setBlockSkyLight(0, 0, 0, 2);
        assertEquals(7, copy.getBlockSkyLight(0, 0, 0));
        assertEquals(11, copy.getBlockSkyLight(1, 0, 0));
        copy.setBlockSkyLight(1, 0, 0, 4);
        assertEquals(11, source.getBlockSkyLight(1, 0, 0));
        assertEquals(2, source.getBlockSkyLight(0, 0, 0));
    }

    @Test
    void compressedAndImplicitCopiesRetainIndependentWrites() throws Exception {
        Section source = new Section();
        byte[] combined = new byte[4096];
        Arrays.fill(combined, 2048, 4096, (byte) 0xa7);
        source.compressed(Zlib.deflate(combined));
        LevelDBChunkSection compressedCopy = (LevelDBChunkSection) source.copy();
        source.setBlockSkyLight(0, 0, 0, 1);
        assertEquals(7, compressedCopy.getBlockSkyLight(0, 0, 0));
        compressedCopy.setBlockSkyLight(1, 0, 0, 3);
        assertEquals(10, source.getBlockSkyLight(1, 0, 0));
        source.uniform(true);
        LevelDBChunkSection implicitCopy = (LevelDBChunkSection) source.copy();
        source.setBlockSkyLight(0, 0, 0, 2);
        assertEquals(15, implicitCopy.getBlockSkyLight(0, 0, 0));
    }

}
