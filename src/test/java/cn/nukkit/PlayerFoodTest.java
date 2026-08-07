package cn.nukkit;

import cn.nukkit.event.entity.EntityRegainHealthEvent;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 玩家食物系统（{@link PlayerFood}）单元测试。
 * <p>
 * 覆盖以下核心机制：
 * <ul>
 *     <li>Exhaustion 阈值触发与溢出保留</li>
 *     <li>自然生命恢复条件（食物等级 ≥ 18）</li>
 *     <li>和平模式下食物系统不重复触发恢复</li>
 * </ul>
 */
class PlayerFoodTest {

    private static Server originalServer;
    private static Server serverMock;

    @BeforeAll
    static void initServer() throws Exception {
        Field instanceField = Server.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        originalServer = (Server) instanceField.get(null);

        serverMock = mock(Server.class);
        PluginManager pluginManagerMock = mock(PluginManager.class);
        when(serverMock.getPluginManager()).thenReturn(pluginManagerMock);
        when(serverMock.getDifficulty()).thenReturn(2);

        instanceField.set(null, serverMock);
    }

    @AfterAll
    static void restoreServer() throws Exception {
        Field instanceField = Server.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, originalServer);
    }

    @Test
    void updateFoodExpLevelConsumesAtExactThreshold() {
        PlayerFood food = new PlayerFood(createPlayer(), 20, 20);

        food.updateFoodExpLevel(4);

        assertEquals(19f, food.getFoodSaturationLevel(), 0.0001f);
    }

    @Test
    void updateFoodExpLevelRetainsOverflow() {
        PlayerFood food = new PlayerFood(createPlayer(), 20, 20);

        food.updateFoodExpLevel(6);
        assertEquals(19f, food.getFoodSaturationLevel(), 0.0001f);

        food.updateFoodExpLevel(2);
        assertEquals(18f, food.getFoodSaturationLevel(), 0.0001f);
    }

    @Test
    void updateFoodExpLevelHandlesMultipleThresholdCrossings() {
        PlayerFood food = new PlayerFood(createPlayer(), 20, 20);

        food.updateFoodExpLevel(10);

        assertEquals(18f, food.getFoodSaturationLevel(), 0.0001f);
    }

    @Test
    void naturalRegenerationDoesNotTriggerBelowFoodLevel18EvenWithSaturation() {
        Player player = createPlayer();
        when(player.getHealth()).thenReturn(10f);
        when(player.getMaxHealth()).thenReturn(20);
        PlayerFood food = new PlayerFood(player, 17, 5);

        food.update(80);

        verify(player, never()).heal(any(EntityRegainHealthEvent.class));
    }

    @Test
    void naturalRegenerationDoesNotTriggerWithoutFoodOrSaturation() {
        Player player = createPlayer();
        when(player.getHealth()).thenReturn(10f);
        when(player.getMaxHealth()).thenReturn(20);
        PlayerFood food = new PlayerFood(player, 17, 0);

        food.update(80);

        verify(player, never()).heal(any(EntityRegainHealthEvent.class));
    }

    @Test
    void naturalRegenerationTriggersAtFoodLevel18() {
        Player player = createPlayer();
        when(player.getHealth()).thenReturn(10f);
        when(player.getMaxHealth()).thenReturn(20);
        PlayerFood food = new PlayerFood(player, 18, 0);

        food.update(80);

        verify(player).heal(any(EntityRegainHealthEvent.class));
    }

    @Test
    void peacefulModeSkipsFoodUpdate() {
        when(serverMock.getDifficulty()).thenReturn(0);
        try {
            Player player = createPlayer();
            when(player.getHealth()).thenReturn(10f);
            when(player.getMaxHealth()).thenReturn(20);
            PlayerFood food = new PlayerFood(player, 20, 20);

            food.update(80);

            verify(player, never()).heal(any(EntityRegainHealthEvent.class));
        } finally {
            when(serverMock.getDifficulty()).thenReturn(2);
        }
    }

    private static Player createPlayer() {
        Player player = mock(Player.class);
        Level level = mock(Level.class);

        when(player.isFoodEnabled()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);
        when(player.getServer()).thenReturn(serverMock);
        when(player.getLevel()).thenReturn(level);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());

        return player;
    }
}
