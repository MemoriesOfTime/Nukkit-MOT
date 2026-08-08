package cn.nukkit.item.enchantment;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTrident;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：三叉戟只能附魔 vanilla 允许的附魔（Loyalty/Riptide/Channeling/Impaling/Unbreaking/Mending/Vanishing），
 * 不能附魔剑类武器附魔（Sharpness/Knockback/Smite/BaneOfArthropods/FireAspect/Looting）。
 * <p>
 * Regression test: a trident must only accept its vanilla enchantment pool
 * (Loyalty/Riptide/Channeling/Impaling/Unbreaking/Mending/Vanishing) and reject sword weapon
 * enchantments (Sharpness/Knockback/Smite/BaneOfArthropods/FireAspect/Looting).
 * <p>
 * 根因是 {@link ItemTrident#isSword()} 历史上返回 true（用于近战伤害），导致 {@link EnchantmentType#SWORD}
 * 分支误放行三叉戟。修复方式与 Cloudburst/PM1 一致，在 SWORD 分支排除三叉戟。
 * <p>
 * See <a href="https://minecraft.wiki/w/Trident">Minecraft Wiki – Trident</a> for the vanilla pool.
 */
public class TridentEnchantmentTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    /**
     * 剑类武器附魔必须被三叉戟拒绝（本次修复的目标）。
     * <p>
     * Sword weapon enchantments must be rejected by a trident (the target of this fix).
     */
    @Test
    public void weaponEnchantmentsRejectedByTrident() {
        Item trident = Item.get(Item.TRIDENT);
        assertTrue(trident instanceof ItemTrident, "Item.TRIDENT should resolve to ItemTrident");

        int[] weaponEnchantments = {
                Enchantment.ID_DAMAGE_ALL,        // sharpness 锋利
                Enchantment.ID_DAMAGE_SMITE,      // smite 亡灵杀手
                Enchantment.ID_DAMAGE_ARTHROPODS, // bane of arthropods 节肢杀手
                Enchantment.ID_KNOCKBACK,         // knockback 击退
                Enchantment.ID_FIRE_ASPECT,       // fire aspect 火焰附加
                Enchantment.ID_LOOTING            // looting 抢夺
        };
        for (int id : weaponEnchantments) {
            Enchantment e = Enchantment.get(id);
            assertFalse(e.canEnchant(trident),
                    e.getOriginalName() + " should NOT be applicable to a trident");
        }
    }

    /**
     * 三叉戟专属附魔和耐久类通用附魔必须仍可附魔（确保修复未误伤合法附魔池）。
     * <p>
     * Trident-exclusive and durability enchantments must still apply (guard against over-narrowing).
     */
    @Test
    public void tridentAndDurabilityEnchantmentsStillApply() {
        Item trident = Item.get(Item.TRIDENT);

        int[] validEnchantments = {
                Enchantment.ID_TRIDENT_IMPALING,   // impaling 穿刺
                Enchantment.ID_TRIDENT_RIPTIDE,    // riptide 激流
                Enchantment.ID_TRIDENT_LOYALTY,    // loyalty 忠诚
                Enchantment.ID_TRIDENT_CHANNELING, // channeling 引雷
                Enchantment.ID_DURABILITY,         // unbreaking 耐久
                Enchantment.ID_MENDING,            // mending 经验修补
                Enchantment.ID_VANISHING_CURSE     // curse of vanishing 消失诅咒
        };
        for (int id : validEnchantments) {
            Enchantment e = Enchantment.get(id);
            assertTrue(e.canEnchant(trident),
                    e.getOriginalName() + " should be applicable to a trident");
        }
    }
}
