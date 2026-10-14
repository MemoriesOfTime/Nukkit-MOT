package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.passive.EntityHorseBase;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryBulkRollbackNotificationTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetEvents() { reset(Server.getInstance().getPluginManager()); }

    @Test void failedSaddleBulkObserverRestoresTheNativeHolderFlag() {
        AtomicBoolean saddled = new AtomicBoolean();
        HorseInventory inventory = horse(saddled, Map.of());
        AssertionError failure = failFirstCredit(inventory);

        assertSame(failure, assertThrows(AssertionError.class, () -> inventory.addItem(Item.get(Item.SADDLE))));

        assertTrue(inventory.getItem(0).isNull());
        assertFalse(saddled.get());
        assertEquals(0, inventory.addItem(Item.get(Item.SADDLE)).length, "failed bulk write ownership must retire");
        assertTrue(saddled.get());
    }

    @Test void failedHorseArmorBulkCreditBroadcastsTheRestoredEmptyEquipment() {
        Player viewer = mock(Player.class);
        List<Item> armor = new ArrayList<>();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof MobArmorEquipmentPacket packet) armor.add(packet.body.clone());
            return true;
        }).when(viewer).dataPacket(any(DataPacket.class));
        HorseInventory inventory = horse(new AtomicBoolean(), Map.of(1, viewer));
        AssertionError failure = failFirstCredit(inventory);

        assertSame(failure, assertThrows(AssertionError.class,
                () -> inventory.addItem(Item.get(Item.DIAMOND_HORSE_ARMOR))));

        assertTrue(inventory.getItem(1).isNull());
        assertEquals(2, armor.size());
        assertEquals(Item.DIAMOND_HORSE_ARMOR, armor.get(0).getId());
        assertTrue(armor.get(1).isNull());
    }

    @Test void restoredBulkObserverCannotRepeatTheOriginalExpectedCredit() {
        AtomicBoolean saddled = new AtomicBoolean();
        HorseInventory inventory = horse(saddled, Map.of());
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean notified = new AtomicBoolean();
        AtomicBoolean nestedWrite = new AtomicBoolean(true);
        AtomicInteger nestedRemaining = new AtomicInteger();
        AssertionError failure = new AssertionError("credit observer failed");
        doAnswer(call -> {
            call.callRealMethod();
            if (!inventory.getItem(0).isNull() && first.compareAndSet(false, true)) throw failure;
            if (inventory.getItem(0).isNull() && notified.compareAndSet(false, true)) {
                nestedWrite.set(inventory.setItem(0, Item.get(Item.SADDLE), false));
                nestedRemaining.set(inventory.addItem(Item.get(Item.SADDLE))[0].getCount());
            }
            return null;
        }).when(inventory).onSlotChange(eq(0), any(Item.class), anyBoolean());

        assertSame(failure, assertThrows(AssertionError.class, () -> inventory.addItem(Item.get(Item.SADDLE))));

        assertTrue(notified.get());
        assertFalse(nestedWrite.get());
        assertEquals(1, nestedRemaining.get());
        assertTrue(inventory.getItem(0).isNull());
        assertFalse(saddled.get());
    }

    @Test void bulkRollbackObserverFailureCannotReplaceTheOriginalWriteFailure() {
        AtomicBoolean saddled = new AtomicBoolean();
        HorseInventory inventory = horse(saddled, Map.of());
        AssertionError writeFailure = new AssertionError("original bulk observer failed");
        IllegalStateException rollbackFailure = new IllegalStateException("rollback observer failed");
        doAnswer(call -> {
            call.callRealMethod();
            if (inventory.getItem(0).isNull()) throw rollbackFailure;
            throw writeFailure;
        }).when(inventory).onSlotChange(eq(0), any(Item.class), anyBoolean());

        assertSame(writeFailure, assertThrows(AssertionError.class, () -> inventory.addItem(Item.get(Item.SADDLE))));

        assertArrayEquals(new Throwable[]{rollbackFailure}, writeFailure.getSuppressed());
        assertTrue(inventory.getItem(0).isNull());
        assertFalse(saddled.get());
    }

    @Test void cancelledBulkWriteDoesNotReplayAnUnchangedNativeSlot() {
        HorseInventory inventory = horse(new AtomicBoolean(), Map.of());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { ((EntityInventoryChangeEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));

        Item[] remaining = inventory.addItem(Item.get(Item.SADDLE));

        assertEquals(1, remaining.length);
        assertTrue(inventory.getItem(0).isNull());
        verify(inventory, never()).onSlotChange(anyInt(), any(Item.class), anyBoolean());
    }

    @Test void acceptedHorseBulkWriteKeepsItsSingleNativeNotification() {
        AtomicBoolean saddled = new AtomicBoolean();
        HorseInventory inventory = horse(saddled, Map.of());

        assertEquals(0, inventory.addItem(Item.get(Item.SADDLE)).length);

        assertTrue(saddled.get());
        verify(inventory).onSlotChange(eq(0), any(Item.class), anyBoolean());
    }

    private static HorseInventory horse(AtomicBoolean saddled, Map<Integer, Player> viewers) {
        EntityHorseBase holder = mock(EntityHorseBase.class);
        when(holder.getViewers()).thenReturn(viewers);
        when(holder.canWearHorseArmor()).thenReturn(true);
        when(holder.isSaddled()).thenAnswer(call -> saddled.get());
        doAnswer(call -> { saddled.set(call.getArgument(0)); return null; }).when(holder).setSaddled(anyBoolean());
        return spy(new HorseInventory(holder, 0));
    }

    private static AssertionError failFirstCredit(HorseInventory inventory) {
        AssertionError failure = new AssertionError("native equipment observer failed");
        AtomicBoolean thrown = new AtomicBoolean();
        doAnswer(call -> {
            call.callRealMethod();
            if (thrown.compareAndSet(false, true)) throw failure;
            return null;
        }).when(inventory).onSlotChange(anyInt(), any(Item.class), anyBoolean());
        return failure;
    }
}
