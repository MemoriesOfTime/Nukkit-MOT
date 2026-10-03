package cn.nukkit;

import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.data.EntityMovementSpeedModifier;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.UpdateAttributesPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.*;

/**
 * 玩家基础移动速度与修饰符叠加的回归测试（issue #836）。
 * <p>
 * Regression tests for player base movement speed with modifiers stacked on top (issue #836).
 */
class PlayerMovementSpeedTest {

    private Player player;

    @BeforeAll
    static void init() {
        MockServer.init();
        Attribute.init();
    }

    @BeforeEach
    void setUp() throws Exception {
        player = mock(Player.class, CALLS_REAL_METHODS);
        // Mockito 跳过字段初始化器，须手动注入 / Mockito skips field initializers; inject manually
        setField(Entity.class, player, "server", MockServer.get());
        setField(EntityLiving.class, player, "movementSpeed", Player.DEFAULT_SPEED);
        setField(EntityLiving.class, player, "movementSpeedModifiers", new HashMap<>());
        setField(Player.class, player, "speedToSend", Player.DEFAULT_SPEED);
        player.spawned = false;
        doNothing().when(player).sendMovementSpeed();
    }

    @Test
    void setMovementSpeedTakesEffect() throws Exception {
        player.setMovementSpeed(0.0001f);

        assertEquals(0.0001f, player.getMovementSpeed(), 1e-6f);
        assertEquals(0.0001f, getSpeedToSend(), 1e-6f);
    }

    @Test
    void setMovementSpeedSendsOnlyWhenSpawned() {
        player.spawned = true;
        player.setMovementSpeed(0.2f, false);
        verify(player, never()).sendMovementSpeed();

        player.setMovementSpeed(0.2f, true);
        verify(player).sendMovementSpeed();
    }

    @Test
    void setMovementSpeedDoesNotSendBeforeSpawn() {
        // 未进入世界前仅更新值，join 时由 sendAttributes 统一下发
        // Before spawning only the value updates; join-time sendAttributes delivers it
        player.setMovementSpeed(0.2f, true);
        verify(player, never()).sendMovementSpeed();
        try {
            assertEquals(0.2f, getSpeedToSend(), 1e-6f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void modifiersApplyOnTopOfBaseSpeed() throws Exception {
        player.setMovementSpeed(0.05f);

        // 仅乘算修饰符，与 HashMap 迭代顺序无关 / MULTIPLY-only, independent of HashMap iteration order
        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.SPRINTING, 1.3f, EntityMovementSpeedModifier.Operation.MULTIPLY));
        assertEquals(0.065f, getSpeedToSend(), 1e-6f);

        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.EFFECT_SPEED, 1.2f, EntityMovementSpeedModifier.Operation.MULTIPLY));
        assertEquals(0.078f, getSpeedToSend(), 1e-6f);

        player.removeMovementSpeedModifier(EntityMovementSpeedModifier.SPRINTING);
        player.removeMovementSpeedModifier(EntityMovementSpeedModifier.EFFECT_SPEED);
        assertEquals(0.05f, getSpeedToSend(), 1e-6f);
    }

    @Test
    void addModifierSubtractsFromBaseSpeed() throws Exception {
        player.setMovementSpeed(0.1f);
        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.FREEZING, -0.05f, EntityMovementSpeedModifier.Operation.ADD));

        assertEquals(0.05f, getSpeedToSend(), 1e-6f);
    }

    @Test
    void baseSpeedSurvivesModifierRecalculation() throws Exception {
        // #562 回归点：修饰符增删曾把基础速度重置回 DEFAULT_SPEED
        // #562 regression: modifier add/remove used to reset base speed back to DEFAULT_SPEED
        player.setMovementSpeed(0.05f);

        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.EFFECT_SPEED, 1.2f, EntityMovementSpeedModifier.Operation.MULTIPLY));
        player.removeMovementSpeedModifier(EntityMovementSpeedModifier.EFFECT_SPEED);

        assertEquals(0.05f, player.getMovementSpeed(), 1e-6f);
        assertEquals(0.05f, getSpeedToSend(), 1e-6f);
    }

    @Test
    void directRecalculateDoesNotTouchBaseSpeed() throws Exception {
        player.setMovementSpeed(0.05f);
        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.SPRINTING, 1.3f, EntityMovementSpeedModifier.Operation.MULTIPLY));

        player.recalculateMovementSpeed();

        assertEquals(0.05f, player.getMovementSpeed(), 1e-6f);
        assertEquals(0.065f, getSpeedToSend(), 1e-6f);
    }

    @Test
    void unsendableModifierExcludedFromSentSpeed() throws Exception {
        // 潜行/爬行用 send=false：客户端本地已减速，服务端不计入
        // Sneaking/crawling use send=false: the client slows locally, server excludes them
        player.setMovementSpeed(0.1f);
        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.SNEAKING, 0.3f, EntityMovementSpeedModifier.Operation.MULTIPLY, false));

        assertEquals(0.1f, getSpeedToSend(), 1e-6f);
        assertEquals(0.1f, player.getMovementSpeed(), 1e-6f);
    }

    @Test
    void readdingModifierReplacesValue() throws Exception {
        // 冻结路径每 tick 重加同 id 修饰符，应替换而非累积
        // The freezing path re-adds the same id every tick; it must replace, not accumulate
        player.setMovementSpeed(0.1f);
        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.FREEZING, -0.01f, EntityMovementSpeedModifier.Operation.ADD));
        assertEquals(0.09f, getSpeedToSend(), 1e-6f);

        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.FREEZING, -0.05f, EntityMovementSpeedModifier.Operation.ADD));
        assertEquals(0.05f, getSpeedToSend(), 1e-6f);
    }

    @Test
    void addModifierClampsToZero() throws Exception {
        player.setMovementSpeed(0.1f);
        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.FREEZING, -1.0f, EntityMovementSpeedModifier.Operation.ADD));

        assertEquals(0.0f, getSpeedToSend(), 1e-6f);
        assertEquals(0.1f, player.getMovementSpeed(), 1e-6f);
    }

    @Test
    void negativeSpeedRejected() throws Exception {
        player.setMovementSpeed(0.05f);
        player.setMovementSpeed(-1.0f);

        assertEquals(0.05f, player.getMovementSpeed(), 1e-6f);
        assertEquals(0.05f, getSpeedToSend(), 1e-6f);
    }

    @Test
    void nonFiniteSpeedRejected() throws Exception {
        // NaN 与 +Inf 通过 < 0 校验：NaN 原样发给客户端，+Inf 会让 sendAttributes 抛异常
        // NaN and +Inf pass a < 0 check: NaN goes to the client raw, +Inf later throws in sendAttributes
        player.setMovementSpeed(0.05f);
        player.setMovementSpeed(Float.NaN);
        player.setMovementSpeed(Float.POSITIVE_INFINITY);

        assertEquals(0.05f, player.getMovementSpeed(), 1e-6f);
        assertEquals(0.05f, getSpeedToSend(), 1e-6f);
    }

    @Test
    void sendMovementSpeedSendsDerivedValue() throws Exception {
        doCallRealMethod().when(player).sendMovementSpeed();
        doReturn(true).when(player).dataPacket(any(DataPacket.class));

        player.setMovementSpeed(0.05f);
        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.SPRINTING, 1.3f, EntityMovementSpeedModifier.Operation.MULTIPLY));
        player.sendMovementSpeed();

        ArgumentCaptor<UpdateAttributesPacket> captor = ArgumentCaptor.forClass(UpdateAttributesPacket.class);
        // 一次来自 addMovementSpeedModifier（无条件发送），一次来自显式调用
        // One from addMovementSpeedModifier (sends unconditionally), one from the explicit call
        verify(player, times(2)).dataPacket(captor.capture());
        for (UpdateAttributesPacket packet : captor.getAllValues()) {
            Attribute sent = findMovementSpeedAttribute(packet);
            assertEquals(0.065f, sent.getValue(), 1e-6f);
            // defaultValue 须为基础速度，与 sendAttributes 路径一致
            // defaultValue must be the base speed, matching the sendAttributes path
            assertEquals(0.05f, sent.getDefaultValue(), 1e-6f);
        }
    }

    @Test
    void sendAttributesSendsDerivedMovementSpeed() throws Exception {
        doReturn(true).when(player).dataPacket(any(DataPacket.class));
        PlayerFood foodData = mock(PlayerFood.class);
        when(foodData.getLevel()).thenReturn(20);
        when(foodData.getMaxLevel()).thenReturn(20);
        setField(Player.class, player, "foodData", foodData);
        setField(Entity.class, player, "effects", new ConcurrentHashMap<>());

        player.setMovementSpeed(0.05f);
        player.addMovementSpeedModifier(EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.EFFECT_SPEED, 1.2f, EntityMovementSpeedModifier.Operation.MULTIPLY));
        player.sendAttributes();

        ArgumentCaptor<UpdateAttributesPacket> captor = ArgumentCaptor.forClass(UpdateAttributesPacket.class);
        verify(player).dataPacket(captor.capture());
        Attribute sent = findMovementSpeedAttribute(captor.getValue());
        assertEquals(0.06f, sent.getValue(), 1e-6f);
        assertEquals(0.05f, sent.getDefaultValue(), 1e-6f);
    }

    private Attribute findMovementSpeedAttribute(UpdateAttributesPacket packet) {
        for (Attribute attribute : packet.entries) {
            if (attribute.getId() == Attribute.MOVEMENT_SPEED) {
                return attribute;
            }
        }
        throw new AssertionError("MOVEMENT_SPEED attribute not found in packet");
    }

    private float getSpeedToSend() throws Exception {
        Field field = Player.class.getDeclaredField("speedToSend");
        field.setAccessible(true);
        return (Float) field.get(player);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
