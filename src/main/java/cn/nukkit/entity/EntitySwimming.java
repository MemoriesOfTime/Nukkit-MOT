package cn.nukkit.entity;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

public abstract class EntitySwimming extends BaseEntity {

    public EntitySwimming(FullChunk chunk, CompoundTag nbt) {
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

                if (creature instanceof BaseEntity && ((BaseEntity) creature).isFriendly() == this.isFriendly()) {
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

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!this.isInTickingRange()) {
            return null;
        }

        if (this.isMovement() && !isImmobile()) {
            if (this.isKnockback()) {
                this.move(this.motionX, this.motionY, this.motionZ);
                this.motionY -= this.getGravity() * (this.isInsideOfWater() ? 0.5 : 1);
                this.updateMovement();
                return null;
            }

            if (this.getServer().getMobAiEnabled()) {
                if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive() && this.followTarget.canBeFollowed()) {
                    double x = this.followTarget.x - this.x;
                    double z = this.followTarget.z - this.z;

                    double diff = Math.abs(x) + Math.abs(z);
                    if (this.stayTime > 0 || this.distance(this.followTarget) <= (this.getWidth() / 2 + 0.05)) {
                        this.motionX = 0;
                        this.motionZ = 0;
                    } else {
                        this.motionX = this.getSpeed() * 0.1 * (x / diff);
                        this.motionZ = this.getSpeed() * 0.1 * (z / diff);
                    }
                    if (this.stayTime <= 0 || Utils.rand()) {
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
                    if (this.stayTime > 0 || this.distance(this.target) <= (this.getWidth() / 2 + 0.05) * nearbyDistanceMultiplier()) {
                        this.motionX = 0;
                        this.motionZ = 0;
                    } else {
                        this.motionX = this.getSpeed() * 0.15 * (x / diff);
                        this.motionZ = this.getSpeed() * 0.15 * (z / diff);
                    }
                    if (this.stayTime <= 0 || Utils.rand()) {
                        this.setBothYaw(FastMath.toDegrees(-FastMath.atan2(x / diff, z / diff)));
                    }
                }
            }

            double dx = this.motionX;
            double dz = this.motionZ;

            boolean inWater = this.isInsideOfWater();
            if (inWater && (this.motionX > 0 || this.motionZ > 0)) {
                this.motionY = Utils.rand(-0.12, 0.12);
            } else if (!this.isOnGround() && !inWater) {
                this.motionY -= this.getGravity();
            } else {
                this.motionY *= 0.1;
            }

            int block;
            if (this.stayTime <= 0 && this.motionY >= 0 && (Math.abs(motionX) > 0 || Math.abs(motionZ) > 0) && (block = this.level.getBlockIdAt(this.getFloorX(), NukkitMath.floorDouble(this.getY() + this.getHeight()), this.getFloorZ())) != Block.WATER && block != Block.STILL_WATER) {
                this.motionY = -0.05;
            }

            if (this.stayTime > 0) {
                this.stayTime -= tickDiff;
                this.move(0, this.motionY, 0);
            } else {
                Vector2 be = new Vector2(this.x + dx, this.z + dz);
                this.move(dx, this.motionY, dz);
                Vector2 af = new Vector2(this.x, this.z);

                if (be.x != af.x || be.y != af.y) {
                    this.moveTime -= 90;
                }
            }

            this.updateMovement();
            return this.target;
        }

        return null;
    }
}
