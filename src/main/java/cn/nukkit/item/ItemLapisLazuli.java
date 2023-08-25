package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimMaterialType;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemLapisLazuli extends Item implements ItemTrimMaterial {

    public ItemLapisLazuli() {
        this(0, 1);
    }

    public ItemLapisLazuli(Integer meta) {
        this(meta, 1);
    }

    public ItemLapisLazuli(Integer meta, int count) {
        super(LAPIS_LAZULI, meta, count, "Lapis Lazuli");
    }

    @Override
    public ItemTrimMaterialType getMaterial() {
        return ItemTrimMaterialType.MATERIAL_LAPIS;
    }
}
