package cn.nukkit.entity.item;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;

public class EntityPotionLingering extends EntityPotion {

    public static final int NETWORK_ID = 101;

    public EntityPotionLingering(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public EntityPotionLingering(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    protected void splash(Entity collidedWith) {
        Potion potion = Potion.getPotion(this.potionId);
        potion.setSplash(true);
        Effect effect = potion.getEffect();
        CompoundTag nbt = Entity.getDefaultNBT(this);
        nbt.putShort("PotionId", this.potionId);
        nbt.putList("mobEffects", new ListTag<>().add(
                new CompoundTag().putByte("Id", effect.getId())
                        .putByte("Amplifier", effect.getAmplifier())
                        .putInt("Duration", effect.getDuration())
                        .putBoolean("Ambient", effect.isAmbient())
                        .putBoolean("DisplayOnScreenTextureAnimation", effect.isVisible())
        ));
        Entity entity = Entity.createEntity("AreaEffectCloud", this.chunk, nbt);
        if (entity instanceof EntityAreaEffectCloud entityAreaEffectCloud) {
            entityAreaEffectCloud.setOwner(this.shootingEntity);
            entityAreaEffectCloud.spawnToAll();
        }
        this.close();
    }
}
