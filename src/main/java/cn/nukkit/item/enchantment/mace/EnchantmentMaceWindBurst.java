package cn.nukkit.item.enchantment.mace;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.particle.HugeExplodeParticle;
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
        double fallDistance = attacker.highestPosition - entity.y;
        if (fallDistance <= 0 || attacker.isOnGround()) {
            return;
        }
        if (attacker.isPlayer && ((Player) attacker).getAdventureSettings().get(AdventureSettings.Type.FLYING)) {
            return;
        }
        float knockbackScaling = (getLevel() + 1) * 0.25f;
        knockBack(attacker, knockbackScaling);
        attacker.resetFallDistance();
    }

    protected void knockBack(Entity entity, double knockbackScaling) {
        Vector3 knockback = new Vector3(entity.motionX, entity.motionY, entity.motionZ);
        knockback.x /= 2d;
        knockback.y /= 2d;
        knockback.z /= 2d;
        knockback.y += 2.0f * knockbackScaling;

        entity.setMotion(knockback);
        entity.getLevel().addParticle(new HugeExplodeParticle(entity));
        entity.getLevel().addLevelSoundEvent(entity, 520);
        entity.getLevel().addSound(entity, "mace.heavy_smash_ground");
    }
}