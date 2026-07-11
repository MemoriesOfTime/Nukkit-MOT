package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockLava;
import cn.nukkit.block.BlockLiquid;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.event.entity.EntityBlockChangeEvent;
import cn.nukkit.event.entity.EntityDamageByBlockEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.DestroyBlockParticle;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.CollisionHelper;

import java.util.Comparator;
import java.util.List;

/**
 * @author MagicDroidX
 */
public class EntityFallingBlock extends Entity {

    public static final int NETWORK_ID = 66;

    protected static final int OUT_OF_WORLD_DESPAWN_TIME = 100;
    protected static final int DESPAWN_TIME = 600;
    protected static final double OVERLAP_RELOCATION_STEP = 1.001;
    protected static final int MAX_CONSECUTIVE_OVERLAP_RELOCATIONS = 3;

    @Override
    public float getWidth() {
        return 0.98f;
    }

    @Override
    public float getLength() {
        return 0.98f;
    }

    @Override
    public float getHeight() {
        return 0.98f;
    }

    @Override
    protected float getGravity() {
        return 0.04f;
    }

    @Override
    protected float getDrag() {
        return 0.02f;
    }

    @Override
    protected float getBaseOffset() {
        return 0.49f;
    }

    @Override
    public boolean canCollide() {
        return blockId == BlockID.ANVIL;
    }

    protected int blockId;
    protected int damage;
    protected boolean breakOnLava;
    protected int aliveTime;
    protected int overlapRelocationCount;
    protected boolean relocatedForOverlap;

    public EntityFallingBlock(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        if (this.namedTag != null) {
            if (this.namedTag.contains("TileID")) {
                this.blockId = this.namedTag.getInt("TileID");
            } else if (this.namedTag.contains("Tile")) {
                this.blockId = this.namedTag.getInt("Tile");
                this.namedTag.putInt("TileID", blockId);
            }

            if (this.namedTag.contains("Data")) {
                damage = this.namedTag.getByte("Data");
            }

            this.breakOnLava = this.namedTag.getBoolean("BreakOnLava");
            this.aliveTime = this.namedTag.getInt("Time");
        }

        if (this.blockId == 0) {
            this.close();
            return;
        }

        this.fireProof = true;
    }

    @Override // Multiversion: display correct block
    public void spawnTo(Player player) {
        if (!this.hasSpawned.containsKey(player.getLoaderId()) && player.usedChunks.containsKey(Level.chunkHash(this.chunk.getX(), this.chunk.getZ()))) {
            AddEntityPacket addEntity = new AddEntityPacket();
            addEntity.type = this.getNetworkId();
            addEntity.entityUniqueId = this.id;
            addEntity.entityRuntimeId = this.id;
            addEntity.yaw = (float) this.yaw;
            addEntity.headYaw = (float) this.yaw;
            addEntity.pitch = (float) this.pitch;
            addEntity.x = (float) this.x;
            addEntity.y = (float) this.y;
            addEntity.z = (float) this.z;
            addEntity.speedX = (float) this.motionX;
            addEntity.speedY = (float) this.motionY;
            addEntity.speedZ = (float) this.motionZ;
            int runtimeId;
            if (player.protocol >= ProtocolInfo.v1_2_13) {
                runtimeId = GlobalBlockPalette.getOrCreateRuntimeId(player.getGameVersion(), this.blockId, this.damage);
            } else {
                runtimeId = this.blockId | this.damage << 8;
            }
            addEntity.metadata = this.dataProperties.clone().put(new IntEntityData(DATA_VARIANT, runtimeId));
            player.dataPacket(addEntity);
            this.hasSpawned.put(player.getLoaderId(), player);
        }
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return blockId == BlockID.ANVIL;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return source.getCause() == DamageCause.VOID && super.attack(source);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        refreshOverlapRelocationState();

        int tickDiff = currentTick - lastUpdate;
        if (tickDiff <= 0 && !justCreated) {
            return true;
        }

        lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        if (this.isAlive()) {
            motionY -= this.getGravity();

            this.move(motionX, motionY, motionZ);

            float friction = 1 - this.getDrag();

            motionX *= friction;
            motionY *= 1 - this.getDrag();
            motionZ *= friction;
            this.aliveTime += tickDiff;

            Vector3 pos = new Vector3(x - 0.5, y, z - 0.5).round();
            if (shouldDespawn(this.aliveTime, this.onGround, this.getFloorY(), this.level.getDimensionData().getMinHeight(), this.level.getDimensionData().getMaxHeight())) {
                this.close();
                if (this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
                    this.level.dropItem(this, Block.get(this.getBlock(), this.getDamage()).toItem());
                }
                return true;
            }

            if (breakOnLava && level.getBlock(pos.subtract(0, 1, 0)) instanceof BlockLava) {
                this.close();
                if (this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
                    this.level.dropItem(this, Block.get(this.getBlock(), this.getDamage()).toItem());
                }
                level.addParticle(new DestroyBlockParticle(pos, Block.get(this.getBlock(), this.getDamage())));
                return true;
            }

            if (!this.onGround && this.motionY >= 0) {
                Block below = this.level.getBlock(this.getFloorX(), this.getFloorY() - 1, this.getFloorZ());
                if (!below.canPassThrough()) {
                    this.close();
                    if (this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
                        this.level.dropItem(this, Block.get(this.getBlock(), this.getDamage()).toItem());
                    }
                    return true;
                }
            }

            if (this.onGround) {
                this.close();

                Block landingBlock = this.level.getBlock(this.add(0, 0.0001, 0));
                if (this.getBlock() == Block.SNOW_LAYER && landingBlock.getId() == Block.SNOW_LAYER && (landingBlock.getDamage() & 0x7) != 0x7) {
                    int mergedHeight = (landingBlock.getDamage() & 0x7) + 1 + (this.getDamage() & 0x7) + 1;
                    if (mergedHeight > 8) {
                        EntityBlockChangeEvent event = new EntityBlockChangeEvent(this, landingBlock, Block.get(Block.SNOW_LAYER, 0x7));
                        this.server.getPluginManager().callEvent(event);
                        if (!event.isCancelled()) {
                            this.level.setBlock(landingBlock, event.getTo(), true);
                            Vector3 abovePos = landingBlock.up();
                            Block aboveBlock = this.level.getBlock(abovePos);
                            if (aboveBlock.getId() == Block.AIR) {
                                EntityBlockChangeEvent event2 = new EntityBlockChangeEvent(this, aboveBlock, Block.get(Block.SNOW_LAYER, mergedHeight - 9)); // -8-1
                                this.server.getPluginManager().callEvent(event2);
                                if (!event2.isCancelled()) {
                                    this.level.setBlock(abovePos, event2.getTo(), true);
                                }
                            }
                        }
                    } else {
                        EntityBlockChangeEvent event = new EntityBlockChangeEvent(this, landingBlock, Block.get(Block.SNOW_LAYER, mergedHeight - 1));
                        this.server.getPluginManager().callEvent(event);
                        if (!event.isCancelled()) {
                            this.level.setBlock(landingBlock, event.getTo(), true);
                        }
                    }
                } else if (shouldDropOnLanding(landingBlock, this.getBlock())) {
                    if (this.getBlock() != Block.SNOW_LAYER ? this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS) : this.level.getGameRules().getBoolean(GameRule.DO_TILE_DROPS)) {
                        this.level.dropItem(this, Block.get(this.getBlock(), this.getDamage()).toItem());
                    }
                } else if (landingBlock.canBeReplaced()) {
                    EntityBlockChangeEvent event = new EntityBlockChangeEvent(this, landingBlock, Block.get(blockId, damage));
                    this.server.getPluginManager().callEvent(event);
                    if (!event.isCancelled()) {
                        this.level.setBlock(landingBlock, event.getTo(), true, true);
                        this.level.scheduleUpdate(this.level.getBlock(landingBlock), 1);

                        // Relocate overlapping falling blocks upward to avoid repeated landing in the same position
                        AxisAlignedBB bb = this.getBoundingBox();
                        bb.setMaxY(bb.getMaxY() + 0.021); //实体高度为0.98，这里增加一点防止误判
                        resolveOverlappingFallingBlocks(CollisionHelper.getCollidingEntities(this.level, bb), landingBlock.y);

                        if (event.getTo().getId() == Item.ANVIL || blockId == Item.POINTED_DRIPSTONE) {
                            if (blockId == Item.ANVIL) {
                                this.getLevel().addLevelEvent(this, LevelEventPacket.EVENT_SOUND_ANVIL_FALL);
                            } else {
                                this.getLevel().dropItem(this, Block.get(blockId, event.getTo().getDamage()).toItem());
                            }

                            List<Entity> e = CollisionHelper.getCollidingEntities(this.level, this.getBoundingBox(), this);
                            for (Entity entity : e) {
                                if (entity instanceof EntityLiving && highestPosition > y) {
                                    entity.attack(new EntityDamageByBlockEvent(event.getTo(), entity, DamageCause.CONTACT, (float) Math.min(40, Math.max(0, (highestPosition - y) * 2))));
                                }
                            }
                        }
                    }
                }
                hasUpdate = true;
            }

            this.updateMovement();
        }

        return hasUpdate || !this.onGround || Math.abs(motionX) > 0.00001 || Math.abs(motionY) > 0.00001 || Math.abs(motionZ) > 0.00001;
    }

    public int getBlock() {
        return blockId;
    }

    public int getDamage() {
        return damage;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        namedTag.putInt("TileID", blockId);
        namedTag.putByte("Data", damage);
        namedTag.putInt("Time", aliveTime);
    }

    @Override
    public boolean canBeMovedByCurrents() {
        return !this.onGround;
    }

    protected static boolean shouldDespawn(int aliveTime, boolean onGround, int blockY, int minHeight, int maxHeight) {
        return !onGround && ((aliveTime > OUT_OF_WORLD_DESPAWN_TIME && (blockY <= minHeight || blockY > maxHeight))
                || aliveTime > DESPAWN_TIME);
    }

    protected static boolean shouldDropOnLanding(Block landingBlock, int fallingBlockId) {
        return !landingBlock.canBeReplaced()
                || fallingBlockId == Block.SNOW_LAYER && landingBlock instanceof BlockLiquid;
    }

    protected static void resolveOverlappingFallingBlocks(List<Entity> collidingEntities, double baseY) {
        List<EntityFallingBlock> overlaps = collidingEntities.stream()
                .filter(EntityFallingBlock.class::isInstance)
                .map(EntityFallingBlock.class::cast)
                .filter(entity -> !entity.closed)
                .sorted(Comparator.comparingDouble((EntityFallingBlock entity) -> entity.y)
                        .thenComparingLong(Entity::getId))
                .toList();

        double targetY = baseY + OVERLAP_RELOCATION_STEP;
        for (EntityFallingBlock overlap : overlaps) {
            overlap.relocateForContinuedFalling(new Vector3(overlap.x, targetY, overlap.z));
            targetY += OVERLAP_RELOCATION_STEP;
        }
    }

    protected void refreshOverlapRelocationState() {
        if (this.relocatedForOverlap) {
            this.relocatedForOverlap = false;
        } else if (this.overlapRelocationCount != 0) {
            this.overlapRelocationCount = 0;
        }
    }

    protected boolean relocateForContinuedFalling(Vector3 targetPos) {
        int nextRelocationCount = this.overlapRelocationCount + 1;
        if (nextRelocationCount > MAX_CONSECUTIVE_OVERLAP_RELOCATIONS) {
            return dropDueToRepeatedOverlapRelocation();
        }

        if (!this.setPosition(targetPos)) {
            return false;
        }

        this.overlapRelocationCount = nextRelocationCount;
        this.relocatedForOverlap = true;
        this.onGround = false;
        this.isCollided = false;
        this.isCollidedVertically = false;
        this.isCollidedHorizontally = false;
        this.blocksAround = null;
        this.collisionBlocks = null;
        this.updateMovement();
        return true;
    }

    protected boolean dropDueToRepeatedOverlapRelocation() {
        this.close();
        if (this.level != null && this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
            this.level.dropItem(this, Block.get(this.getBlock(), this.getDamage()).toItem());
        }
        return false;
    }

    @Override
    public void resetFallDistance() {
        if (!this.closed) { // For falling anvil: do not reset fall distance before dealing damage to entities
            this.highestPosition = this.y;
        }
    }
}
