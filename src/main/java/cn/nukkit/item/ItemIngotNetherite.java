package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimMaterialType;

public class ItemIngotNetherite extends Item implements ItemTrimMaterial {

    public ItemIngotNetherite() {
        this(0, 1);
    }

    public ItemIngotNetherite(Integer meta) {
        this(meta, 1);
    }

    public ItemIngotNetherite(Integer meta, int count) {
        super(NETHERITE_INGOT, 0, count, "Netherite Ingot");
    }

    @Override
    public ItemTrimMaterialType getMaterial() {
        return ItemTrimMaterialType.MATERIAL_NETHERITE;
    }
}
