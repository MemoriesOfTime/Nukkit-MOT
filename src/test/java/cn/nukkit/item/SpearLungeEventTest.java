package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.Server;
import cn.nukkit.event.entity.EntityMotionEvent;
import cn.nukkit.event.Event;
import cn.nukkit.event.Cancellable;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SpearLungeEventTest {
    @Test
    void cancellationLeavesMotionSoundAndFoodUntouched() throws Exception {
        Fixture fixture = new Fixture();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            assertEquals("cn.nukkit.event.player.PlayerSpearLungeEvent", event.getClass().getName());
            assertSame(fixture.player, ((cn.nukkit.event.player.PlayerEvent) event).getPlayer());
            assertSame(fixture.spear, event.getClass().getMethod("getSpear").invoke(event));
            ((Cancellable) event).setCancelled(true);
            return null;
        }).when(fixture.events).callEvent(any(Event.class));

        fixture.lunge();

        verify(fixture.player, never()).setMotion(any(Vector3.class));
        verify(fixture.level, never()).addSound(any(), eq(Sound.ITEM_SPEAR_LUNGE));
        verify(fixture.food, never()).updateFoodExpLevel(anyDouble());
    }

    @Test
    void allowedLungeKeepsVanillaImpulseSoundAndFood() throws Exception {
        Fixture fixture = new Fixture();

        fixture.lunge();

        verify(fixture.events).callEvent(any(Event.class));
        verify(fixture.player).setMotion(new Vector3(0.2, 0.3, 1.3));
        verify(fixture.level).addSound(fixture.player, Sound.ITEM_SPEAR_LUNGE);
        verify(fixture.food).updateFoodExpLevel(4);
    }

    @Test
    void chargedHitEntersCombatBeforeLungeCancellationAndStillWearsSpear() throws Exception {
        Fixture fixture = new Fixture();
        java.util.concurrent.atomic.AtomicBoolean inCombat = new java.util.concurrent.atomic.AtomicBoolean();
        when(fixture.spear.getMaxDurability()).thenReturn(100);
        when(fixture.spear.getChargeDamageTicks()).thenReturn(100);
        when(fixture.spear.chargeStab(fixture.player)).thenAnswer(ignored -> {
            inCombat.set(true);
            return true;
        });
        doCallRealMethod().when(fixture.spear).onRelease(fixture.player, 10);
        doAnswer(call -> {
            assertTrue(inCombat.get(), "charged damage must precede the lunge decision");
            ((Cancellable) call.getArgument(0)).setCancelled(true);
            return null;
        }).when(fixture.events).callEvent(any(Event.class));

        assertTrue(fixture.spear.onRelease(fixture.player, 10));

        verify(fixture.spear).chargeStab(fixture.player);
        verify(fixture.spear).damageSpear(fixture.player);
        verify(fixture.player, never()).setMotion(any(Vector3.class));
        verify(fixture.food, never()).updateFoodExpLevel(anyDouble());
    }

    @Test
    void unenchantedSpearDoesNotEmitLungeOrMotion() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.spear.getEnchantmentLevel(Enchantment.ID_LUNGE)).thenReturn(0);

        fixture.lunge();

        verifyNoInteractions(fixture.events);
        verify(fixture.player, never()).setMotion(any(Vector3.class));
    }

    @Test
    void lungeHandlersAreSeparateFromGenericMotionHandlers() throws Exception {
        assertNotSame(EntityMotionEvent.getHandlers(), assertDoesNotThrow(() -> Class.forName("cn.nukkit.event.player.PlayerSpearLungeEvent")).getDeclaredMethod("getHandlers").invoke(null));
    }

    private static final class Fixture {
        final ItemSpear spear = mock(ItemSpear.class);
        final Player player = mock(Player.class);
        final PlayerFood food = mock(PlayerFood.class);
        final Level level = mock(Level.class);
        final PluginManager events = mock(PluginManager.class);

        Fixture() {
            Server server = mock(Server.class);
            when(server.getPluginManager()).thenReturn(events);
            when(player.getServer()).thenReturn(server);
            when(player.getLevel()).thenReturn(level);
            when(player.getFoodData()).thenReturn(food);
            when(player.isSurvival()).thenReturn(true);
            when(food.getLevel()).thenReturn(20);
            when(player.getDirectionVector()).thenAnswer(ignored -> new Vector3(0, 0, 1));
            when(player.getMotion()).thenReturn(new Vector3(0.2, 0.3, 0.4));
            when(spear.getEnchantmentLevel(Enchantment.ID_LUNGE)).thenReturn(1);
        }

        void lunge() throws Exception {
            Method lunge = ItemSpear.class.getDeclaredMethod("applyLunge", Player.class);
            lunge.setAccessible(true);
            lunge.invoke(spear, player);
        }
    }
}
