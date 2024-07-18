package cn.nukkit.entity.passive;

import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

public class EntityCamel extends EntityWalkingAnimal {

    public static final int NETWORK_ID = 138;

    public EntityCamel(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(32);

        super.initEntity();
    }

    @Override
    public float getWidth() {
        if (isBaby()) {
            return 0.85f;
        }
        return 1.7f;
    }

    @Override
    public float getHeight() {
        if (isBaby()) {
            return 1.1875f;
        }
        return 2.375f;
    }

    @Override
    public int getKillExperience() {
        return Utils.rand(1, 3);
    }
}