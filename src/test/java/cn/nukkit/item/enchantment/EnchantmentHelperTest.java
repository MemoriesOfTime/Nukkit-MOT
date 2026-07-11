package cn.nukkit.item.enchantment;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EnchantmentHelperTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    @SuppressWarnings("unchecked")
    void enchantingTableCandidatesExcludeTreasureAndCurses() throws Exception {
        Method filterApplicable = EnchantmentHelper.class.getDeclaredMethod("filterApplicable", Item.class, int.class);
        filterApplicable.setAccessible(true);

        List<Enchantment> candidates = (List<Enchantment>) filterApplicable.invoke(
                null,
                Item.get(Item.DIAMOND_PICKAXE),
                30
        );

        assertFalse(candidates.isEmpty(), "test item should have normal enchantment candidates");
        assertFalse(candidates.stream().anyMatch(enchantment -> enchantment.isTreasure() || enchantment.isCurse()));
    }
}
