package cn.nukkit.inventory.transaction.action;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.transaction.CraftingTransaction;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingTransferMaterialActionTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void partialConsumptionRecordsOnlyConsumedInput() {
        TestCraftingTransaction transaction = new TestCraftingTransaction();
        Item source = Item.get(BlockID.LOG, 0, 64);
        Item target = Item.get(BlockID.LOG, 0, 63);

        new CraftingTransferMaterialAction(source, target, 0).onAddToTransaction(transaction);

        assertEquals(1, transaction.getInputList().size());
        Item consumed = transaction.getInputList().get(0);
        assertTrue(consumed.equals(source, true, true));
        assertEquals(1, consumed.getCount());
        assertTrue(transaction.getExtraOutputList().isEmpty());
    }

    @Test
    void partialConsumptionExposesConsumedDeltaForBalanceCheck() {
        Item source = Item.get(BlockID.LOG, 0, 64);
        Item target = Item.get(BlockID.LOG, 0, 63);
        Item consumedFromInventory = Item.get(BlockID.LOG);
        TestCraftingTransaction transaction = new TestCraftingTransaction(List.of(
                new CraftingTransferMaterialAction(source, target, 0),
                new BalanceAction(mockBalanceInventory(), 1, consumedFromInventory)
        ));

        assertTrue(transaction.matchesBalance());
    }

    @Test
    void partialConsumptionRejectsDifferentTargetItem() {
        TestCraftingTransaction transaction = new TestCraftingTransaction();
        Item source = Item.get(BlockID.LOG, 0, 64);
        Item target = Item.get(Item.PAPER, 0, 63);

        assertThrows(RuntimeException.class,
                () -> new CraftingTransferMaterialAction(source, target, 0).onAddToTransaction(transaction));
    }

    private static final class TestCraftingTransaction extends CraftingTransaction {

        private TestCraftingTransaction() {
            this(List.of());
        }

        private TestCraftingTransaction(List<InventoryAction> actions) {
            super(mockPlayer(), actions);
        }

        private List<Item> getExtraOutputList() {
            return this.secondaryOutputs;
        }

        private boolean matchesBalance() {
            return matchItems(false, false);
        }
    }

    /**
     * Balance action supplying haveItems to cancel needItems. Extends SlotChangeAction to pass the
     * crafting action whitelist, but keeps isValid trivially true since no real inventory is involved.
     */
    private static final class BalanceAction extends SlotChangeAction {
        private BalanceAction(BaseInventory inventory, int slot, Item sourceItem) {
            super(inventory, slot, sourceItem, Item.get(Item.AIR));
        }

        @Override
        public boolean isValid(Player source) {
            return true;
        }

        @Override
        public boolean execute(Player source) {
            return true;
        }

        @Override
        public void onExecuteSuccess(Player source) {
        }

        @Override
        public void onExecuteFail(Player source) {
        }
    }

    private static BaseInventory mockBalanceInventory() {
        BaseInventory inventory = Mockito.mock(BaseInventory.class);
        Mockito.lenient().when(inventory.allowedToAdd(Mockito.any())).thenReturn(true);
        return inventory;
    }

    private static Player mockPlayer() {
        Player player = Mockito.mock(Player.class);
        player.craftingType = Player.CRAFTING_SMALL;
        return player;
    }
}
