package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimMaterialType;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemIngotGold extends Item implements ItemTrimMaterial {

    public ItemIngotGold() {
        this(0, 1);
    }

    public ItemIngotGold(Integer meta) {
        this(meta, 1);
    }

    public ItemIngotGold(Integer meta, int count) {
        super(GOLD_INGOT, 0, count, "Gold Ingot");
    }

    @Override
    public ItemTrimMaterialType getMaterial() {
        return ItemTrimMaterialType.MATERIAL_GOLD;
    }
}
