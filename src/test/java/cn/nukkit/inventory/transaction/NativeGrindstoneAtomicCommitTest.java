package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.GrindItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.action.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native grinding setter/event/XP acceptance; no helper class or API absence is a regression assertion. */
@org.junit.jupiter.api.Timeout(10)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class NativeGrindstoneAtomicCommitTest {
    @BeforeAll static void init() { CraftAnvilAtomicNativeAuditTest.init(); }

    private record Grind(CraftAnvilAtomicNativeAuditTest.Fixture f, GrindstoneInventory inventory,
                         Item input, Item output, GrindstoneTransaction transaction) { }
    private Grind grind() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture();
        var inventory = new GrindstoneInventory(f.ui, new Position(0,64,0,f.level));
        when(f.player.getWindowById(Player.GRINDSTONE_WINDOW_ID)).thenReturn(inventory);
        Item input = Item.get(Item.IRON_SWORD,100,1);
        input.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_DAMAGE_ALL).setLevel(1));
        inventory.setItemForce(0,input);
        Item output = inventory.getResult();
        var transaction = new GrindstoneTransaction(f.player,List.of(
                new GrindstoneItemAction(Item.AIR_ITEM.clone(),input.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_INPUT),
                new GrindstoneItemAction(output.clone(),Item.AIR_ITEM.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT),
                new SlotChangeAction(inventory,0,input.clone(),Item.AIR_ITEM.clone()),
                new SlotChangeAction(f.main,0,Item.AIR_ITEM.clone(),output.clone())));
        assertTrue(transaction.canExecute());
        return new Grind(f,inventory,input,output,transaction);
    }

    private void unchanged(Grind g) {
        assertTrue(g.input.equalsExact(g.inventory.getEquipment())); assertTrue(g.f.main.getItem(0).isNull());
        assertFalse(g.transaction.hasExecuted()); assertEquals(0,g.transaction.getExperienceDropped());
    }
    @Test void grindCancelledDestinationRetainsInputAndNoExperience() {
        Grind g=grind();
        doAnswer(call -> { if (call.getArgument(0) instanceof EntityInventoryChangeEvent e
                && e.getNewItem().getId()==Item.IRON_SWORD) e.setCancelled(); return null; }).when(g.f.plugins).callEvent(any(Event.class));
        assertFalse(g.transaction.execute()); unchanged(g);
    }
    @Test void grindCancelledInputCannotGrantOutput() {
        Grind g=grind();
        doAnswer(call -> { if (call.getArgument(0) instanceof EntityInventoryChangeEvent e
                && e.getNewItem().isNull()) e.setCancelled(); return null; }).when(g.f.plugins).callEvent(any(Event.class));
        assertFalse(g.transaction.execute()); unchanged(g);
    }
    @Test void grindCommitsOnceAndExposesExperienceOnlyAfterInventoryCommit() {
        Grind g=grind();
        doAnswer(call -> { if (call.getArgument(0) instanceof EntityInventoryChangeEvent) {
            assertEquals(0,g.transaction.getExperienceDropped()); assertFalse(g.transaction.hasExecuted()); }
            return null; }).when(g.f.plugins).callEvent(any(Event.class));
        assertTrue(g.transaction.execute()); assertTrue(g.inventory.getEquipment().isNull());
        assertTrue(g.output.equalsExact(g.f.main.getItem(0))); assertTrue(g.transaction.hasExecuted());
        assertTrue(g.transaction.getExperienceDropped()>0);
        int experience=g.transaction.getExperienceDropped();
        assertFalse(g.transaction.execute()); assertEquals(experience,g.transaction.getExperienceDropped());
    }
    @Test void grindEventReentryDoesNotDuplicate() {
        Grind g=grind();
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> { if (call.getArgument(0) instanceof GrindItemEvent && once.compareAndSet(false,true)) assertFalse(g.transaction.execute()); return null; })
                .when(g.f.plugins).callEvent(any(Event.class));
        assertTrue(g.transaction.execute()); assertEquals(1,g.f.main.getItem(0).getCount());
    }
    @Test void grindIngredientOnlyLegalInputStillCommits() {
        Grind g=grind(); g.inventory.setItemForce(0,Item.AIR_ITEM.clone()); g.inventory.setItemForce(1,g.input.clone());
        var transaction=new GrindstoneTransaction(g.f.player,List.of(
                new GrindstoneItemAction(Item.AIR_ITEM.clone(),g.input.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_MATERIAL),
                new GrindstoneItemAction(g.output.clone(),Item.AIR_ITEM.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT),
                new SlotChangeAction(g.inventory,1,g.input.clone(),Item.AIR_ITEM.clone()),
                new SlotChangeAction(g.f.main,0,Item.AIR_ITEM.clone(),g.output.clone())));
        assertTrue(transaction.canExecute()); assertTrue(transaction.execute());
        assertTrue(g.inventory.getIngredient().isNull()); assertTrue(g.output.equalsExact(g.f.main.getItem(0)));
    }
    @Test void grindTwoInputsRepairAndProduceOnlyOneResult() {
        Grind g=grind(); g.inventory.setItemForce(1,g.input.clone()); Item result=g.inventory.getResult();
        var transaction=new GrindstoneTransaction(g.f.player,List.of(
                new GrindstoneItemAction(Item.AIR_ITEM.clone(),g.input.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_INPUT),
                new GrindstoneItemAction(Item.AIR_ITEM.clone(),g.input.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_MATERIAL),
                new GrindstoneItemAction(result.clone(),Item.AIR_ITEM.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT),
                new SlotChangeAction(g.inventory,0,g.input.clone(),Item.AIR_ITEM.clone()),
                new SlotChangeAction(g.inventory,1,g.input.clone(),Item.AIR_ITEM.clone()),
                new SlotChangeAction(g.f.main,0,Item.AIR_ITEM.clone(),result.clone())));
        assertTrue(transaction.canExecute()); assertTrue(transaction.execute());
        assertTrue(g.inventory.getEquipment().isNull()); assertTrue(g.inventory.getIngredient().isNull());
        assertTrue(result.equalsExact(g.f.main.getItem(0)));
    }
    @Test void grindEventCancellationKeepsSourceAndNoExperience() {
        Grind g=grind();
        doAnswer(call -> { if (call.getArgument(0) instanceof GrindItemEvent e) e.setCancelled(); return null; })
                .when(g.f.plugins).callEvent(any(Event.class));
        assertFalse(g.transaction.execute()); unchanged(g);
    }
    @Test void grindQuitInEventKeepsSourceAndNoExperience() {
        Grind g=grind();
        doAnswer(call -> { if (call.getArgument(0) instanceof GrindItemEvent)
            when(g.f.player.isOnline()).thenReturn(false); return null; })
                .when(g.f.plugins).callEvent(any(Event.class));
        assertFalse(g.transaction.execute()); unchanged(g);
        verify(g.f.player,never()).addExperience(anyInt());
    }
    @Test void grindRawMutationDuringLastSetterRollsBackEntireBacking() {
        Grind g=grind(); Item sentinel=Item.get(Item.EMERALD,0,7); g.f.main.slots.put(900,sentinel.clone());
        doAnswer(call -> { if (call.getArgument(0) instanceof EntityInventoryChangeEvent e && !e.getNewItem().isNull()) {
            g.f.main.slots.remove(900); g.f.ui.slots.put(901,Item.get(Item.DIAMOND)); }
            return null; }).when(g.f.plugins).callEvent(any(Event.class));
        assertFalse(g.transaction.execute()); unchanged(g); assertTrue(sentinel.equalsExact(g.f.main.slots.get(900)));
        assertFalse(g.f.ui.slots.containsKey(901));
    }
    @Test void grindDuplicateVirtualResultCannotDoublePhysicalOutput() {
        Grind g=grind();
        g.transaction.addAction(new GrindstoneItemAction(g.output.clone(),Item.AIR_ITEM.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT));
        g.transaction.addAction(new SlotChangeAction(g.f.main,1,Item.AIR_ITEM.clone(),g.output.clone()));
        assertFalse(g.transaction.execute()); unchanged(g); assertTrue(g.f.main.getItem(1).isNull());
    }
    @Test void grindVirtualInputCannotReplacePhysicalConsumption() {
        Grind g=grind();
        g.transaction.getActionList().removeIf(a -> a instanceof SlotChangeAction write && write.getInventory()==g.inventory);
        g.transaction.addAction(new GrindstoneItemAction(g.input.clone(),Item.AIR_ITEM.clone(),Integer.MAX_VALUE));
        assertFalse(g.transaction.execute()); unchanged(g);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void outputSetterFailureRestoresOpaqueInputsAndNoXp(boolean error) {
        Grind g=grind(); Item hidden=Item.get(Item.EMERALD,0,7); g.f.main.slots.put(901,hidden.clone());
        doAnswer(call -> { if(call.getArgument(0) instanceof EntityInventoryChangeEvent event && !event.getNewItem().isNull()) {
            g.f.ui.slots.put(-4,Item.get(Item.DIAMOND));
            if(error) throw new AssertionError("grindstone output callback");
            throw new IllegalStateException("grindstone output callback");
        } return null; }).when(g.f.plugins).callEvent(any(Event.class));
        if(error) assertThrows(AssertionError.class,g.transaction::execute);
        else assertThrows(IllegalStateException.class,g.transaction::execute);
        unchanged(g); assertTrue(hidden.equalsExact(g.f.main.slots.get(901))); assertFalse(g.f.ui.slots.containsKey(-4));
    }
    @Test void approvedOpaqueOutputMetadataAndZeroXpRemainNativeEventChoices() {
        Grind g=grind();
        doAnswer(call -> { if(call.getArgument(0) instanceof GrindItemEvent event) {
            event.getNewItem().setCustomName("Approved grinding result");
            event.setExperienceDropped(0);
        } return null; }).when(g.f.plugins).callEvent(any(Event.class));
        assertTrue(g.transaction.execute()); assertTrue(g.transaction.hasExecuted());
        assertTrue(g.inventory.getEquipment().isNull()); assertEquals("Approved grinding result",g.f.main.getItem(0).getCustomName());
        assertEquals(0,g.transaction.getExperienceDropped());
    }
}
