package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.ChunkSection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LevelDBSectionParentPublicationTest {
    @BeforeAll static void setup() { MockServer.init(); }

    @Test
    void anObserverOfThePublishedSectionCanNotifyItsParentImmediately() {
        WatchingChunk chunk = new WatchingChunk();
        LevelDBChunkSection section = new LevelDBChunkSection(0);
        assertDoesNotThrow(() -> chunk.install(section));
        assertSame(chunk, section.getParent());
        assertEquals(BlockID.DIRT, chunk.getBlockId(2, 1, 1));
        assertTrue(chunk.isSubChunksDirty());
    }

    private static final class WatchingChunk extends LevelDBChunk {
        private boolean probePublication;

        WatchingChunk() { super(null, 0, 0); }

        void install(ChunkSection section) {
            probePublication = true;
            super.setInternalSection(0, section);
        }

        @Override public void setChanged() {
            super.setChanged();
            if (probePublication) {
                probePublication = false;
                // BaseChunk calls setChanged after publishing the section slot.
                // This deterministic observer performs the same write as a reader
                // scheduled between slot publication and LevelDB's setParent.
                getSection(0).setBlockId(2, 1, 1, BlockID.DIRT);
            }
        }
    }
}
