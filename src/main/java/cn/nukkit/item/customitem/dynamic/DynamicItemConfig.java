package cn.nukkit.item.customitem.dynamic;

import lombok.Data;

/**
 * 物品配置数据类
 * Item configuration data class
 */
@Data
public class DynamicItemConfig {
    private final int maxStackSize;
    private final int maxDurability;
    private final int attackDamage;
    private final int scaleOffset;
    private final boolean isSword;
    private final boolean isTool;
    private final boolean allowOffHand;
    private final boolean handEquipped;
    private final boolean foil;
    private final String creativeCategory;
    private final String creativeGroup;
    private final boolean canDestroyInCreative;

    public DynamicItemConfig(int maxStackSize, int maxDurability, int attackDamage, int scaleOffset,
                             boolean isSword, boolean isTool, boolean allowOffHand, boolean handEquipped,
                             boolean foil, String creativeCategory, String creativeGroup, boolean canDestroyInCreative) {
        this.maxStackSize = maxStackSize;
        this.maxDurability = maxDurability;
        this.attackDamage = attackDamage;
        this.scaleOffset = scaleOffset;
        this.isSword = isSword;
        this.isTool = isTool;
        this.allowOffHand = allowOffHand;
        this.handEquipped = handEquipped;
        this.foil = foil;
        this.creativeCategory = creativeCategory;
        this.creativeGroup = creativeGroup;
        this.canDestroyInCreative = canDestroyInCreative;
    }
}
