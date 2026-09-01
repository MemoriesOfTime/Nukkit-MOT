package cn.nukkit.level.format.generic;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that tiles whose type cannot be constructed at load time (unregistered/unknown id,
 * such as a modded or custom container tile) are retained verbatim by {@link BaseFullChunk#initChunk()}
 * instead of being silently dropped and force-saved away.
 * <p>
 * Regression guard for the "channel ①" data-loss defect documented in
 * {@code .claude/tile-nbt-loss-channel1-initchunk.md}.
 */
@ExtendWith(MockitoExtension.class)
public class UnknownTilePreservationTest {

    private BaseFullChunk chunk;

    @BeforeAll
    static void initServer() {
        // Provides a mock Server.instance whose getLogger() is non-null, used by
        // BlockEntity.createBlockEntity() when it logs an unknown tile id.
        MockServer.init();
    }

    @BeforeEach
    void setUp() throws Exception {
        chunk = mock(BaseFullChunk.class, CALLS_REAL_METHODS);

        // Mockito does not run field initializers; the changes counter must be set manually.
        Field changesField = BaseFullChunk.class.getDeclaredField("changes");
        changesField.setAccessible(true);
        changesField.set(chunk, new AtomicLong());

        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(provider.getMinBlockY()).thenReturn(0);
        chunk.setProvider(provider);
    }

    private static CompoundTag tileAt(String id, int x, int y, int z) {
        CompoundTag tag = new CompoundTag().putInt("x", x).putInt("y", y).putInt("z", z);
        if (id != null) {
            tag.putString("id", id);
        }
        return tag;
    }

    @Test
    @DisplayName("unconstructable tile is retained and does NOT force the chunk dirty")
    void unknownTileRetainedWithoutForcingDirty() {
        CompoundTag unknown = tileAt("Sales", 0, 64, 0);
        chunk.NBTtiles = new ArrayList<>(List.of(unknown));

        chunk.initChunk();

        assertEquals(1, chunk.getUnknownTiles().size(), "unknown tile NBT must be preserved");
        assertTrue(chunk.getUnknownTiles().contains(unknown), "the exact raw NBT must be kept");
        assertFalse(chunk.hasChanged(),
                "preserving an unknown tile must not force-mark the chunk dirty (that was the data-loss trigger)");
    }

    @Test
    @DisplayName("malformed tile (no id) is still dropped and marks the chunk dirty")
    void malformedTileStillDropped() {
        CompoundTag noId = tileAt(null, 0, 64, 0);
        chunk.NBTtiles = new ArrayList<>(List.of(noId));

        chunk.initChunk();

        assertTrue(chunk.getUnknownTiles().isEmpty(), "genuinely malformed tiles are not retained");
        assertTrue(chunk.hasChanged(), "dropping malformed data still marks the chunk changed");
    }

    @Test
    @DisplayName("unknown tile is skipped when a real tile already occupies that slot")
    void unknownTileDeduplicatedAgainstRealTile() {
        // index for (x=0,y=64,z=0) with minBlockY=0 == 64
        chunk.tileList = new Long2ObjectNonBlockingMap<>();
        chunk.tileList.put(64L, mock(BlockEntity.class));

        CompoundTag unknown = tileAt("Sales", 0, 64, 0);
        chunk.NBTtiles = new ArrayList<>(List.of(unknown));

        chunk.initChunk();

        assertTrue(chunk.getUnknownTiles().isEmpty(),
                "a real tile at the same position must take precedence over the unknown NBT");
    }

    @Test
    @DisplayName("retained unknown tile is dropped once its block can no longer host a tile")
    void unknownTileDroppedWhenBlockNoLongerHoldsTile() {
        CompoundTag unknown = tileAt("Sales", 0, 64, 0);
        chunk.NBTtiles = new ArrayList<>(List.of(unknown));
        chunk.initChunk();
        assertEquals(1, chunk.getUnknownTiles().size(), "precondition: unknown tile retained");

        // Block at this slot is now air (default getBlockId == 0) — not a BlockEntityHolder,
        // mirroring a player/plugin breaking or replacing the modded block.
        chunk.removeInvalidUnknownTile(0, 64, 0);

        assertTrue(chunk.getUnknownTiles().isEmpty(),
                "stale unknown tile must be dropped so it is not resurrected on the next save");
    }

    @Test
    @DisplayName("retained unknown tile is kept while its block can still host a tile")
    void unknownTileKeptWhileBlockCanHostTile() {
        CompoundTag unknown = tileAt("Sales", 0, 64, 0);
        chunk.NBTtiles = new ArrayList<>(List.of(unknown));
        chunk.initChunk();
        assertEquals(1, chunk.getUnknownTiles().size(), "precondition: unknown tile retained");

        // A tile-bearing block (hopper) still occupies the slot, so the NBT must be preserved.
        when(chunk.getBlockId(0, 64, 0)).thenReturn(BlockID.HOPPER_BLOCK);
        chunk.removeInvalidUnknownTile(0, 64, 0);

        assertEquals(1, chunk.getUnknownTiles().size(),
                "unknown tile must be preserved while the block can still host a block entity");
    }
}
