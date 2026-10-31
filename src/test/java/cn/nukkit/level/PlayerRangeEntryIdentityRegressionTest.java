package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PlayerRangeEntryIdentityRegressionTest {
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
