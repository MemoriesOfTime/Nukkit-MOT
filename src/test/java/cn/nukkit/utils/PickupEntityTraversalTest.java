package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickupEntityTraversalTest {
    @BeforeAll
    static void initializeServer() { MockServer.init(); }

    @Test
    void emptyIndexedAndMissingChunksDoNotScanEntityMapsOrLoadChunks() {
        Level level = mock(Level.class);
        BaseFullChunk empty = mock(BaseFullChunk.class);
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(empty);
        AxisAlignedBB area = new SimpleAxisAlignedBB(0, 64, 0, 1, 65, 1);
        assertTrue(CollisionHelper.getPickupEntities(level, area, false).isEmpty());
        verify(empty, never()).getEntities();
        verify(level, never()).getChunkEntities(anyInt(), anyInt(), anyBoolean());
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(null);
        assertTrue(CollisionHelper.getPickupEntities(level, area, true).isEmpty());
        verify(level, never()).getChunk(anyInt(), anyInt());
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void playerKindsAndHopperItemsKeepOriginalChunkAndEntityIterationOrder() {
        Level level = mock(Level.class);
        AxisAlignedBB area = new SimpleAxisAlignedBB(-1, 64, -1, 1, 65, 1);
        LevelDBChunk negative = new LevelDBChunk(null, -1, -1);
        LevelDBChunk positive = new LevelDBChunk(null, 0, 0);
        EntityXPOrb xp = entity(EntityXPOrb.class, 1, area);
        EntityArrow arrow = entity(EntityArrow.class, 2, area);
        EntityThrownTrident trident = entity(EntityThrownTrident.class, 3, area);
        EntityItem item = entity(EntityItem.class, 4, area);
        Entity mob = entity(Entity.class, 5, area);
        negative.addEntity(xp);
        negative.addEntity(arrow);
        positive.addEntity(trident);
        positive.addEntity(item);
        positive.addEntity(mob);
        when(level.getChunkIfLoaded(-1, -1)).thenReturn(negative);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(positive);
        List<Entity> expected = new ArrayList<>(negative.getEntities().values());
        positive.getEntities().values().stream().filter(entity -> entity != mob).forEach(expected::add);
        assertEquals(expected, CollisionHelper.getPickupEntities(level, area, false));
        assertEquals(List.of(item), CollisionHelper.getPickupEntities(level, area, true));
    }

    @Test
    void callbackMovementRemovalAndSpawnsDoNotChangePreEventSpatialSnapshot() {
        Level level = mock(Level.class);
        AxisAlignedBB area = new SimpleAxisAlignedBB(0, 64, 0, 1, 65, 1);
        LevelDBChunk chunk = new LevelDBChunk(null, 0, 0);
        EntityItem first = entity(EntityItem.class, 1, area);
        EntityItem second = entity(EntityItem.class, 2, area);
        chunk.addEntity(first);
        chunk.addEntity(second);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        List<Entity> expected = new ArrayList<>(chunk.getEntities().values());
        List<Entity> snapshot = CollisionHelper.getPickupEntities(level, area, true);
        Entity moved = snapshot.get(1);
        moved.boundingBox = new SimpleAxisAlignedBB(100, 64, 100, 101, 65, 101);
        chunk.removeEntity(moved);
        chunk.addEntity(entity(EntityItem.class, 3, area));
        assertEquals(expected, snapshot, "pickup callbacks see the original candidates and order");
    }

    @Test
    void nonIntersectingTypedCandidatesAreExcluded() {
        Level level = mock(Level.class);
        AxisAlignedBB area = new SimpleAxisAlignedBB(0, 64, 0, 1, 65, 1);
        LevelDBChunk chunk = new LevelDBChunk(null, 0, 0);
        chunk.addEntity(entity(EntityItem.class, 1, new SimpleAxisAlignedBB(4, 64, 4, 5, 65, 5)));
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        assertTrue(CollisionHelper.getPickupEntities(level, area, true).isEmpty());
    }

    private static <T extends Entity> T entity(Class<T> kind, long id, AxisAlignedBB area) {
        T entity = mock(kind);
        when(entity.getId()).thenReturn(id);
        entity.boundingBox = area;
        when(entity.getBoundingBox()).thenReturn(area);
        return entity;
    }
}
