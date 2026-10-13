package cn.nukkit.inventory.transaction;

import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.inventory.RepairItemEvent;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.PlayerEnderChestInventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeRecipeCallbackAtomicityTest {
    @BeforeAll static void init() { CraftAnvilAtomicNativeAuditTest.init(); }

    @ParameterizedTest @ValueSource(strings={"craft", "rename", "repair"})
    void nativeSetterErrorRestoresInputsAndDoesNotPayExperience(String recipe) {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture();
        InventoryTransaction tx = transaction(f, recipe);
        assertTrue(tx.canExecute());
        AssertionError expected = new AssertionError("native output callback");
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change && !change.getNewItem().isNull()) {
                f.offhand.setItemForce(0, Item.get(Item.DIAMOND, 0, 5));
                throw expected;
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertSame(expected, assertThrows(AssertionError.class, tx::execute));
        assertOriginal(f, recipe); assertTrue(f.offhand.getItem(0).isNull()); assertFalse(tx.hasExecuted());
        verify(f.player, never()).setExperience(anyInt(), anyInt());
        doNothing().when(f.plugins).callEvent(any(Event.class));
        assertTrue(tx.execute(), "same transaction can retry a fully restored refused attempt");
    }

    @ParameterizedTest @ValueSource(strings={"main", "armor", "offhand", "ender", "ui51", "raw103"})
    void lateAcceptedOutputCannotLeaveAnEscapedCopyInAnyCanonicalBacking(String view) {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture();
        var ender = new PlayerEnderChestInventory(f.player); when(f.player.getEnderChestInventory()).thenReturn(ender);
        Item hidden = Item.get(Item.DIAMOND, 0, 7); hidden.setNamedTag(new CompoundTag().putString("original", "opaque"));
        ender.slots.put(101, hidden.clone());
        var tx = f.planks(); AtomicInteger callbacks = new AtomicInteger();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change && change.getNewItem().getId() == Item.PLANKS) {
                callbacks.incrementAndGet();
                switch (view) {
                    case "main" -> f.main.setItemForce(35, change.getNewItem());
                    case "armor" -> f.main.setItemForce(f.main.getSize(), change.getNewItem());
                    case "offhand" -> f.offhand.setItemForce(0, change.getNewItem());
                    case "ender" -> { ender.setSize(28); ender.setItemForce(27, change.getNewItem()); }
                    case "ui51" -> f.ui.setItemForce(51, change.getNewItem());
                    case "raw103" -> f.ui.slots.put(103, change.getNewItem());
                    default -> throw new AssertionError(view);
                }
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertFalse(tx.execute()); assertEquals(1, callbacks.get()); assertOriginal(f, "craft");
        assertEquals(27, ender.getSize()); assertEquals(1, ender.slots.size()); assertTrue(ender.slots.get(101).equalsExact(hidden));
        assertTrue(f.main.slots.isEmpty()); assertTrue(f.offhand.slots.isEmpty());
        assertFalse(f.ui.slots.containsKey(51)); assertFalse(f.ui.slots.containsKey(103));
    }

    @ParameterizedTest @ValueSource(strings={"craft", "rename", "repair"})
    void lastSetterRestoringEarlierSourceRollsBackEveryOutput(String recipe) {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = transaction(f, recipe);
        Item original = (recipe.equals("craft") ? f.grid : f.anvil).getItem(0);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change && !change.getNewItem().isNull()) {
                (recipe.equals("craft") ? f.grid : f.anvil).setItemForce(0, original);
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertFalse(tx.execute()); assertOriginal(f, recipe); assertFalse(tx.hasExecuted());
        verify(f.player, never()).setExperience(anyInt(), anyInt());
    }

    @ParameterizedTest @ValueSource(strings={"craft", "rename", "repair"})
    void retryAfterSuccessfulCommitCannotApplyTheSameRecipeAgain(String recipe) {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = transaction(f, recipe);
        assertTrue(tx.execute()); assertTrue(tx.hasExecuted()); Item committed = f.main.getItem(0);
        assertFalse(tx.execute()); assertTrue(f.main.getItem(0).equalsExact(committed));
        if (!recipe.equals("craft")) verify(f.player, times(1)).setExperience(0, 9);
    }

    @ParameterizedTest @ValueSource(strings={"craft", "rename"})
    void sameTransactionReentryFromSetterCannotExecuteAnotherCopy(String recipe) {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = transaction(f, recipe); AtomicInteger nested = new AtomicInteger();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityInventoryChangeEvent && nested.getAndIncrement() == 0) {
                assertFalse(tx.execute());
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertTrue(tx.execute()); assertTrue(nested.get() > 0); assertEquals(recipe.equals("craft") ? 4 : 1, f.main.getItem(0).getCount());
    }

    @ParameterizedTest @ValueSource(strings={"craft", "rename", "repair"})
    void eventCancellationKeepsInputsAndSuppressesCommittedEffects(String recipe) {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = transaction(f, recipe);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof CraftItemEvent event) event.setCancelled();
            if (call.getArgument(0) instanceof RepairItemEvent event) event.setCancelled();
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertFalse(tx.execute()); assertOriginal(f, recipe); assertFalse(tx.hasExecuted());
        verify(f.player, never()).setExperience(anyInt(), anyInt());
        verify(f.player, never()).awardAchievement(anyString());
    }

    @Test void eventCannotMutateFrozenInputOrAppendNewNativeAction() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = f.planks();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof CraftItemEvent) {
                tx.getInputList().get(0).setCount(2);
                tx.getActionList().add(new SlotChangeAction(f.main, 1, Item.AIR_ITEM.clone(), Item.get(Item.DIAMOND)));
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertFalse(tx.execute()); assertOriginal(f, "craft"); assertTrue(f.main.getItem(1).isNull());
    }

    @Test void authoritativeCraftEventNbtIsKeptInTheAcceptedOutput() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = f.planks();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof CraftItemEvent) {
                Item output = tx.getPrimaryOutput().clone(); output.setCustomName("Server NBT");
                output.setNamedTag(output.getNamedTag().putString("receiptPayload", "unchanged")); tx.setPrimaryOutput(output);
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertTrue(tx.execute()); assertEquals(4, f.main.getItem(0).getCount());
        assertEquals("Server NBT", f.main.getItem(0).getCustomName()); assertEquals("unchanged", f.main.getItem(0).getNamedTag().getString("receiptPayload"));
    }

    @Test void authoritativeRenameEventNbtIsKeptAndPaidOnce() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = f.rename();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof RepairItemEvent event) event.getNewItem().setCustomName("Event name");
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertTrue(tx.execute()); assertEquals("Event name", f.main.getItem(0).getCustomName());
        verify(f.player, times(1)).setExperience(0, 9);
    }

    @Test void callbackReplacingTargetNbtIsRefusedAndRestoresOriginalOpaqueInput() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = f.rename(); Item input = f.anvil.getItem(0);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityInventoryChangeEvent event && !event.getNewItem().isNull()) {
                Item replacement = event.getNewItem().clone(); replacement.setCustomName("callback forged"); event.setNewItem(replacement);
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertFalse(tx.execute()); assertTrue(f.anvil.getItem(0).equalsExact(input)); assertTrue(f.main.slots.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"closed", "version", "window", "limits"})
    void changedActorOrCapacityCannotCommitAValidatedRepair(String change) {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = f.rename();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof RepairItemEvent) {
                switch (change) {
                    case "closed" -> f.player.closed = true;
                    case "version" -> f.player.protocol--;
                    case "window" -> when(f.player.getWindowById(cn.nukkit.Player.ANVIL_WINDOW_ID)).thenReturn(null);
                    case "limits" -> f.main.setMaxStackSize(1);
                    default -> throw new AssertionError(change);
                }
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertFalse(tx.execute()); assertOriginal(f, "rename"); assertEquals(64, f.main.getMaxStackSize());
        verify(f.player, never()).setExperience(anyInt(), anyInt());
    }

    @Test void postCommitExperienceNotificationErrorNeverRefundsOrRepeatsAcceptedOutput() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = f.rename(); Error expected = new AssertionError("XP notification");
        doThrow(expected).when(f.player).setExperience(anyInt(), anyInt());
        assertSame(expected, assertThrows(Error.class, tx::execute));
        assertTrue(tx.hasExecuted()); assertTrue(f.anvil.getItem(0).isNull()); assertEquals(1, f.main.getItem(0).getCount());
        assertFalse(tx.execute()); verify(f.player, times(1)).setExperience(0, 9);
    }

    private static InventoryTransaction transaction(CraftAnvilAtomicNativeAuditTest.Fixture f, String recipe) {
        return switch (recipe) { case "craft" -> f.planks(); case "rename" -> f.rename(); case "repair" -> CraftAnvilCancelledNativeWriteTest.repair(f); default -> throw new AssertionError(recipe); };
    }
    private static void assertOriginal(CraftAnvilAtomicNativeAuditTest.Fixture f, String recipe) {
        assertTrue(f.main.getItem(0).isNull());
        if (recipe.equals("craft")) { assertEquals(Item.LOG, f.grid.getItem(0).getId()); assertEquals(1, f.grid.getItem(0).getCount()); }
        else { assertEquals(Item.IRON_SWORD, f.anvil.getItem(0).getId()); assertEquals(1, f.anvil.getItem(0).getCount());
            if (recipe.equals("repair")) { assertEquals(100, f.anvil.getItem(0).getDamage()); assertEquals(1, f.anvil.getItem(1).getCount()); }
        }
    }
}
