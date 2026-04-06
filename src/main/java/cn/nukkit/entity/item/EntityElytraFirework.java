package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

/**
 * 鞘翅烟花火箭实体 - 跟随玩家移动
 */
public class EntityElytraFirework extends EntityFirework {

    private final Player followingPlayer;
    private int fireworkAge = 0;

    public EntityElytraFirework(FullChunk chunk, CompoundTag nbt, Player player) {
        super(chunk, nbt, true, player);
        this.followingPlayer = player;

        this.setDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_INVISIBLE, true);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0 && !this.justCreated) {
            return true;
        }

        this.lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        if (this.isAlive() && this.followingPlayer != null && !this.followingPlayer.closed) {
            if (!this.followingPlayer.isGliding()) {
                this.setPosition(this.followingPlayer.add(0, this.followingPlayer.getEyeHeight() * 0.5, 0));
                this.motionX = 0;
                this.motionY = 0;
                this.motionZ = 0;
                this.updateMovement();
            } else {
                Vector3 motion = this.followingPlayer.getMotion();
                Vector3 look = this.followingPlayer.getDirectionVector();
                this.followingPlayer.setMotion(motion.add(
                        look.x * 0.1 + (look.x * 1.5 - motion.x) * 0.5,
                        look.y * 0.1 + (look.y * 1.5 - motion.y) * 0.5,
                        look.z * 0.1 + (look.z * 1.5 - motion.z) * 0.5
                ));
                motion = this.followingPlayer.getMotion();

                this.motionX = motion.x;
                this.motionY = motion.y;
                this.motionZ = motion.z;

                this.setPosition(this.followingPlayer.add(0, this.followingPlayer.getEyeHeight() * 0.5, 0));

                updateRotation();

                this.updateMovement();
            }

            Vector3 moveVector = new Vector3(this.x + this.motionX, this.y + this.motionY, this.z + this.motionZ);
            Entity collisionEntity = this.findCollisionEntity(moveVector);
            if (collisionEntity != null) {
                this.explode();
                return true;
            }

            if (this.hasExplosions()) {
                boolean isCollidedWithBlock = this.level
                        .getCollisionCubes(this, this.boundingBox.addCoord(this.motionX, this.motionY, this.motionZ))
                        .length > 0;
                if (isCollidedWithBlock && !this.hadCollision) {
                    this.hadCollision = true;
                    this.explode();
                    return true;
                } else if (!isCollidedWithBlock && this.hadCollision) {
                    this.hadCollision = false;
                }
            }

            if (this.fireworkAge == 0) {
                this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_LAUNCH);
            }

            ++this.fireworkAge;
            hasUpdate = true;

            if (this.fireworkAge >= this.lifetime) {
                explode();
            }
        } else {
            this.kill();
        }

        return hasUpdate;
    }

    private void updateRotation() {
        float f = (float) Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        this.yaw = (float) (Math.atan2(this.motionX, this.motionZ) * 57.29577951308232D);
        this.pitch = (float) (Math.atan2(this.motionY, f) * 57.29577951308232D);
    }

    @Override
    public void setFirework(Item item) {
        super.setFirework(item);
        this.setDataProperty(new LongEntityData(Entity.DATA_HAS_DISPLAY, -1), false);
        this.setDataProperty(new Vector3fEntityData(Entity.DATA_FIREWORK_DIRECTION,
                new Vector3f((float) motionX, (float) motionY, (float) motionZ)), false);
    }

    @Override
    protected void dealExplosionDamage() {
        int explosionCount = this.getExplosionCount();
        if (explosionCount <= 0) {
            return;
        }

        if (this.followingPlayer != null && this.followingPlayer.isAlive()) {
            this.followingPlayer.attack(this.createExplosionDamageEvent(this.followingPlayer, 5 + explosionCount * 2));
        }

        super.dealExplosionDamage();
    }

    @Override
    protected boolean shouldSkipExplosionDamageTarget(Entity target) {
        return target == this.followingPlayer;
    }

    public Player getFollowingPlayer() {
        return followingPlayer;
    }
}
