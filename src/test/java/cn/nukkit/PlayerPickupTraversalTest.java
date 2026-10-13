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
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    void pickupDelayAndASecondDelayedSourceStillPreventConsumption() throws Exception {
        Fixture f = new Fixture();
        EntityItem delayed = f.add(EntityItem.class, 1, 10);
        EntityItem locked = f.add(EntityItem.class, 2, 10);
        f.player.checkNearEntities();
        verify(delayed, never()).close();
        verify(locked, never()).close();
        verify(f.plugins, never()).callEvent(any(InventoryPickupItemEvent.class));
        verify(f.inventory, never()).addItem(any(Item[].class));
        verify(f.inventory, never()).setItem(anyInt(), any(Item.class), anyBoolean());
        verify(f.inventory, never()).clear(anyInt(), anyBoolean());
        assertTrue(f.inventory.getContents().isEmpty());
        assertEquals(1, delayed.getItem().getCount());
        assertEquals(1, locked.getItem().getCount());
        assertSame(delayed, f.level.getEntity(delayed.getId()));
        assertSame(locked, f.level.getEntity(locked.getId()));
    }

    @Test
    void cancelledItemArrowAndTridentEventsStillLeaveEntitiesInWorld() throws Exception {
        Fixture f = new Fixture();
        EntityItem item = f.add(EntityItem.class, 1);
        EntityArrow arrow = f.add(EntityArrow.class, 2);
        arrow.hadCollision = true;
        arrow.setPickupMode(EntityArrow.PICKUP_ANY);
        EntityThrownTrident trident = f.add(EntityThrownTrident.class, 3);
        trident.hadCollision = true;
        trident.setPlayer(true);
        trident.setItem(Item.get(Item.TRIDENT));
        trident.setPickupMode(EntityThrownTrident.PICKUP_ANY);
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
        final LevelDBChunk chunk;
        final PlayerInventory inventory = spy(new PlayerInventory(player));
        final PluginManager plugins = mock(PluginManager.class);
        final Map<Long, Entity> entities = new Long2ObjectNonBlockingMap<>();
        final Map<Long, Entity> updates = new Long2ObjectNonBlockingMap<>();
        Fixture() throws Exception {
            MockServer.reset();
            Server server = MockServer.get();
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getServer()).thenReturn(server);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getDimensionData()).thenReturn(DimensionData.LEGACY_DIMENSION);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            chunk = new LevelDBChunk(provider, 0, 0);
            field(Level.class, level, "entities", entities);
            field(Level.class, level, "updateEntities", updates);
            when(level.getEntity(anyLong())).thenAnswer(call -> entities.get(call.getArgument(0)));
            doAnswer(call -> { Entity entity = call.getArgument(0); entities.put(entity.getId(), entity); return null; })
                    .when(level).addEntity(any(Entity.class));
            doAnswer(call -> { Entity entity = call.getArgument(0); entities.remove(entity.getId(), entity); return null; })
                    .when(level).removeEntity(any(Entity.class));
            field(Entity.class, player, "server", server);
            field(EntityHumanType.class, player, "inventory", inventory);
            player.level = level;
            player.spawned = true;
            player.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 1, 66, 1);
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(false).when(player).isSpectator();
            when(level.getChunk(0, 0, true)).thenReturn(chunk);
            when(level.getChunk(0, 0)).thenReturn(chunk);
            when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
            clearInvocations(inventory);
        }
        private static void field(Class<?> owner, Object target, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
        }
        <T extends Entity> T add(Class<T> kind, long ordinal) {
            return add(kind, ordinal, 0);
        }
        <T extends Entity> T add(Class<T> kind, long ordinal, int pickupDelay) {
            CompoundTag nbt = Entity.getDefaultNBT(new Vector3(.25, 64, .25))
                    .putShort("Health", 5).putShort("PickupDelay", pickupDelay).putLong("FixtureOrdinal", ordinal);
            Entity original;
            if (kind == EntityItem.class) original = new EntityItem(chunk, nbt.putCompound("Item", NBTIO.putItemHelper(Item.get(Item.DIAMOND, 0, 1))));
            else if (kind == EntityXPOrb.class) original = new EntityXPOrb(chunk, nbt.putShort("Value", 1));
            else if (kind == EntityArrow.class) original = new EntityArrow(chunk, nbt);
            else if (kind == EntityThrownTrident.class) original = new EntityThrownTrident(chunk, nbt.putCompound("Trident", NBTIO.putItemHelper(Item.get(Item.TRIDENT, 0, 1))));
            else throw new IllegalArgumentException("Unsupported native fixture actor: " + kind);
            T entity = kind.cast(spy(original));
            // Mockito spies copy the actor: all native registries must refer to the exact offered identity.
            chunk.removeEntity(original); chunk.addEntity(entity);
            entities.put(entity.getId(), entity); updates.put(entity.getId(), entity);
            entity.boundingBox = player.boundingBox;
            assertSame(entity, level.getEntity(entity.getId()));
            assertSame(entity, chunk.getEntities().get(entity.getId()));
            assertSame(entity, updates.get(entity.getId()));
            assertSame(level, entity.getLevel()); assertSame(chunk, entity.chunk);
            assertFalse(entity.isClosed()); assertTrue(entity.isAlive());
            clearInvocations(entity);
            return entity;
        }
    }
}
