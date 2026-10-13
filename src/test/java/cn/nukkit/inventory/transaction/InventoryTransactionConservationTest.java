package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.NativePublishedPickupSourceFixture;
import cn.nukkit.inventory.FurnaceInventory;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryTransactionConservationTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetEvents() { reset(Server.getInstance().getPluginManager()); }

    @Test void cancelledSourceRemovalCannotMintTheDestination() throws Exception { assertCancelled(false, 0); }
    @Test void cancelledSourceRemovalRollsBackAnEarlierDestination() throws Exception { assertCancelled(true, 0); }
    @Test void cancelledDestinationRestoresAnEarlierSourceRemoval() throws Exception { assertCancelled(false, 1); }
    @Test void lateExceptionCannotRetainAnEarlierDestinationAndTheSource() throws Exception { assertThrowing(true, 0); }
    @Test void lateExceptionCannotLoseAnEarlierSourceRemoval() throws Exception { assertThrowing(false, 1); }

    @Test void nestedNormalTransactionCannotConsumeTheSameSourceDuringItsEvent() throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction outer = f.move(1, false);
        InventoryTransaction inner = f.move(2, false);
        AtomicBoolean entered = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean(true);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            if (entered.compareAndSet(false, true)) {
                assertTrue(inner.canExecute(), "the nested request is a valid ordinary move at entry");
                accepted.set(inner.execute());
            }
            return null;
        }).when(plugins).callEvent(any(InventoryTransactionEvent.class));
        assertTrue(outer.canExecute());
        assertTrue(outer.execute());
        assertEquals(4, f.total(), "only one valid move may consume the same four source items");
        assertFalse(accepted.get());
    }

    @Test void rewrittenDestinationCountCannotMintUnmatchedItems() throws Exception {
        assertRewrittenCount(1, 8);
    }

    @Test void rewrittenSourceTargetMustMatchTheReportedRemoval() throws Exception {
        assertRewrittenCount(0, 1);
    }

    @Test void inPlaceCallbackCountRewriteCannotAlterTheAuthoredTarget() throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction transaction = f.move(1, false);
        assertTrue(transaction.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == 1) event.getNewItem().setCount(8);
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertFalse(transaction.execute());
        assertEquals(4, f.total());
        assertEquals(4, transaction.getActionList().get(1).getTargetItem().getCount());
    }

    @Test void postWriteObserverFailureRestoresBothSlotsAndReleasesTheClaim() throws Exception {
        Fixture f = new Fixture(true);
        InventoryTransaction transaction = f.move(1, false);
        assertTrue(transaction.canExecute());
        doThrow(new IllegalStateException("destination observer failed"))
                .when(f.inventory).onSlotChange(eq(1), any(Item.class), eq(false));
        assertThrows(IllegalStateException.class, transaction::execute);
        assertEquals(4, f.inventory.getItem(0).getCount());
        assertTrue(f.inventory.getItem(1).isNull());
        assertFalse(transaction.hasExecuted());
        doCallRealMethod().when(f.inventory).onSlotChange(eq(1), any(Item.class), eq(false));
        assertTrue(transaction.execute());
        assertEquals(4, f.total());
    }

    @Test void persistedLegacyStack128IsRejectedBeforeAnyWrite() throws Exception { assertLegacyOversizedGate(128); }
    @Test void persistedLegacyStack200IsRejectedBeforeAnyWrite() throws Exception { assertLegacyOversizedGate(200); }

    private void assertLegacyOversizedGate(int count) throws Exception {
        Fixture f = new Fixture();
        // Represent an already persisted legacy snapshot without invoking a grant/splitter.
        f.inventory.slots.put(0, Item.get(Item.EMERALD, 0, count));
        InventoryTransaction transaction = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(f.inventory, 0, Item.get(Item.EMERALD, 0, count), Item.get(Item.AIR)),
                new SlotChangeAction(f.inventory, 1, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, count))));
        assertFalse(transaction.canExecute(), "initial item maximum gate rejects authored legacy overstack moves");
        assertFalse(transaction.execute());
        assertEquals(count, f.inventory.getItem(0).getCount());
        assertTrue(f.inventory.getItem(1).isNull());
    }

    @Test void targetAboveTheEffectiveInventoryMaximumCannotOverflowBeforeRollback() throws Exception {
        Fixture source = new Fixture();
        Fixture destination = new Fixture();
        destination.inventory.setItemForce(0, Item.get(Item.AIR));
        for (int slot=0; slot<destination.inventory.getSize(); slot++) {
            if(slot!=1) destination.inventory.setItemForce(slot, Item.get(Item.STONE, 0, 64));
        }
        destination.inventory.setMaxStackSize(1);
        Level level=mock(Level.class);
        destination.player.level=level;
        AtomicInteger dropped=new AtomicInteger();
        doAnswer(call->{dropped.addAndGet(((Item)call.getArgument(1)).getCount());return null;})
                .when(level).dropItem(any(Vector3.class),any(Item.class));
        InventoryTransaction transaction=new InventoryTransaction(source.player,List.of(
                new SlotChangeAction(source.inventory,0,Item.get(Item.EMERALD,0,4),Item.get(Item.AIR)),
                new SlotChangeAction(destination.inventory,1,Item.get(Item.AIR),Item.get(Item.EMERALD,0,4))));
        assertTrue(transaction.canExecute(), "normal matcher accepts a balanced item-legal transfer");
        assertFalse(transaction.execute());
        int retained=source.inventory.getContents().values().stream().filter(i->i.getId()==Item.EMERALD).mapToInt(Item::getCount).sum()
                + destination.inventory.getContents().values().stream().filter(i->i.getId()==Item.EMERALD).mapToInt(Item::getCount).sum();
        assertEquals(4,retained+dropped.get(),"effective inventory overflow must not precede rollback");
        assertEquals(4,source.inventory.getItem(0).getCount());
        assertTrue(destination.inventory.getItem(1).isNull());
        assertFalse(transaction.hasExecuted());
    }

    @Test void sourceCallbackCannotOverflowAnotherInventoryOwnedByTheSameTransfer() throws Exception {
        assertCrossInventoryOverflow(false);
    }

    @Test void destinationCallbackCannotOverflowAnotherInventoryOwnedByTheSameTransfer() throws Exception {
        assertCrossInventoryOverflow(true);
    }

    private void assertCrossInventoryOverflow(boolean destinationFirst) throws Exception {
        Fixture source = new Fixture();
        Fixture destination = new Fixture();
        destination.inventory.setItemForce(0, Item.get(Item.AIR));
        for (int slot = 1; slot < source.inventory.getSize(); slot++) {
            source.inventory.setItemForce(slot, Item.get(Item.STONE, 0, 64));
        }
        for (int slot = 0; slot < destination.inventory.getSize(); slot++) {
            if (slot != 1) destination.inventory.setItemForce(slot, Item.get(Item.STONE, 0, 64));
        }
        Level level = mock(Level.class);
        source.player.level = level;
        destination.player.level = level;
        AtomicInteger dropped = new AtomicInteger();
        doAnswer(call -> { dropped.addAndGet(((Item) call.getArgument(1)).getCount()); return null; })
                .when(level).dropItem(any(Vector3.class), any(Item.class));
        InventoryAction remove = new SlotChangeAction(source.inventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR));
        InventoryAction add = new SlotChangeAction(destination.inventory, 1, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4));
        InventoryTransaction transaction = new InventoryTransaction(source.player,
                destinationFirst ? List.of(add, remove) : List.of(remove, add));
        assertTrue(transaction.canExecute());
        AtomicBoolean entered = new AtomicBoolean();
        AtomicBoolean nestedAccepted = new AtomicBoolean(true);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            if (entered.compareAndSet(false, true)) {
                nestedAccepted.set((destinationFirst ? source.inventory : destination.inventory)
                        .setItem(destinationFirst ? 0 : 1, Item.get(Item.EMERALD, 0, 200), false));
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        transaction.execute();
        int retained = source.inventory.getContents().values().stream()
                .filter(item -> item.getId() == Item.EMERALD).mapToInt(Item::getCount).sum()
                + destination.inventory.getContents().values().stream()
                .filter(item -> item.getId() == Item.EMERALD).mapToInt(Item::getCount).sum();
        assertEquals(4, retained + dropped.get(), "another claimed inventory must not route callback overflow");
        assertFalse(nestedAccepted.get());
        assertTrue(transaction.hasExecuted());
    }

    @Test void callbackOverstackCannotDropOverflowBeforeTheTransferIsRejected() throws Exception {
        Fixture f = new Fixture();
        for (int slot = 2; slot < f.inventory.getSize(); slot++) {
            f.inventory.setItemForce(slot, Item.get(Item.STONE, 0, 64));
        }
        Level level = mock(Level.class);
        f.player.level = level;
        AtomicInteger dropped = new AtomicInteger();
        doAnswer(call -> { dropped.addAndGet(((Item) call.getArgument(1)).getCount()); return null; })
                .when(level).dropItem(any(Vector3.class), any(Item.class));
        InventoryTransaction transaction = f.move(1, false);
        assertTrue(transaction.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == 1) event.setNewItem(Item.get(Item.EMERALD, 0, 200));
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertFalse(transaction.execute());
        int retained = f.inventory.getContents().values().stream().filter(item -> item.getId() == Item.EMERALD).mapToInt(Item::getCount).sum();
        assertEquals(4, retained + dropped.get(), "rollback must not leave callback-authored overflow in the world");
    }

    @Test void failedDoubleChestTransferRestoresItsPhysicalChestSide() throws Exception {
        Fixture f = new Fixture();
        f.inventory.clear(0, false);
        BlockEntityChest leftHolder = mock(BlockEntityChest.class);
        BlockEntityChest rightHolder = mock(BlockEntityChest.class);
        ChestInventory left = new ChestInventory(leftHolder);
        ChestInventory right = new ChestInventory(rightHolder);
        when(leftHolder.getRealInventory()).thenReturn(left);
        when(rightHolder.getRealInventory()).thenReturn(right);
        DoubleChestInventory chest = new DoubleChestInventory(leftHolder, rightHolder);
        chest.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        InventoryTransaction transaction = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(chest, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                new SlotChangeAction(f.inventory, 0, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4))));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { ((EntityInventoryChangeEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertTrue(transaction.canExecute());
        assertFalse(transaction.execute());
        assertEquals(4, chest.getItem(0).getCount() + f.total());
        assertEquals(4, left.getItem(0).getCount());
    }

    @Test void failedBundleContentTransferRestoresItsSavedHolderNbt() throws Exception {
        Fixture f = new Fixture();
        f.inventory.clear(0, false);
        ItemBundle bundle = new ItemBundle();
        var contents = bundle.getInventory();
        contents.setItem(0, Item.get(Item.EMERALD, 0, 4), false);
        InventoryTransaction transaction = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(contents, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                new SlotChangeAction(f.inventory, 0, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4))));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { ((EntityInventoryChangeEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertTrue(transaction.canExecute());
        assertFalse(transaction.execute());
        assertEquals(4, contents.getItem(0).getCount() + f.total());
        assertEquals(1, bundle.getNamedTag().getList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT).size());
    }

    @Test void offhandCallbackOverflowCannotEscapeTheRejectedTransfer() throws Exception {
        Fixture f = new Fixture();
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(f.player);
        Level level = mock(Level.class);
        f.player.level = level;
        AtomicInteger dropped = new AtomicInteger();
        doAnswer(call -> { dropped.addAndGet(((Item) call.getArgument(1)).getCount()); return null; })
                .when(level).dropItem(any(Vector3.class), any(Item.class));
        InventoryTransaction transaction = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(f.inventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                new SlotChangeAction(offhand, 0, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4))));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (!event.getNewItem().isNull()) event.setNewItem(Item.get(Item.EMERALD, 0, 200));
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertTrue(transaction.canExecute());
        assertFalse(transaction.execute());
        assertEquals(4, f.total() + offhand.getItem(0).getCount() + dropped.get());
    }

    @Test void nestedTransactionUsingAnotherInventoryRemainsIndependent() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        InventoryTransaction outer = first.move(1, false);
        InventoryTransaction inner = second.move(1, false);
        AtomicBoolean entered = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean();
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            if (entered.compareAndSet(false, true)) accepted.set(inner.execute());
            return null;
        }).when(plugins).callEvent(any(InventoryTransactionEvent.class));
        assertTrue(outer.canExecute());
        assertTrue(outer.execute());
        assertTrue(accepted.get());
        assertEquals(8, first.total() + second.total());
        assertEquals(4, first.inventory.getItem(1).getCount());
        assertEquals(4, second.inventory.getItem(1).getCount());
    }

    @Test void nestedPhysicalPickupCannotBeLostByTransactionRollback() throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction transaction = f.move(1, true);
        Source source = new Source(f.player);
        AtomicBoolean attempted = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean(true);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            if (((EntityInventoryChangeEvent) call.getArgument(0)).getSlot() == 1 && attempted.compareAndSet(false, true)) {
                accepted.set(f.player.pickupEntity(source.entity, true));
                throw new IllegalStateException("transaction callback failed after attempted pickup");
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertTrue(transaction.canExecute());
        assertThrows(IllegalStateException.class, transaction::execute);
        assertEquals(5, f.total() + (source.closed.get() ? 0 : 1));
        assertFalse(accepted.get());
        assertFalse(source.closed.get());
        reset(plugins);
        assertTrue(f.player.pickupEntity(source.entity, true));
        assertEquals(5, f.total());
    }

    @Test void nestedTransactionCannotReenterThePhysicalPickupInventory() throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction transaction = f.move(1, false);
        Source source = new Source(f.player);
        AtomicBoolean accepted = new AtomicBoolean(true);
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            assertTrue(transaction.canExecute());
            accepted.set(transaction.execute());
            return null;
        }).when(plugins).callEvent(any(InventoryPickupItemEvent.class));
        assertTrue(f.player.pickupEntity(source.entity, true));
        assertFalse(accepted.get());
        assertEquals(5, f.total());
        assertTrue(source.closed.get());
    }

    @Test void failedFurnaceTransferDoesNotReleaseItsExperience() throws Exception {
        assertFurnaceTransfer(true);
    }

    @Test void committedFurnaceTransferReleasesItsExperienceOnce() throws Exception {
        assertFurnaceTransfer(false);
    }

    @Test void destinationFirstBundleMovePreservesAuthoredNbtAcrossRuntimeIdentityAssignment() throws Exception {
        assertBundleMove(false);
    }

    @Test void rewrittenBundleSerialMustNotBeIgnoredAsRuntimeIdentity() throws Exception {
        assertBundleMove(true);
    }

    private void assertBundleMove(boolean rewriteSerial) throws Exception {
        Fixture f = new Fixture();
        ItemBundle bundle = new ItemBundle();
        bundle.getBundleId();
        bundle.setNamedTag(bundle.getNamedTag().putString("km_serial", "original"));
        bundle.getInventory().setItem(0, Item.get(Item.STONE, 0, 3), false);
        f.inventory.setItemForce(0, bundle);
        Item source = f.inventory.getItem(0);
        InventoryTransaction transaction = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(f.inventory, 1, Item.get(Item.AIR), source.clone()),
                new SlotChangeAction(f.inventory, 0, source.clone(), Item.get(Item.AIR))));
        assertTrue(transaction.canExecute());
        if (rewriteSerial) {
            var plugins = Server.getInstance().getPluginManager();
            doAnswer(call -> {
                EntityInventoryChangeEvent event = call.getArgument(0);
                if (event.getSlot() == 1) {
                    Item changed = event.getNewItem().clone();
                    changed.setNamedTag(changed.getNamedTag().putString("km_serial", "changed"));
                    event.setNewItem(changed);
                }
                return null;
            }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        }
        assertEquals(!rewriteSerial, transaction.execute());
        ItemBundle result = (ItemBundle) f.inventory.getItem(rewriteSerial ? 0 : 1);
        assertEquals("original", result.getNamedTag().getString("km_serial"));
        assertEquals(3, result.getInventory().getItem(0).getCount());
        assertTrue(f.inventory.getItem(rewriteSerial ? 1 : 0).isNull());
    }

    private void assertFurnaceTransfer(boolean rejectDestination) throws Exception {
        Fixture f = new Fixture();
        f.inventory.clear(0, false);
        BlockEntityFurnace holder = mock(BlockEntityFurnace.class);
        FurnaceInventory furnace = new FurnaceInventory(holder);
        furnace.setItemForce(2, Item.get(Item.EMERALD, 0, 4));
        InventoryTransaction transaction = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(furnace, 2, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                new SlotChangeAction(f.inventory, 0, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4))));
        assertTrue(transaction.canExecute());
        if (rejectDestination) {
            var plugins = Server.getInstance().getPluginManager();
            doAnswer(call -> { ((EntityInventoryChangeEvent) call.getArgument(0)).setCancelled(); return null; })
                    .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        }
        assertEquals(!rejectDestination, transaction.execute());
        assertEquals(4, furnace.getResult().getCount() + f.total());
        if (rejectDestination) verify(holder, never()).releaseExperience();
        else {
            verify(holder, times(1)).releaseExperience();
            assertFalse(transaction.execute());
            verify(holder, times(1)).releaseExperience();
        }
    }

    private static final class Source {
        final EntityItem entity;
        final java.util.function.Supplier<Boolean> closed;
        Source(Player player) {
            entity = NativePublishedPickupSourceFixture.create(player, Item.get(Item.EMERALD, 0, 1));
            closed = entity::isClosed;
        }
    }

    private void assertRewrittenCount(int rewrittenSlot, int count) throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction transaction = f.move(1, false);
        assertTrue(transaction.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == rewrittenSlot) event.setNewItem(Item.get(Item.EMERALD, 0, count));
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        transaction.execute();
        assertEquals(4, f.total(), "accepted but rewritten counts must not bypass the matched transfer");
        assertEquals(4, f.inventory.getItem(0).getCount());
        assertTrue(f.inventory.getItem(1).isNull());
        assertFalse(transaction.hasExecuted());
    }

    @Test void acceptedNormalMovePreservesAllFourItems() throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction transaction = f.move(1, false);
        assertTrue(transaction.canExecute());
        assertTrue(transaction.execute());
        assertTrue(transaction.hasExecuted());
        assertEquals(4, f.total());
        assertTrue(f.inventory.getItem(0).isNull());
        assertEquals(4, f.inventory.getItem(1).getCount());
    }

    private void assertCancelled(boolean destinationFirst, int rejectedSlot) throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction transaction = f.move(1, destinationFirst);
        assertTrue(transaction.canExecute(), "use the production matcher, with valid balanced slot actions");
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == rejectedSlot) event.setCancelled();
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        transaction.execute();
        assertEquals(4, f.total(), "a rejected half of a move must neither mint nor lose items");
        assertEquals(4, f.inventory.getItem(0).getCount());
        assertTrue(f.inventory.getItem(1).isNull());
        assertFalse(transaction.hasExecuted());
    }

    private void assertThrowing(boolean destinationFirst, int failingSlot) throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction transaction = f.move(1, destinationFirst);
        assertTrue(transaction.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            if (((EntityInventoryChangeEvent) call.getArgument(0)).getSlot() == failingSlot) {
                throw new IllegalStateException("late slot callback failed");
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        assertThrows(IllegalStateException.class, transaction::execute);
        assertEquals(4, f.total(), "a failed half of a move must restore its earlier write");
        assertEquals(4, f.inventory.getItem(0).getCount());
        assertTrue(f.inventory.getItem(1).isNull());
        assertFalse(transaction.hasExecuted());
    }

    private static final class Fixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory inventory;

        Fixture() throws Exception { this(false); }

        Fixture(boolean spyInventory) throws Exception {
            inventory = spyInventory ? spy(new PlayerInventory(player)) : new PlayerInventory(player);
            Field server = Entity.class.getDeclaredField("server");
            server.setAccessible(true);
            server.set(player, Server.getInstance());
            Field nativeInventory = EntityHumanType.class.getDeclaredField("inventory");
            nativeInventory.setAccessible(true);
            nativeInventory.set(player, inventory);
            player.spawned = true;
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(false).when(player).isSpectator();
            doReturn(false).when(player).isCreative();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));
            doReturn(0).when(player).getWindowId(any());
            doReturn(Collections.emptyMap()).when(player).getViewers();
            inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        }

        InventoryTransaction move(int destination, boolean destinationFirst) {
            InventoryAction remove = new SlotChangeAction(inventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR));
            InventoryAction add = new SlotChangeAction(inventory, destination, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4));
            return new InventoryTransaction(player, destinationFirst ? List.of(add, remove) : List.of(remove, add));
        }

        int total() { return inventory.getContents().values().stream().mapToInt(Item::getCount).sum(); }
    }
}
