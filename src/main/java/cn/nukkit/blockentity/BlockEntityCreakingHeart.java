package cn.nukkit.blockentity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockCreakingHeart;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityCreaking;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class BlockEntityCreakingHeart extends BlockEntity {

    private static final String TAG_AXIS = "Axis";
    private static final String TAG_LINKED_CREAKING_ID = "LinkedCreakingId";
    private static final String TAG_LINKED_CREAKING_UUID_MOST = "LinkedCreakingUUIDMost";
    private static final String TAG_LINKED_CREAKING_UUID_LEAST = "LinkedCreakingUUIDLeast";
    private static final double UNLINK_DISTANCE = 32d;
    private static final double UNLINK_DISTANCE_SQUARED = UNLINK_DISTANCE * UNLINK_DISTANCE;

    private EntityCreaking linkedCreaking;
    private final double spawnRangeHorizontal = 16.5;
    private final double spawnRangeVertical = 8.5;

    public BlockEntityCreakingHeart(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        super.initBlockEntity();

        if (!this.namedTag.contains(TAG_AXIS)) {
            this.namedTag.putInt(TAG_AXIS, this.getBlock() instanceof BlockCreakingHeart block ? block.getDamage() & 0b11 : 0);
        }
        this.loadLinkedCreaking();

        if (this.getLevel().isOverWorld()) {
            this.scheduleUpdate();
        }
    }

    @Override
    public boolean onUpdate() {
        if (this.closed) {
            return false;
        }

        // TODO: play creaking heart ambient sound once Sound enum contains creaking entries.
        if (this.level.getCurrentTick() % 40 == 0) {
            EntityCreaking creaking = this.getLinkedCreaking();
            if (creaking == null && this.isActive() && this.canSpawnProtector()) {
                this.spawnProtector();
            } else if (creaking != null && creaking.distanceSquared(this) > UNLINK_DISTANCE_SQUARED) {
                creaking.kill();
                this.setLinkedCreaking(null);
            }
        }

        return true;
    }

    public void setLinkedCreaking(EntityCreaking linkedCreaking) {
        if (this.linkedCreaking != null && this.linkedCreaking != linkedCreaking) {
            this.linkedCreaking.setCreakingHeart(null);
        }
        this.linkedCreaking = linkedCreaking;
        if (linkedCreaking != null) {
            linkedCreaking.setCreakingHeart(this);
        }
        this.saveNBT();
        this.setDirty();
    }

    public void removeProtector() {
        EntityCreaking creaking = this.getLinkedCreaking();
        if (creaking != null) {
            creaking.kill();
        }
        this.setLinkedCreaking(null);
    }

    public void onHeartDormant() {
        EntityCreaking creaking = this.getLinkedCreaking();
        if (creaking == null) {
            return;
        }
        if (creaking.hasCustomName()) {
            this.setLinkedCreaking(null);
        } else {
            creaking.kill();
            this.setLinkedCreaking(null);
        }
    }

    public EntityCreaking getLinkedCreaking() {
        if (this.linkedCreaking == null || this.linkedCreaking.isClosed() || !this.linkedCreaking.isAlive()) {
            this.linkedCreaking = null;
        }
        return this.linkedCreaking;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        BlockCreakingHeart block = this.getBlock() instanceof BlockCreakingHeart creakingHeart ? creakingHeart : null;
        if (block != null) {
            this.namedTag.putInt(TAG_AXIS, block.getDamage() & 0b11);
        }

        EntityCreaking creaking = this.getLinkedCreaking();
        if (creaking != null) {
            this.namedTag.putLong(TAG_LINKED_CREAKING_ID, creaking.getId());
            UUID uuid = creaking.getUniqueId();
            this.namedTag.putLong(TAG_LINKED_CREAKING_UUID_MOST, uuid.getMostSignificantBits());
            this.namedTag.putLong(TAG_LINKED_CREAKING_UUID_LEAST, uuid.getLeastSignificantBits());
        } else {
            this.namedTag.remove(TAG_LINKED_CREAKING_ID);
            this.namedTag.remove(TAG_LINKED_CREAKING_UUID_MOST);
            this.namedTag.remove(TAG_LINKED_CREAKING_UUID_LEAST);
        }
    }

    @Override
    public void onBreak() {
        this.removeProtector();
        super.onBreak();
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.getLevelBlock() instanceof BlockCreakingHeart;
    }

    private void loadLinkedCreaking() {
        if (this.level == null) {
            return;
        }

        if (this.namedTag.contains(TAG_LINKED_CREAKING_ID)) {
            Entity entity = this.level.getEntity(this.namedTag.getLong(TAG_LINKED_CREAKING_ID));
            if (entity instanceof EntityCreaking creaking && !creaking.isClosed() && creaking.isAlive()) {
                this.linkedCreaking = creaking;
                creaking.setCreakingHeart(this);
                return;
            }
        }

        if (this.namedTag.contains(TAG_LINKED_CREAKING_UUID_MOST) && this.namedTag.contains(TAG_LINKED_CREAKING_UUID_LEAST)) {
            UUID uuid = new UUID(this.namedTag.getLong(TAG_LINKED_CREAKING_UUID_MOST), this.namedTag.getLong(TAG_LINKED_CREAKING_UUID_LEAST));
            for (Entity entity : this.level.getEntities()) {
                if (entity instanceof EntityCreaking creaking && uuid.equals(creaking.getUniqueId()) && !creaking.isClosed() && creaking.isAlive()) {
                    this.linkedCreaking = creaking;
                    creaking.setCreakingHeart(this);
                    return;
                }
            }
        }
    }

    private boolean isActive() {
        return this.getBlock() instanceof BlockCreakingHeart block && block.isActive();
    }

    private boolean canSpawnProtector() {
        return !this.level.isDaytime() || this.level.isRaining() || this.level.isThundering();
    }

    private void spawnProtector() {
        Position pos = this.findSpawnPosition();
        if (pos == null) {
            return;
        }

        Entity entity = Entity.createEntity("Creaking", pos);
        if (!(entity instanceof EntityCreaking creaking)) {
            return;
        }

        CreatureSpawnEvent event = new CreatureSpawnEvent(creaking.getNetworkId(), pos, creaking.namedTag, CreatureSpawnEvent.SpawnReason.NATURAL, null);
        Server.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            creaking.close();
            return;
        }

        // TODO: play creaking spawn sound once Sound enum contains creaking entries.
        this.setLinkedCreaking(creaking);
        creaking.spawnToAll();
    }

    private Position findSpawnPosition() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            int x = this.getFloorX() + random.nextInt((int) -this.spawnRangeHorizontal, (int) this.spawnRangeHorizontal + 1);
            int z = this.getFloorZ() + random.nextInt((int) -this.spawnRangeHorizontal, (int) this.spawnRangeHorizontal + 1);
            int startY = this.getFloorY() + random.nextInt((int) -this.spawnRangeVertical, (int) this.spawnRangeVertical + 1);
            Position pos = this.findSpawnPositionAt(x, startY, z);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    private Position findSpawnPositionAt(int x, int startY, int z) {
        int minY = Math.max(this.level.getMinBlockY() + 1, (int) Math.floor(this.y - this.spawnRangeVertical));
        int maxY = Math.min(this.level.getMaxBlockY() - 2, (int) Math.ceil(this.y + this.spawnRangeVertical));
        startY = Math.max(minY, Math.min(maxY, startY));

        for (int offset = 0; offset <= maxY - minY; offset++) {
            int upY = startY + offset;
            if (upY <= maxY && this.isValidSpawnPosition(x, upY, z)) {
                return new Position(x + 0.5, upY, z + 0.5, this.level);
            }

            int downY = startY - offset;
            if (offset != 0 && downY >= minY && this.isValidSpawnPosition(x, downY, z)) {
                return new Position(x + 0.5, downY, z + 0.5, this.level);
            }
        }
        return null;
    }

    private boolean isValidSpawnPosition(int x, int y, int z) {
        Block ground = this.level.getBlock(x, y - 1, z);
        Block feet = this.level.getBlock(x, y, z);
        Block head = this.level.getBlock(x, y + 1, z);
        return ground.isSolid() && feet.canPassThrough() && head.canPassThrough();
    }
}
