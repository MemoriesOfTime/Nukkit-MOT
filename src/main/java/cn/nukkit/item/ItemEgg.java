package cn.nukkit.item;

import cn.nukkit.entity.EntityClimateVariant;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemEgg extends ProjectileItem {

    public ItemEgg() {
        this(0, 1);
    }

    public ItemEgg(Integer meta) {
        this(meta, 1);
    }

    public ItemEgg(Integer meta, int count) {
        super(EGG, meta, count, "Egg");
    }

    @Override
    public String getProjectileEntityType() {
        return "Egg";
    }

    @Override
    public float getThrowForce() {
        return 1.5f;
    }
    
    @Override
    public int getMaxStackSize() {
        return 16;
    }

    @Override
    protected void correctNBT(CompoundTag nbt) {
        nbt.putString("variant", EntityClimateVariant.Variant.TEMPERATE.getName());
    }
}
