package cn.nukkit.entity.mob;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityChicken;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.SetEntityLinkPacket;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

/**
 * @author Nissining
 */
public class EntityDefaultJockey extends EntityWalkingMob {
    /**
     * todo 支持其他的敌对型生物
     */
    @Getter
    @Setter
    private EntityWalkingMob mob;
    @Getter
    @Setter
    private boolean isJockey = false;

    public EntityDefaultJockey(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public int getNetworkId() {
        return EntityChicken.NETWORK_ID;
    }

    @Override
    public float getHeight() {
        return 0.95F;
    }

    @Override
    public float getWidth() {
        return 0.3F;
    }

    @Override
    public int getDamage() {
        return 0;
    }

    @Override
    public double getSpeed() {
        return 1.5D;
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        setMob(new EntityZombie(this.getChunk(), Entity.getDefaultNBT(this)));
        Optional.ofNullable(getMob())
                .ifPresent(mob -> {
                    mob.setBaby(true);
                    mob.spawnToAll();
                    this.mountEntity(mob);
                });
    }

    @Override
    public boolean mountEntity(Entity entity) {
        broadcastLinkPacket(entity, SetEntityLinkPacket.TYPE_RIDE);

        // Add variables to entity
        entity.riding = this;
        entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
        passengers.add(entity);

        entity.setSeatPosition(new Vector3f(0, getHeight() * 0.5f));
        updatePassengerPosition(entity);
        return true;
    }

    @Override
    public boolean dismountEntity(Entity entity) {
        broadcastLinkPacket(entity, SetEntityLinkPacket.TYPE_REMOVE);
        // Refurbish the entity
        entity.riding = null;
        entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
        passengers.remove(entity);

        entity.setSeatPosition(new Vector3f());
        updatePassengerPosition(entity);

        // Avoid issues with anti fly
        entity.resetFallDistance();
        return true;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        boolean b = super.onUpdate(currentTick);
        if (b) {
            Optional.ofNullable(getMob())
                    .ifPresent(mob -> {
                        if (mob.isAlive()) {
                            this.setRotation(mob.getYaw(), 0);
                        } else {
                            setMob(null);
                            followTarget = null;
                        }
                    });
        }
        return b;
    }

    @Override
    public void attackEntity(Entity player) {

    }
}
