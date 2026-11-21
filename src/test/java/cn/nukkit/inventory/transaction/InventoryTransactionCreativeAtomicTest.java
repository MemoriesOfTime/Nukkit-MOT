package cn.nukkit.inventory.transaction;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.action.CreativeInventoryAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemMace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryTransactionCreativeAtomicTest {
    private Map<Item, CreativeItemGroup> previousItems;
    private List<CreativeItemGroup> previousGroups;

    @BeforeAll static void initialize() { MockServer.init(); }
    @BeforeEach void catalog() {
        var catalog = Item.getCreativeItemsAndGroups();
        previousItems = new LinkedHashMap<>(catalog.getContents());
        previousGroups = new ArrayList<>(catalog.getGroups());
        Item.addCreativeItem(emeralds(1));
        Item.addCreativeItem(new ItemMace());
    }
    @AfterEach void cleanup() {
        reset(Server.getInstance().getPluginManager());
        var catalog = Item.getCreativeItemsAndGroups();
        catalog.clear();
        catalog.getContents().putAll(previousItems);
        catalog.getGroups().addAll(previousGroups);
    }

    @Test void neutralVirtualDeleteCannotBypassACancelledNativeSource() throws Exception { cancelled(false, 0); }
    @Test void nativeCreditBeforeCancelledSourceIsRolledBack() throws Exception { cancelled(true, 0); }
    @Test void cancelledDestinationRestoresAnEarlierSource() throws Exception { cancelled(false, 1); }

    private void cancelled(boolean creditFirst, int cancelledSlot) throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction tx = f.move(creditFirst);
        duringSlot(event -> { if (event.getSlot() == cancelledSlot) event.setCancelled(); });
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void nativeObserverFailureRestoresBothPhysicalSlotsAndReleasesClaim() throws Exception {
        Fixture f = new Fixture(true);
        InventoryTransaction tx = f.move(false);
        doThrow(new IllegalStateException("observer failed")).when(f.inventory)
                .onSlotChange(eq(1), any(Item.class), eq(false));
        assertThrows(IllegalStateException.class, tx::execute);
        assertOriginal(f, tx);
        doCallRealMethod().when(f.inventory).onSlotChange(eq(1), any(Item.class), eq(false));
        assertTrue(tx.execute());
        assertEquals(4, f.inventory.getItem(1).getCount());
        assertTrue(f.inventory.getItem(0).isNull());
    }

    @Test void cancelledDebitCannotCommitAnAdditionalCatalogCredit() throws Exception {
        Fixture f = new Fixture();
        var tx = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(f.inventory, 0, emeralds(4), air()), create(emeralds(4)),
                new SlotChangeAction(f.inventory, 1, air(), emeralds(8))));
        assertTrue(tx.canExecute());
        duringSlot(event -> { if (event.getSlot() == 0) event.setCancelled(); });
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void nativeCallbackCannotRewriteCountAndMintUnmatchedItems() throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction tx = f.move(false);
        duringSlot(event -> { if (event.getSlot() == 1) event.setNewItem(emeralds(8)); });
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void finalPhysicalPostconditionRejectsAnEarlierHalfRewrittenByALaterCallback() throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction tx = f.move(true);
        duringSlot(event -> { if (event.getSlot() == 0) f.inventory.slots.put(1, emeralds(8)); });
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void nestedMixedTransactionCannotConsumeTheOuterClaimedSource() throws Exception {
        Fixture f = new Fixture();
        InventoryTransaction outer = f.move(false);
        var inner = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(f.inventory, 0, emeralds(4), air()), neutral(),
                new SlotChangeAction(f.inventory, 2, air(), emeralds(4))));
        AtomicBoolean entered = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean(true);
        duringTransaction(() -> {
            if (entered.compareAndSet(false, true)) accepted.set(inner.execute());
        });
        assertTrue(outer.execute());
        assertFalse(accepted.get());
        assertTrue(f.inventory.getItem(0).isNull());
        assertEquals(4, f.inventory.getItem(1).getCount());
        assertTrue(f.inventory.getItem(2).isNull());
    }

    @Test void modeChangeAfterNativeCallbackRollsBackCatalogCreation() throws Exception {
        lateCreateChange((f, action, tx) -> f.creative.set(false));
    }
    @Test void catalogRemovalAfterNativeCallbackRollsBackCatalogCreation() throws Exception {
        lateCreateChange((f, action, tx) -> Item.removeCreativeItem(emeralds(1)));
    }
    @Test void sessionVersionChangeAfterNativeCallbackCannotKeepUnavailableCreation() throws Exception {
        Fixture f = new Fixture();
        f.inventory.clear(0, false);
        Item mace = new ItemMace();
        var tx = new InventoryTransaction(f.player, List.of(create(mace),
                new SlotChangeAction(f.inventory, 1, air(), mace)));
        assertTrue(tx.canExecute());
        duringSlot(event -> f.version.set(GameVersion.V1_20_0));
        assertFalse(assertDoesNotThrow(tx::execute));
        assertTrue(f.inventory.getItem(1).isNull());
        assertFalse(tx.hasExecuted());
    }
    @Test void validCatalogSourceCountCannotBeChangedByANativeCallback() throws Exception {
        lateCreateChange((f, action, tx) -> action.setSourceItem(emeralds(5)));
    }
    @Test void fullNbtCannotBeChangedByANativeCallback() throws Exception {
        lateCreateChange((f, action, tx) -> action.setSourceItem(marked()));
    }
    @Test void virtualTargetCannotBeChangedByANativeCallback() throws Exception {
        lateCreateChange((f, action, tx) -> action.setTargetItem(emeralds(4)));
    }
    @Test void virtualTypeCannotBeChangedByANativeCallback() throws Exception {
        lateCreateChange((f, action, tx) -> setType(action, 47));
    }
    @Test void removedVirtualCreditCannotLeaveItsNativeGrantCommitted() throws Exception {
        lateCreateChange((f, action, tx) -> tx.getActionList().remove(action));
    }
    @Test void replacedVirtualCreditCannotLeaveItsNativeGrantCommitted() throws Exception {
        lateCreateChange((f, action, tx) -> tx.getActionList().set(0, create(emeralds(4))));
    }
    @Test void changedTrackedInventoryMembershipCannotCommitTheGrant() throws Exception {
        lateCreateChange((f, action, tx) -> tx.getInventories().clear());
    }

    private void lateCreateChange(Change change) throws Exception {
        Fixture f = new Fixture();
        f.inventory.clear(0, false);
        CreativeInventoryAction action = create(emeralds(4));
        var tx = new InventoryTransaction(f.player, List.of(action,
                new SlotChangeAction(f.inventory, 1, air(), emeralds(4))));
        assertTrue(tx.canExecute());
        duringSlot(event -> change.apply(f, action, tx));
        assertFalse(assertDoesNotThrow(tx::execute));
        assertTrue(f.inventory.getContents().isEmpty(), "no native grant survives invalidated virtual credit");
        assertFalse(tx.hasExecuted());
    }

    @Test void transactionCallbackCannotReplaceBalancedVirtualAndPhysicalItemsTogether() throws Exception {
        Fixture f = new Fixture();
        f.inventory.clear(0, false);
        CreativeInventoryAction virtual = create(emeralds(4));
        SlotChangeAction grant = new SlotChangeAction(f.inventory, 1, air(), emeralds(4));
        var tx = new InventoryTransaction(f.player, List.of(virtual, grant));
        assertTrue(tx.canExecute());
        duringTransaction(() -> {
            virtual.setSourceItem(emeralds(5));
            grant.setTargetItem(emeralds(5));
        });
        assertFalse(assertDoesNotThrow(tx::execute));
        assertTrue(f.inventory.getItem(1).isNull());
    }

    @Test void transactionCallbackCannotAppendAVirtualActionEvenIfItIsNeutral() throws Exception {
        Fixture f = new Fixture();
        var tx = f.move(false);
        duringTransaction(() -> tx.getActionList().add(neutral()));
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void finalModeChangeAlsoRejectsANeutralVirtualDelete() throws Exception {
        Fixture f = new Fixture();
        var tx = f.move(false);
        duringSlot(event -> { if (event.getSlot() == 1) f.creative.set(false); });
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void initialNativeValidationCannotMutateTheActionListBeingMatched() throws Exception {
        Fixture f = new Fixture(true);
        var tx = f.move(false);
        AtomicBoolean entered = new AtomicBoolean();
        doAnswer(call -> {
            Item current = (Item) call.callRealMethod();
            if (entered.compareAndSet(false, true)) tx.getActionList().remove(1);
            return current;
        }).when(f.inventory).getItem(0);
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void removedPhysicalActionStillResynchronizesItsOriginalSlot() throws Exception {
        Fixture f = new Fixture(true);
        f.player.protocol = GameVersion.V1_20_0.getProtocol();
        var tx = f.move(false);
        clearInvocations(f.inventory);
        duringTransaction(() -> tx.getActionList().remove(0));
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
        verify(f.inventory).sendSlot(0, f.player);
        verify(f.inventory).sendSlot(1, f.player);
    }

    @Test void transactionCallbackCannotReorderTheOriginalMixedActions() throws Exception {
        Fixture f = new Fixture();
        var tx = f.move(false);
        duringTransaction(() -> java.util.Collections.reverse(tx.getActionList()));
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void nativeDeletionCallbackCannotReplaceItsApprovedVirtualSink() throws Exception {
        Fixture f = new Fixture();
        Item old = marked();
        f.inventory.setItemForce(0, old);
        CreativeInventoryAction sink = delete(old);
        var tx = new InventoryTransaction(f.player, List.of(sink,
                new SlotChangeAction(f.inventory, 0, old, air())));
        assertTrue(tx.canExecute());
        duringSlot(event -> sink.setTargetItem(emeralds(4)));
        assertFalse(assertDoesNotThrow(tx::execute));
        assertTrue(f.inventory.getItem(0).equalsExact(old));
        assertFalse(tx.hasExecuted());
    }

    @Test void virtualActionWithNullSourceAfterNativeCallbackCannotKeepAGrant() throws Exception {
        lateCreateChange((f, action, tx) -> action.setSourceItem(null));
    }

    @Test void lastPhysicalReadCallbackCannotInvalidateCreativeModeAfterTheFinalRecheck() throws Exception {
        Fixture f = new Fixture(true);
        f.inventory.clear(0, false);
        var tx = new InventoryTransaction(f.player, List.of(create(emeralds(4)),
                new SlotChangeAction(f.inventory, 1, air(), emeralds(4))));
        assertTrue(tx.canExecute());
        AtomicBoolean armed = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        doAnswer(call -> { call.callRealMethod(); armed.set(true); return null; })
                .when(f.inventory).onSlotChange(eq(1), any(Item.class), eq(false));
        doAnswer(call -> {
            Item current = (Item) call.callRealMethod();
            if (armed.get() && reads.incrementAndGet() == 2) f.creative.set(false);
            return current;
        }).when(f.inventory).getItem(1);
        assertFalse(assertDoesNotThrow(tx::execute));
        assertTrue(f.inventory.getItem(1).isNull());
        assertFalse(tx.hasExecuted());
    }

    @Test void pureNativeTransactionEventCannotAddAnUncapturedCreativeCredit() throws Exception {
        Fixture f = new Fixture();
        var tx = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(f.inventory, 0, emeralds(4), air()),
                new SlotChangeAction(f.inventory, 1, air(), emeralds(4))));
        duringTransaction(() -> tx.getActionList().add(neutral()));
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void pureNativeTransactionEventCannotCommitAnEmptyActionList() throws Exception {
        Fixture f = new Fixture();
        var tx = new InventoryTransaction(f.player, List.of(
                new SlotChangeAction(f.inventory, 0, emeralds(4), air()),
                new SlotChangeAction(f.inventory, 1, air(), emeralds(4))));
        duringTransaction(() -> tx.getActionList().clear());
        assertFalse(assertDoesNotThrow(tx::execute));
        assertOriginal(f, tx);
    }

    @Test void registeredCatalogVariantKeepsItsExactFullNbtAndDamage() throws Exception {
        Fixture f = new Fixture();
        f.inventory.clear(0, false);
        Item variant = Item.get(Item.DIAMOND_PICKAXE, 17, 1)
                .setNamedTag(new CompoundTag().putString("registered_variant", "authoritative"));
        Item.addCreativeItem(variant);
        var tx = new InventoryTransaction(f.player, List.of(create(variant.clone()),
                new SlotChangeAction(f.inventory, 1, air(), variant.clone())));
        assertTrue(tx.execute());
        assertTrue(f.inventory.getItem(1).equalsExact(variant));
    }

    @Test void legalNeutralVirtualMoveKeepsBothActionOrders() throws Exception {
        for (boolean creditFirst : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            var tx = f.move(creditFirst);
            assertTrue(tx.execute());
            assertTrue(f.inventory.getItem(0).isNull());
            assertEquals(4, f.inventory.getItem(1).getCount());
            assertFalse(tx.execute(), "a committed action cannot be replayed");
        }
    }

    @Test void legalIntentionalMarkedDeletionKeepsItsSinkSemantics() throws Exception {
        Fixture f = new Fixture();
        Item old = marked();
        f.inventory.setItemForce(0, old);
        var tx = new InventoryTransaction(f.player, List.of(delete(old),
                new SlotChangeAction(f.inventory, 0, old, air())));
        assertTrue(tx.canExecute());
        assertTrue(tx.execute());
        assertTrue(f.inventory.getItem(0).isNull());
        Item legacy = old.clone(); legacy.setCount(128);
        assertTrue(delete(legacy).isValid(f.player), "this patch must not restrict the virtual deletion sink");
    }

    @Test void catalogCreationWorksAcrossTheSupportedSessionWindow() throws Exception {
        for (GameVersion version : GameVersion.values()) {
            if (version.isNetEase() || version.getProtocol() < GameVersion.V1_20_0.getProtocol()
                    || version.getProtocol() > GameVersion.V1_26_50.getProtocol()) continue;
            Fixture f = new Fixture();
            f.version.set(version);
            f.inventory.clear(0, false);
            var tx = new InventoryTransaction(f.player, List.of(create(emeralds(4)),
                    new SlotChangeAction(f.inventory, 1, air(), emeralds(4))));
            assertTrue(tx.execute(), version.name());
            assertEquals(4, f.inventory.getItem(1).getCount(), version.name());
        }
    }

    @Test void customCreativeSubclassRetainsItsSeparateLegacyHook() throws Exception {
        Fixture f = new Fixture();
        f.inventory.clear(0, false);
        AtomicInteger calls = new AtomicInteger();
        CreativeInventoryAction custom = new CreativeInventoryAction(emeralds(4), air(),
                CreativeInventoryAction.TYPE_CREATE_ITEM) {
            @Override public boolean execute(Player source) { calls.incrementAndGet(); return true; }
        };
        var tx = new InventoryTransaction(f.player, List.of(custom,
                new SlotChangeAction(f.inventory, 1, air(), emeralds(4))));
        assertTrue(tx.execute());
        assertEquals(1, calls.get(), "only the exact core virtual class is safe to omit execution");
    }

    @FunctionalInterface private interface Change {
        void apply(Fixture fixture, CreativeInventoryAction action, InventoryTransaction tx) throws Exception;
    }
    @FunctionalInterface private interface SlotCallback {
        void apply(EntityInventoryChangeEvent event) throws Exception;
    }
    private static void duringSlot(SlotCallback callback) {
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { callback.apply(call.getArgument(0)); return null; })
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
    }
    private static void duringTransaction(Runnable callback) {
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { callback.run(); return null; })
                .when(plugins).callEvent(any(InventoryTransactionEvent.class));
    }
    private static void setType(CreativeInventoryAction action, int type) throws ReflectiveOperationException {
        Field field = CreativeInventoryAction.class.getDeclaredField("actionType");
        field.setAccessible(true); field.setInt(action, type);
    }
    private static void assertOriginal(Fixture f, InventoryTransaction tx) {
        assertEquals(4, f.inventory.getItem(0).getCount());
        assertTrue(f.inventory.getItem(1).isNull());
        assertFalse(tx.hasExecuted());
    }
    private static Item air() { return Item.get(Item.AIR); }
    private static Item emeralds(int count) { return Item.get(Item.EMERALD, 0, count); }
    private static Item marked() {
        return emeralds(4).setNamedTag(new CompoundTag().putString("owner_marker", "intentional deletion"));
    }
    private static CreativeInventoryAction create(Item item) {
        return new CreativeInventoryAction(item, air(), CreativeInventoryAction.TYPE_CREATE_ITEM);
    }
    private static CreativeInventoryAction delete(Item item) {
        return new CreativeInventoryAction(air(), item, CreativeInventoryAction.TYPE_DELETE_ITEM);
    }
    private static CreativeInventoryAction neutral() { return delete(air()); }

    private static final class Fixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory inventory;
        final AtomicBoolean creative = new AtomicBoolean(true);
        final AtomicReference<GameVersion> version = new AtomicReference<>(GameVersion.getLastVersion());
        Fixture() throws Exception { this(false); }
        Fixture(boolean observed) throws Exception {
            PlayerInventory actual = new PlayerInventory(player);
            inventory = observed ? spy(actual) : actual;
            Field field = Entity.class.getDeclaredField("server");
            field.setAccessible(true); field.set(player, Server.getInstance());
            doAnswer(call -> creative.get()).when(player).isCreative();
            doAnswer(call -> version.get()).when(player).getGameVersion();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));
            doReturn(0).when(player).getWindowId(any());
            inventory.setItemForce(0, emeralds(4));
        }
        InventoryTransaction move(boolean creditFirst) {
            InventoryAction debit = new SlotChangeAction(inventory, 0, emeralds(4), air());
            InventoryAction credit = new SlotChangeAction(inventory, 1, air(), emeralds(4));
            var tx = new InventoryTransaction(player, creditFirst
                    ? List.of(credit, neutral(), debit) : List.of(debit, neutral(), credit));
            assertTrue(tx.canExecute());
            return tx;
        }
    }
}
