package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimMaterialType;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemAmethystShard extends Item implements ItemTrimMaterial {

    public ItemAmethystShard() {
        this(0, 1);
    }

    public ItemAmethystShard(Integer meta) {
        this(meta, 1);
    }

    public ItemAmethystShard(Integer meta, int count) {
        super(AMETHYST_SHARD, meta, count, "Amethyst Shard");
    }

    @Override
    public ItemTrimMaterialType getMaterial() {
        return ItemTrimMaterialType.MATERIAL_AMETHYST;
    }
}
