package cn.nukkit.entity;

import cn.nukkit.block.*;
import cn.nukkit.entity.mob.EntityDrowned;
import cn.nukkit.entity.mob.EntityPillager;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.entity.passive.EntityIronGolem;
import cn.nukkit.entity.passive.EntityLlama;
import cn.nukkit.entity.passive.EntityPig;
import cn.nukkit.entity.passive.EntitySkeletonHorse;
import cn.nukkit.entity.route.RouteFinder;
import cn.nukkit.entity.route.RouteFinderSearchTask;
import cn.nukkit.entity.route.RouteFinderThreadPool;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

public abstract class EntityWalking extends BaseEntity {
    private static final double FLOW_MULTIPLIER = 0.1;

    @Getter
    @Setter
    protected RouteFinder route;

    private int stuckTimer = 0;
    private Vector3 lastPosition;
    private int noPathFoundTimer = 0;
    private int alternativePathAttempts = 0;
    private int caveNavigationMode = 0;
    private int lastTargetCheck = 0;
    private Vector3 lastTargetPos = null;

    private int doorBreakingProgress = 0;
    private BlockDoor doorBreakingTarget = null;
    private int doorBreakCooldown = 0;

    public EntityWalking(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.lastPosition = new Vector3(this.x, this.y, this.z);
    }

    protected void checkTarget() {
        if (this.isKnockback()) {
            return;
        }

        if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive() &&
                this.followTarget.canBeFollowed() &&
                targetOption((EntityCreature) this.followTarget, this.distanceSquared(this.followTarget)) &&
                this.target != null) {
            this.lastTargetCheck = 0;
            return;
        }

        this.followTarget = null;
        if (!this.passengers.isEmpty() && !(this instanceof EntityLlama) && !(this instanceof EntityPig)) {
            return;
        }

        double near = Integer.MAX_VALUE;
        Entity closestTarget = null;
        for (Entity entity : this.getLevel().getSharedNearbyEntities(this, EntityRanges.createTargetSearchBox(this))) {

            if (entity == this || !(entity instanceof EntityCreature creature) || entity.closed || !this.canTarget(entity)) {
                continue;
            }

            if (creature instanceof BaseEntity base && base.isFriendly() == this.isFriendly() && !this.isInLove()) {
                continue;
            }
            if (!canSeeEntity(creature)) {
                continue;
            }
            double distance = this.distanceSquared(creature);
            if (distance > near || !this.targetOption(creature, distance)) {
                continue;
            }
            near = distance;
            closestTarget = creature;
        }

        if (closestTarget != null) {
            this.stayTime = 0;
            this.moveTime = 0;
            this.followTarget = closestTarget;
            if (this.route == null && this.passengers.isEmpty()) {
                this.target = closestTarget;
            }
            this.noPathFoundTimer = 0;
            this.alternativePathAttempts = 0;
            this.caveNavigationMode = 0;
            this.lastTargetPos = new Vector3(closestTarget.x, closestTarget.y, closestTarget.z);
        } else if (this.canSetTemporalTarget()) {
            this.lastTargetCheck++;
            if (this.lastTargetCheck > 40) {
                this.lastTargetCheck = 0;
                this.caveNavigationMode = 1;
            }

            if (this.stayTime > 0) {
                if (Utils.rand(1, 100) > 5) {
                    return;
                }
                Vector3 newTarget = findPosition(20);
                this.target = Objects.requireNonNullElseGet(newTarget, () -> new Vector3(this.x, this.y, this.z));
            } else if (Utils.rand(1, 100) == 1) {
                this.stayTime = Utils.rand(40, 120);
                Vector3 newTarget = findPosition(15);
                this.target = Objects.requireNonNullElseGet(newTarget, () -> new Vector3(this.x, this.y, this.z));
            } else if (this.moveTime <= 0 || this.target == null) {
                this.stayTime = 0;
                this.moveTime = Utils.rand(60, 180);
                if (this.caveNavigationMode > 0 && Utils.rand(1, 100) > 50) {
                    Vector3 newTarget = exploreArea();
                    this.target = Objects.requireNonNullElseGet(newTarget, () -> new Vector3(
                            this.x + Utils.rand(-12, 12),
                            this.y,
                            this.z + Utils.rand(-12, 12)
                    ));
                } else {
                    Vector3 newTarget = findPosition(20);
                    this.target = Objects.requireNonNullElseGet(newTarget, () -> new Vector3(
                            this.x + Utils.rand(-10, 10),
                            this.y,
                            this.z + Utils.rand(-10, 10)
                    ));
                }
            }
        }
    }

    private boolean canSeeEntity(Entity target) {
        double distance = this.distance(target);
        if (distance < 1.5) {
            return true;
        }
        Vector3 from = new Vector3(this.x, this.y + this.getEyeHeight(), this.z);
        Vector3 to = new Vector3(target.x, target.y + target.getHeight() / 2, target.z);
        return !hasVisionObstacle(from, to);
    }

    private boolean hasVisionObstacle(Vector3 from, Vector3 to) {
        double distance = from.distance(to);
        if (distance > 16) return true;
        Vector3 direction = to.subtract(from).normalize();
        int steps = (int) Math.ceil(distance * 2);
        for (int i = 1; i < steps; i++) {
            Vector3 point = from.add(direction.multiply(i * 0.5));
            int x = NukkitMath.floorDouble(point.x);
            int y = NukkitMath.floorDouble(point.y);
            int z = NukkitMath.floorDouble(point.z);
            Block block = level.getBlock(x, y, z, false);
            if (isVisionBlockingBlock(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVisionBlockingBlock(Block block) {
        if (block == null) return false;
        int id = block.getId();
        if (block instanceof BlockSlab ||
                block instanceof BlockFence ||
                block instanceof BlockFenceGate ||
                block instanceof BlockTrapdoor ||
                block instanceof BlockDoor) {
            return false;
        }
        return !block.isTransparent() && !block.canPassThrough();
    }

    private Vector3 findPosition(int radius) {
        if (isInCave()) {
            return findCavePosition(radius);
        } else {
            return findSurfacePosition(radius);
        }
    }

    private Vector3 findCavePosition(int radius) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double tx = this.x + Utils.rand(-radius, radius);
            double tz = this.z + Utils.rand(-radius, radius);
            int txFloor = NukkitMath.floorDouble(tx);
            int tzFloor = NukkitMath.floorDouble(tz);
            int currentY = Math.max(level.getMinBlockY(), NukkitMath.floorDouble(this.y));
            int checkRange = 6;
            for (int yOffset = -checkRange; yOffset <= checkRange; yOffset++) {
                int checkY = currentY + yOffset;
                if (checkY < level.getMinBlockY() || checkY > level.getMaxBlockY()) {
                    continue;
                }
                if (isCavePositionWalkable(txFloor, checkY, tzFloor)) {
                    return new Vector3(tx, checkY + 1, tz);
                }
            }
        }
        return null;
    }

    private Vector3 findSurfacePosition(int radius) {
        double tx = this.x + Utils.rand(-radius, radius);
        double tz = this.z + Utils.rand(-radius, radius);
        int txFloor = NukkitMath.floorDouble(tx);
        int tzFloor = NukkitMath.floorDouble(tz);
        int y = level.getHighestBlockAt(txFloor, tzFloor);
        Block floor = level.getBlock(txFloor, y, tzFloor, false);
        if (!floor.canPassThrough() && !Block.isWater(floor.getId()) && !Block.isLava(floor.getId())) {
            return new Vector3(tx, y + 1, tz);
        }

        return new Vector3(tx, this.y, tz);
    }

    private boolean isCavePositionWalkable(int x, int y, int z) {
        if (y < level.getMinBlockY() || y > level.getMaxBlockY()) {
            return false;
        }
        Block floor = level.getBlock(x, y, z, false);
        if (!isCaveWalkableSurface(floor)) {
            return false;
        }
        Block body = level.getBlock(x, y + 1, z, false);
        if (body != null && !canPassThroughInCave(body)) {
            return false;
        }
        Block head = level.getBlock(x, y + 2, z, false);
        return head == null || canPassThroughInCave(head);
    }

    private boolean isCaveWalkableSurface(Block block) {
        if (block == null) return false;
        int id = block.getId();
        if (id == Block.STONE ||
                id == Block.COBBLESTONE ||
                id == Block.GRAVEL ||
                id == Block.SAND ||
                id == Block.GRASS ||
                id == Block.MYCELIUM ||
                id == Block.DEEPSLATE ||
                id == Block.COBBLED_DEEPSLATE ||
                id == Block.POLISHED_DEEPSLATE ||
                id == Block.TUFF ||
                id == Block.DRIPSTONE_BLOCK ||
                id == Block.MOSS_BLOCK ||
                id == Block.ROOTED_DIRT ||
                id == Block.SANDSTONE ||
                id == Block.RED_SANDSTONE) {
            return true;
        }
        return !block.canPassThrough() && !Block.isWater(id) && !Block.isLava(id);
    }

    private boolean canPassThroughInCave(Block block) {
        if (block == null) return true;

        int id = block.getId();
        if (block instanceof BlockFlowable ||
                id == Block.AIR ||
                id == Block.FLOWER) {
            return true;
        }
        return block.canPassThrough() || Block.isWater(id);
    }

    private Vector3 exploreArea() {
        if (isInCave()) {
            return exploreCaveArea();
        } else {
            return findSurfacePosition(20);
        }
    }

    private Vector3 exploreCaveArea() {
        if (this.lastTargetPos != null) {
            double angle = Utils.rand(0, 360) * Math.PI / 180.0;
            double distance = Utils.rand(5, 25);
            double tx = this.lastTargetPos.x + Math.cos(angle) * distance;
            double tz = this.lastTargetPos.z + Math.sin(angle) * distance;
            int txFloor = NukkitMath.floorDouble(tx);
            int tzFloor = NukkitMath.floorDouble(tz);
            int highestY = Math.max(level.getMinBlockY(), level.getHighestBlockAt(txFloor, tzFloor));
            for (int yOffset = -3; yOffset <= 3; yOffset++) {
                int checkY = highestY + yOffset;
                if (isCavePositionWalkable(txFloor, checkY, tzFloor)) {
                    return new Vector3(tx, checkY + 1, tz);
                }
            }
        }
        return findCavePosition(25);
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
        Block block = that.getSide(this.getHorizontalFacing());
        if (this.followTarget == null && this.passengers.isEmpty()) {
            Block down = block.down();
            if (!isSolidGround(down) && !isSolidGround(block) && !isSolidGround(down.down())) {
                this.stayTime = 10;
            }
        }

        if (block.equals(doorBreakingTarget)) {
            return false;
        }

        if (!canPassThroughInCave(block) && !(block instanceof BlockFlowable || block.getId() == BlockID.SOUL_SAND) &&
                canPassThroughInCave(block.up()) && canPassThroughInCave(that.up(2))) {
            if (block instanceof BlockFence || block instanceof BlockFenceGate) {
                this.motionY = this.getGravity() * 2;
            } else if (block instanceof BlockSlab) {
                this.motionY = this.getGravity() * 3;
            } else if (block instanceof BlockTrapdoor trapdoor) {
                if (trapdoor.isOpen()) {
                    return false;
                } else {
                    this.motionY = this.getGravity() * 3;
                }
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

    private boolean isSolidGround(Block block) {
        if (block == null) return false;
        if (block.getFloorY() < level.getMinBlockY()) {
            return false;
        }

        int id = block.getId();
        if (block instanceof BlockFlowable || id == Block.FLOWER) {
            return false;
        }

        return !block.canPassThrough() && id != Block.AIR && !Block.isWater(id) && !Block.isLava(id);
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!this.isInTickingRange()) {
            return null;
        }

        updateStuckDetection(tickDiff);

        if (this.server.isHardcore || this.server.getDifficulty() >= 3) handleDoorBreaking(tickDiff);

        if (!isImmobile()) {
            if (this.age % 10 == 0 && this.route != null && !this.route.isSearching()) {
                RouteFinderThreadPool.executeRouteFinderThread(new RouteFinderSearchTask(this.route));
                if (this.route.hasNext()) {
                    this.target = this.route.next();
                }
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

            if (this.target == null) {
                this.target = new Vector3(this.x, this.y, this.z);
            }

            if (this.target.y < this.level.getMinBlockY()) {
                this.target = new Vector3(this.target.x, this.level.getMinBlockY(), this.target.z);
            }

            Block levelBlock = getLevelBlock();
            boolean inWater = levelBlock.getId() == 8 || levelBlock.getId() == 9;
            int floorY = getFloorY() - 1;

            if (floorY >= level.getMinBlockY()) {
                Block down = level.getBlock(chunk, getFloorX(), floorY, getFloorZ(), false);
                int downId = down.getId();
                if (inWater && (downId == 0 || downId == 8 || downId == 9 || downId == BlockID.LAVA || downId == BlockID.STILL_LAVA)) {
                    onGround = false;
                }
                if (downId == 0) {
                    onGround = false;
                }
            } else {
                onGround = false;
            }

            if (this.getServer().getMobAiEnabled()) {
                if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive() && this.followTarget.canBeFollowed() && this.target != null) {
                    if (this.noPathFoundTimer > 60) {
                        attemptCaveNavigation();
                    }
                    double x = this.target.x - this.x;
                    double z = this.target.z - this.z;
                    double diff = Math.abs(x) + Math.abs(z);
                    if (this.riding != null || diff <= 0.001 || !inWater && (this.stayTime > 0 || this.distance(this.followTarget) <= (this.getWidth() / 2 + 0.3) * nearbyDistanceMultiplier())) {
                        if (!this.isInsideOfWater()) {
                            this.motionX = 0;
                            this.motionZ = 0;
                        }
                    } else {
                        if (levelBlock.getId() == BlockID.WATER) {
                            BlockWater blockWater = (BlockWater) levelBlock;
                            Vector3 flowVector = blockWater.getFlowVector();
                            motionX = flowVector.getX() * FLOW_MULTIPLIER;
                            motionZ = flowVector.getZ() * FLOW_MULTIPLIER;
                        } else if (levelBlock.getId() == BlockID.STILL_WATER) {
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

                    if (this.noRotateTicks <= 0 && (this.passengers.isEmpty() || this instanceof EntityLlama || this instanceof EntityPig) && (this.stayTime <= 0 || Utils.rand()) && diff != 0) {
                        this.setBothYaw(FastMath.toDegrees(-FastMath.atan2(x / diff, z / diff)));
                    }
                }

                boolean shouldActivelyMoveToTarget = !this.isFriendly() || this.followTarget != null;

                this.checkTarget();
                if (this.target != null && shouldActivelyMoveToTarget) {
                    double x = this.target.x - this.x;
                    double z = this.target.z - this.z;

                    double diff = Math.abs(x) + Math.abs(z);
                    boolean distance = false;
                    if (this.riding != null || diff <= 0.001 || !inWater && (this.stayTime > 0 || (this.distance(this.target) <= (this.getWidth() / 2 + 0.3) * nearbyDistanceMultiplier()))) {
                        if (!this.isInsideOfWater()) {
                            this.motionX = 0;
                            this.motionZ = 0;
                        }
                    } else {
                        if (levelBlock.getId() == BlockID.WATER) {
                            BlockWater blockWater = (BlockWater) levelBlock;
                            Vector3 flowVector = blockWater.getFlowVector();
                            motionX = flowVector.getX() * FLOW_MULTIPLIER;
                            motionZ = flowVector.getZ() * FLOW_MULTIPLIER;
                        } else if (levelBlock.getId() == BlockID.STILL_WATER) {
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

                    if (this.noRotateTicks <= 0 && !distance && (this.passengers.isEmpty() || this instanceof EntityLlama || this instanceof EntityPig) && (this.stayTime <= 0 || Utils.rand()) && diff > 0.001) {
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
                if (this.onGround) {
                    double friction = (1 - this.getDrag()) * 0.6;
                    this.motionX *= friction;
                    this.motionZ *= friction;
                }

                double newX = this.x + this.motionX;
                double newY = this.y + this.motionY;
                double newZ = this.z + this.motionZ;

                if (newY >= level.getMinBlockY() && newY <= level.getMaxBlockY()) {
                    this.move(this.motionX, this.motionY, this.motionZ);
                } else {
                    newY = Math.max(level.getMinBlockY(), Math.min(newY, level.getMaxBlockY()));
                    this.setPosition(new Vector3(newX, newY, newZ));
                }

                Vector2 be = new Vector2(this.x + this.motionX, this.z + this.motionZ);
                Vector2 af = new Vector2(this.x, this.z);
                if ((be.x != af.x || be.y != af.y) && !isJump) {
                    Block collisionBlock = getFrontCollisionBlock();
                    if (isSolidGround(collisionBlock)) {
                        this.moveTime -= 60;
                    }
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
                    if ((this instanceof EntityDrowned || this instanceof EntityIronGolem || this instanceof EntitySkeletonHorse) && inWater && this.motionY < 0) {
                        this.motionY = this.getGravity() * -0.3;
                        this.stayTime = 40;
                    } else {
                        this.motionY -= this.getGravity();
                    }
                }
            }

            this.updateMovement();

            if (this.route != null) {
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

    private void updateStuckDetection(int tickDiff) {
        Vector3 currentPos = new Vector3(this.x, this.y, this.z);
        double distanceMoved = currentPos.distance(lastPosition);
        if (distanceMoved < 0.05) {
            stuckTimer += tickDiff;
        } else {
            stuckTimer = 0;
        }

        if (this.target != null && !(this.target instanceof Entity)) {
            double distanceToTarget = this.distance(this.target);
            if (distanceToTarget > 1.5) {
                noPathFoundTimer += tickDiff;
            } else {
                noPathFoundTimer = 0;
            }
        }

        lastPosition = currentPos;

        if (stuckTimer > 80) {
            handleCaveStuckSituation();
            stuckTimer = 0;
        }

        if (noPathFoundTimer > 50) {
            noPathFoundTimer = 0;
            if (this.followTarget != null) {
                this.target = this.followTarget;
            }
        }
    }

    private void handleCaveStuckSituation() {
        if (this.followTarget != null) {
            attemptCaveNavigation();
        } else {
            this.stayTime = 0;
            this.moveTime = Utils.rand(30, 80);
            this.caveNavigationMode = 1;
            Vector3 escapePos;
            if (isInCave()) {
                escapePos = findCaveEscape();
            } else {
                escapePos = findSurfacePosition(10);
            }
            if (escapePos != null) {
                this.target = escapePos;
            } else {
                double angle = Utils.rand(0, 360) * Math.PI / 180.0;
                double distance = Utils.rand(3, 8);
                this.target = new Vector3(
                        this.x + Math.cos(angle) * distance,
                        this.y,
                        this.z + Math.sin(angle) * distance
                );
            }
        }
    }

    private void attemptCaveNavigation() {
        alternativePathAttempts++;
        if (alternativePathAttempts > 2) {
            alternativePathAttempts = 0;
            this.caveNavigationMode = 2;
            if (Utils.rand(1, 100) > 70) {
                this.followTarget = null;
                return;
            }
        }

        if (this.followTarget != null) {
            double angle = Utils.rand(0, 360) * Math.PI / 180.0;
            double offsetDistance = Utils.rand(2, 6);
            double offsetX = Math.cos(angle) * offsetDistance;
            double offsetZ = Math.sin(angle) * offsetDistance;
            this.target = new Vector3(
                    this.followTarget.x + offsetX,
                    this.followTarget.y,
                    this.followTarget.z + offsetZ
            );
        }
    }

    private Vector3 findCaveEscape() {
        for (int i = 0; i < 12; i++) {
            double angle = i * 30 * Math.PI / 180.0;
            double distance = Utils.rand(3, 10);
            double checkX = this.x + Math.cos(angle) * distance;
            double checkZ = this.z + Math.sin(angle) * distance;
            int floorX = NukkitMath.floorDouble(checkX);
            int floorZ = NukkitMath.floorDouble(checkZ);
            int highestY = Math.max(level.getMinBlockY(), level.getHighestBlockAt(floorX, floorZ));
            for (int yOffset = -4; yOffset <= 4; yOffset++) {
                int checkY = highestY + yOffset;
                if (isCavePositionWalkable(floorX, checkY, floorZ)) {
                    return new Vector3(checkX, checkY + 1, checkZ);
                }
            }
        }
        return null;
    }

    private Block getFrontCollisionBlock() {
        Vector3 direction = new Vector3(
                Math.cos(Math.toRadians(this.yaw + 90)),
                0,
                Math.sin(Math.toRadians(this.yaw + 90))
        ).normalize();
        int checkX = NukkitMath.floorDouble(this.x + direction.x);
        int checkY = Math.max(level.getMinBlockY(), NukkitMath.floorDouble(this.y));
        int checkZ = NukkitMath.floorDouble(this.z + direction.z);
        if (checkY < level.getMinBlockY() || checkY > level.getMaxBlockY()) {
            return null;
        }
        return level.getBlock(chunk, checkX, checkY, checkZ, false);
    }

    private boolean lastCave;
    private int lastCaveCheckTick = 0;

    private boolean isInCave() {
        int currentX = NukkitMath.floorDouble(this.x);
        int currentY = NukkitMath.floorDouble(this.y);
        int currentZ = NukkitMath.floorDouble(this.z);

        if (lastCaveCheckTick + 10 > this.age) {
            return lastCave;
        }

        int skyLight = this.level.getBlockSkyLightAt(currentX, NukkitMath.floorDouble(this.y + 1), currentZ);
        if (skyLight == 15) {
            lastCave = false;
            lastCaveCheckTick = this.age;
            return false;
        }

        int startY = currentY + 2;
        int maxCheckHeight = Math.min(startY + 64, this.level.getMaxBlockY());

        for (int y = startY; y <= maxCheckHeight; y++) {
            int blockId = this.level.getBlockIdAt(currentX, y, currentZ);

            if (blockId == Block.AIR ||
                    blockId == Block.LEAVES ||
                    blockId == Block.LEAVES2 ||
                    blockId == Block.NETHERRACK ||
                    Block.isWater(blockId)) {
                continue;
            }

            Block block = this.level.getBlock(currentX, y, currentZ, false);
            if (block != null && !block.canPassThrough()) {
                lastCave = true;
                lastCaveCheckTick = this.age;
                return true;
            }
        }

        lastCave = false;
        lastCaveCheckTick = this.age;
        return false;
    }

    private void handleDoorBreaking(int tickDiff) {
        boolean isZombie = this instanceof EntityZombie;
        boolean isPillager = this instanceof EntityPillager;

        if (!isZombie && !isPillager) {
            if (doorBreakingProgress > 0) {
                doorBreakingProgress = 0;
                doorBreakingTarget = null;
            }
            return;
        }

        if (doorBreakCooldown > 0) {
            doorBreakCooldown -= tickDiff;
            return;
        }

        Vector3 frontPos = getFrontBlockPosition();
        if (frontPos == null) {
            if (doorBreakingProgress > 0) {
                doorBreakingProgress = 0;
                doorBreakingTarget = null;
            }
            return;
        }

        if (Block.isDoor(this.level.getBlockIdAt((int) frontPos.x, (int) frontPos.y, (int) frontPos.z))) {

            Block block = this.level.getBlock(frontPos, false);

            if (block instanceof BlockDoor door) {
                if (!door.isOpen()) {
                    if (doorBreakingTarget == null || !doorBreakingTarget.equals(door)) {
                        doorBreakingProgress = 0;
                        doorBreakingTarget = door;
                    }

                    doorBreakingProgress += calculateDoorDamage();

                    doorBreakCooldown = 32;

                    if (doorBreakingProgress > 0 && doorBreakingProgress < door.getMaxHealth()) {
                        door.damage(doorBreakingProgress);

                        this.level.addSound(frontPos, Sound.MOB_ZOMBIE_WOODBREAK);
                    }

                    if (doorBreakingProgress >= door.getMaxHealth()) {
                        this.level.useBreakOn(frontPos, null, null, true);
                        doorBreakingProgress = 0;
                        doorBreakingTarget = null;
                    }
                } else {
                    if (doorBreakingProgress > 0) {
                        doorBreakingProgress = 0;
                        doorBreakingTarget = null;
                    }
                }
            }
        } else {
            if (doorBreakingProgress > 0) {
                doorBreakingProgress = 0;
                doorBreakingTarget = null;
            }
        }
    }

    private Vector3 getFrontBlockPosition() {
        if (this.level == null) return null;

        double dx = Math.cos(Math.toRadians(this.yaw + 90));
        double dz = Math.sin(Math.toRadians(this.yaw + 90));

        int checkX = NukkitMath.floorDouble(this.x + dx * 0.3);
        int checkY = NukkitMath.floorDouble(this.y + 1);
        int checkZ = NukkitMath.floorDouble(this.z + dz * 0.3);

        return new Vector3(checkX, checkY, checkZ);
    }

    private int calculateDoorDamage() {
        if (this instanceof EntityZombie) {
            return Utils.rand(2, 3);
        } else if (this instanceof EntityPillager) {
            return Utils.rand(1, 2);
        }
        return 0;
    }

    @Override
    public void close() {
        if (doorBreakingProgress > 0 && doorBreakingTarget != null) {
            if (doorBreakingProgress < 7) {
                doorBreakingTarget.setDamage(0);
            }
        }
        doorBreakingProgress = 0;
        doorBreakingTarget = null;

        super.close();
    }
}