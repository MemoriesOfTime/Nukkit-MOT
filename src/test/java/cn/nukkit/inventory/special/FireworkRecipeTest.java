package cn.nukkit.inventory.special;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.item.ItemID;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FireworkRecipeTest {

    private FireworkRecipe recipe;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        recipe = new FireworkRecipe();
    }

    @Test
    void rejectsBatchOutputWithoutBatchMaterials() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 6);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER)
        )));
    }

    @Test
    void rejectsBatchOutputWhenGunpowderIsNotDivisibleByBatchCount() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 6);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 3)
        )));
    }

    @Test
    void acceptsBatchOutputWhenPaperAndGunpowderMatchBatchCount() {
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 2)
        );
        Item output = serverOutput(6, inputs);

        assertTrue(recipe.canExecute(null, output, inputs));
    }

    @Test
    void rejectsClientAuthoredFireworkNbt() {
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 2)
        );
        ItemFirework output = new ItemFirework(0, 6);
        output.setFlight(3);

        assertFalse(recipe.canExecute(null, output, inputs));
    }

    @Test
    void rejectsFireworkStarStacksThatCannotBeSplitPerBatch() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 6);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 2),
                fireworkStar(1, (byte) 1),
                fireworkStar(1, (byte) 2)
        )));
    }

    @Test
    void acceptsEquivalentFireworkStarStacksAggregatedPerBatch() {
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 2),
                fireworkStar(1, (byte) 1),
                fireworkStar(1, (byte) 1)
        );
        Item output = serverOutput(6, inputs);

        assertTrue(recipe.canExecute(null, output, inputs));
        ListTag<CompoundTag> explosions = output.getNamedTag()
                .getCompound("Fireworks")
                .getList("Explosions", CompoundTag.class);
        assertEquals(1, explosions.size());
    }

    @Test
    void acceptsFireworkStarWithoutExplosionComponent() {
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                Item.get(ItemID.FIREWORKSCHARGE)
        );
        ItemFirework output = new ItemFirework(0, 3);
        output.setFlight(1);

        assertTrue(recipe.canExecute(null, output, inputs));

        Recipe craftedRecipe = recipe.toRecipe(output, inputs);
        assertInstanceOf(ItemFirework.class, craftedRecipe.getResult());
        ItemFirework firework = (ItemFirework) craftedRecipe.getResult();
        ListTag<CompoundTag> explosions = firework.getNamedTag()
                .getCompound("Fireworks")
                .getList("Explosions", CompoundTag.class);
        assertTrue(explosions.isEmpty());
    }

    @Test
    void rejectsSmallGridRecipeThatRequiresCraftingTable() {
        Player player = Mockito.mock(Player.class);
        player.craftingType = Player.CRAFTING_SMALL;
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER, 0, 3),
                fireworkStar(1, (byte) 1)
        );
        Item output = serverOutput(3, inputs);

        assertFalse(recipe.canExecute(player, output, inputs));
    }

    @Test
    void toRecipeUsesGunpowderPerBatchAsFlightDuration() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 6);

        Recipe craftedRecipe = recipe.toRecipe(output, List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 6)
        ));

        assertInstanceOf(ItemFirework.class, craftedRecipe.getResult());
        ItemFirework firework = (ItemFirework) craftedRecipe.getResult();
        assertEquals(6, firework.getCount());
        assertEquals(3, firework.getFlight());
    }

    private static Item fireworkStar(int count, byte color) {
        Item star = Item.get(ItemID.FIREWORKSCHARGE, 0, count);
        CompoundTag fireworksItem = new CompoundTag("FireworksItem")
                .putByteArray("FireworkColor", new byte[]{color})
                .putBoolean("FireworkFlicker", false)
                .putBoolean("FireworkTrail", false)
                .putByte("FireworkType", 0);
        star.setNamedTag(new CompoundTag().putCompound("FireworksItem", fireworksItem));
        return star;
    }

    private Item serverOutput(int count, List<Item> inputs) {
        return recipe.toRecipe(Item.get(ItemID.FIREWORKS, 0, count), inputs).getResult();
    }
}
