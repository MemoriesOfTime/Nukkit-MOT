package cn.nukkit.item;

import cn.nukkit.entity.Entity;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author glorydark
 */
public class ItemMace extends StringItemToolBase {

    public ItemMace() {
        super("minecraft:mace", "Mace");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_MACE;
    }

    @Override
    public int getAttackDamage() {
        return 5;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_DIAMOND;
    }

    @Override
    public boolean isSword() {
        return true;
    }

    @Override
    public int getAttackDamage(Entity entity) {
        int damage = 6;
        int height = NukkitMath.floorDouble(entity.highestPosition - entity.y);
        if (height < 1.5f) return damage;
        for (int i = 0; i <= height; i++) {
            if (i < 3) damage+=4;
            else if (i < 8) damage+=2;
            else damage++;
        }
        return damage;
    }
}