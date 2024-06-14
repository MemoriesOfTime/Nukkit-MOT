package cn.nukkit.item.enchantment.mace;

import cn.nukkit.entity.Entity;
import cn.nukkit.item.enchantment.Enchantment;

public class EnchantmentMaceWindBurst extends EnchantmentMace {

    public EnchantmentMaceWindBurst() {
        super(Enchantment.ID_WIND_BURST, "heavy_weapon.windburst", Rarity.RARE);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public void doAttack(Entity attacker, Entity entity) {
        float fallDistance = attacker.fallDistance;
        if (fallDistance <= 0) {
            return;
        }

        float knockbackScaling = (getLevel() + 1) * 0.25f;
        //TODO: WindBurstUtility::burst
    }
}