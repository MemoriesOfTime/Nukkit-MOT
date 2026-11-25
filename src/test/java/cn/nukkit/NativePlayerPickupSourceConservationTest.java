package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryPickupArrowEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.inventory.InventoryPickupTridentEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NativePlayerPickupSourceConservationTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetEvents() { reset(Server.getInstance().getPluginManager()); }

    @Test void rejectedItemWriteMustLeaveItsWorldSource() throws Exception { assertRejected(Kind.ITEM); }
    @Test void rejectedArrowWriteMustLeaveItsWorldSource() throws Exception { assertRejected(Kind.ARROW); }
    @Test void rejectedFavoredTridentWriteMustLeaveItsWorldSource() throws Exception { assertRejected(Kind.TRIDENT); }
    @Test void itemPickupCallbackMustNotConsumeTheSameSourceTwice() throws Exception { assertReentry(Kind.ITEM); }
    @Test void arrowPickupCallbackMustNotConsumeTheSameSourceTwice() throws Exception { assertReentry(Kind.ARROW); }
    @Test void tridentPickupCallbackMustNotConsumeTheSameSourceTwice() throws Exception { assertReentry(Kind.TRIDENT); }

    @Test void tridentWithoutAFavoredSlotUsesTheOrdinaryInventory() throws Exception {
        Fixture f = new Fixture(Kind.TRIDENT);
        ((EntityThrownTrident) f.entity).setFavoredSlot(-1);
        assertTrue(f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total());
        assertTrue(f.entity.isClosed());
        assertEquals(Item.TRIDENT, f.inventory.getItem(0).getId());
    }

    @Test void aThrownPickupCallbackDoesNotLeaveItsEntityPermanentlyClaimed() throws Exception {
        Fixture f = new Fixture(Kind.ITEM);
        var plugins = Server.getInstance().getPluginManager();
        doThrow(new IllegalStateException("listener failed")).when(plugins).callEvent(any(InventoryPickupItemEvent.class));
        assertThrows(IllegalStateException.class, () -> f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total());
        reset(plugins);
        assertTrue(f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total());
        assertTrue(f.entity.isClosed());
    }

    @Test void disconnectedPlayerIsRecheckedAfterThePickupCallback() throws Exception {
        Fixture f = new Fixture(Kind.ITEM);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { doReturn(false).when(f.player).isOnline(); return null; })
                .when(plugins).callEvent(any(InventoryPickupItemEvent.class));
        assertFalse(f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total());
        reset(plugins);
        doReturn(true).when(f.player).isOnline();
        assertTrue(f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total());
    }

    @Test void failedPickupPacketCannotLeaveASecondSourceAfterTheGrant() throws Exception {
        Fixture f = new Fixture(Kind.ITEM);
        doThrow(new IllegalStateException("packet failed")).when(f.player).dataPacket(any(DataPacket.class));
        assertThrows(IllegalStateException.class, () -> f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total());
        assertTrue(f.entity.isClosed());
        assertFalse(f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total());
        doReturn(true).when(f.player).dataPacket(any(DataPacket.class));
        Fixture next = new Fixture(Kind.ITEM);
        assertTrue(f.player.pickupEntity(next.entity, true), "packet failure must release the destination claim");
        assertTrue(next.entity.isClosed());
        assertEquals(2, f.total());
    }

    @Test void aLateSlotCallbackFailureCannotLeaveAPartialGrantAndTheFullSource() throws Exception {
        Fixture f = new Fixture(Kind.ITEM);
        ((EntityItem) f.entity).getItem().setCount(10);
        f.inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 60));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() != 0) throw new IllegalStateException("second slot callback failed");
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertThrows(IllegalStateException.class, () -> f.player.pickupEntity(f.entity, true));
        assertEquals(70, f.total(), "a completed first merge must not coexist with the full source");
        assertEquals(60, f.inventory.getItem(0).getCount());
        assertFalse(f.entity.isClosed());
        reset(plugins);
        assertTrue(f.player.pickupEntity(f.entity, true));
        assertEquals(70, f.total());
    }

    @Test void favoredSlotFailureAfterItsWriteRestoresTheInventoryAndLeavesTheTrident() throws Exception {
        Fixture f = new Fixture(Kind.TRIDENT, true);
        doThrow(new IllegalStateException("slot observer failed"))
                .when(f.inventory).onSlotChange(eq(0), any(Item.class), eq(true));
        assertThrows(IllegalStateException.class, () -> f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total(), "a failed post-write observer must not leave two tridents");
        assertFalse(f.entity.isClosed());
        assertTrue(f.inventory.getContents().isEmpty());
        doCallRealMethod().when(f.inventory).onSlotChange(eq(0), any(Item.class), eq(true));
        assertTrue(f.player.pickupEntity(f.entity, true));
        assertEquals(1, f.total());
    }

    @Test void aFailingSlotCallbackCannotChangeTheRetainedSourceCount() throws Exception {
        Fixture f = new Fixture(Kind.ITEM);
        ((EntityItem) f.entity).getItem().setCount(10);
        f.inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 60));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            if (((EntityInventoryChangeEvent) call.getArgument(0)).getSlot() != 0) {
                ((EntityItem) f.entity).getItem().setCount(20);
                throw new IllegalStateException("callback changed its source before failing");
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertThrows(IllegalStateException.class, () -> f.player.pickupEntity(f.entity, true));
        assertEquals(70, f.total());
        assertEquals(10, ((EntityItem) f.entity).getItem().getCount());
        assertEquals(60, f.inventory.getItem(0).getCount());
        assertFalse(f.entity.isClosed());
    }

    @Test void partialAcceptanceUsesTheOfferedCountWhenALaterCallbackChangesTheSource() throws Exception {
        Fixture f = new Fixture(Kind.ITEM);
        ((EntityItem) f.entity).getItem().setCount(10);
        f.inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 60));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == 0) ((EntityItem) f.entity).getItem().setCount(1);
            else event.setCancelled();
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertTrue(f.player.pickupEntity(f.entity, true), "four items were actually accepted");
        assertEquals(70, f.total());
        assertEquals(6, ((EntityItem) f.entity).getItem().getCount());
        assertFalse(f.entity.isClosed());
    }

    @Test void anotherPlayerCannotConsumeTheSameEntityInsideItsPickupCallback() throws Exception {
        Fixture first = new Fixture(Kind.ITEM);
        Fixture second = new Fixture(Kind.ITEM);
        AtomicBoolean nested = new AtomicBoolean();
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof InventoryPickupItemEvent && nested.compareAndSet(false, true)) {
                second.player.pickupEntity(first.entity, true);
            }
            return null;
        }).when(plugins).callEvent(any());
        first.player.pickupEntity(first.entity, true);
        assertEquals(1, first.total() + second.inventory.getContents().values().stream().mapToInt(Item::getCount).sum());
    }

    @Test void partialItemAcceptanceLeavesOnlyTheUnacceptedRemainderInTheWorld() throws Exception {
        Fixture f = new Fixture(Kind.ITEM);
        ((EntityItem) f.entity).getItem().setCount(10);
        f.inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 60));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() != 0) event.setCancelled();
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertTrue(f.player.pickupEntity(f.entity, true));
        assertEquals(70, f.total());
        assertEquals(6, ((EntityItem) f.entity).getItem().getCount());
        assertFalse(f.entity.isClosed());
        reset(plugins);
        assertTrue(f.player.pickupEntity(f.entity, true));
        assertEquals(70, f.total());
        assertTrue(f.entity.isClosed());
    }

    @Test void nestedItemPickupCannotBeErasedByTheOuterRollback() throws Exception {
        assertNestedPickupRollback(Kind.ITEM, false);
    }

    @Test void nestedArrowPickupCannotBeErasedByTheOuterRollback() throws Exception {
        assertNestedPickupRollback(Kind.ARROW, false);
    }

    @Test void nestedTridentPickupCannotBeErasedByTheOuterRollback() throws Exception {
        assertNestedPickupRollback(Kind.TRIDENT, false);
    }

    @Test void anotherPlayerSharingTheDestinationCannotNestItsPickup() throws Exception {
        assertNestedPickupRollback(Kind.ITEM, true);
    }

    @Test void nestedPickupIntoAnotherInventorySurvivesTheOuterRollback() throws Exception {
        Fixture outer = new Fixture(Kind.ITEM);
        Fixture inner = new Fixture(Kind.ITEM);
        ((EntityItem) outer.entity).getItem().setCount(10);
        outer.inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 60));
        AtomicBoolean entered = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean();
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getEntity() == outer.player && event.getSlot() == 1 && entered.compareAndSet(false, true)) {
                accepted.set(inner.player.pickupEntity(inner.entity, true));
                throw new IllegalStateException("outer callback failed after another inventory completed");
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertThrows(IllegalStateException.class, () -> outer.player.pickupEntity(outer.entity, true));
        assertTrue(accepted.get(), "a different destination must remain independently available");
        assertTrue(inner.entity.isClosed());
        assertFalse(outer.entity.isClosed());
        assertEquals(71, outer.total() + inner.total());
        assertEquals(60, outer.inventory.getItem(0).getCount());
        reset(plugins);
        assertTrue(outer.player.pickupEntity(outer.entity, true));
        assertEquals(71, outer.total() + inner.total());
    }

    @Test void destinationIsClaimedBeforeThePickupEventAndScanDoesNotAcquireIt() throws Exception {
        Fixture outer = new Fixture(Kind.ITEM);
        Fixture inner = new Fixture(Kind.ITEM);
        AtomicBoolean entered = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean(true);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            if (entered.compareAndSet(false, true)) {
                assertFalse(outer.player.pickupEntity(inner.entity, false));
                accepted.set(outer.player.pickupEntity(inner.entity, true));
            }
            return null;
        }).when(plugins).callEvent(any(InventoryPickupItemEvent.class));
        assertTrue(outer.player.pickupEntity(outer.entity, true));
        assertFalse(accepted.get(), "claim must cover the pickup event before any slot snapshot");
        assertFalse(inner.entity.isClosed());
        assertEquals(2, outer.total() + 1);
        reset(plugins);
        assertTrue(outer.player.pickupEntity(inner.entity, true));
        assertTrue(inner.entity.isClosed());
        assertEquals(2, outer.total());
    }

    private void assertNestedPickupRollback(Kind innerKind, boolean sharedInventoryPlayer) throws Exception {
        Fixture outer = new Fixture(Kind.ITEM);
        Fixture inner = new Fixture(innerKind);
        ((EntityItem) outer.entity).getItem().setCount(10);
        outer.inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 60));
        if (sharedInventoryPlayer) {
            Fixture.set(EntityHumanType.class, inner.player, "inventory", outer.inventory);
        }
        Player nestedPlayer = sharedInventoryPlayer ? inner.player : outer.player;
        AtomicBoolean entered = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean(true);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == 1 && entered.compareAndSet(false, true)) {
                accepted.set(nestedPlayer.pickupEntity(inner.entity, true));
                throw new IllegalStateException("outer callback failed after attempted nested pickup");
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertThrows(IllegalStateException.class, () -> outer.player.pickupEntity(outer.entity, true));
        assertEquals(71, outer.total() + (inner.entity.isClosed() ? 0 : 1), "outer rollback must conserve the independent source");
        assertFalse(accepted.get(), "nested pickup into the same inventory must apply no grant");
        assertFalse(inner.entity.isClosed(), "the deferred source must remain available for a later retry");
        assertFalse(outer.entity.isClosed());
        assertEquals(60, outer.inventory.getItem(0).getCount());
        assertEquals(10, ((EntityItem) outer.entity).getItem().getCount());
        reset(plugins);
        assertTrue(outer.player.pickupEntity(outer.entity, true));
        assertTrue(outer.player.pickupEntity(inner.entity, true), "both claims must be released after the callback exception");
        assertTrue(inner.entity.isClosed());
        assertEquals(71, outer.total());
    }

    private void assertRejected(Kind kind) throws Exception {
        Fixture f = new Fixture(kind);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { ((EntityInventoryChangeEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        f.player.pickupEntity(f.entity, true);
        assertEquals(1, f.total(), "rejected inventory writes must preserve the source entity");
        assertFalse(f.entity.isClosed());
    }

    private void assertReentry(Kind kind) throws Exception {
        Fixture f = new Fixture(kind);
        AtomicBoolean first = new AtomicBoolean(true);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            Object event = call.getArgument(0);
            if ((event instanceof InventoryPickupItemEvent || event instanceof InventoryPickupArrowEvent
                    || event instanceof InventoryPickupTridentEvent) && first.getAndSet(false)) {
                f.player.pickupEntity(f.entity, true);
            }
            return null;
        }).when(plugins).callEvent(any());
        f.player.pickupEntity(f.entity, true);
        assertEquals(1, f.total(), "a nested public pickup must not mint a second item");
    }

    @Test void errorAfterEarlierMergeRestoresOriginalInventoryAndLiveNativeSource() throws Exception {
        Fixture f=new Fixture(Kind.ITEM);
        ((EntityItem)f.entity).getItem().setCount(10);
        f.inventory.setItemForce(0,Item.get(Item.EMERALD,0,60));
        var plugins=Server.getInstance().getPluginManager();
        doAnswer(call->{if(((EntityInventoryChangeEvent)call.getArgument(0)).getSlot()!=0)
            throw new AssertionError("later native slot callback");return null;})
            .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertThrows(AssertionError.class,()->f.player.pickupEntity(f.entity,true));
        assertEquals(70,f.total());assertEquals(60,f.inventory.getItem(0).getCount());assertFalse(f.entity.isClosed());
    }
    @Test void pickupEventReplacingDestinationCannotEscapeItsNativeOwnerClaim() throws Exception {
        Fixture f=new Fixture(Kind.ITEM);PlayerInventory replacement=new PlayerInventory(f.player);
        var plugins=Server.getInstance().getPluginManager();
        doAnswer(call->{Fixture.set(EntityHumanType.class,f.player,"inventory",replacement);return null;})
            .when(plugins).callEvent(any(InventoryPickupItemEvent.class));
        assertFalse(f.player.pickupEntity(f.entity,true));
        assertFalse(f.entity.isClosed());assertTrue(f.inventory.getContents().isEmpty());assertTrue(replacement.getContents().isEmpty());
    }

    private enum Kind { ITEM, ARROW, TRIDENT }
    private static final class Fixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory inventory;
        final Entity entity;

        Fixture(Kind kind) throws Exception {
            this(kind, false);
        }

        Fixture(Kind kind, boolean spyInventory) throws Exception {
            inventory = spyInventory ? spy(new PlayerInventory(player)) : new PlayerInventory(player);
            set(Entity.class, player, "server", Server.getInstance());
            set(EntityHumanType.class, player, "inventory", inventory);
            player.spawned = true;
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(false).when(player).isCreative();
            doReturn(false).when(player).isSpectator();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));
            cn.nukkit.level.Level level = mock(cn.nukkit.level.Level.class);
            level.isBeingConverted = true;
            when(level.getServer()).thenReturn(Server.getInstance());
            when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Collections.emptyMap());
            when(level.getGameRules()).thenReturn(cn.nukkit.level.GameRules.getDefault());
            cn.nukkit.level.format.LevelProvider provider = mock(cn.nukkit.level.format.LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            cn.nukkit.level.format.generic.BaseFullChunk chunk = mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(level.getChunk(anyInt(),anyInt())).thenReturn(chunk);
            player.level = level;
            var nbt = Entity.getDefaultNBT(new cn.nukkit.math.Vector3(1,64,1));
            if (kind == Kind.ITEM) {
                entity = new EntityItem(chunk, nbt.putShort("Health",5).putShort("PickupDelay",0)
                        .putCompound("Item",cn.nukkit.nbt.NBTIO.putItemHelper(Item.get(Item.EMERALD,0,1))));
            } else if (kind == Kind.ARROW) {
                var arrow = new EntityArrow(chunk,nbt);
                arrow.hadCollision = true;
                arrow.setPickupMode(EntityProjectile.PICKUP_ANY);
                entity = arrow;
            } else {
                var trident = new EntityThrownTrident(chunk,nbt.putBoolean("player",true)
                        .putInt("favoredSlot",0).putByte("pickup",EntityProjectile.PICKUP_ANY)
                        .putCompound("Trident",cn.nukkit.nbt.NBTIO.putItemHelper(Item.get(Item.TRIDENT,0,1))));
                trident.hadCollision = true;
                entity = trident;
            }
            when(level.getEntity(entity.getId())).thenReturn(entity);
            assertFalse(entity.isClosed(), "Actual public native entity constructor publishes a live source");
        }

        int total() {
            int source = entity instanceof EntityItem item ? item.getItem().getCount() : 1;
            return inventory.getContents().values().stream().mapToInt(Item::getCount).sum() + (entity.isClosed() ? 0 : source);
        }
        private static void set(Class<?> owner, Object object, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(object, value);
        }
    }
}
