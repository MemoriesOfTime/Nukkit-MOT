package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimMaterialType;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemCopperIngot extends StringItemBase implements ItemTrimMaterial {

    public ItemCopperIngot() {
        super("minecraft:copper_ingot", "Copper Ingot");
    }

    @Override
    public ItemTrimMaterialType getMaterial() {
        return ItemTrimMaterialType.MATERIAL_COPPER;
    }
}
