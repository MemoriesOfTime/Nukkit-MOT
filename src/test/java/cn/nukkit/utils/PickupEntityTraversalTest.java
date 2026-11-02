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

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
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

    private static final AxisAlignedBB AREA = new SimpleAxisAlignedBB(8, 64, 8, 9, 65, 9);
    private static final AxisAlignedBB INSIDE = new SimpleAxisAlignedBB(8.1, 64.1, 8.1, 8.9, 64.9, 8.9);
    private static final AxisAlignedBB OUTSIDE = new SimpleAxisAlignedBB(32, 64, 32, 33, 65, 33);

    @Test
    void listAndIteratorMutationDoNotChangeIndexOrAnotherSnapshot() {
        SnapshotFixture f = new SnapshotFixture();
        EntityItem a = f.add(1, EntityItem.class), b = f.add(2, EntityItem.class), c = f.add(3, EntityItem.class);
        List<Entity> first = f.query(false), retained = f.query(false);
        assertNotSame(first, retained);
        assertEquals(List.of(a, b, c), first);
        Iterator<Entity> iterator = first.iterator();
        assertSame(a, iterator.next());
        iterator.remove();
        assertEquals(List.of(b, c), first);
        assertEquals(List.of(a, b, c), retained);
        first.set(0, a);
        first.add(c);
        first.remove(1);
        first.clear();
        assertEquals(List.of(a, b, c), retained);
        assertEquals(List.of(a, b, c), List.copyOf(f.entities.values()));
        assertEquals(List.of(a, b, c), f.query(false));
        verify(f.chunk, times(3)).getEntities();
    }

    @Test
    void trueUsesVirtualBoundsAndFalseUsesTheDirectField() {
        SnapshotFixture f = new SnapshotFixture();
        EntityItem item = f.add(1, EntityItem.class);
        when(item.getBoundingBox()).thenReturn(OUTSIDE);
        assertSame(Collections.emptyList(), f.query(true));
        assertEquals(List.of(item), f.query(false));
        verify(item).getBoundingBox();
    }

    @Test
    void reentrantGetterOwnsAnIndependentCompletedSnapshot() {
        SnapshotFixture f = new SnapshotFixture();
        EntityItem item = f.add(1, EntityItem.class);
        AtomicBoolean entered = new AtomicBoolean();
        AtomicReference<List<Entity>> inner = new AtomicReference<>();
        when(item.getBoundingBox()).thenAnswer(invocation -> {
            if (entered.compareAndSet(false, true)) inner.set(f.query(true));
            return INSIDE;
        });
        List<Entity> outer = f.query(true);
        assertEquals(List.of(item), outer);
        assertEquals(List.of(item), inner.get());
        assertNotSame(outer, inner.get());
        outer.clear();
        assertEquals(List.of(item), inner.get());
        assertSame(item, f.entities.get(1L));
        verify(item, times(2)).getBoundingBox();
        verify(f.chunk, times(2)).getEntities();
    }

    @Test
    void fourKindsKeepOrderAndNonItemFilteringPrecedesGetterCalls() {
        SnapshotFixture f = new SnapshotFixture();
        EntityItem item = f.add(1, EntityItem.class);
        EntityXPOrb xp = f.add(2, EntityXPOrb.class);
        EntityArrow arrow = f.add(3, EntityArrow.class);
        EntityThrownTrident trident = f.add(4, EntityThrownTrident.class);
        assertEquals(List.of(item, xp, arrow, trident), f.query(false));
        assertEquals(List.of(item), f.query(true));
        verify(item).getBoundingBox();
        verify(xp, never()).getBoundingBox();
        verify(arrow, never()).getBoundingBox();
        verify(trident, never()).getBoundingBox();
    }

    private static final class SnapshotFixture {
        final Level level = mock(Level.class);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final Map<Long, Entity> entities = new LinkedHashMap<>();

        SnapshotFixture() {
            when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
            when(chunk.hasPickupEntities(anyBoolean())).thenReturn(true);
            when(chunk.getEntities()).thenReturn(entities);
        }

        <T extends Entity> T add(long id, Class<T> type) {
            T entity = mock(type);
            entity.boundingBox = INSIDE;
            when(entity.getBoundingBox()).thenReturn(INSIDE);
            entities.put(id, entity);
            return entity;
        }

        List<Entity> query(boolean itemsOnly) { return CollisionHelper.getPickupEntities(level, AREA, itemsOnly); }
    }
}
