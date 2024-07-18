package cn.nukkit.entity;

import cn.nukkit.block.*;
import cn.nukkit.entity.passive.EntityIronGolem;
import cn.nukkit.entity.passive.EntityLlama;
import cn.nukkit.entity.passive.EntityPig;
import cn.nukkit.entity.passive.EntitySkeletonHorse;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

public abstract class EntityWalking extends BaseEntity {

    public EntityWalking(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    protected void checkTarget() {
        if (this.isKnockback()) {
            return;
        }

        if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive() && this.followTarget.canBeFollowed()) {
            return;
        }

        Vector3 target = this.target;
        if (!(target instanceof EntityCreature) || (!((EntityCreature) target).closed && !this.targetOption((EntityCreature) target, this.distanceSquared(target))) || !((Entity) target).canBeFollowed()) {
            double near = Integer.MAX_VALUE;

            for (Entity entity : this.getLevel().getEntities()) {
                if (entity == this || !(entity instanceof EntityCreature creature) || entity.closed || !this.canTarget(entity)) {
                    continue;
                }

                if (creature instanceof BaseEntity baseEntity && baseEntity.isFriendly() == this.isFriendly() && !this.isInLove()) {
                    continue;
                }

                double distance = this.distanceSquared(creature);
                if (distance > near || !this.targetOption(creature, distance)) {
                    continue;
                }
                near = distance;

                this.stayTime = 0;
                this.moveTime = 0;
                this.target = creature;
            }
        }

        if (this.target instanceof EntityCreature && !((EntityCreature) this.target).closed && ((EntityCreature) this.target).isAlive() && this.targetOption((EntityCreature) this.target, this.distanceSquared(this.target))) {
            return;
        }

        int x, z;
        if (this.stayTime > 0) {
            if (Utils.rand(1, 100) > 5) {
                return;
            }
            x = Utils.rand(10, 30);
            z = Utils.rand(10, 30);
            this.target = this.add(Utils.rand() ? x : -x, Utils.rand(-20.0, 20.0) / 10, Utils.rand() ? z : -z);
        } else if (Utils.rand(1, 100) == 1) {
            x = Utils.rand(10, 30);
            z = Utils.rand(10, 30);
            this.stayTime = Utils.rand(100, 200);
            this.target = this.add(Utils.rand() ? x : -x, Utils.rand(-20.0, 20.0) / 10, Utils.rand() ? z : -z);
        } else if (this.moveTime <= 0 || this.target == null) {
            x = Utils.rand(20, 100);
            z = Utils.rand(20, 100);
            this.stayTime = 0;
            this.moveTime = Utils.rand(100, 200);
            this.target = this.add(Utils.rand() ? x : -x, 0, Utils.rand() ? z : -z);
        }
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

        if (!this.onGround || this.stayTime > 0) {
            return false;
        }

        Block that = this.getLevel().getBlock(new Vector3(NukkitMath.floorDouble(this.x + dx), (int) this.y, NukkitMath.floorDouble(this.z + dz)));
        /*if (this.getDirection() == null) {
            return false;
        }*/

        Block block = that.getSide(this.getHorizontalFacing());
        Block down;
        if (this.followTarget == null && this.passengers.isEmpty() && !(down = block.down()).isSolid() && !block.isSolid() && !down.down().isSolid()) {
            // "hack": try to make mobs not to be so suicidal
            this.stayTime = 10;
        } else if (!block.canPassThrough() && !(block instanceof BlockFlowable || block.getId() == BlockID.SOUL_SAND) && block.up().canPassThrough() && that.up(2).canPassThrough()) {
            if (block instanceof BlockFence || block instanceof BlockFenceGate) {
                this.motionY = this.getGravity();
            } else if (this.motionY <= this.getGravity() * 4) {
                this.motionY = this.getGravity() * 4;
            } else if (block instanceof BlockStairs) {
                this.motionY = this.getGravity() * 4;
            } else if (this.motionY <= (this.getGravity() * 8)) {
                this.motionY = this.getGravity() * 8;
            } else {
                this.motionY += this.getGravity() * 0.25;
            }
            return true;
        }
        return false;
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!this.isInTickingRange()) {
            return null;
        }

        if (this.isMovement() && !isImmobile()) {
            if (this.isKnockback()) {
                this.move(this.motionX, this.motionY, this.motionZ);
                if (this.isDrowned && this.isInsideOfWater()) {
                    this.motionY -= this.getGravity() * 0.3;
                } else {
                    this.motionY -= this.getGravity();
                }
                this.motionY -= this.getGravity();
                this.updateMovement();
                return null;
            }

            Block levelBlock = getLevelBlock();
            boolean inWater = levelBlock.getId() == 8 || levelBlock.getId() == 9;
            int downId = level.getBlockIdAt(chunk, getFloorX(), getFloorY() - 1, getFloorZ());
            if (inWater && (downId == 0 || downId == 8 || downId == 9 || downId == BlockID.LAVA || downId == BlockID.STILL_LAVA || downId == BlockID.SIGN_POST || downId == BlockID.WALL_SIGN)) {
                onGround = false;
            }
            if (downId == 0 || downId == BlockID.SIGN_POST || downId == BlockID.WALL_SIGN) {
                onGround = false;
            }

            if (this.getServer().getMobAiEnabled()) {
                if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive() && this.followTarget.canBeFollowed()) {
                    double x = this.followTarget.x - this.x;
                    double z = this.followTarget.z - this.z;

                    double diff = Math.abs(x) + Math.abs(z);
                    if (diff == 0 || !inWater && (this.stayTime > 0 || this.distance(this.followTarget) <= (this.getWidth() / 2 + 0.05))) {
                        this.motionX = 0;
                        this.motionZ = 0;
                    } else {
                        if (levelBlock.getId() == BlockID.WATER) {
                            BlockWater blockWater = (BlockWater) levelBlock;
                            Vector3 flowVector = blockWater.getFlowVector();
                            motionX = flowVector.getX() * .05;
                            motionZ = flowVector.getZ() * .05;
                        } else if (levelBlock.getId() == BlockID.STILL_WATER) {
                            this.motionX = this.getSpeed() * moveMultiplier * 0.05 * (x / diff);
                            this.motionZ = this.getSpeed() * moveMultiplier * 0.05 * (z / diff);
                            if (!(this.isDrowned || this instanceof EntityIronGolem || this instanceof EntitySkeletonHorse)) {
                                this.level.addParticle(new BubbleParticle(this.add(Utils.rand(-2.0, 2.0), Utils.rand(-0.5, 0), Utils.rand(-2.0, 2.0))));
                            }
                        } else {
                            this.motionX = this.getSpeed() * moveMultiplier * 0.1 * (x / diff);
                            this.motionZ = this.getSpeed() * moveMultiplier * 0.1 * (z / diff);
                        }
                    }
                    if ((this.passengers.isEmpty() || this instanceof EntityLlama || this instanceof EntityPig) && (this.stayTime <= 0 || Utils.rand()) && diff != 0) {
                        this.setBothYaw(FastMath.toDegrees(-FastMath.atan2(x / diff, z / diff)));
                    }
                    return this.followTarget;
                }

                Vector3 before = this.target;
                this.checkTarget();
                if (this.target instanceof EntityCreature || before != this.target) {
                    double x = this.target.x - this.x;
                    double z = this.target.z - this.z;

                    double diff = Math.abs(x) + Math.abs(z);
                    boolean distance = false;
                    if (diff == 0 || !inWater && (this.stayTime > 0 || (this.distance(this.target) <= (this.getWidth() / 2 + 0.05) * nearbyDistanceMultiplier()))) {
                        this.motionX = 0;
                        this.motionZ = 0;
                    } else {
                        if (levelBlock.getId() == BlockID.WATER) {
                            BlockWater blockWater = (BlockWater) levelBlock;
                            Vector3 flowVector = blockWater.getFlowVector();
                            motionX = flowVector.getX() * .05;
                            motionZ = flowVector.getZ() * .05;
                        } else if (levelBlock.getId() == BlockID.STILL_WATER) {
                            this.motionX = this.getSpeed() * moveMultiplier * 0.05 * (x / diff);
                            this.motionZ = this.getSpeed() * moveMultiplier * 0.05 * (z / diff);
                            if (!(this.isDrowned || this instanceof EntityIronGolem || this instanceof EntitySkeletonHorse)) {
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
                    if (!distance && (this.passengers.isEmpty() || this instanceof EntityLlama || this instanceof EntityPig) && (this.stayTime <= 0 || Utils.rand()) && diff != 0) {
                        this.setBothYaw(FastMath.toDegrees(-FastMath.atan2(x / diff, z / diff)));
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
                Vector2 be = new Vector2(this.x + dx, this.z + dz);
                this.move(dx, this.motionY, dz);
                Vector2 af = new Vector2(this.x, this.z);

                if ((be.x != af.x || be.y != af.y) && !isJump) {
                    this.moveTime -= 90;
                }
            }

            if (!isJump) {
                if (this.onGround && !inWater) {
                    this.motionY = 0;
                } else if (this.motionY > -this.getGravity() * 4) {
                    if (!(this.level.getBlock(NukkitMath.floorDouble(this.x), (int) (this.y + 0.8), NukkitMath.floorDouble(this.z)) instanceof BlockLiquid)) {
                        this.motionY -= this.getGravity();
                    }
                } else {
                    if ((this.isDrowned || this instanceof EntityIronGolem || this instanceof EntitySkeletonHorse) && this.isInsideOfWater() && this.motionY < 0) {
                        this.motionY = this.getGravity() * -0.3;
                        this.stayTime = 40;
                    } else {
                        this.motionY -= this.getGravity();
                    }
                }
            }

            this.updateMovement();
            return this.target;
        }
        return null;
    }
}
