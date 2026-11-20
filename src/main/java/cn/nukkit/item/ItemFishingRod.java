package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;

public class ItemFishingRod extends ItemTool {

    public ItemFishingRod() {
        this(0, 1);
    }

    public ItemFishingRod(Integer meta) {
        this(meta, 1);
    }

    public ItemFishingRod(Integer meta, int count) {
        super(FISHING_ROD, meta, count, "Fishing Rod");
    }

    @Override
    public int getEnchantAbility() {
        return 1;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        if (player.fishing != null) {
            if (!this.isUnbreakable() && !player.isCreative()) {
                int damage = player.fishing.getTarget() > 0 ? 2 : 1;
                this.setDamage(this.getDamage() + damage);
                if (this.getDamage() >= this.getMaxDurability()) {
                    this.setCount(0);
                    player.level.addSoundToViewers(player, Sound.RANDOM_BREAK);
                    player.level.addParticle(new ItemBreakParticle(player, this));
                }
            }
			player.stopFishing(true);
		} else {
			player.startFishing(this);
        }
        return true;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_FISHING_ROD;
    }

    @Override
    public boolean noDamageOnAttack() {
        return true;
    }

    @Override
    public boolean noDamageOnBreak() {
        return true;
    }
}
