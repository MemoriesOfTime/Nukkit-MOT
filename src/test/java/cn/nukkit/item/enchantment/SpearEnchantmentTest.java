package cn.nukkit.item.enchantment;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: vanilla spears must accept the full spear enchantment pool.
 * <p>
 * Prior to the fix, sharpness/smite/bane_of_arthropods/knockback/fire_aspect/looting were
 * bound to {@link EnchantmentType#SWORD} and rejected spears (which report {@code isSword() == false}),
 * so an anvil would refuse to transfer these enchantments from books onto a spear. Only
 * lunge/unbreaking/mending/vanishing were accepted.
 * <p>
 * See <a href="https://minecraft.wiki/w/Spear">Minecraft Wiki – Spear</a> for the vanilla pool.
 */
public class SpearEnchantmentTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    static Stream<Arguments> spearVariants() {
        return Stream.of(
                Arguments.of("minecraft:wooden_spear"),
                Arguments.of("minecraft:stone_spear"),
                Arguments.of("minecraft:iron_spear"),
                Arguments.of("minecraft:golden_spear"),
                Arguments.of("minecraft:diamond_spear"),
                Arguments.of("minecraft:copper_spear"),
                Arguments.of("minecraft:netherite_spear")
        );
    }

    /**
     * Weapon-class enchantments (SWORD-typed) must apply to spears, matching vanilla behavior.
     */
    @ParameterizedTest
    @MethodSource("spearVariants")
    public void weaponEnchantmentsApplyToSpear(String spearId) {
        Item spear = Item.fromString(spearId);
        assertTrue(spear.isSpear(), spearId + " should report isSpear()");

        int[] weaponEnchantments = {
                Enchantment.ID_DAMAGE_ALL,        // sharpness
                Enchantment.ID_DAMAGE_SMITE,      // smite
                Enchantment.ID_DAMAGE_ARTHROPODS, // bane of arthropods
                Enchantment.ID_KNOCKBACK,         // knockback
                Enchantment.ID_FIRE_ASPECT,       // fire aspect
                Enchantment.ID_LOOTING            // looting
        };
        for (int id : weaponEnchantments) {
            Enchantment e = Enchantment.get(id);
            assertTrue(e.canEnchant(spear),
                    e.getOriginalName() + " should be applicable to " + spearId);
        }
    }

    /**
     * Spear-exclusive and durability enchantments must still apply to spears.
     */
    @ParameterizedTest
    @MethodSource("spearVariants")
    public void spearAndDurabilityEnchantmentsApplyToSpear(String spearId) {
        Item spear = Item.fromString(spearId);

        int[] durabilityEnchantments = {
                Enchantment.ID_LUNGE,          // lunge (spear-exclusive)
                Enchantment.ID_DURABILITY,     // unbreaking
                Enchantment.ID_MENDING,        // mending
                Enchantment.ID_VANISHING_CURSE // curse of vanishing
        };
        for (int id : durabilityEnchantments) {
            Enchantment e = Enchantment.get(id);
            assertTrue(e.canEnchant(spear),
                    e.getOriginalName() + " should be applicable to " + spearId);
        }
    }

    /**
     * Non-weapon enchantments (e.g. efficiency, silk touch) must still be rejected by spears,
     * so this fix does not over-broaden the enchantment pool.
     */
    @ParameterizedTest
    @MethodSource("spearVariants")
    public void toolEnchantmentsStillRejectedBySpear(String spearId) {
        Item spear = Item.fromString(spearId);
        Enchantment efficiency = Enchantment.get(Enchantment.ID_EFFICIENCY);
        assertTrue(!efficiency.canEnchant(spear),
                "efficiency must not apply to " + spearId);

        Enchantment silkTouch = Enchantment.get(Enchantment.ID_SILK_TOUCH);
        assertTrue(!silkTouch.canEnchant(spear),
                "silk touch must not apply to " + spearId);
    }
}
