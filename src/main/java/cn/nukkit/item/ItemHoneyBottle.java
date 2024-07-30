package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.math.Vector3;
import cn.nukkit.potion.Effect;

public class ItemHoneyBottle extends ItemEdible {
    
    public ItemHoneyBottle() {
        this(0, 1);
    }
    
    public ItemHoneyBottle(Integer meta) {
        this(meta, 1);
    }
    
    public ItemHoneyBottle(Integer meta, int count) {
        super(HONEY_BOTTLE, meta, count, "Honey Bottle");
    }
    
    @Override
    public int getMaxStackSize() {
        return 16;
    }
    
    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return true;
    }

    @Override
    public boolean onUse(Player player, int ticksUsed) {
        if (ticksUsed < 10) return false;
        super.onUse(player, ticksUsed);

        if (player.hasEffect(Effect.POISON)) {
            player.removeEffect(Effect.POISON, EntityPotionEffectEvent.Cause.FOOD);
        }

        if (!player.isCreative()) {
            this.count--;
            player.getInventory().setItemInHand(this);
            player.getInventory().addItem(Item.get(ItemID.BOTTLE, 0, 1));
        }
        return true;
    }
}
