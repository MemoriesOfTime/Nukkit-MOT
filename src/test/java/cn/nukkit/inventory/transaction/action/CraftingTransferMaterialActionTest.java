package cn.nukkit.inventory.transaction.action;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.BlockID;
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
    void partialConsumptionRejectsDifferentTargetItem() {
        TestCraftingTransaction transaction = new TestCraftingTransaction();
        Item source = Item.get(BlockID.LOG, 0, 64);
        Item target = Item.get(Item.PAPER, 0, 63);

        assertThrows(RuntimeException.class,
                () -> new CraftingTransferMaterialAction(source, target, 0).onAddToTransaction(transaction));
    }

    private static final class TestCraftingTransaction extends CraftingTransaction {

        private TestCraftingTransaction() {
            super(mockPlayer(), List.of());
        }

        private List<Item> getExtraOutputList() {
            return this.secondaryOutputs;
        }
    }

    private static Player mockPlayer() {
        Player player = Mockito.mock(Player.class);
        player.craftingType = Player.CRAFTING_SMALL;
        return player;
    }
}
