package cn.nukkit.entity.projectile;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFire;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.BlockIgniteEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;

public class EntitySmallFireBall extends EntityProjectile {

    public static final int NETWORK_ID = 94;

    public EntitySmallFireBall(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntitySmallFireBall(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.31f;
    }

    @Override
    public float getHeight() {
        return 0.31f;
    }

    @Override
    public float getGravity() {
        return 0.01f;
    }

    @Override
    public float getDrag() {
        return 0.005f;
    }

    @Override
    public double getBaseDamage() {
        return 5;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.age > 1200 || this.isCollided || this.hadCollision) {
            this.close();
            return false;
        } else {
            this.fireTicks = 2;
        }

        super.onUpdate(currentTick);
        return !this.closed;
    }
    
    @Override
    public void onCollideWithEntity(Entity entity) {
        super.onCollideWithEntity(entity);
        this.isCollided = true;
        entity.setOnFire(5);
    }

    @Override
    protected void onHitGround(Vector3 vector3) {
        Block block = this.level.getBlock(this.chunk, vector3.getFloorX(), vector3.getFloorY(), vector3.getFloorZ(), 0, false);
        if (block.hasEntityCollision()) {
            block.onEntityCollide(this);
            return;
        }
        if (this.getLevelBlock().getId() == Block.AIR) {
            BlockFire fire = (BlockFire) Block.get(BlockID.FIRE);
            fire.x = this.x;
            fire.y = this.y;
            fire.z = this.z;
            fire.level = level;

            if (fire.isBlockTopFacingSurfaceSolid(fire.down()) || fire.canNeighborBurn()) {
                BlockIgniteEvent e = new BlockIgniteEvent(this.getLevelBlock(), null, null, BlockIgniteEvent.BlockIgniteCause.FIREBALL);
                level.getServer().getPluginManager().callEvent(e);
                if (!e.isCancelled()) {
                    level.setBlock(fire, fire, true);
                }
            }
        }
    }
}
