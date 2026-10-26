package cn.nukkit.entity;

import cn.nukkit.block.*;
import cn.nukkit.entity.mob.EntityDrowned;
import cn.nukkit.entity.passive.EntityIronGolem;
import cn.nukkit.entity.passive.EntityLlama;
import cn.nukkit.entity.passive.EntityPig;
import cn.nukkit.entity.passive.EntitySkeletonHorse;
import cn.nukkit.entity.route.RouteFinder;
import cn.nukkit.entity.route.RouteFinderSearchTask;
import cn.nukkit.entity.route.RouteFinderThreadPool;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.math3.util.FastMath;

public abstract class EntityWalking extends BaseEntity {
    private static final double FLOW_MULTIPLIER = 0.1;

    @Getter
    @Setter
    protected RouteFinder route;

    public EntityWalking(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    protected void checkTarget() {
        if (this.isKnockback()) {
            return;
        }

        if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive() && this.followTarget.canBeFollowed() && targetOption((EntityCreature) this.followTarget, this.distanceSquared(this.followTarget)) && this.target != null) {
            return;
        }

        boolean searchForTarget = this.shouldRunTargetCheck();
        this.followTarget = null;

        if (!this.passengers.isEmpty() && !(this instanceof EntityLlama) && !(this instanceof EntityPig)) {
            return;
        }

        if (searchForTarget) {
            for (Entity entity : this.getLevel().getNearbyEntities(EntityRanges.createTargetSearchBox(this), this, false, true)) {
                if (entity == this || !(entity instanceof EntityCreature creature) || entity.closed || !this.canTarget(entity)) {
                    continue;
                }

                if (creature instanceof BaseEntity baseEntity && baseEntity.isFriendly() == this.isFriendly() && !this.isInLove()) {
                    continue;
                }

                double distance = this.distanceSquared(creature);
                if (!this.targetOption(creature, distance)) {
                    continue;
                }

                this.stayTime = 0;
                this.moveTime = 0;
                this.followTarget = creature;
                if (this.route == null && this.passengers.isEmpty()) {
                    this.target = creature;
                }
            }
        }

        if (!this.canSetTemporalTarget()) {
            return;
        }

        if (this.stayTime > 0) {
            // Rest means rest; the old 5%-per-tick re-roll made standing entities twitch their facing.
            return;
        } else if (Utils.rand(1, 100) == 1) {
            this.stayTime = Utils.rand(80, 200);
            int wanderX = Utils.rand(4, 10);
            int wanderZ = Utils.rand(4, 10);
            this.target = this.add(Utils.rand() ? wanderX : -wanderX, Utils.rand(-20.0, 20.0) / 10, Utils.rand() ? wanderZ : -wanderZ);
        } else if (this.moveTime <= 0 || this.target == null) {
            this.stayTime = 0;
            this.moveTime = Utils.rand(80, 200);
            double tx = this.x;
            double tz = this.z;
            int attempts = 0;
            boolean inWater = true;
            boolean foundLoadedColumn = false;
            while (attempts++ < 10 && inWater) {
                int wanderX = Utils.rand(4, 10);
                int wanderZ = Utils.rand(4, 10);
                double candidateX = this.x + (Utils.rand() ? wanderX : -wanderX);
                double candidateZ = this.z + (Utils.rand() ? wanderZ : -wanderZ);
                int txFloor = NukkitMath.floorDouble(candidateX);
                int tzFloor = NukkitMath.floorDouble(candidateZ);
                FullChunk candidateChunk = level.getChunkIfLoaded(txFloor >> 4, tzFloor >> 4);
                if (candidateChunk == null) {
                    continue;
                }
                tx = candidateX;
                tz = candidateZ;
                foundLoadedColumn = true;
                int surfaceY = candidateChunk.getHighestBlockAt(txFloor & 15, tzFloor & 15);
                inWater = Block.isWater(level.getBlockIdAt(candidateChunk, txFloor, surfaceY, tzFloor));
            }
            if (!foundLoadedColumn) {
                // Retry on the next active tick instead of loading disk chunks for a random target.
                this.target = null;
                this.moveTime = 0;
                return;
            }
            this.target = new Vector3(tx, this.y + Utils.rand(-20.0, 20.0) / 10, tz);
        }
    }

    private boolean isSolidBlockAt(Level level, int id, int x, int y, int z) {
        int state = Block.solidState(id);
        if (state >= 0) {
            return state == 1;
        }
        return level.getBlock(x, y, z).isSolid();
    }

    private boolean isPassableBlockAt(Level level, int id, int x, int y, int z) {
        int state = Block.passableState(id);
        if (state >= 0) {
            return state == 1;
        }
        return level.getBlock(x, y, z).canPassThrough();
    }

    protected boolean checkJump(double dx, double dz) {
        if (this.motionY == this.getGravity() * 2) {
            return this.canSwimIn(level.getBlockIdAt(chunk, NukkitMath.floorDouble(this.x), (int) this.y, NukkitMath.floorDouble(this.z)));
        } else {
            if (this.canSwimIn(level.getBlockIdAt(chunk, NukkitMath.floorDouble(this.x), (int) (this.y + 0.8), NukkitMath.floorDouble(this.z)))) {
                if (!(this.isDrowned || this instanceof EntityIronGolem || this instanceof EntitySkeletonHorse) || this.target == null) {
                    this.motionY = this.getGravity() * 2;
                }
                return true;
            }
        }

        if (!this.onGround || this.stayTime > 0 || (dx == 0 && dz == 0)) {
            return false;
        }

        Level level = this.level;
        int thatX = NukkitMath.floorDouble(this.x + dx);
        int thatY = (int) this.y;
        int thatZ = NukkitMath.floorDouble(this.z + dz);
        BlockFace face = this.getHorizontalFacing();
        int blockX = thatX + face.getXOffset();
        int blockY = thatY;
        int blockZ = thatZ + face.getZOffset();
        int blockId = this.getBuiltinBlockIdAt(blockX, blockY, blockZ, 0);
        int downId = this.getBuiltinBlockIdAt(blockX, blockY - 1, blockZ, 0);
        int downDownId = this.getBuiltinBlockIdAt(blockX, blockY - 2, blockZ, 0);
        int upId = this.getBuiltinBlockIdAt(blockX, blockY + 1, blockZ, 0);
        int thatUpId = this.getBuiltinBlockIdAt(thatX, thatY + 2, thatZ, 0);
        // Fall back as a group: custom blocks may override neighbour reads.
        if (blockId < 0 || downId < 0 || downDownId < 0 || upId < 0 || thatUpId < 0
                || this.getBuiltinBlockIdAt(thatX, thatY, thatZ, 0) < 0) {
            return this.checkJumpUsingBlocks(thatX, thatY, thatZ, face);
        }

        if (this.followTarget == null && this.passengers.isEmpty()
                && !this.isSolidBlockAt(level, downId, blockX, blockY - 1, blockZ)
                && !this.isSolidBlockAt(level, blockId, blockX, blockY, blockZ)
                && !this.isSolidBlockAt(level, downDownId, blockX, blockY - 2, blockZ)) {
            // "hack": try to make mobs not to be so suicidal
            this.stayTime = 10;
            return false;
        }

        if (!this.isPassableBlockAt(level, blockId, blockX, blockY, blockZ)
                && !Block.isFlowableId(blockId) && blockId != BlockID.SOUL_SAND
                && this.isPassableBlockAt(level, upId, blockX, blockY + 1, blockZ)
                && this.isPassableBlockAt(level, thatUpId, thatX, thatY + 2, thatZ)) {
            return this.jumpOver(level.getBlock(blockX, blockY, blockZ));
        }
        return false;
    }

    private boolean checkJumpUsingBlocks(int x, int y, int z, BlockFace face) {
        Block that = this.getLevel().getBlock(new Vector3(x, y, z));
        Block block = that.getSide(face);
        Block down;
        if (this.followTarget == null && this.passengers.isEmpty()
                && !(down = block.down()).isSolid() && !block.isSolid() && !down.down().isSolid()) {
            this.stayTime = 10;
            return false;
        }
        if (!block.canPassThrough() && !(block instanceof BlockFlowable)
                && block.getId() != BlockID.SOUL_SAND && block.up().canPassThrough() && that.up(2).canPassThrough()) {
            return this.jumpOver(block);
        }
        return false;
    }

    private boolean jumpOver(Block block) {
        if (block instanceof BlockFence || block instanceof BlockFenceGate) {
            this.motionY = this.getGravity();
        } else if (this.motionY <= this.getGravity() * 4 || block instanceof BlockStairs) {
            this.motionY = this.getGravity() * 4;
        } else if (this.motionY <= this.getGravity() * 8) {
            this.motionY = this.getGravity() * 8;
        } else {
            this.motionY += this.getGravity() * 0.25;
        }
        return true;
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!this.isInTickingRange()) {
            return null;
        }

        if (!isImmobile()) {
            if (this.age % 10 == 0 && this.route != null && !this.route.isSearching()) {
                // Only start the search: reading next() here raced with the async rebuild and
                // returned the fresh path's first node, a point right beside the entity.
                RouteFinderThreadPool.executeRouteFinderThread(new RouteFinderSearchTask(this.route));
            }

            if (this.isKnockback()) {
                if (this.riding == null) {
                    this.move(this.motionX, this.motionY, this.motionZ);
                    if (this instanceof EntityDrowned && this.isInsideOfWater()) {
                        this.motionY -= this.getGravity() * 0.3;
                    } else {
                        this.motionY -= this.getGravity();
                    }
                    this.updateMovement();
                }
                return this.followTarget != null ? this.followTarget : this.target;
            }

            Level level = this.level;
            int fx = this.getFloorX();
            int fy = this.getFloorY();
            int fz = this.getFloorZ();
            int levelBlockId = this.getBuiltinBlockIdAt(fx, fy, fz, 0);
            Block levelBlock = null;
            if (levelBlockId < 0) {
                levelBlock = this.getLevelBlock();
                levelBlockId = levelBlock.getId();
            }
            boolean inWater = levelBlockId == BlockID.WATER || levelBlockId == BlockID.STILL_WATER;
            int downId = this.getBuiltinBlockIdAt(fx, fy - 1, fz, 0);
            Block down = null;
            if (downId < 0) {
                down = level.getBlock(chunk, fx, fy - 1, fz, false);
                downId = down.getId();
            }
            if (inWater && (downId == 0 || downId == 8 || downId == 9 || downId == BlockID.LAVA || downId == BlockID.STILL_LAVA || downId == BlockID.SIGN_POST || downId == BlockID.WALL_SIGN)) {
                onGround = false;
            }
            if (downId == 0 || downId == BlockID.SIGN_POST || downId == BlockID.WALL_SIGN) {
                onGround = false;
            }

            if (this.getServer().getMobAiEnabled()) {
                if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive() && this.target != null && this.followTarget.canBeFollowed()) {
                    double x = this.target.x - this.x;
                    double z = this.target.z - this.z;

                    double diff = Math.abs(x) + Math.abs(z);
                    if (this.riding != null || diff <= 0.001 || !inWater && (this.stayTime > 0 || this.distance(this.followTarget) <= (this.getWidth() / 2 + 0.3) * nearbyDistanceMultiplier())) {
                        if (!this.isInsideOfWater()) {
                            this.motionX = 0;
                            this.motionZ = 0;
                        }
                    } else {
                        if (levelBlockId == BlockID.WATER) {
                            BlockWater blockWater = (BlockWater) (levelBlock != null ? levelBlock : level.getBlock(chunk, fx, fy, fz, false));
                            Vector3 flowVector = blockWater.getFlowVector();
                            motionX = flowVector.getX() * FLOW_MULTIPLIER;
                            motionZ = flowVector.getZ() * FLOW_MULTIPLIER;
                        } else if (levelBlockId == BlockID.STILL_WATER) {
                            this.motionX = this.getSpeed() * moveMultiplier * 0.05 * (x / diff);
                            this.motionZ = this.getSpeed() * moveMultiplier * 0.05 * (z / diff);
                            if (!(this instanceof EntityDrowned || this instanceof EntityIronGolem || this instanceof EntitySkeletonHorse)) {
                                this.level.addParticle(new BubbleParticle(this.add(Utils.rand(-2.0, 2.0), Utils.rand(-0.5, 0), Utils.rand(-2.0, 2.0))));
                            }
                        } else {
                            this.motionX = this.getSpeed() * moveMultiplier * 0.1 * (x / diff);
                            this.motionZ = this.getSpeed() * moveMultiplier * 0.1 * (z / diff);
                        }
                    }
                    if (this.noRotateTicks <= 0 && (this.passengers.isEmpty() || this instanceof EntityLlama || this instanceof EntityPig) && this.stayTime <= 0 && diff != 0) {
                        this.lookAt(this.followTarget);
                    }
                }

                if (this.isLookupForTarget()) {
                    this.checkTarget();
                }
                if (this.target != null) {
                    double x = this.target.x - this.x;
                    double z = this.target.z - this.z;

                    double diff = Math.abs(x) + Math.abs(z);
                    boolean distance = false;
                    // A route node is a navigation point, not a ranged combat target.
                    double stopDistance = this.getWidth() / 2 + 0.3;
                    boolean nearTarget = this.distance(this.target) <= stopDistance
                            * (this.target instanceof EntityLiving ? nearbyDistanceMultiplier() : 1);
                    boolean nearFollowTarget = this.followTarget != null && !this.followTarget.closed
                            && this.followTarget.isAlive() && this.followTarget.canBeFollowed()
                            && this.distance(this.followTarget) <= stopDistance * nearbyDistanceMultiplier();
                    if (this.riding != null || diff <= 0.001 || !inWater && (this.stayTime > 0 || nearTarget || nearFollowTarget)) {
                        if (!this.isInsideOfWater()) {
                            this.motionX = 0;
                            this.motionZ = 0;
                        }
                    } else {
                        if (levelBlockId == BlockID.WATER) {
                            BlockWater blockWater = (BlockWater) (levelBlock != null ? levelBlock : level.getBlock(chunk, fx, fy, fz, false));
                            Vector3 flowVector = blockWater.getFlowVector();
                            motionX = flowVector.getX() * FLOW_MULTIPLIER;
                            motionZ = flowVector.getZ() * FLOW_MULTIPLIER;
                        } else if (levelBlockId == BlockID.STILL_WATER) {
                            this.motionX = this.getSpeed() * moveMultiplier * 0.05 * (x / diff);
                            this.motionZ = this.getSpeed() * moveMultiplier * 0.05 * (z / diff);
                            if (!(this instanceof EntityDrowned || this instanceof EntityIronGolem || this instanceof EntitySkeletonHorse)) {
                                this.level.addParticle(new BubbleParticle(this.add(Utils.rand(-2.0, 2.0), Utils.rand(-0.5, 0), Utils.rand(-2.0, 2.0))));
                            } else if (this.followTarget != null) {
                                double y = this.followTarget.y - this.y;
                                this.motionY = this.getSpeed() * moveMultiplier * 0.05 * (y / (diff + Math.abs(y)));
                            }
                        } else {
                            this.motionX = this.getSpeed() * moveMultiplier * 0.15 * (x / diff);
                            this.motionZ = this.getSpeed() * moveMultiplier * 0.15 * (z / diff);
                        }
                    }
                    if (this.noRotateTicks <= 0 && !distance && (this.passengers.isEmpty() || this instanceof EntityLlama || this instanceof EntityPig) && this.stayTime <= 0 && diff > 0.001) {
                        this.turnBodyTowards(FastMath.toDegrees(-FastMath.atan2(x / diff, z / diff)));
                    }
                }
            }

            double dx = this.motionX;
            double dz = this.motionZ;
            boolean isJump = this.checkJump(dx, dz);
            if (this.stayTime > 0 && !inWater) {
                this.stayTime -= tickDiff;
                this.move(0, this.motionY, 0);
            } else {
                if (this.onGround) {
                    if (down == null) {
                        down = level.getBlock(chunk, fx, fy - 1, fz, false);
                    }
                    double friction = (1 - this.getDrag()) * down.getFrictionFactor();
                    this.motionX *= friction;
                    this.motionZ *= friction;
                }
                Vector2 be = new Vector2(this.x + this.motionX, this.z + this.motionZ);
                this.move(this.motionX, this.motionY, this.motionZ);
                Vector2 af = new Vector2(this.x, this.z);

                if ((be.x != af.x || be.y != af.y) && !isJump) {
                    this.moveTime -= 90;
                }
            }

            if (!isJump) {
                if (this.onGround && !inWater) {
                    this.motionY = 0;
                } else if (this.motionY > -this.getGravity() * 4) {
                    int headX = NukkitMath.floorDouble(this.x);
                    int headY = (int) (this.y + 0.8);
                    int headZ = NukkitMath.floorDouble(this.z);
                    int headId = this.getBuiltinBlockIdAt(headX, headY, headZ, 0);
                    boolean headLiquid = headId < 0 ? level.getBlock(headX, headY, headZ) instanceof BlockLiquid
                            : headId == BlockID.WATER || headId == BlockID.STILL_WATER
                            || headId == BlockID.LAVA || headId == BlockID.STILL_LAVA;
                    if (!headLiquid) {
                        this.motionY -= this.getGravity();
                    }
                } else {
                    if ((this instanceof EntityDrowned || this instanceof EntityIronGolem || this instanceof EntitySkeletonHorse) && inWater && this.motionY < 0) {
                        this.motionY = this.getGravity() * -0.3;
                        this.stayTime = 40;
                    } else {
                        this.motionY -= this.getGravity();
                    }
                }
            }

            this.updateMovement();

            if (this.route != null && !this.route.isSearching()) {
                if (this.route.hasCurrentNode() && this.route.hasArrivedNode(this)) {
                    if (this.route.hasNext()) {
                        this.target = this.route.next();
                    }
                }
            }
            return this.followTarget != null ? this.followTarget : this.target;
        }
        return null;
    }
}
