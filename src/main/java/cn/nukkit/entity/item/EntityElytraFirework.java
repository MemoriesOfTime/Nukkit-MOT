package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.data.NBTEntityData;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 鞘翅烟花火箭实体 - 跟随玩家移动
 */
public class EntityElytraFirework extends EntityFirework {

    private final Player followingPlayer;
    private int fireworkAge = 0;

    public EntityElytraFirework(FullChunk chunk, CompoundTag nbt, Player player) {
        super(chunk, nbt, true);
        this.followingPlayer = player;

        // 鞘翅烟花生命周期较短：20-32 tick (约1-1.6秒)
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        this.setLifetime(20 + rand.nextInt(13));

        if (namedTag.contains("FireworkItem")) {
            this.setFirework(NBTIO.getItemHelper(this.namedTag.getCompound("FireworkItem")));
        }
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
            Vector3 motion = this.followingPlayer.getMotion();
            this.motionX = motion.x;
            this.motionY = motion.y;
            this.motionZ = motion.z;

            this.setPosition(this.followingPlayer.getNextPosition().add(0, -0.5, 0));

            updateRotation();

            this.updateMovement();

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
        this.firework = item;
        this.setDataProperty(new NBTEntityData(Entity.DATA_DISPLAY_ITEM, firework));
        this.setDataProperty(new LongEntityData(Entity.DATA_HAS_DISPLAY, -1), false);
        this.setDataProperty(new Vector3fEntityData(Entity.DATA_FIREWORK_DIRECTION,
                new Vector3f((float) motionX, (float) motionY, (float) motionZ)), false);
    }

    public Player getFollowingPlayer() {
        return followingPlayer;
    }
}
