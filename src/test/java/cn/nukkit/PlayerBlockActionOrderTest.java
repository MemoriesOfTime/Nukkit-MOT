package cn.nukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.types.PlayerActionType;
import cn.nukkit.network.protocol.types.PlayerBlockActionData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A player who holds the dig button finishes one block and reaches for the next one inside a single
 * input tick. {@code PlayerAuthInputPacket} keeps the block actions of that tick in an
 * {@link EnumMap}, so they are replayed by {@link PlayerActionType} ordinal and the start of the
 * next block overtakes the completion of the current one. That order restarts the dig timer an
 * instant before the completion is judged, and the finished block is refused as a fast break.
 */
class PlayerBlockActionOrderTest {

    private static PlayerBlockActionData action(PlayerActionType type, int x, int y, int z) {
        return new PlayerBlockActionData(type, new BlockVector3(x, y, z), 1);
    }

    /** The tick as the packet hands it over: iteration follows the enum, not the wire. */
    private static Map<PlayerActionType, PlayerBlockActionData> tick(PlayerBlockActionData... actions) {
        Map<PlayerActionType, PlayerBlockActionData> map = new EnumMap<>(PlayerActionType.class);
        for (PlayerBlockActionData action : actions) {
            map.put(action.getAction(), action);
        }
        return map;
    }

    private static List<PlayerActionType> types(List<PlayerBlockActionData> actions) {
        List<PlayerActionType> types = new ArrayList<>(actions.size());
        for (PlayerBlockActionData action : actions) {
            types.add(action.getAction());
        }
        return types;
    }

    @Test
    @DisplayName("completion runs before the next block starts")
    void completionRunsBeforeTheNextStart() {
        Map<PlayerActionType, PlayerBlockActionData> tick = tick(
                action(PlayerActionType.PREDICT_DESTROY_BLOCK, 10, 64, 10),
                action(PlayerActionType.START_DESTROY_BLOCK, 11, 64, 10));

        // Without the fix the map alone would replay the start first: it is the first enum constant.
        assertEquals(
                List.of(PlayerActionType.START_DESTROY_BLOCK, PlayerActionType.PREDICT_DESTROY_BLOCK),
                types(new ArrayList<>(tick.values())));

        assertEquals(
                List.of(PlayerActionType.PREDICT_DESTROY_BLOCK, PlayerActionType.START_DESTROY_BLOCK),
                types(Player.orderBlockActions(tick.values())));
    }

    @Test
    @DisplayName("ending the old block runs before starting the new block")
    void abortRunsBeforeTheNextStart() {
        Map<PlayerActionType, PlayerBlockActionData> tick = tick(
                action(PlayerActionType.ABORT_DESTROY_BLOCK, 10, 64, 10),
                action(PlayerActionType.STOP_DESTROY_BLOCK, 10, 64, 10),
                action(PlayerActionType.START_DESTROY_BLOCK, 11, 64, 10),
                action(PlayerActionType.CONTINUE_DESTROY_BLOCK, 11, 64, 10));

        assertEquals(
                List.of(
                        PlayerActionType.ABORT_DESTROY_BLOCK,
                        PlayerActionType.STOP_DESTROY_BLOCK,
                        PlayerActionType.START_DESTROY_BLOCK,
                        PlayerActionType.CONTINUE_DESTROY_BLOCK),
                types(Player.orderBlockActions(tick.values())));
    }

    @Test
    @DisplayName("actions within the same group keep their order")
    void theSortIsStableInsideAGroup() {
        Map<PlayerActionType, PlayerBlockActionData> tick = tick(
                action(PlayerActionType.START_DESTROY_BLOCK, 1, 64, 1),
                action(PlayerActionType.CONTINUE_DESTROY_BLOCK, 1, 64, 1));

        assertEquals(
                List.of(PlayerActionType.START_DESTROY_BLOCK, PlayerActionType.CONTINUE_DESTROY_BLOCK),
                types(Player.orderBlockActions(tick.values())));
    }

    @Test
    @DisplayName("a single action is returned unchanged")
    void aSingleActionIsReturnedAsIs() {
        Map<PlayerActionType, PlayerBlockActionData> tick =
                tick(action(PlayerActionType.CONTINUE_DESTROY_BLOCK, 1, 64, 1));

        assertEquals(
                List.of(PlayerActionType.CONTINUE_DESTROY_BLOCK),
                types(Player.orderBlockActions(tick.values())));
    }

    @Test
    @DisplayName("only destruction-ending actions are classified as endings")
    void onlyDestructionEndingActionsCount() {
        assertTrue(Player.endsBlockDestruction(PlayerActionType.PREDICT_DESTROY_BLOCK));
        assertTrue(Player.endsBlockDestruction(PlayerActionType.ABORT_DESTROY_BLOCK));
        assertTrue(Player.endsBlockDestruction(PlayerActionType.STOP_DESTROY_BLOCK));

        assertFalse(Player.endsBlockDestruction(PlayerActionType.START_DESTROY_BLOCK));
        assertFalse(Player.endsBlockDestruction(PlayerActionType.CONTINUE_DESTROY_BLOCK));
        assertFalse(Player.endsBlockDestruction(PlayerActionType.CRACK_BLOCK));
    }
}
