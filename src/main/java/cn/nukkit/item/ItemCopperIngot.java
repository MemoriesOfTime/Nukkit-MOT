package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimMaterialType;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemCopperIngot extends Item implements ItemTrimMaterial {

    public ItemCopperIngot() {
        this(0, 1);
    }

    public ItemCopperIngot(Integer meta) {
        this(meta, 1);
    }

    public ItemCopperIngot(Integer meta, int count) {
        super(COPPER_INGOT, meta, count, "Copper Ingot");
    }

    @Override
    public ItemTrimMaterialType getMaterial() {
        return ItemTrimMaterialType.MATERIAL_COPPER;
    }
}
