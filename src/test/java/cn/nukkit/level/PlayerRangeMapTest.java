package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PlayerRangeMapTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void everyPublicMutationRefreshesMembership(String name, BiConsumer<Map<Long, Player>, Player> mutation) {
        PlayerRangeMap players = new PlayerRangeMap();
        players.put(0L, mock(Player.class));
        players.put(1L, mock(Player.class));
        players.beginTick();
        try {
            players.tickSnapshot();
            mutation.accept(players, mock(Player.class));
            assertArrayEquals(players.values().toArray(Player.EMPTY_ARRAY), players.tickSnapshot());
        } finally {
            players.endTick();
        }
    }

    static Stream<Arguments> mutations() {
        return Stream.of(
                mutation("put", (m, p) -> m.put(0L, p)),
                mutation("putAll", (m, p) -> m.putAll(Map.of(0L, p, 2L, p))),
                mutation("putIfAbsent", (m, p) -> m.putIfAbsent(2L, p)),
                mutation("remove", (m, p) -> m.remove(0L)),
                mutation("conditional remove", (m, p) -> m.remove(0L, m.get(0L))),
                mutation("replace", (m, p) -> m.replace(0L, p)),
                mutation("conditional replace", (m, p) -> m.replace(0L, m.get(0L), p)),
                mutation("replaceAll", (m, p) -> m.replaceAll((k, v) -> p)),
                mutation("compute", (m, p) -> m.compute(0L, (k, v) -> p)),
                mutation("compute removal", (m, p) -> m.compute(0L, (k, v) -> null)),
                mutation("computeIfAbsent", (m, p) -> m.computeIfAbsent(2L, k -> p)),
                mutation("computeIfPresent", (m, p) -> m.computeIfPresent(0L, (k, v) -> p)),
                mutation("merge", (m, p) -> m.merge(0L, p, (old, value) -> value)),
                mutation("clear", (m, p) -> m.clear()),
                mutation("key remove", (m, p) -> m.keySet().remove(0L)),
                mutation("key iterator", (m, p) -> { var it = m.keySet().iterator(); it.next(); it.remove(); }),
                mutation("key retainAll", (m, p) -> m.keySet().retainAll(List.of(1L))),
                mutation("key removeAll", (m, p) -> m.keySet().removeAll(List.of(0L))),
                mutation("key clear", (m, p) -> m.keySet().clear()),
                mutation("value remove", (m, p) -> m.values().remove(m.get(0L))),
                mutation("value iterator", (m, p) -> { var it = m.values().iterator(); it.next(); it.remove(); }),
                mutation("value removeIf", (m, p) -> m.values().removeIf(v -> true)),
                mutation("value retainAll", (m, p) -> m.values().retainAll(List.of(m.get(1L)))),
                mutation("value clear", (m, p) -> m.values().clear()),
                mutation("entry remove", (m, p) -> m.entrySet().remove(new AbstractMap.SimpleEntry<>(0L, m.get(0L)))),
                mutation("entry iterator", (m, p) -> { var it = m.entrySet().iterator(); it.next(); it.remove(); }),
                mutation("entry setValue", (m, p) -> m.entrySet().iterator().next().setValue(p)),
                mutation("entry array setValue", (m, p) -> ((Map.Entry<Long, Player>) m.entrySet().toArray()[0]).setValue(p)),
                mutation("entry stream setValue", (m, p) -> m.entrySet().stream().findFirst().orElseThrow().setValue(p)),
                mutation("entry clear", (m, p) -> m.entrySet().clear())
        );
    }

    private static Arguments mutation(String name, BiConsumer<Map<Long, Player>, Player> operation) {
        return Arguments.of(name, operation);
    }

    @Test
    void unchangedMembershipSharesOneReferenceSnapshotOnlyInsideTick() {
        PlayerRangeMap players = new PlayerRangeMap();
        Player player = mock(Player.class);
        players.put(1L, player);
        assertNull(players.tickSnapshot());
        players.beginTick();
        try {
            Player[] first = players.tickSnapshot();
            assertArrayEquals(new Player[]{player}, first);
            player.x = 150;
            assertSame(first, players.tickSnapshot(), "position changes must not rebuild membership");
            assertEquals(150, first[0].x);
        } finally {
            players.endTick();
        }
        assertNull(players.tickSnapshot());
    }

    @Test
    void entryMustEqualItselfAndBeRemovableFromEntrySetSnapshot() {
        Player player = mock(Player.class);
        Long2ObjectNonBlockingMap<Player> nativeMap = new Long2ObjectNonBlockingMap<>();
        PlayerRangeMap trackedMap = new PlayerRangeMap();
        nativeMap.put(1L, player);
        trackedMap.put(1L, player);

        ArrayList<Map.Entry<Long, Player>> nativeSnapshot = new ArrayList<>(nativeMap.entrySet());
        ArrayList<Map.Entry<Long, Player>> trackedSnapshot = new ArrayList<>(trackedMap.entrySet());
        assertEquals(1, nativeSnapshot.size());
        assertEquals(1, trackedSnapshot.size());
        Map.Entry<Long, Player> nativeEntry = nativeSnapshot.get(0);
        Map.Entry<Long, Player> trackedEntry = trackedSnapshot.get(0);
        boolean nativeSelf = nativeEntry.equals(nativeEntry);
        boolean nativeRemoved = nativeSnapshot.remove(nativeEntry);
        boolean trackedSelf = trackedEntry.equals(trackedEntry);
        boolean trackedRemoved = trackedSnapshot.remove(trackedEntry);
        System.out.printf("ENTRY_IDENTITY native=%s/%s wrapper=%s/%s%n",
                nativeSelf, nativeRemoved, trackedSelf, trackedRemoved);

        // Removing a held entry from a detached list must not remove the live map member.
        assertSame(player, nativeMap.get(1L));
        assertSame(player, trackedMap.get(1L));
        assertAll("entry identity and detached entry-set snapshot",
                () -> assertTrue(nativeSelf, "native entry.equals(entry) control"),
                () -> assertTrue(nativeRemoved, "native ArrayList.remove(exact entry) control"),
                () -> assertTrue(trackedSelf, "entry.equals(entry) must be reflexive"),
                () -> assertTrue(trackedRemoved, "ArrayList.remove(exact returned wrapper) must succeed"));
    }

    @Test
    void distinctEntriesKeepNativeIdentityBoundary() {
        Player player = mock(Player.class);
        Long2ObjectNonBlockingMap<Player> nativeMap = new Long2ObjectNonBlockingMap<>();
        PlayerRangeMap trackedMap = new PlayerRangeMap();
        nativeMap.put(1L, player);
        trackedMap.put(1L, player);
        Map.Entry<Long, Player> nativeFirst = nativeMap.entrySet().iterator().next();
        Map.Entry<Long, Player> nativeSecond = nativeMap.entrySet().iterator().next();
        Map.Entry<Long, Player> trackedFirst = trackedMap.entrySet().iterator().next();
        Map.Entry<Long, Player> trackedSecond = trackedMap.entrySet().iterator().next();
        Map.Entry<Long, Player> samePair = new AbstractMap.SimpleImmutableEntry<>(1L, player);

        assertNotSame(nativeFirst, nativeSecond);
        assertNotSame(trackedFirst, trackedSecond);
        assertEquals(nativeFirst.getKey(), trackedFirst.getKey());
        assertSame(nativeFirst.getValue(), trackedFirst.getValue());
        assertFalse(nativeFirst.equals(nativeSecond));
        assertFalse(nativeSecond.equals(nativeFirst));
        assertFalse(trackedFirst.equals(trackedSecond));
        assertFalse(trackedSecond.equals(trackedFirst));
        assertFalse(nativeFirst.equals(samePair));
        assertFalse(trackedFirst.equals(samePair));
        assertFalse(trackedFirst.equals(nativeFirst));
        assertFalse(nativeFirst.equals(trackedFirst));
        assertFalse(trackedFirst.equals(null));
        assertFalse(trackedFirst.equals("not an entry"));
    }

    @Test
    void entryMutationAndRemovalKeepLiveMembershipAndSnapshotInvalidation() {
        Player first = mock(Player.class);
        Player replacement = mock(Player.class);
        PlayerRangeMap players = new PlayerRangeMap();
        players.put(1L, first);
        players.beginTick();
        try {
            assertSame(first, players.tickSnapshot()[0]);
            Map.Entry<Long, Player> entry = players.entrySet().iterator().next();
            assertSame(first, entry.setValue(replacement));
            assertSame(replacement, players.get(1L));
            assertSame(replacement, players.tickSnapshot()[0]);

            // Native entrySet removal matches the pair even though entry.equals stays identity-based.
            Map.Entry<Long, Player> pair = new AbstractMap.SimpleImmutableEntry<>(1L, replacement);
            assertTrue(players.entrySet().contains(pair));
            assertTrue(players.entrySet().remove(pair));
            assertTrue(players.isEmpty());
            assertEquals(0, players.tickSnapshot().length);

            players.put(2L, first);
            assertSame(first, players.tickSnapshot()[0]);
            Iterator<Map.Entry<Long, Player>> iterator = players.entrySet().iterator();
            assertSame(first, iterator.next().getValue());
            iterator.remove();
            assertTrue(players.isEmpty());
            assertEquals(0, players.tickSnapshot().length);
        } finally {
            players.endTick();
        }
        assertNull(players.tickSnapshot());
    }
}
