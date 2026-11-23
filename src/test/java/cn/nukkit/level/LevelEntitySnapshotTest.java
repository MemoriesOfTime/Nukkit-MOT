package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.CollisionHelper;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelEntitySnapshotTest {
    @BeforeAll
    static void initializeServer() { MockServer.init(); }

    private static final class EqualIdEntity extends Entity {
        Runnable update = () -> {};

        EqualIdEntity(long id, FullChunk chunk) {
            super(chunk, Entity.getDefaultNBT(new Vector3(0, 64, 0)));
            this.id = id;
        }

        @Override public int getNetworkId() { return 1; }
        @Override protected void initEntity() {}
        @Override public boolean onUpdate(int tick) { update.run(); return false; }
    }

    @Test
    void oldCallbackCannotRemoveAnEqualButDifferentEntityWithTheSameId() throws Exception {
        Level fixture = mock(Level.class);
        field(fixture, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
        when(fixture.getServer()).thenReturn(MockServer.get());
        when(fixture.getGameRules()).thenReturn(GameRules.getDefault());
        when(fixture.getChunkPlayers(0, 0)).thenReturn(Map.of());
        fixture.isBeingConverted = true;
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(fixture);
        FullChunk chunk = mock(FullChunk.class);
        when(chunk.getProvider()).thenReturn(provider);
        Entity old = new EqualIdEntity(7, chunk);
        Entity replacement = new EqualIdEntity(7, chunk);
        assertEquals(old, replacement, "exercise Entity.equals rather than Mockito identity equality");
        assertNotSame(old, replacement);
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        var scheduled = new Long2ObjectNonBlockingMap<Entity>();
        field(level, "updateEntities", scheduled);
        scheduled.put(7L, old);
        ((EqualIdEntity) old).update = () -> scheduled.put(7L, replacement);
        level.tickEntities(20);
        assertSame(replacement, scheduled.get(7L));
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Entity entity(long id, Level level, FullChunk chunk) {
        Entity entity = mock(Entity.class);
        when(entity.getId()).thenReturn(id);
        entity.level = level;
        entity.chunk = chunk;
        entity.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);
        when(entity.getBoundingBox()).thenReturn(entity.boundingBox);
        return entity;
    }

    @Test
    void chunkSnapshotTracksExternalMapMutationAndMovementWithoutExposingMutableStorage() {
        BaseFullChunk first = mock(BaseFullChunk.class, CALLS_REAL_METHODS);
        BaseFullChunk second = mock(BaseFullChunk.class, CALLS_REAL_METHODS);
        Entity entity = entity(1, null, first);
        first.addEntity(entity);
        List<Entity> old = first.getEntitySnapshot();
        assertSame(old, first.getEntitySnapshot());
        assertThrows(UnsupportedOperationException.class, old::clear);
        first.getEntities().values().remove(entity);
        entity.chunk = second;
        second.addEntity(entity);
        assertTrue(first.getEntitySnapshot().isEmpty());
        assertEquals(List.of(entity), second.getEntitySnapshot());
        assertEquals(List.of(entity), old, "old memberships are immutable, state references remain live");
        entity.closed = true;
        assertTrue(old.get(0).closed);
        second.removeEntity(entity);
        assertTrue(second.getEntitySnapshot().isEmpty());
    }

    @Test
    void chunkSnapshotAndSchedulingHonorAnOverriddenEntityMap() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        BaseFullChunk chunk = mock(BaseFullChunk.class, CALLS_REAL_METHODS);
        Entity entity = entity(19, level, chunk);
        doReturn(Map.of(19L, entity)).when(chunk).getEntities();
        assertEquals(List.of(entity), chunk.getEntitySnapshot());
        level.scheduleChunkEntities(chunk);
        verify(entity).scheduleUpdate();
    }

    @Test
    void getEntitiesKeepsIndependentArrayContractAndReflectsReplacements() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        var entities = new Long2ObjectNonBlockingMap<Entity>();
        field(level, "entities", entities);
        Entity original = entity(1, level, null);
        entities.put(1L, original);
        Entity[] first = level.getEntities();
        first[0] = null;
        assertSame(original, level.getEntities()[0]);
        Entity replacement = entity(1, level, null);
        entities.put(1L, replacement);
        assertSame(replacement, level.getEntities()[0]);
    }

    @Test
    void scheduledTickSkipsRemovedSnapshotMembersAndKeepsCallbackReplacement() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        var scheduled = new Long2ObjectNonBlockingMap<Entity>();
        field(level, "updateEntities", scheduled);
        scheduled.put(1L, entity(1, level, null));
        scheduled.put(2L, entity(2, level, null));
        List<Entity> order = scheduled.valuesSnapshot();
        Entity first = order.get(0);
        Entity removed = order.get(1);
        Entity replacement = entity(first.getId(), level, null);
        when(first.onUpdate(20)).thenAnswer(invocation -> {
            scheduled.remove(removed.getId());
            scheduled.put(first.getId(), replacement);
            return false;
        });
        when(replacement.onUpdate(21)).thenReturn(true);
        level.tickEntities(20);
        verify(removed, never()).onUpdate(anyInt());
        assertSame(replacement, scheduled.get(first.getId()), "old callback must not remove replacement");
        level.tickEntities(21);
        verify(replacement).onUpdate(21);
    }

    @Test
    void chunkSchedulingSkipsClosedMovedAndWrongWorldMembersAfterEarlierCallback() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        FullChunk chunk = mock(FullChunk.class);
        Entity first = entity(1, level, chunk);
        Entity closed = entity(2, level, chunk);
        Entity moved = entity(3, level, chunk);
        Entity changedWorld = entity(4, level, chunk);
        when(chunk.getEntitySnapshot()).thenReturn(List.of(first, closed, moved, changedWorld));
        doAnswer(invocation -> {
            closed.closed = true;
            moved.chunk = mock(FullChunk.class);
            changedWorld.level = mock(Level.class);
            return null;
        }).when(first).scheduleUpdate();
        level.scheduleChunkEntities(chunk);
        verify(first).scheduleUpdate();
        verify(closed, never()).scheduleUpdate();
        verify(moved, never()).scheduleUpdate();
        verify(changedWorld, never()).scheduleUpdate();
    }

    @Test
    void bothSpatialQueriesUseCurrentChunkMembershipAndPreserveLoadedOnlyLookup() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        var entities = new Long2ObjectNonBlockingMap<Entity>();
        Entity entity = entity(1, level, null);
        entities.put(1L, entity);
        doReturn(Map.of()).when(level).getChunkEntities(anyInt(), anyInt(), anyBoolean());
        doReturn(entities).when(level).getChunkEntities(0, 0, false);
        var bounds = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);
        assertArrayEquals(new Entity[]{entity}, level.getNearbyEntities(bounds));
        assertEquals(List.of(entity), CollisionHelper.getCollidingEntities(level, bounds));
        entities.remove(1L);
        assertEquals(0, level.getNearbyEntities(bounds).length);
        assertTrue(CollisionHelper.getCollidingEntities(level, bounds).isEmpty());
        verify(level, never()).getChunkEntities(anyInt(), anyInt(), eq(true));
    }
}
