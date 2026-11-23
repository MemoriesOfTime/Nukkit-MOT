package cn.nukkit.level.format.generic;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkPlayerTargetIndexTest {
    @BeforeAll static void initialize() { MockServer.init(); }

    @Test
    void snapshotMatchesCompleteMapPlayerOrderAndReusesUntilEveryMutation() {
        Long2ObjectNonBlockingMap<Entity> backing = new Long2ObjectNonBlockingMap<>();
        PickupEntityIndex index = new PickupEntityIndex(backing);
        Player first = mock(Player.class), second = mock(Player.class);
        Entity mob = mock(Entity.class);
        index.put(61L, first);
        index.put(1039L, second);
        for (long i = 0; i < 150; i++) index.put(5000L + i, mob);
        assertOrder(index, backing);
        List<Player> snapshot = index.playersInIterationOrder();
        assertSame(snapshot, index.playersInIterationOrder());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.set(0, second));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(0));
        assertOrder(index, backing);
        assertSame(snapshot, index.playersInIterationOrder());
        index.put(999L, mob);
        assertNotSame(snapshot, index.playersInIterationOrder(), "non-player writes can rehash and reorder players");
        assertOrder(index, backing);
        index.replace(61L, mob);
        assertOrder(index, backing);
        assertEquals(List.of(second), index.playersInIterationOrder());
        index.entrySet().stream().filter(e -> e.getKey() == 61L).findFirst().orElseThrow().setValue(first);
        assertOrder(index, backing);
        index.values().remove(second);
        assertEquals(List.of(first), index.playersInIterationOrder());
        index.compute(61L, (key, old) -> second);
        assertEquals(List.of(second), index.playersInIterationOrder());
        index.keySet().remove(61L);
        assertEquals(0, index.playersInIterationOrder().size());
        index.putAll(Map.of(7L, first));
        assertEquals(List.of(first), index.playersInIterationOrder());
        index.clear();
        assertEquals(0, index.playersInIterationOrder().size());
    }

    @Test
    void inFlightMutationRequestsCompleteQueryInsteadOfStaleSnapshot() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Long2ObjectNonBlockingMap<Entity> backing = new Long2ObjectNonBlockingMap<>() {
            @Override public Entity put(Long key, Entity value) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return super.put(key, value);
            }
        };
        PickupEntityIndex index = new PickupEntityIndex(backing);
        assertEquals(0, index.playersInIterationOrder().size());
        Player player = mock(Player.class);
        CompletableFuture<Entity> writer = CompletableFuture.supplyAsync(() -> index.put(1L, player));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNull(index.playersInIterationOrder());
        } finally { release.countDown(); }
        writer.get(5, TimeUnit.SECONDS);
        assertEquals(List.of(player), index.playersInIterationOrder());
    }

    private static void assertOrder(PickupEntityIndex index, Long2ObjectNonBlockingMap<Entity> backing) {
        ArrayList<Player> expected = new ArrayList<>();
        for (Entity entity : backing.values()) if (entity instanceof Player player) expected.add(player);
        assertEquals(expected, index.playersInIterationOrder());
    }
}
