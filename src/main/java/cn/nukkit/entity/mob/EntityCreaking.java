package cn.nukkit.entity.mob;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCreakingHeart;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;

public class EntityCreaking extends EntityWalkingMob {

    public static final int NETWORK_ID = 146;

    private static final String TAG_HEART_X = "CreakingHeartX";
    private static final String TAG_HEART_Y = "CreakingHeartY";
    private static final String TAG_HEART_Z = "CreakingHeartZ";

    private BlockEntityCreakingHeart creakingHeart;
    private int heartX, heartY, heartZ;

    public EntityCreaking(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(1);
        super.initEntity();

        if (this.namedTag.contains(TAG_HEART_X)) {
            this.heartX = this.namedTag.getInt(TAG_HEART_X);
            this.heartY = this.namedTag.getInt(TAG_HEART_Y);
            this.heartZ = this.namedTag.getInt(TAG_HEART_Z);
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        if (this.creakingHeart != null && !this.creakingHeart.closed) {
            this.namedTag.putInt(TAG_HEART_X, this.creakingHeart.getFloorX());
            this.namedTag.putInt(TAG_HEART_Y, this.creakingHeart.getFloorY());
            this.namedTag.putInt(TAG_HEART_Z, this.creakingHeart.getFloorZ());
        } else {
            this.namedTag.remove(TAG_HEART_X);
            this.namedTag.remove(TAG_HEART_Y);
            this.namedTag.remove(TAG_HEART_Z);
        }
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.9f;
    }

    @Override
    public float getHeight() {
        return 2.7f;
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        // Orphan check every 40 ticks for Heart-linked Creakings
        if (currentTick % 40 == 0 && this.hasHeartPosition()) {
            BlockEntityCreakingHeart heart = this.getCreakingHeart();
            if (heart == null || heart.closed || !heart.isBlockEntityValid()) {
                this.kill();
            }
        }

        return super.onUpdate(currentTick);
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        // Heart-linked Creakings are immune to all damage except VOID and SUICIDE
        if (this.hasHeartPosition()) {
            DamageCause cause = source.getCause();
            if (cause != DamageCause.VOID && cause != DamageCause.SUICIDE) {
                return false;
            }
        }
        return super.attack(source);
    }

    @Override
    public boolean canDespawn() {
        return !this.hasHeartPosition() && !this.hasCustomName();
    }

    @Override
    public void attackEntity(Entity player) {

    }

    public void setCreakingHeart(BlockEntityCreakingHeart heart) {
        this.creakingHeart = heart;
        if (heart != null) {
            this.heartX = heart.getFloorX();
            this.heartY = heart.getFloorY();
            this.heartZ = heart.getFloorZ();
        }
    }

    public BlockEntityCreakingHeart getCreakingHeart() {
        if (this.creakingHeart != null && !this.creakingHeart.closed) {
            return this.creakingHeart;
        }

        // Lazy-resolve from persisted coordinates
        if (this.hasHeartPosition() && this.level.isChunkLoaded(this.heartX >> 4, this.heartZ >> 4)) {
            BlockEntity be = this.level.getBlockEntity(new Vector3(this.heartX, this.heartY, this.heartZ));
            if (be instanceof BlockEntityCreakingHeart heart && heart.isBlockEntityValid()) {
                this.creakingHeart = heart;
                return heart;
            }
        }

        return null;
    }

    private boolean hasHeartPosition() {
        return this.namedTag.contains(TAG_HEART_X) || this.creakingHeart != null;
    }
}
