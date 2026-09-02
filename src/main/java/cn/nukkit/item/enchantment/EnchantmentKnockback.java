package cn.nukkit.item.enchantment;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.item.Item;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentKnockback extends Enchantment {

    protected EnchantmentKnockback() {
        super(ID_KNOCKBACK, "knockback", Rarity.UNCOMMON, EnchantmentType.SWORD);
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 5 + (level - 1) * 20;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return super.getMinEnchantAbility(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public boolean canEnchant(Item item) {
        return item.isSpear() || super.canEnchant(item);
    }

    @Override
    public void doPostAttack(Entity attacker, Entity entity) {
        if (this.level < 1 || !(entity instanceof EntityLiving victim)) {
            return;
        }

        victim.knockBack(attacker, 0, entity.x - attacker.x, entity.z - attacker.z, this.level * 0.5);
    }
}
