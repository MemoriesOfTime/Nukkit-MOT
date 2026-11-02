package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.passive.EntityHorseBase;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.level.Level;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryRollbackNotificationTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetEvents() { reset(Server.getInstance().getPluginManager()); }

    @Test void refusedDestinationRestoresTheNativeHorseSaddleFlag() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(false);
        AtomicBoolean saddled = new AtomicBoolean();
        HorseInventory horse = horse(saddled, Map.of());
        horse.setItemForce(0, Item.get(Item.SADDLE));
        AtomicInteger events = refusePlayerWrite(player);

        InventoryTransaction transaction = move(player.player, horse, 0, player.inventory, 0, Item.get(Item.SADDLE));
        assertTrue(transaction.canExecute());
        assertFalse(transaction.execute());

        assertEquals(Item.SADDLE, horse.getItem(0).getId());
        assertTrue(saddled.get(), "the restored saddle slot must restore the holder flag");
        assertEquals(2, events.get(), "rollback must not replay cancellable setter events");
        assertTrue(player.inventory.getItem(0).isNull());
        assertFalse(transaction.hasExecuted());
    }

    @Test void refusedSourceRestoresTheNativeHorseUnsaddledFlag() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(false);
        player.inventory.setItemForce(0, Item.get(Item.SADDLE));
        AtomicBoolean saddled = new AtomicBoolean();
        HorseInventory horse = horse(saddled, Map.of());
        refusePlayerWrite(player);

        InventoryTransaction transaction = move(player.player, horse, 0, player.inventory, 0, Item.get(Item.SADDLE), true);
        assertTrue(transaction.canExecute());
        assertFalse(transaction.execute());

        assertTrue(horse.getItem(0).isNull());
        assertFalse(saddled.get(), "an aborted saddle credit must clear the provisional holder flag");
        assertEquals(Item.SADDLE, player.inventory.getItem(0).getId());
    }

    @Test void restoredHorseArmorIsBroadcastToOtherEntityViewers() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(false);
        Player viewer = mock(Player.class);
        List<Item> armorPackets = new ArrayList<>();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof MobArmorEquipmentPacket packet) armorPackets.add(packet.body.clone());
            return true;
        }).when(viewer).dataPacket(any(DataPacket.class));
        HorseInventory horse = horse(new AtomicBoolean(), Map.of(1, viewer));
        Item armor = Item.get(Item.DIAMOND_HORSE_ARMOR);
        horse.setItemForce(1, armor);
        armorPackets.clear();
        refusePlayerWrite(player);

        InventoryTransaction transaction = move(player.player, horse, 1, player.inventory, 0, armor);
        assertTrue(transaction.canExecute());
        assertFalse(transaction.execute());

        assertEquals(2, armorPackets.size(), "provisional removal and restored equipment both need native notifications");
        assertTrue(armorPackets.get(0).isNull());
        assertTrue(armor.equalsExact(armorPackets.get(1)));
    }

    @Test void restoredContainerRefreshesComparatorDirtyStateAndOtherViewers() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(false);
        BlockEntityChest holder = mock(BlockEntityChest.class);
        Level level = mock(Level.class);
        when(holder.getLevel()).thenReturn(level);
        ChestInventory chest = new ChestInventory(holder);
        chest.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        List<Integer> strengths = new ArrayList<>();
        doAnswer(call -> { strengths.add(ContainerInventory.calculateRedstone(chest)); return null; })
                .when(level).updateComparatorOutputLevel(holder);
        Player viewer = mock(Player.class);
        viewer.protocol = ProtocolInfo.CURRENT_PROTOCOL;
        when(viewer.getWindowId(chest)).thenReturn(1);
        List<Item> slots = new ArrayList<>();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof InventorySlotPacket packet && packet.slot == 0) slots.add(packet.item.clone());
            return true;
        }).when(viewer).dataPacket(any(DataPacket.class));
        chest.getViewers().add(viewer);
        clearInvocations(holder);
        refusePlayerWrite(player);

        InventoryTransaction transaction = move(player.player, chest, 0, player.inventory, 0, Item.get(Item.EMERALD, 0, 4));
        assertTrue(transaction.canExecute());
        assertFalse(transaction.execute());

        assertEquals(List.of(0, 1), strengths, "comparator must observe the authoritative restored contents");
        verify(holder, times(3)).setDirty();
        assertFalse(slots.isEmpty(), "other open viewers need an authoritative rollback slot update");
        assertEquals(4, slots.get(slots.size() - 1).getCount());
    }

    @Test void rollbackNotificationsCannotGrantItemsOrStartANestedTransfer() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(false);
        BlockEntityChest holder = mock(BlockEntityChest.class);
        ChestInventory chest = spy(new ChestInventory(holder));
        chest.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        AtomicBoolean notified = new AtomicBoolean();
        AtomicBoolean nestedWrite = new AtomicBoolean(true);
        AtomicBoolean nestedTransaction = new AtomicBoolean(true);
        doAnswer(call -> {
            if (!chest.getItem(0).isNull() && notified.compareAndSet(false, true)) {
                nestedWrite.set(chest.setItem(1, Item.get(Item.EMERALD, 0, 64), false));
                nestedTransaction.set(move(player.player, chest, 0, chest, 1, Item.get(Item.EMERALD, 0, 4)).execute());
            }
            return call.callRealMethod();
        }).when(chest).onSlotChange(eq(0), any(Item.class), anyBoolean());
        refusePlayerWrite(player);

        assertFalse(move(player.player, chest, 0, player.inventory, 0, Item.get(Item.EMERALD, 0, 4)).execute());

        assertTrue(notified.get());
        assertFalse(nestedWrite.get());
        assertFalse(nestedTransaction.get());
        assertEquals(4, chest.getItem(0).getCount());
        assertTrue(chest.getItem(1).isNull());
        reset(Server.getInstance().getPluginManager());
        assertTrue(move(player.player, chest, 0, player.inventory, 0, Item.get(Item.EMERALD, 0, 4)).execute(),
                "the claim must be released after rollback");
        assertEquals(4, player.inventory.getItem(0).getCount());
    }

    @Test void acceptedTransferKeepsItsNativeEquipmentNotification() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(false);
        AtomicBoolean saddled = new AtomicBoolean();
        HorseInventory horse = horse(saddled, Map.of());
        horse.setItemForce(0, Item.get(Item.SADDLE));

        InventoryTransaction transaction = move(player.player, horse, 0, player.inventory, 0, Item.get(Item.SADDLE));
        assertTrue(transaction.canExecute());
        assertTrue(transaction.execute());

        assertFalse(saddled.get());
        assertTrue(horse.getItem(0).isNull());
        assertEquals(Item.SADDLE, player.inventory.getItem(0).getId());
    }

    @Test void failingRollbackObserverDoesNotSkipOtherChangedSlots() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(false);
        player.inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        ChestInventory chest = spy(new ChestInventory(mock(BlockEntityChest.class)));
        Set<Integer> notified = new HashSet<>();
        IllegalStateException[] failures = {new IllegalStateException("rollback slot zero"), new IllegalStateException("rollback slot one")};
        doAnswer(call -> {
            call.callRealMethod();
            int slot = call.getArgument(0);
            if (chest.getItem(slot).isNull()) {
                notified.add(slot);
                throw failures[slot];
            }
            return null;
        }).when(chest).onSlotChange(anyInt(), any(Item.class), anyBoolean());
        refusePlayerWrite(player);
        InventoryTransaction transaction = new InventoryTransaction(player.player, List.of(
                new SlotChangeAction(chest, 0, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 2)),
                new SlotChangeAction(chest, 1, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 2)),
                new SlotChangeAction(player.inventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR))));

        IllegalStateException failure = assertThrows(IllegalStateException.class, transaction::execute);

        assertEquals(Set.of(0, 1), notified);
        assertTrue(failure == failures[0] || failure == failures[1]);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(4, player.inventory.getItem(0).getCount());
        assertTrue(chest.getContents().isEmpty());
    }

    @Test void rollbackObserverFailureCannotReplaceTheOriginalWriteFailure() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(true);
        player.inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        ChestInventory chest = spy(new ChestInventory(mock(BlockEntityChest.class)));
        AssertionError writeFailure = new AssertionError("source write observer failed");
        IllegalStateException rollbackFailure = new IllegalStateException("destination rollback observer failed");
        doThrow(writeFailure).when(player.inventory).onSlotChange(eq(0), any(Item.class), eq(false));
        doAnswer(call -> {
            call.callRealMethod();
            if (chest.getItem(0).isNull()) throw rollbackFailure;
            return null;
        }).when(chest).onSlotChange(eq(0), any(Item.class), anyBoolean());
        InventoryTransaction transaction = new InventoryTransaction(player.player, List.of(
                new SlotChangeAction(chest, 0, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4)),
                new SlotChangeAction(player.inventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR))));

        assertSame(writeFailure, assertThrows(AssertionError.class, transaction::execute));
        assertArrayEquals(new Throwable[]{rollbackFailure}, writeFailure.getSuppressed());
        assertEquals(4, player.inventory.getItem(0).getCount());
        assertTrue(chest.getItem(0).isNull());
    }

    @Test void failedBundleMirrorStillNotifiesOtherRestoredInventories() throws Exception {
        InventoryExactBulkWriteTest.Fixture player = new InventoryExactBulkWriteTest.Fixture(false);
        player.inventory.setItemForce(0, Item.get(Item.DIAMOND));
        ItemBundle bundle = spy(new ItemBundle());
        BundleInventory contents = bundle.getInventory();
        contents.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        IllegalStateException mirrorFailure = new IllegalStateException("bundle mirror failed");
        doAnswer(call -> {
            if (!contents.getItem(0).isNull()) throw mirrorFailure;
            return call.callRealMethod();
        }).when(bundle).saveNBT();
        ChestInventory chest = spy(new ChestInventory(mock(BlockEntityChest.class)));
        AtomicBoolean notified = new AtomicBoolean();
        doAnswer(call -> {
            call.callRealMethod();
            if (chest.getItem(0).isNull()) notified.set(true);
            return null;
        }).when(chest).onSlotChange(eq(0), any(Item.class), anyBoolean());
        refusePlayerWrite(player);
        InventoryTransaction transaction = new InventoryTransaction(player.player, List.of(
                new SlotChangeAction(contents, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                new SlotChangeAction(chest, 0, Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4)),
                new SlotChangeAction(player.inventory, 0, Item.get(Item.DIAMOND), Item.get(Item.AIR)),
                new SlotChangeAction(player.inventory, 1, Item.get(Item.AIR), Item.get(Item.DIAMOND))));

        assertSame(mirrorFailure, assertThrows(IllegalStateException.class, transaction::execute));
        assertTrue(notified.get(), "a failing public bundle mirror cannot skip the other restored observers");
        assertEquals(4, contents.getItem(0).getCount());
        assertTrue(chest.getItem(0).isNull());
        assertEquals(Item.DIAMOND, player.inventory.getItem(0).getId());
    }

    private static HorseInventory horse(AtomicBoolean saddled, Map<Integer, Player> viewers) {
        EntityHorseBase holder = mock(EntityHorseBase.class);
        when(holder.getViewers()).thenReturn(viewers);
        when(holder.canWearHorseArmor()).thenReturn(true);
        when(holder.isSaddled()).thenAnswer(call -> saddled.get());
        doAnswer(call -> { saddled.set(call.getArgument(0)); return null; }).when(holder).setSaddled(anyBoolean());
        return new HorseInventory(holder, 0);
    }

    private static AtomicInteger refusePlayerWrite(InventoryExactBulkWriteTest.Fixture player) {
        AtomicInteger events = new AtomicInteger();
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            events.incrementAndGet();
            if (event.getEntity() == player.player) event.setCancelled();
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        return events;
    }

    private static InventoryTransaction move(Player player, BaseInventory source, int sourceSlot,
                                             BaseInventory destination, int destinationSlot, Item stack) {
        return move(player, source, sourceSlot, destination, destinationSlot, stack, false);
    }

    private static InventoryTransaction move(Player player, BaseInventory source, int sourceSlot,
                                             BaseInventory destination, int destinationSlot, Item stack, boolean destinationFirst) {
        SlotChangeAction remove = new SlotChangeAction(source, sourceSlot, stack, Item.get(Item.AIR));
        SlotChangeAction add = new SlotChangeAction(destination, destinationSlot, Item.get(Item.AIR), stack);
        // Reverse source/destination meanings for the destination-first refusal control.
        if (destinationFirst) {
            remove = new SlotChangeAction(destination, destinationSlot, stack, Item.get(Item.AIR));
            add = new SlotChangeAction(source, sourceSlot, Item.get(Item.AIR), stack);
        }
        return new InventoryTransaction(player, destinationFirst ? List.of(add, remove) : List.of(remove, add));
    }
}
