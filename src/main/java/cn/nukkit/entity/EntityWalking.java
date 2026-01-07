package cn.nukkit.entity;

import cn.nukkit.block.*;
import cn.nukkit.entity.passive.EntityIronGolem;
import cn.nukkit.entity.passive.EntityLlama;
import cn.nukkit.entity.passive.EntityPig;
import cn.nukkit.entity.passive.EntitySkeletonHorse;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

public abstract class EntityWalking extends BaseEntity {

    private int collisionTicks = 0;
    private Vector3 lastTarget = null;
    private List<Vector3> currentPath = new ArrayList<>();
    private int pathIndex = 0;
    private int repathTicks = 0;

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

        if (this.stayTime > 0) {
            if (Utils.rand(1, 100) > 5) {
                return;
            }

            for (int attempts = 0; attempts < 10; attempts++) {
                int x = Utils.rand(5, 15);
                int z = Utils.rand(5, 15);
                final int fX = Utils.rand() ? x : -x;
                final double fY = Utils.rand(-10.0, 10.0) / 10;
                final int fZ = Utils.rand() ? z : -z;

                if (canReachPosition(this.add(fX, fY, fZ))) {
                    this.target = this.add(fX, fY, fZ);
                    break;
                }
            }
        } else if (Utils.rand(1, 100) == 1) {
            for (int attempts = 0; attempts < 10; attempts++) {
                int x = Utils.rand(5, 15);
                int z = Utils.rand(5, 15);
                final int fX = Utils.rand() ? x : -x;
                final double fY = Utils.rand(-10.0, 10.0) / 10;
                final int fZ = Utils.rand() ? z : -z;

                if (canReachPosition(this.add(fX, fY, fZ))) {
                    this.stayTime = Utils.rand(100, 200);
                    this.target = this.add(fX, fY, fZ);
                    break;
                }
            }
        } else if (this.moveTime <= 0 || this.target == null) {
            for (int attempts = 0; attempts < 10; attempts++) {
                int x = Utils.rand(10, 20);
                int z = Utils.rand(10, 20);
                final int fX = Utils.rand() ? x : -x;
                final int fZ = Utils.rand() ? z : -z;

                if (canReachPosition(this.add(fX, 0, fZ))) {
                    this.stayTime = 0;
                    this.moveTime = Utils.rand(100, 200);
                    this.target = this.add(fX, 0, fZ);
                    break;
                }
            }
        }
    }

    private boolean canReachPosition(Vector3 pos) {
        int x = NukkitMath.floorDouble(pos.x);
        int y = NukkitMath.floorDouble(pos.y);
        int z = NukkitMath.floorDouble(pos.z);

        Block block = level.getBlock(x, y, z);
        Block below = level.getBlock(x, y - 1, z);
        Block above = level.getBlock(x, y + 1, z);

        boolean isBelowWalkable = below.isSolid() || below instanceof BlockStairs || below instanceof BlockSlab;
        boolean isBlockPassable = block.canPassThrough() || block instanceof BlockStairs || block instanceof BlockSlab;

        if (isBlockPassable && isBelowWalkable && above.canPassThrough()) {
            return true;
        }

        if (level.getBlock(x, y + 1, z).canPassThrough() &&
                level.getBlock(x, y + 2, z).canPassThrough() &&
                (block.isSolid() || block instanceof BlockStairs || block instanceof BlockSlab)) {
            return true;
        }

        return false;
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

        Block frontBlock = level.getBlock(new Vector3(
                NukkitMath.floorDouble(this.x + dx),
                (int) this.y,
                NukkitMath.floorDouble(this.z + dz)
        ));

        if (frontBlock instanceof BlockStairs || frontBlock instanceof BlockSlab) {
            if (this.motionY <= this.getGravity() * 4) {
                this.motionY = this.getGravity() * 4;
                return true;
            }
        }

        Block that = this.getLevel().getBlock(new Vector3(NukkitMath.floorDouble(this.x + dx), (int) this.y, NukkitMath.floorDouble(this.z + dz)));
        Block block = that.getSide(this.getHorizontalFacing());
        Block down;

        if (block instanceof BlockStairs || block instanceof BlockSlab) {
            if (this.motionY <= this.getGravity() * 4) {
                this.motionY = this.getGravity() * 4;
            }
            return true;
        }

        if (!block.canPassThrough() && block.up().canPassThrough() && that.up(2).canPassThrough()) {
            if (this.motionY <= this.getGravity() * 4) {
                this.motionY = this.getGravity() * 4;
                return true;
            }
        }

        down = block.down();
        if (this.followTarget == null && this.passengers.isEmpty()) {
            if (!down.isSolid() && !block.isSolid() && down.down().isSolid()) {
                return true;
            }

            if (!down.isSolid() && !block.isSolid() && !down.down().isSolid()) {
                this.stayTime = 10;
            }
        } else if (!block.canPassThrough() && !(block instanceof BlockFlowable || block.getId() == BlockID.SOUL_SAND) && block.up().canPassThrough() && that.up(2).canPassThrough()) {
            if (block instanceof BlockFence || block instanceof BlockFenceGate) {
                this.motionY = this.getGravity();
            } else if (this.motionY <= this.getGravity() * 4) {
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

            if (this.getServer().mobAiEnabled) {
                if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive() && this.followTarget.canBeFollowed()) {
                    if (!this.currentPath.isEmpty() && this.pathIndex < this.currentPath.size()) {
                        Vector3 nextPoint = this.currentPath.get(this.pathIndex);
                        if (this.distance(nextPoint) < 1.0) {
                            this.pathIndex++;
                            if (this.pathIndex >= this.currentPath.size()) {
                                this.currentPath.clear();
                                this.pathIndex = 0;
                            }
                        } else {
                            double x = nextPoint.x - this.x;
                            double z = nextPoint.z - this.z;
                            double diff = Math.abs(x) + Math.abs(z);
                            if (diff > 0) {
                                this.motionX = this.getSpeed() * moveMultiplier * 0.15 * (x / diff);
                                this.motionZ = this.getSpeed() * moveMultiplier * 0.15 * (z / diff);
                                this.setBothYaw(FastMath.toDegrees(-FastMath.atan2(x / diff, z / diff)));
                            }
                        }
                    } else {
                        double x = this.followTarget.x - this.x;
                        double z = this.followTarget.z - this.z;
                        double diff = Math.abs(x) + Math.abs(z);
                        if (diff == 0 || !inWater && (this.stayTime > 0 || this.distance(this.followTarget) <= (this.getWidth() / 2 + 0.05))) {
                            this.motionX = 0;
                            this.motionZ = 0;
                        } else {
                            this.motionX = this.getSpeed() * moveMultiplier * 0.15 * (x / diff);
                            this.motionZ = this.getSpeed() * moveMultiplier * 0.15 * (z / diff);
                        }
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
                        this.motionX = this.getSpeed() * moveMultiplier * 0.15 * (x / diff);
                        this.motionZ = this.getSpeed() * moveMultiplier * 0.15 * (z / diff);
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
                    this.motionY -= this.getGravity();
                }
            }

            this.updateMovement();
            return this.target;
        }
        return null;
    }
}
