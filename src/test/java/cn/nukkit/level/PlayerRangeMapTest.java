package cn.nukkit.level;

import cn.nukkit.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
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
}
