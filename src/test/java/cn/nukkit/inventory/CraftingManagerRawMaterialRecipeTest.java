package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CraftingManagerRawMaterialRecipeTest {

    private CraftingManager manager;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        manager = new CraftingManager();
    }

    @Test
    void matchesRawIronBlockUnpackingRecipe() {
        assertMatches(
                List.of(Item.get(255 - BlockID.RAW_IRON_BLOCK)),
                rawIron(9)
        );
    }

    @Test
    void matchesRawIronPackingRecipe() {
        assertMatches(
                List.of(rawIron(9)),
                Item.get(255 - BlockID.RAW_IRON_BLOCK)
        );
    }

    @Test
    void matchesRawGoldBlockUnpackingRecipe() {
        assertMatches(
                List.of(Item.get(255 - BlockID.RAW_GOLD_BLOCK)),
                rawGold(9)
        );
    }

    @Test
    void matchesRawGoldPackingRecipe() {
        assertMatches(
                List.of(rawGold(9)),
                Item.get(255 - BlockID.RAW_GOLD_BLOCK)
        );
    }

    @Test
    void keepsRawCopperUnpackingRecipeAsControl() {
        assertMatches(
                List.of(Item.get(255 - BlockID.RAW_COPPER_BLOCK)),
                rawCopper(9)
        );
    }

    @Test
    void keepsRawCopperPackingRecipeAsControl() {
        assertMatches(
                List.of(rawCopper(9)),
                Item.get(255 - BlockID.RAW_COPPER_BLOCK)
        );
    }

    @Test
    void matchesRawCopperPackingRecipeWithWildcardStringItemDamage() {
        Item damagedRawCopper = rawCopper(9);
        damagedRawCopper.setDamage(7);

        assertMatches(
                List.of(damagedRawCopper),
                Item.get(255 - BlockID.RAW_COPPER_BLOCK)
        );
    }

    @Test
    void rawMaterialsUseStringIdentifiedItems() {
        assertEquals(Item.STRING_IDENTIFIED_ITEM, rawIron(1).getId());
        assertEquals(Item.STRING_IDENTIFIED_ITEM, rawGold(1).getId());
        assertEquals(Item.STRING_IDENTIFIED_ITEM, rawCopper(1).getId());
    }

    private static Item rawIron(int count) {
        Item item = Item.fromString("minecraft:raw_iron");
        item.setCount(count);
        return item;
    }

    private static Item rawGold(int count) {
        Item item = Item.fromString("minecraft:raw_gold");
        item.setCount(count);
        return item;
    }

    private static Item rawCopper(int count) {
        Item item = Item.fromString("minecraft:raw_copper");
        item.setCount(count);
        return item;
    }

    private void assertMatches(List<Item> inputs, Item output) {
        CraftingRecipe recipe = manager.matchRecipe(new ArrayList<>(inputs), output, Collections.emptyList());
        assertNotNull(recipe);
    }
}
