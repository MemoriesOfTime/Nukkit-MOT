package cn.nukkit.inventory.special;

import cn.nukkit.MockServer;
import cn.nukkit.item.*;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RepairItemVanillaMetadataTest {
    @BeforeAll static void init() { MockServer.init(); }
    private final RepairItemRecipe recipe = new RepairItemRecipe();
    private Item first() { return new ItemShovelIron(200, 1); }
    private Item second() { return new ItemShovelIron(225, 1); }
    private Item output() { return new ItemShovelIron(163, 1); }
    private boolean matches(Item first, Item second, Item output) {
        Item beforeFirst = first.clone(), beforeSecond = second.clone(), beforeOutput = output.clone();
        boolean result = recipe.canExecute(null, output, List.of(first, second));
        assertEquals(beforeFirst, first); assertEquals(beforeSecond, second); assertEquals(beforeOutput, output);
        return result;
    }
    @Test void namedVanillaInputsAcceptCleanResultInEitherOrder() {
        Item named = first().setCustomName("First shovel");
        assertTrue(matches(named, second(), output()));
        assertTrue(matches(second(), named, output()));
    }
    @Test void ordinaryEnchantmentAndPriorWorkCanBeRemoved() {
        Item enchanted = first().setCustomName("First shovel");
        enchanted.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(2));
        enchanted.setNamedTag(enchanted.getNamedTag().putInt("RepairCost", 3));
        assertTrue(matches(enchanted, second(), output()));
    }
    @Test void originalPlainAndExactMetadataPathsArePreserved() {
        assertTrue(matches(first(), second(), output()));
        Item marked = first().setNamedTag(new CompoundTag().putString("example:origin", "example:original"));
        Item markedOutput = output().setNamedTag(marked.getNamedTag().copy());
        assertTrue(matches(marked, second(), markedOutput));
        Item secondMarked = second().setNamedTag(marked.getNamedTag().copy());
        assertTrue(matches(marked, secondMarked, markedOutput));
        assertFalse(matches(marked, second(), output()));
    }
    @Test void newAcceptanceNeverDiscardsServerMetadataFromEitherInput() {
        for (String key : List.of("example:origin", "example:owner", "example:seal", "custom")) {
            Item marked = second().setNamedTag(new CompoundTag().putString(key, "value"));
            Item named = first().setCustomName("First shovel");
            assertFalse(matches(named, marked, output()), key);
            assertFalse(matches(marked, named, output()), key);
        }
    }
    @Test void cursesAboveVanillaEnchantmentsAndCustomLoreStayOnExactPath() {
        Item cursed = first(); cursed.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_VANISHING_CURSE));
        assertFalse(matches(cursed, second(), output()));
        Item high = first(); high.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(10, false));
        assertFalse(matches(high, second(), output()));
        Item lore = first().setCustomName("First shovel"); lore.setLore("server-owned");
        assertFalse(matches(lore, second(), output()));
    }
    @Test void forgedOutputAndWrongDurabilityStayRejected() {
        Item named = first().setCustomName("First shovel");
        assertFalse(matches(named, second(), new ItemShovelIron(162, 1)));
        assertFalse(matches(named, second(), output().setCustomName("Forged")));
        assertFalse(matches(named, second(), output().setNamedTag(new CompoundTag().putString("example:origin", "example:forged"))));
    }
}
