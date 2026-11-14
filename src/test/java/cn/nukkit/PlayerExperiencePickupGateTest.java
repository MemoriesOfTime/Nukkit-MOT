package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.inventory.InventoryPickupExperienceEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerExperiencePickupGateTest {
    @org.junit.jupiter.api.BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void cancelledPickupNeverConsumesOrbOrTouchesCanonicalItemsAndExperience() throws Exception {
        Fixture f = new Fixture();
        doAnswer(call -> {
            InventoryPickupExperienceEvent event = call.getArgument(0);
            assertSame(f.inventory, event.getInventory());
            assertSame(f.orb, event.getExperienceOrb());
            event.setCancelled();
            return null;
        }).when(f.plugins).callEvent(any(InventoryPickupExperienceEvent.class));
        assertFalse(f.player.pickupEntity(f.orb, true));
        verify(f.orb, never()).close();
        verifyNoInteractions(f.inventory, f.offhand);
        verify(f.player, never()).addExperience(anyInt());
        assertEquals(0, f.player.pickedXPOrb);
    }

    @Test
    void allowedPickupKeepsNormalExperienceAndConsumesOneOrb() throws Exception {
        Fixture f = new Fixture();
        f.emptyEquipment();
        doNothing().when(f.player).addExperience(anyInt());
        assertTrue(f.player.pickupEntity(f.orb, true));
        verify(f.orb, times(1)).close();
        verify(f.player).addExperience(3);
        assertEquals(1, f.player.pickedXPOrb);
    }

    @Test
    void allowedPickupStillMendsTheRealOffhandShield() throws Exception {
        Fixture f = new Fixture();
        f.emptyEquipment();
        Item shield = Item.get(Item.SHIELD, 10, 1);
        shield.addEnchantment(Enchantment.get(Enchantment.ID_MENDING));
        when(f.offhand.getItem(0)).thenReturn(shield);
        assertTrue(f.player.pickupEntity(f.orb, true));
        verify(f.orb).close();
        verify(f.offhand).setItem(0, shield);
        assertEquals(4, shield.getDamage());
        verify(f.player, never()).addExperience(anyInt());
    }

    @Test
    void disabledHookPreservesLegacyXpAndDoesNotDispatchAnEvent() throws Exception {
        Fixture f = new Fixture();
        f.server.experiencePickupEvent = false;
        f.emptyEquipment();
        doNothing().when(f.player).addExperience(anyInt());
        assertTrue(f.player.pickupEntity(f.orb, true));
        verify(f.plugins, never()).callEvent(any(InventoryPickupExperienceEvent.class));
        verify(f.orb).close();
        verify(f.player).addExperience(3);
    }

    @Test
    void disabledHookPreservesLegacyMending() throws Exception {
        Fixture f = new Fixture();
        f.server.experiencePickupEvent = false;
        f.emptyEquipment();
        Item shield = Item.get(Item.SHIELD, 10, 1);
        shield.addEnchantment(Enchantment.get(Enchantment.ID_MENDING));
        when(f.offhand.getItem(0)).thenReturn(shield);
        assertTrue(f.player.pickupEntity(f.orb, true));
        assertEquals(4, shield.getDamage());
        verify(f.plugins, never()).callEvent(any(InventoryPickupExperienceEvent.class));
        verify(f.offhand).setItem(0, shield);
        verify(f.player, never()).addExperience(anyInt());
    }

    @Test
    void noEventUntilOrbIsInsideReadyAndTickIsAvailable() throws Exception {
        Fixture f = new Fixture();
        when(f.player.boundingBox.isVectorInside(f.orb)).thenReturn(false);
        assertFalse(f.player.pickupEntity(f.orb, true));
        when(f.player.boundingBox.isVectorInside(f.orb)).thenReturn(true);
        when(f.orb.getPickupDelay()).thenReturn(3);
        assertFalse(f.player.pickupEntity(f.orb, true));
        when(f.orb.getPickupDelay()).thenReturn(0);
        f.player.pickedXPOrb = 1;
        assertFalse(f.player.pickupEntity(f.orb, true));
        verify(f.plugins, never()).callEvent(any(InventoryPickupExperienceEvent.class));
        verify(f.orb, never()).close();
        verifyNoInteractions(f.inventory, f.offhand);
    }

    @Test
    void eventSeesIntactOrbAndInventoryImmediatelyBeforeConsumption() throws Exception {
        Fixture f = new Fixture();
        f.emptyEquipment();
        doNothing().when(f.player).addExperience(anyInt());
        doAnswer(call -> {
            verify(f.orb, never()).close();
            verify(f.inventory, never()).getArmorItem(anyInt());
            verify(f.offhand, never()).getItem(anyInt());
            verify(f.player, never()).addExperience(anyInt());
            assertEquals(0, f.player.pickedXPOrb);
            return null;
        }).when(f.plugins).callEvent(any(InventoryPickupExperienceEvent.class));
        assertTrue(f.player.pickupEntity(f.orb, true));
        verify(f.orb).close();
    }

    @Test
    void orbConsumedDuringCallbackCannotGrantExperienceOrMendingAgain() throws Exception {
        Fixture f = new Fixture();
        doAnswer(call -> {
            when(f.orb.isClosed()).thenReturn(true);
            return null;
        }).when(f.plugins).callEvent(any(InventoryPickupExperienceEvent.class));
        assertFalse(f.player.pickupEntity(f.orb, true));
        verify(f.orb, never()).close();
        verifyNoInteractions(f.inventory, f.offhand);
        verify(f.player, never()).addExperience(anyInt());
    }

    @Test
    void callbackReentryCannotConsumeAnotherOrbOrRunASecondEvent() throws Exception {
        Fixture f = new Fixture();
        f.emptyEquipment();
        doNothing().when(f.player).addExperience(anyInt());
        doAnswer(call -> {
            assertFalse(f.player.pickupEntity(f.orb, true));
            return null;
        }).when(f.plugins).callEvent(any(InventoryPickupExperienceEvent.class));
        assertTrue(f.player.pickupEntity(f.orb, true));
        verify(f.plugins, times(1)).callEvent(any(InventoryPickupExperienceEvent.class));
        verify(f.orb, times(1)).close();
        verify(f.player, times(1)).addExperience(3);
        assertFalse(f.player.pickupEntity(f.orb, true), "one successful pickup per tick");
    }

    @Test
    void callbackChangingPlayerLevelCannotConsumeAnOrbAtTheOldCoordinates() throws Exception {
        Fixture f = new Fixture();
        doAnswer(call -> {
            doReturn(mock(Level.class)).when(f.player).getLevel();
            return null;
        }).when(f.plugins).callEvent(any(InventoryPickupExperienceEvent.class));
        assertFalse(f.player.pickupEntity(f.orb, true));
        verify(f.orb, never()).close();
        verifyNoInteractions(f.inventory, f.offhand);
        verify(f.player, never()).addExperience(anyInt());
        assertEquals(0, f.player.pickedXPOrb);
    }

    @Test
    void callbackChangingOrbLevelCannotConsumeAnOrbAtTheSameCoordinates() throws Exception {
        Fixture f = new Fixture();
        doAnswer(call -> {
            when(f.orb.getLevel()).thenReturn(mock(Level.class));
            return null;
        }).when(f.plugins).callEvent(any(InventoryPickupExperienceEvent.class));
        assertFalse(f.player.pickupEntity(f.orb, true));
        verify(f.orb, never()).close();
        verifyNoInteractions(f.inventory, f.offhand);
        verify(f.player, never()).addExperience(anyInt());
        assertEquals(0, f.player.pickedXPOrb);
    }

    @Test
    void callbackChangingLastPickedTickPreventsAnotherConsumption() throws Exception {
        Fixture f = new Fixture();
        doAnswer(call -> {
            f.player.pickedXPOrb = 1;
            return null;
        }).when(f.plugins).callEvent(any(InventoryPickupExperienceEvent.class));
        assertFalse(f.player.pickupEntity(f.orb, true));
        verify(f.orb, never()).close();
        verifyNoInteractions(f.inventory, f.offhand);
    }

    private static class Fixture {
        final Server server = mock(Server.class);
        final Level level = mock(Level.class);
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final PlayerOffhandInventory offhand = mock(PlayerOffhandInventory.class);
        final EntityXPOrb orb = mock(EntityXPOrb.class);
        final PluginManager plugins = mock(PluginManager.class);
        Fixture() throws Exception {
            server.experiencePickupEvent = true;
            when(server.getTick()).thenReturn(1);
            when(server.getPluginManager()).thenReturn(plugins);
            set(Entity.class, player, "server", server);
            set(EntityHumanType.class, player, "inventory", inventory);
            set(EntityHumanType.class, player, "offhandInventory", offhand);
            player.spawned = true;
            player.boundingBox = mock(AxisAlignedBB.class);
            when(player.boundingBox.isVectorInside(orb)).thenReturn(true);
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(false).when(player).isSpectator();
            doReturn(level).when(player).getLevel();
            when(orb.getLevel()).thenReturn(level);
            when(orb.getExp()).thenReturn(3);
        }
        void emptyEquipment() {
            when(inventory.getArmorItem(anyInt())).thenReturn(Item.AIR_ITEM);
            when(inventory.getItemInHandFast()).thenReturn(Item.AIR_ITEM);
            when(offhand.getItem(0)).thenReturn(Item.AIR_ITEM);
        }
        static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
    }
}
