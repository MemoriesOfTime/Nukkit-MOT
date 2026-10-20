package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.event.inventory.InventoryPickupArrowEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.inventory.InventoryPickupTridentEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerPickupTraversalTest {
    @BeforeAll
    static void initializeServer() { MockServer.init(); }

    @Test
    void traversalStillOffersItemsExperienceArrowsAndReturningTridents() throws Exception {
        Fixture f = new Fixture();
        EntityItem item = f.add(EntityItem.class, 1);
        EntityXPOrb orb = f.add(EntityXPOrb.class, 2);
        EntityArrow arrow = f.add(EntityArrow.class, 3);
        EntityThrownTrident trident = f.add(EntityThrownTrident.class, 4);
        doReturn(false).when(f.player).pickupEntity(any(), eq(true));
        f.player.checkNearEntities();
        verify(f.player).pickupEntity(item, true);
        verify(f.player).pickupEntity(orb, true);
        verify(f.player).pickupEntity(arrow, true);
        verify(f.player).pickupEntity(trident, true);
        verify(f.level, never()).getNearbyEntities(any(), any());
    }

    @Test
    void pickupCallbackMovingAndSpawningItemsKeepsOriginalCandidateOrder() throws Exception {
        Fixture f = new Fixture();
        f.add(EntityItem.class, 1);
        f.add(EntityItem.class, 2);
        List<Entity> expected = new ArrayList<>(f.chunk.getEntities().values());
        List<Entity> offered = new ArrayList<>();
        doAnswer(call -> {
            offered.add(call.getArgument(0));
            if (offered.size() == 1) {
                Entity later = expected.get(1);
                later.boundingBox = new SimpleAxisAlignedBB(100, 64, 100, 101, 65, 101);
                f.chunk.removeEntity(later);
                f.add(EntityItem.class, 3);
            }
            return false;
        }).when(f.player).pickupEntity(any(), eq(true));
        f.player.checkNearEntities();
        assertEquals(expected, offered);
    }

    @Test
    void pickupDelayAndDurableCombatLockStillPreventConsumption() throws Exception {
        Fixture f = new Fixture();
        EntityItem delayed = f.add(EntityItem.class, 1);
        when(delayed.getPickupDelay()).thenReturn(10);
        EntityItem locked = f.add(EntityItem.class, 2);
        locked.namedTag = new CompoundTag().putByte("km_combat_drop_locked", 1);
        f.player.checkNearEntities();
        verify(delayed, never()).close();
        verify(locked, never()).close();
        verify(f.plugins, never()).callEvent(any(InventoryPickupItemEvent.class));
        verifyNoInteractions(f.inventory);
    }

    @Test
    void cancelledItemArrowAndTridentEventsStillLeaveEntitiesInWorld() throws Exception {
        Fixture f = new Fixture();
        EntityItem item = f.add(EntityItem.class, 1);
        when(item.getItem()).thenReturn(Item.get(Item.DIAMOND));
        EntityArrow arrow = f.add(EntityArrow.class, 2);
        arrow.hadCollision = true;
        when(arrow.getPickupMode()).thenReturn(EntityArrow.PICKUP_ANY);
        EntityThrownTrident trident = f.add(EntityThrownTrident.class, 3);
        trident.hadCollision = true;
        when(trident.isPlayer()).thenReturn(true);
        when(trident.getItem()).thenReturn(Item.get(Item.TRIDENT));
        when(trident.getPickupMode()).thenReturn(EntityThrownTrident.PICKUP_ANY);
        when(f.inventory.canAddItem(any())).thenReturn(true);
        doAnswer(call -> { ((InventoryPickupItemEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(f.plugins).callEvent(any(InventoryPickupItemEvent.class));
        doAnswer(call -> { ((InventoryPickupArrowEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(f.plugins).callEvent(any(InventoryPickupArrowEvent.class));
        doAnswer(call -> { ((InventoryPickupTridentEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(f.plugins).callEvent(any(InventoryPickupTridentEvent.class));
        f.player.checkNearEntities();
        verify(f.plugins).callEvent(any(InventoryPickupItemEvent.class));
        verify(f.plugins).callEvent(any(InventoryPickupArrowEvent.class));
        verify(f.plugins).callEvent(any(InventoryPickupTridentEvent.class));
        verify(item, never()).close();
        verify(arrow, never()).close();
        verify(trident, never()).close();
        verify(f.inventory, never()).addItem(any(Item[].class));
    }

    private static class Fixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final Level level = mock(Level.class);
        final LevelDBChunk chunk = new LevelDBChunk(null, 0, 0);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final PluginManager plugins = mock(PluginManager.class);
        Fixture() throws Exception {
            Server server = mock(Server.class);
            when(server.getPluginManager()).thenReturn(plugins);
            Field serverField = Entity.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(player, server);
            Field inventoryField = EntityHumanType.class.getDeclaredField("inventory");
            inventoryField.setAccessible(true);
            inventoryField.set(player, inventory);
            player.level = level;
            player.spawned = true;
            player.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 1, 66, 1);
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(false).when(player).isSpectator();
            when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        }
        <T extends Entity> T add(Class<T> kind, long id) {
            T entity = mock(kind);
            when(entity.getId()).thenReturn(id);
            when(entity.isAlive()).thenReturn(true);
            entity.boundingBox = player.boundingBox;
            when(entity.getBoundingBox()).thenReturn(entity.boundingBox);
            chunk.addEntity(entity);
            return entity;
        }
    }
}
