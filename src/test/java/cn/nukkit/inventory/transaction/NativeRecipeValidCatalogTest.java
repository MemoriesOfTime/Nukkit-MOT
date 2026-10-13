package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.inventory.CraftingGrid;
import cn.nukkit.inventory.ShapedRecipe;
import cn.nukkit.inventory.ShapelessRecipe;
import cn.nukkit.inventory.transaction.action.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBucket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeRecipeValidCatalogTest {
    @BeforeAll static void init() { CraftAnvilAtomicNativeAuditTest.init(); }

    @Test void realShapedWorkbenchRecipePaysOneAchievementAfterAllWrites() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture();
        var tx = craft(f, List.of(Item.get(Item.PLANKS, 0, 4)), Item.get(Item.CRAFTING_TABLE), List.of());
        assertTrue(tx.canExecute()); assertInstanceOf(ShapedRecipe.class, tx.getRecipe());
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityInventoryChangeEvent) verify(f.player, never()).awardAchievement(anyString());
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertTrue(tx.execute()); assertTrue(f.player.getCraftingGrid().getItem(0).isNull());
        assertEquals(Item.CRAFTING_TABLE, f.main.getItem(0).getId()); verify(f.player, times(1)).awardAchievement("buildWorkBench");
        assertFalse(tx.execute()); verify(f.player, times(1)).awardAchievement("buildWorkBench");
    }

    @Test void realShapelessBookRecipeCommitsAcceptedNativeSlots() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture();
        var tx = craft(f, List.of(Item.get(Item.PAPER, 0, 3), Item.get(Item.LEATHER)), Item.get(Item.BOOK), List.of());
        assertTrue(tx.canExecute()); assertInstanceOf(ShapelessRecipe.class, tx.getRecipe());
        assertTrue(tx.execute()); assertEquals(Item.BOOK, f.main.getItem(0).getId());
        assertTrue(f.player.getCraftingGrid().getItem(0).isNull());
    }

    @Test void actualMultiRecipeCombinesTwoDamagedElytrasWithServerDurability() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture();
        var tx = craft(f, List.of(Item.get(Item.ELYTRA, 200), Item.get(Item.ELYTRA, 150)), Item.get(Item.ELYTRA, 0), List.of());
        assertTrue(tx.canExecute()); assertNotNull(tx.getTransactionRecipe());
        assertTrue(tx.execute()); assertEquals(Item.ELYTRA, f.main.getItem(0).getId()); assertEquals(0, f.main.getItem(0).getDamage());
        assertTrue(f.player.getCraftingGrid().getItem(0).isNull()); assertTrue(f.player.getCraftingGrid().getItem(1).isNull());
    }

    @Test void actualCakeRecipeKeepsThreeEmptySecondaryBuckets() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture();
        var tx = cake(f); assertTrue(tx.canExecute()); assertEquals(Item.CAKE, tx.getTransactionRecipe().getResult().getId());
        assertTrue(tx.execute()); assertEquals(Item.CAKE, f.main.getItem(0).getId());
        assertEquals(Item.BUCKET, f.main.getItem(1).getId()); assertEquals(ItemBucket.EMPTY_BUCKET, f.main.getItem(1).getDamage());
        assertEquals(3, f.main.getItem(1).getCount());
        for (int i = 0; i < 6; i++) assertTrue(f.player.getCraftingGrid().getItem(i).isNull());
    }

    @Test void refusalOfLastSecondaryBucketRestoresAllCakeMaterialsAndPrimaryOutput() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); var tx = cake(f); assertTrue(tx.canExecute());
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityInventoryChangeEvent event && event.getNewItem().getId() == Item.BUCKET) event.setCancelled();
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        assertFalse(tx.execute()); assertTrue(f.main.slots.isEmpty());
        assertEquals(1, f.player.getCraftingGrid().getItem(0).getCount()); assertEquals(ItemBucket.MILK_BUCKET, f.player.getCraftingGrid().getItem(0).getDamage());
        assertEquals(1, f.player.getCraftingGrid().getItem(1).getCount()); assertEquals(1, f.player.getCraftingGrid().getItem(2).getCount());
        assertEquals(2, f.player.getCraftingGrid().getItem(3).getCount()); assertEquals(1, f.player.getCraftingGrid().getItem(4).getCount());
        assertEquals(3, f.player.getCraftingGrid().getItem(5).getCount());
    }

    @Test void bulkRecipeNeedingUnpreparedWorldOverflowRefusesBeforeDebiting() {
        var f = new CraftAnvilAtomicNativeAuditTest.Fixture(); Item input = Item.get(Item.LOG, 0, 17); Item output = Item.get(Item.PLANKS, 0, 68);
        assertNotNull(CraftAnvilAtomicNativeAuditTest.manager.matchRecipe(new ArrayList<>(List.of(input.clone())), output.clone(), new ArrayList<>()));
        var tx = craft(f, List.of(input), output, List.of()); assertFalse(tx.execute());
        assertEquals(17, f.player.getCraftingGrid().getItem(0).getCount()); assertTrue(f.main.slots.isEmpty());
        verify(f.plugins, never()).callEvent(any(Event.class)); verify(f.player, never()).dropItem(any());
    }

    private static CraftingTransaction cake(CraftAnvilAtomicNativeAuditTest.Fixture f) {
        return craft(f, List.of(Item.get(Item.BUCKET, ItemBucket.MILK_BUCKET), Item.get(Item.BUCKET, ItemBucket.MILK_BUCKET), Item.get(Item.BUCKET, ItemBucket.MILK_BUCKET), Item.get(Item.SUGAR, 0, 2), Item.get(Item.EGG), Item.get(Item.WHEAT, 0, 3)),
                Item.get(Item.CAKE), List.of(Item.get(Item.BUCKET, ItemBucket.EMPTY_BUCKET, 3)));
    }

    private static CraftingTransaction craft(CraftAnvilAtomicNativeAuditTest.Fixture f, List<Item> inputs, Item output, List<Item> extras) {
        f.player.craftingType = Player.CRAFTING_BIG; CraftingGrid grid = f.ui.getBigCraftingGrid();
        when(f.player.getCraftingGrid()).thenReturn(grid);
        List<InventoryAction> actions = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            Item input = inputs.get(i); grid.setItemForce(i, input);
            actions.add(new CraftingTransferMaterialAction(Item.AIR_ITEM.clone(), input.clone(), i));
            actions.add(new SlotChangeAction(grid, i, input.clone(), Item.AIR_ITEM.clone()));
        }
        actions.add(new CraftingTakeResultAction(output.clone(), Item.AIR_ITEM.clone()));
        actions.add(new SlotChangeAction(f.main, 0, Item.AIR_ITEM.clone(), output.clone()));
        for (int i = 0; i < extras.size(); i++) {
            Item extra = extras.get(i); actions.add(new CraftingTransferMaterialAction(extra.clone(), Item.AIR_ITEM.clone(), i));
            actions.add(new SlotChangeAction(f.main, i + 1, Item.AIR_ITEM.clone(), extra.clone()));
        }
        return new CraftingTransaction(f.player, actions);
    }
}
