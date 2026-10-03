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
    void acceptsBasicFireworkWithPaperGunpowderStar() {
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                fireworkStar((byte) 1)
        );
        ItemFirework output = new ItemFirework(0, 3);
        output.setFlight(1);

        assertTrue(recipe.canExecute(null, output, inputs));
    }

    @Test
    void acceptsCreativeStackedInputs() {
        // #798: creative-mode inputs carry full stacks (64), but we validate by slot count.
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER, 0, 64),
                Item.get(ItemID.GUNPOWDER, 0, 64),
                fireworkStar((byte) 1)
        );
        ItemFirework output = new ItemFirework(0, 3);
        output.setFlight(1);

        assertTrue(recipe.canExecute(null, output, inputs),
                "Full stacked inputs in creative mode should be accepted");
    }

    @Test
    void rejectsMissingPaper() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 3);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.GUNPOWDER),
                fireworkStar((byte) 1)
        )));
    }

    @Test
    void rejectsTooMuchPaper() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 3);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER)
        )));
    }

    @Test
    void rejectsNoGunpowder() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 3);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER),
                fireworkStar((byte) 1)
        )));
    }

    @Test
    void rejectsTooMuchGunpowder() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 3);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                Item.get(ItemID.GUNPOWDER),
                Item.get(ItemID.GUNPOWDER),
                Item.get(ItemID.GUNPOWDER)
        )));
    }

    @Test
    void rejectsInvalidIngredient() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 3);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                Item.get(cn.nukkit.block.BlockID.DIRT)
        )));
    }

    @Test
    void rejectsSmallGridOverflow() {
        // 2x2 grid (4 slots) can't fit 1 paper + 3 gunpowder + 1 star = 5 slots
        Player player = org.mockito.Mockito.mock(Player.class);
        player.craftingType = Player.CRAFTING_SMALL;
        Item output = Item.get(ItemID.FIREWORKS, 0, 3);

        assertFalse(recipe.canExecute(player, output, List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                Item.get(ItemID.GUNPOWDER),
                Item.get(ItemID.GUNPOWDER),
                fireworkStar((byte) 1)
        )));
    }

    @Test
    void flightDerivedFromGunpowderSlotCount() {
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                Item.get(ItemID.GUNPOWDER),
                fireworkStar((byte) 1)
        );
        Item output = Item.get(ItemID.FIREWORKS, 0, 3);

        ItemFirework result = (ItemFirework) recipe.toRecipe(output, inputs).getResult();
        assertEquals(3, result.getCount());
        assertEquals(2, result.getFlight(), "flight should equal the gunpowder slot count");
    }

    @Test
    void explosionCountMatchesStarSlotCount() {
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                fireworkStar((byte) 1),
                fireworkStar((byte) 2)
        );
        Item output = Item.get(ItemID.FIREWORKS, 0, 3);

        ItemFirework result = (ItemFirework) recipe.toRecipe(output, inputs).getResult();
        ListTag<CompoundTag> explosions = result.getNamedTag()
                .getCompound("Fireworks")
                .getList("Explosions", CompoundTag.class);
        assertEquals(2, explosions.size(), "explosion count should equal the firework-star slot count");
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
        ItemFirework firework = (ItemFirework) craftedRecipe.getResult();
        ListTag<CompoundTag> explosions = firework.getNamedTag()
                .getCompound("Fireworks")
                .getList("Explosions", CompoundTag.class);
        assertTrue(explosions.isEmpty());
    }

    @Test
    void overridesClientAuthoredNbtWithServerAuthoritativeOutput() {
        // #798: client-authored NBT (forged explosions/flight) must be discarded by the server authoritative output.
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                fireworkStar((byte) 1)
        );

        // Client claims flight=3 and 5 explosions (mismatched with the server rebuild).
        ItemFirework maliciousOutput = new ItemFirework(0, 3);
        maliciousOutput.setFlight(3);
        ListTag<CompoundTag> fakeExplosions = maliciousOutput.getNamedTag()
                .getCompound("Fireworks")
                .getList("Explosions", CompoundTag.class);
        for (int i = 0; i < 5; i++) {
            fakeExplosions.add(new CompoundTag()
                    .putByteArray("FireworkColor", new byte[]{(byte) i})
                    .putByte("FireworkType", i % 3));
        }
        maliciousOutput.setNamedTag(maliciousOutput.getNamedTag());

        assertTrue(recipe.canExecute(null, maliciousOutput, inputs),
                "Craft is accepted; security is enforced by the server rebuild");

        ItemFirework result = (ItemFirework) recipe.toRecipe(maliciousOutput, inputs).getResult();
        assertEquals(1, result.getFlight(), "flight must come from the gunpowder slot count, ignoring the client value");
        ListTag<CompoundTag> explosions = result.getNamedTag()
                .getCompound("Fireworks")
                .getList("Explosions", CompoundTag.class);
        assertEquals(1, explosions.size(), "server should only emit the explosion of the single input star");
    }

    private static Item fireworkStar(byte color) {
        Item star = Item.get(ItemID.FIREWORKSCHARGE, 0, 1);
        CompoundTag fireworksItem = new CompoundTag("FireworksItem")
                .putByteArray("FireworkColor", new byte[]{color})
                .putBoolean("FireworkFlicker", false)
                .putBoolean("FireworkTrail", false)
                .putByte("FireworkType", 0);
        star.setNamedTag(new CompoundTag().putCompound("FireworksItem", fireworksItem));
        return star;
    }
}
