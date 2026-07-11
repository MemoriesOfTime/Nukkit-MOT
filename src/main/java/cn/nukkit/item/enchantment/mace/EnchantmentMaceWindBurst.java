package cn.nukkit.item.enchantment.mace;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.particle.GenericParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.Vector3;

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
        double fallDistance = attacker.highestPosition - attacker.y;
        if (fallDistance < 1.5d || attacker.isOnGround()) {
            return;
        }
        if (attacker instanceof Player player && player.getAdventureSettings().get(AdventureSettings.Type.FLYING)) {
            return;
        }
        float knockbackScaling = (getLevel() + 1) * 0.25f;
        knockBack(attacker, knockbackScaling);
        attacker.resetFallDistance();
    }

    /**
     * 应用风爆击退与气浪粒子。此处不播放猛击音效，避免与
     * {@link cn.nukkit.item.ItemMace#onPostAttack} 重复。参考 Allay 的
     * {@code ItemMaceBaseComponentImpl}（风爆仅负责击退与气浪，音效统一在
     * {@code applySmashEffects} 处理）。
     * <p>
     * Applies wind-burst knockback and wind particle. Smash sounds are handled
     * solely by {@link cn.nukkit.item.ItemMace#onPostAttack} to avoid duplicates,
     * matching Allay's {@code ItemMaceBaseComponentImpl}.
     */
    protected void knockBack(Entity entity, double knockbackScaling) {
        Vector3 knockback = new Vector3(entity.motionX, entity.motionY, entity.motionZ);
        knockback.x /= 2d;
        knockback.y /= 2d;
        knockback.z /= 2d;
        knockback.y += 2.0f * knockbackScaling;

        entity.setMotion(knockback);
        entity.getLevel().addParticle(new GenericParticle(entity, Particle.TYPE_WIND_EXPLOSION));
    }
}

