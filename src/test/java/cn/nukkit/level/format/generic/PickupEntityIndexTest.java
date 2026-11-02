package cn.nukkit.level.format.generic;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickupEntityIndexTest {
    @BeforeAll
    static void initializeServer() { MockServer.init(); }

    @Test
    void absentKindsAreCachedUntilMembershipChangesEvenAtTheSameSize() {
        Long2ObjectNonBlockingMap<Entity> backing = spy(new Long2ObjectNonBlockingMap<>());
        PickupEntityIndex index = new PickupEntityIndex(backing);
        Entity mob = mock(Entity.class);
        EntityItem item = mock(EntityItem.class);
        index.put(1L, mob);
        clearInvocations(backing);
        assertFalse(index.hasPickupEntities(false));
        assertFalse(index.hasPickupEntities(true));
        assertFalse(index.hasPickupEntities(false));
        verify(backing, times(1)).values();

        index.put(1L, item);
        assertTrue(index.hasPickupEntities(true));
        index.replace(1L, mob);
        assertFalse(index.hasPickupEntities(false));
        assertTrue(index.replace(1L, mob, item));
        assertTrue(index.hasPickupEntities(true));
        assertTrue(index.remove(1L, item));
        assertFalse(index.hasPickupEntities(false));
    }

    @Test
    void defaultComputeMergeReplaceAllAndBulkMutationsInvalidatePresence() {
        PickupEntityIndex index = new PickupEntityIndex(new Long2ObjectNonBlockingMap<>());
        Entity mob = mock(Entity.class);
        EntityItem item = mock(EntityItem.class);
        assertFalse(index.hasPickupEntities(false));
        index.computeIfAbsent(1L, key -> item);
        assertTrue(index.hasPickupEntities(true));
        index.computeIfPresent(1L, (key, entity) -> mob);
        assertFalse(index.hasPickupEntities(false));
        index.compute(1L, (key, entity) -> item);
        assertTrue(index.hasPickupEntities(true));
        index.merge(1L, mob, (oldValue, newValue) -> newValue);
        assertFalse(index.hasPickupEntities(false));
        index.replaceAll((key, entity) -> item);
        assertTrue(index.hasPickupEntities(true));
        index.clear();
        assertFalse(index.hasPickupEntities(false));
        index.putAll(Map.of(1L, item));
        assertTrue(index.hasPickupEntities(true));
        index.putIfAbsent(2L, mob);
        index.remove(1L);
        assertFalse(index.hasPickupEntities(false));
    }

    @Test
    void mutableViewsAndIteratorRemovalInvalidatePresence() {
        PickupEntityIndex index = new PickupEntityIndex(new Long2ObjectNonBlockingMap<>());
        Entity mob = mock(Entity.class);
        EntityItem item = mock(EntityItem.class);
        index.put(1L, mob);
        assertFalse(index.hasPickupEntities(false));
        index.entrySet().iterator().next().setValue(item);
        assertTrue(index.hasPickupEntities(true));
        index.values().removeIf(entity -> entity == item);
        assertFalse(index.hasPickupEntities(false));
        index.put(1L, item);
        assertTrue(index.hasPickupEntities(true));
        Iterator<Long> keys = index.keySet().iterator();
        keys.next();
        keys.remove();
        assertFalse(index.hasPickupEntities(false));
        index.put(1L, item);
        assertTrue(index.hasPickupEntities(true));
        assertTrue(index.entrySet().remove(Map.entry(1L, item)));
        assertFalse(index.hasPickupEntities(false));
        index.put(1L, item);
        assertTrue(index.hasPickupEntities(true));
        index.values().clear();
        assertFalse(index.hasPickupEntities(false));
    }

    @Test
    void concurrentInFlightMutationNeverReportsDefinitelyEmpty() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Long2ObjectNonBlockingMap<Entity> backing = new Long2ObjectNonBlockingMap<>() {
            @Override public Entity put(Long key, Entity value) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return super.put(key, value);
            }
        };
        PickupEntityIndex index = new PickupEntityIndex(backing);
        assertFalse(index.hasPickupEntities(true));
        EntityItem item = mock(EntityItem.class);
        CompletableFuture<Entity> writer = CompletableFuture.supplyAsync(() -> index.put(1L, item));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(index.hasPickupEntities(true));
        } finally { release.countDown(); }
        writer.get(5, TimeUnit.SECONDS);
        assertTrue(index.hasPickupEntities(true));
    }

    @Test
    void chunkLifecyclePublicMapCloneAndReplacementKeepPresenceCorrect() {
        TestChunk first = new TestChunk();
        TestChunk second = new TestChunk();
        EntityXPOrb orb = mock(EntityXPOrb.class);
        when(orb.getId()).thenReturn(7L);
        EntityItem item = mock(EntityItem.class);
        when(item.getId()).thenReturn(8L);
        assertFalse(first.hasPickupEntities(false));
        first.addEntity(orb);
        first.addEntity(orb);
        assertTrue(first.hasPickupEntities(false));
        assertFalse(first.hasPickupEntities(true));
        first.removeEntity(orb);
        second.addEntity(orb);
        assertFalse(first.hasPickupEntities(false));
        assertTrue(second.hasPickupEntities(false));
        first.addEntity(item);
        BaseFullChunk liveClone = first.clone();
        assertTrue(liveClone.hasPickupEntities(true));
        first.getEntities().replace(8L, orb);
        assertFalse(liveClone.hasPickupEntities(true));
        assertTrue(liveClone.hasPickupEntities(false));
        first.getEntities().keySet().remove(8L);
        assertFalse(first.hasPickupEntities(false));
        first.replaceBacking(Map.of(8L, item));
        assertTrue(first.hasPickupEntities(true), "converter/reload backing replacement is reindexed");
        BaseFullChunk sendingClone = first.forSending();
        assertFalse(sendingClone.hasPickupEntities(false));
    }

    private static class TestChunk extends LevelDBChunk {
        TestChunk() {
            super(null, 0, 0);
            for (int i = 0; i < this.sections.length; i++) {
                if (this.sections[i] == null) this.sections[i] = EmptyChunkSection.bySectionY(i);
            }
        }
        void replaceBacking(Map<Long, Entity> map) {
            this.entities = new Long2ObjectNonBlockingMap<>();
            this.entities.putAll(map);
        }
        BaseFullChunk forSending() { return cloneForChunkSending(); }
    }
}
