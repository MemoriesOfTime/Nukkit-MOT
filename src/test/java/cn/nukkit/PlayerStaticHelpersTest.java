package cn.nukkit;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.protocol.types.PlayerActionType;
import cn.nukkit.network.protocol.types.PlayerBlockActionData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 {@link Player} 的纯静态辅助方法：长枪戳刺冷却、近期速度、登录名修剪与方块动作排序。
 * <p>
 * Covers {@link Player}'s pure static helpers: spear jab cool down, recent velocity, verified login name trimming, and block action ordering.
 */
class PlayerStaticHelpersTest {

    @Test
    void firstJabIsAlwaysAccepted() {
        assertTrue(Player.isSpearJabReady(Integer.MIN_VALUE, 0, 19));
        assertTrue(Player.isSpearJabReady(Integer.MIN_VALUE, Integer.MAX_VALUE, 19));
    }

    @Test
    void jabInsideCoolDownIsRefused() {
        assertFalse(Player.isSpearJabReady(100, 100, 19));
        assertFalse(Player.isSpearJabReady(100, 115, 19));
    }

    @Test
    void jabAfterCoolDownWithJitterIsAccepted() {
        assertTrue(Player.isSpearJabReady(100, 116, 19));
        assertTrue(Player.isSpearJabReady(100, 119, 19));
    }

    @Test
    void freshStepIsTheVelocityPerTick() {
        // speed holds from - to: a step of +0.56 on z over two ticks.
        Vector3 velocity = Player.recentVelocity(new Vector3(0, 0, -0.56), 100, 2, 101);
        assertEquals(0.28, velocity.z, 1e-9);
        assertEquals(0.0, velocity.x, 1e-9);
    }

    @Test
    void staleStepIsStandingStill() {
        Vector3 velocity = Player.recentVelocity(new Vector3(0, 0, -0.28), 100, 1, 103);
        assertEquals(0.0, velocity.lengthSquared(), 1e-12);
    }

    @Test
    void noMovementYetIsStandingStill() {
        assertEquals(0.0, Player.recentVelocity(null, 100, 1, 100).lengthSquared(), 1e-12);
        assertEquals(0.0, Player.recentVelocity(new Vector3(0, 0, -0.28), -1, 1, 5).lengthSquared(), 1e-12);
    }

    @Test
    void paddedNameIsTrimmedButInnerSpacesStay() {
        assertEquals("Steve alex", Player.verifiedLoginName("Steve alex "));
        assertEquals("Steve alex", Player.verifiedLoginName(" Steve alex"));
        assertEquals("STEVE", Player.verifiedLoginName("  STEVE"));
        assertEquals("Steve  alex", Player.verifiedLoginName("Steve  alex "));
        assertEquals("Name", Player.verifiedLoginName("§aName §r"));
        assertNull(Player.verifiedLoginName(null));
    }

    @Test
    void paddedAndTrimmedNamesDeriveTheSameOfflineIdentity() {
        assertEquals(EncryptionUtils.deriveOfflineIdentity("Name"),
                EncryptionUtils.deriveOfflineIdentity(Player.verifiedLoginName("Name ")));
        assertNotEquals(EncryptionUtils.deriveOfflineIdentity("Steve alex"),
                EncryptionUtils.deriveOfflineIdentity(Player.verifiedLoginName("Steve  alex")),
                "a space inside the name is part of it");
    }

    @Test
    void paddingNoLongerHidesReservedNames() {
        assertEquals("rcon", Player.verifiedLoginName("rcon "));
        assertEquals("console", Player.verifiedLoginName(" console"));
    }

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
