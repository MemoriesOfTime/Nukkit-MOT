package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.special.RepairItemRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemShovelIron;
import cn.nukkit.level.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GrindstoneDurabilityTest {
    private GrindstoneInventory grindstone;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        grindstone = new GrindstoneInventory(new PlayerUIInventory(mock(Player.class)),
                new Position(0, 64, 0));
    }

    @Test
    void ironShovelCombinationMatchesCraftingRepair() {
        Item first = new ItemShovelIron(200, 1);
        Item second = new ItemShovelIron(225, 1);
        grindstone.setItemForce(0, first);
        grindstone.setItemForce(1, second);

        Item result = grindstone.getResult();

        assertEquals(162, result.getDamage());
        assertEquals(new RepairItemRecipe().calculateDamage(200, 225, first.getMaxDurability()), result.getDamage());
    }

    @Test
    void elytraCombinationMatchesCraftingRepair() {
        Item first = Item.get(Item.ELYTRA, 300);
        Item second = Item.get(Item.ELYTRA, 300);
        grindstone.setItemForce(0, first);
        grindstone.setItemForce(1, second);

        Item result = grindstone.getResult();

        assertEquals(146, result.getDamage());
        assertEquals(new RepairItemRecipe().calculateDamage(300, 300, first.getMaxDurability()), result.getDamage());
    }
}
