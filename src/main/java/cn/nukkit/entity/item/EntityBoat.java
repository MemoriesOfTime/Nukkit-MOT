package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockWater;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.ByteEntityData;
import cn.nukkit.entity.data.FloatEntityData;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AnimatePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SetEntityLinkPacket;
import cn.nukkit.network.protocol.SetEntityMotionPacket;
import cn.nukkit.utils.CollisionHelper;

import java.util.ArrayList;

/**
 * Created by yescallop on 2016/2/13.
 */
public class EntityBoat extends EntityVehicle {

    public static final int NETWORK_ID = 90;

    public static final int DATA_WOOD_ID = 20;

    public static final Vector3f RIDER_PLAYER_OFFSET = new Vector3f(0, 1.02001f, 0);
    public static final Vector3f RIDER_OFFSET = new Vector3f(0, -0.2f, 0);

    public static final Vector3f PASSENGER_OFFSET = new Vector3f(-0.6f);
    public static final Vector3f RIDER_PASSENGER_OFFSET = new Vector3f(0.2f);

    public static final int RIDER_INDEX = 0;
    public static final int PASSENGER_INDEX = 1;

    public static final double SINKING_DEPTH = 0.07;
    public static final double SINKING_SPEED = 0.0005;
    public static final double SINKING_MAX_SPEED = 0.005;

    protected boolean sinking = true;
    public int woodID;

    private final BoatPaddlePhysics paddlePhysics = new BoatPaddlePhysics();
    private Player paddleController;
    private boolean paddleUp;
    private boolean paddleButtons;
    private boolean paddleLeft;
    private boolean paddleRight;
    private int paddleInputTicks;

    public double paddleMotionX = 0d;
    public double paddleMotionY = 0d;

    public float paddleTimeLeft = 0f;
    public float paddleTimeRight = 0f;

    public EntityBoat(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);

        this.setMaxHealth(4);
        this.setHealth(4);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        if (this.namedTag.contains("Variant")) {
            this.woodID = this.namedTag.getInt("Variant");
        }
        this.dataProperties.putInt(DATA_VARIANT, this.woodID);
        this.dataProperties.putBoolean(DATA_IS_BUOYANT, true);
        this.dataProperties.putString(DATA_BUOYANCY_DATA, "{\"apply_gravity\":true,\"base_buoyancy\":1.0,\"big_wave_probability\":0.02999999932944775,\"big_wave_speed\":10.0,\"drag_down_on_buoyancy_removed\":0.0,\"liquid_blocks\":[\"minecraft:water\",\"minecraft:flowing_water\"],\"simulate_waves\":true}");
        this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_COLLIDABLE, true);
    }

    @Override
    public float getHeight() {
        return 0.455f;
    }

    @Override
    public float getWidth() {
        return 1.4f;
    }

    @Override
    protected float getDrag() {
        return 0.1f;
    }

    @Override
    protected float getGravity() {
        return 0.03999999910593033F;
    }

    @Override
    public float getBaseOffset() {
        return 0.375F;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (invulnerable) {
            return false;
        } else {
            boolean attack = super.attack(source);

            if (isAlive()) {
                performHurtAnimation();
            }

            return attack;
        }
    }

    @Override
    public void close() {
        super.close();

        for (Entity linkedEntity : this.passengers) {
            linkedEntity.riding = null;
        }
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean hasUpdate = false;

        WaterState waterState = this.collectWaterState();
        double waterDiff = waterState.waterDiff();

        Entity passenger = getPassenger();
        Player paddleController = passenger instanceof Player player && player.protocol >= ProtocolInfo.v1_21_130_28 ? player : null;
        if (paddleController != null) {
            this.tickPaddleControl(paddleController, waterState.inWater());
        } else {
            double groundFriction = this.getGroundFriction();
            this.motionX *= groundFriction;
            this.motionZ *= groundFriction;
        }

        boolean simulateWaves = (paddleController != null || !(passenger instanceof Player)) && !getDataFlag(DATA_FLAGS, DATA_FLAG_OUT_OF_CONTROL);

        if (simulateWaves) {
            if (waterDiff > SINKING_DEPTH && !this.sinking) {
                this.sinking = true;
            } else if (waterDiff < -0.07 && this.sinking) {
                this.sinking = false;
            }

            if (waterDiff < -0.07) {
                this.motionY = Math.min(0.05, this.motionY + 0.005);
            } else if (waterDiff < 0 || !this.sinking) {
                this.motionY = this.motionY > SINKING_MAX_SPEED ? Math.max(this.motionY - 0.02, SINKING_MAX_SPEED) : this.motionY + SINKING_SPEED;
            }
        }

        if (this.checkObstruction(this.x, this.y, this.z)) {
            hasUpdate = true;
        }

        if (simulateWaves) {
            if (waterDiff > SINKING_DEPTH || this.sinking) {
                this.motionY = waterDiff > 0.5 ? this.motionY - this.getGravity() : (this.motionY - SINKING_SPEED < -0.005 ? this.motionY : this.motionY - SINKING_SPEED);
            }
        }

        this.move(this.motionX, this.motionY, this.motionZ);

        dispatchVehicleMovementEvents();

        if (this.age % 5 == 0) {
            if (!this.passengers.isEmpty() && this.passengers.get(0) instanceof Player) {
                CollisionHelper.getCollisionBlocks(this.level, this.getBoundingBox().grow(0.1, 0.3, 0.1))
                        .stream()
                        .filter(block -> block.getId() == Block.LILY_PAD)
                        .forEach(block -> {
                            this.level.setBlockAt((int) block.x, (int) block.y, (int) block.z, 0, 0);
                            this.level.dropItem(block, Item.get(Item.LILY_PAD, 0, 1));
                        });
            }
        }

        if (this.isAlive() && this.health < this.getMaxHealth()) {
            this.health = Math.min(this.getMaxHealth(), this.health + 0.1f);
        }

        // We call super here after movement code so block collision checks use up-to-date position
        return super.entityBaseTick(tickDiff) || hasUpdate || !this.onGround || Math.abs(this.motionX) > 0.00001 || Math.abs(this.motionY) > 0.00001 || Math.abs(this.motionZ) > 0.00001;
    }

    @Override
    public void updatePassengers() {
        updatePassengers(false);
    }

    public void updatePassengers(boolean sendLinks) {
        if (this.passengers.isEmpty()) {
            return;
        }

        for (Entity passenger : new ArrayList<>(passengers)) {
            if (!passenger.isAlive()) {
                dismountEntity(passenger);
            }
        }

        Entity ent;

        if (passengers.size() == 1) {
            (ent = this.passengers.get(0)).setSeatPosition(getMountedOffset(ent));
            super.updatePassengerPosition(ent);

            if (sendLinks) {
                broadcastLinkPacket(ent, SetEntityLinkPacket.TYPE_RIDE);
            }
        } else if (passengers.size() == 2) {
            if (!((ent = passengers.get(0)) instanceof Player)) { //swap
                Entity passenger2 = passengers.get(1);

                if (passenger2 instanceof Player) {
                    this.passengers.set(0, passenger2);
                    this.passengers.set(1, ent);

                    ent = passenger2;
                }
            }

            ent.setSeatPosition(getMountedOffset(ent).add(RIDER_PASSENGER_OFFSET));
            super.updatePassengerPosition(ent);
            if (sendLinks) {
                broadcastLinkPacket(ent, SetEntityLinkPacket.TYPE_RIDE);
            }

            (ent = this.passengers.get(1)).setSeatPosition(getMountedOffset(ent).add(PASSENGER_OFFSET));

            super.updatePassengerPosition(ent);

            if (sendLinks) {
                broadcastLinkPacket(ent, SetEntityLinkPacket.TYPE_PASSENGER);
            }

            float yawDiff = ent.getId() % 2 == 0 ? 90 : 270;
            ent.setRotation(this.yaw + yawDiff, ent.pitch);
            ent.updateMovement();
        } else {
            for (Entity passenger : passengers) {
                super.updatePassengerPosition(passenger);
            }
        }
    }

    /**
     * 返回船体参考点与水面高差；无副作用，供外部查询。
     * Returns the water-surface offset of the hull anchor; side-effect free for external callers.
     */
    public double getWaterLevel() {
        return this.collectWaterState().waterDiff();
    }

    // 单次扫描同时返回高差与入水标记，保持 getWaterLevel() 无副作用。
    // One scan returns both values, keeping getWaterLevel() side-effect free.
    private WaterState collectWaterState() {
        double maxY = this.boundingBox.getMinY() + getBaseOffset();
        class WaterProbe implements AxisAlignedBB.BBConsumer<Double> {

            private double diffY = Double.MAX_VALUE;
            private boolean inWater;

            @Override
            public void accept(int x, int y, int z) {
                Block block = EntityBoat.this.level.getBlock(EntityBoat.this.temporalVector.setComponents(x, y, z));

                if (block instanceof BlockWater) {
                    double level = block.getMaxY();

                    diffY = Math.min(maxY - level, diffY);
                    this.inWater = true;
                }
            }

            @Override
            public Double get() {
                return this.diffY;
            }
        }
        WaterProbe probe = new WaterProbe();
        this.boundingBox.forEach(probe);
        return new WaterState(probe.get(), probe.inWater);
    }

    private record WaterState(double waterDiff, boolean inWater) {
    }

    @Override
    public boolean mountEntity(Entity entity) {
        boolean player = !this.passengers.isEmpty() && this.passengers.get(0) instanceof Player;
        byte mode = SetEntityLinkPacket.TYPE_PASSENGER;

        if (!player && (entity instanceof Player || this.passengers.isEmpty())) {
            mode = SetEntityLinkPacket.TYPE_RIDE;
        }

        boolean r = super.mountEntity(entity, mode);

        if (entity.riding != null) {
            updatePassengers(true);

            entity.setDataProperty(new ByteEntityData(DATA_RIDER_ROTATION_LOCKED, 1), !(entity instanceof Player));
            if (entity instanceof Player playerEntity) {
                entity.setDataProperty(new FloatEntityData(DATA_RIDER_MAX_ROTATION, 90), false);
                if (playerEntity.protocol < ProtocolInfo.v1_21_130_28) {
                    entity.setDataProperty(new FloatEntityData(DATA_RIDER_MIN_ROTATION, 1), false);
                }
                if (playerEntity.protocol >= ProtocolInfo.v1_16_210) {
                    entity.setDataProperty(new FloatEntityData(DATA_RIDER_ROTATION_OFFSET, -90), false);
                }
                entity.sendData(playerEntity);
            }
        }

        return r;
    }

    @Override
    protected void updatePassengerPosition(Entity passenger) {
        updatePassengers();
    }

    @Override
    public boolean dismountEntity(Entity entity, boolean sendLinks) {
        boolean r = super.dismountEntity(entity, sendLinks);

        if (r) {
            if (entity == this.paddleController) {
                // 立即重置，同一玩家重新上船也不会沿用旧角速度。
                // Reset now so even the same player remounting cannot resume old angular velocity.
                this.paddleController = null;
                this.paddlePhysics.reset();
                this.clearPaddleInput();
            }
            updatePassengers();
            if (entity instanceof Player) {
                entity.setDataPropertyAndSendOnlyToSelf(new ByteEntityData(DATA_RIDER_ROTATION_LOCKED, 0));
            } else {
                entity.setDataProperty(new ByteEntityData(DATA_RIDER_ROTATION_LOCKED, 0), true);
            }
        }
        return r;
    }

    @Override
    public boolean isControlling(Entity entity) {
        return entity instanceof Player && this.passengers.indexOf(entity) == 0;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (this.isFull() || getWaterLevel() < -SINKING_DEPTH) {
            return false;
        }

        this.mountEntity(player);
        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public Vector3f getMountedOffset(Entity entity) {
        return entity instanceof Player ? RIDER_PLAYER_OFFSET : RIDER_OFFSET;
    }

    public void onPaddle(AnimatePacket.Action animation, float value) {
        int propertyId = animation == AnimatePacket.Action.ROW_RIGHT ? DATA_PADDLE_TIME_RIGHT : DATA_PADDLE_TIME_LEFT;

        if (getDataPropertyFloat(propertyId) != value) {
            this.setDataProperty(new FloatEntityData(propertyId, value));
        }
    }

    @Override
    public void applyEntityCollision(Entity entity) {
        if (getPassenger() instanceof Player player) {
            if (player.protocol >= ProtocolInfo.v1_21_130_28) {
                return;
            }
        }
        if (this.riding == null && entity.riding != this && !entity.passengers.contains(this)) {
            if (!entity.boundingBox.intersectsWith(this.boundingBox.grow(0.20000000298023224, -0.1, 0.20000000298023224))
                    || entity instanceof Player && ((Player) entity).getGamemode() == Player.SPECTATOR) {
                return;
            }

            double diffX = entity.x - this.x;
            double diffZ = entity.z - this.z;

            double direction = NukkitMath.getDirection(diffX, diffZ);

            if (direction >= 0.009999999776482582D) {
                direction = Math.sqrt(direction);
                diffX /= direction;
                diffZ /= direction;

                double d3 = Math.min(1 / direction, 1);

                diffX *= d3;
                diffZ *= d3;
                diffX *= 0.05000000074505806;
                diffZ *= 0.05000000074505806;
                diffX *= 1 + entityCollisionReduction;
                diffZ *= 1 + entityCollisionReduction;

                if (this.riding == null) {
                    motionX -= diffX;
                    motionZ -= diffZ;
                }
            }
        }
    }

    @Override
    public boolean canPassThrough() {
        return false;
    }

    @Override
    public void kill() {
        if (!this.isAlive()) {
            return;
        }

        super.kill();

        if (this.lastDamageCause instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) this.lastDamageCause).getDamager();
            if (damager instanceof Player && ((Player) damager).isCreative()) {
                return;
            }
        }

        if (level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
            this.dropItem();
        }
    }

    protected void dropItem() {
        this.level.dropItem(this, Item.get(ItemID.BOAT, this.woodID));
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putInt("Variant", this.woodID);
    }

    public int getVariant() {
        return this.woodID;
    }

    public void setVariant(int variant) {
        this.woodID = variant;
        this.dataProperties.putInt(DATA_VARIANT, variant);
    }

    public void onInput(double x, double y, double z, double yaw) {
        this.setPositionAndRotation(this.temporalVector.setComponents(x, y - this.getBaseOffset(), z), yaw % 360, 0);
    }

    public boolean isFull() {
        return this.passengers.size() >= 2;
    }

    @Override
    public String getInteractButtonText() {
        return !this.isFull() ? "action.interact.ride.boat" : "";
    }

    @Override
    public void addMotion(double motionX, double motionY, double motionZ) {
        SetEntityMotionPacket pk = new SetEntityMotionPacket();
        pk.eid = this.id;
        pk.motionX = (float) motionX;
        pk.motionY = (float) motionY;
        pk.motionZ = (float) motionZ;
        for (Player player : getViewers().values()) {
            if (player.protocol < ProtocolInfo.v1_21_130_28 && passengers.indexOf(player) == RIDER_INDEX) {
                continue;
            }
            player.dataPacket(pk);
        }
    }

    /**
     * Since 1.21.130, boat movement turns server-controlled
     */
    public void onPlayerInput(Player player, double motionX, double motionY) {
        this.onPlayerInput(player, motionX, motionY, false);
    }

    /**
     * 模拟摇杆/键盘输入。移动向量按 tick 消费，一个 tick 内多个包不会重复加速。
     * Analog input (joystick/keyboard). The vector is consumed once per tick, so several packets in one tick cannot stack thrust.
     */
    public void onPlayerInput(Player player, double motionX, double motionY, boolean up) {
        if (player.protocol < ProtocolInfo.v1_21_130_28 || !this.isControlling(player)) {
            return;
        }
        // 包级校验不含 motion，它仍可能是 NaN/Infinity。
        // The packet-level check omits motion, which can still be NaN/Infinity.
        if (!Double.isFinite(motionX) || !Double.isFinite(motionY)) {
            return;
        }
        this.bindPaddleController(player);
        this.paddleButtons = false;
        this.paddleMotionX = motionX;
        this.paddleMotionY = motionY;
        this.paddleUp = up;
        this.paddleInputTicks = 2;
    }

    /**
     * 经典触屏布局的左右桨按钮。按钮相互独立，只按一支桨仍会提供约一半的前进推力。
     * The classic touch layout drives the two paddles independently, so holding one paddle still rows at about half thrust.
     */
    public void onPlayerInput(Player player, boolean paddleLeft, boolean paddleRight) {
        if (player.protocol < ProtocolInfo.v1_21_130_28 || !this.isControlling(player)) {
            return;
        }
        this.bindPaddleController(player);
        this.paddleButtons = true;
        this.paddleLeft = paddleLeft;
        this.paddleRight = paddleRight;
        this.paddleInputTicks = 2;
    }

    private void tickPaddleControl(Player controller, boolean inWater) {
        this.bindPaddleController(controller);

        if (this.paddleInputTicks > 0) {
            this.paddleInputTicks--;
        } else {
            this.clearPaddleInput();
        }

        BoatFriction friction = this.resolveFriction(inWater);
        BoatPaddlePhysics.TickResult result = this.paddlePhysics.tick(
                this.paddleButtons, this.paddleMotionX, this.paddleMotionY, this.paddleUp,
                this.paddleLeft, this.paddleRight,
                friction.value, friction.canPaddle,
                (float) this.motionX, (float) this.motionZ, (float) this.yaw);
        this.motionX = result.motionX();
        this.motionZ = result.motionZ();
        this.yaw += result.yawVelocity();
        this.setHeadYaw(this.getYaw());
        this.updatePaddleAnimation(result.firstPaddleSpeed(), result.secondPaddleSpeed());
    }

    /**
     * 绑定划桨控制者。换人时立即重置，避免继承上一任的角速度与按键；输入先绑定再缓存，
     * 新骑手上船后的第一个输入不会被这里清掉。
     * Binds the paddle controller. A rider change resets immediately so no previous rider's
     * angular velocity or buttons carry over; binding before input is cached keeps the new
     * rider's first input from being wiped.
     */
    private void bindPaddleController(Player controller) {
        if (this.paddleController == controller) {
            return;
        }
        this.paddleController = controller;
        this.paddlePhysics.reset();
        this.clearPaddleInput();
    }

    private void clearPaddleInput() {
        this.paddleInputTicks = 0;
        this.paddleButtons = false;
        this.paddleMotionX = 0;
        this.paddleMotionY = 0;
        this.paddleUp = false;
        this.paddleLeft = false;
        this.paddleRight = false;
    }

    private void updatePaddleAnimation(float firstSpeed, float secondSpeed) {
        // 单参 setDataProperty 已广播给所有观察者，无需整份 metadata 重发。
        // The single-argument setDataProperty already broadcasts, so no full metadata resend.
        float left = updatePaddleTime(this.paddleTimeLeft, firstSpeed);
        if (Float.compare(left, this.paddleTimeLeft) != 0) {
            this.paddleTimeLeft = left;
            this.setDataProperty(new FloatEntityData(Entity.DATA_PADDLE_TIME_LEFT, left));
        }
        float right = updatePaddleTime(this.paddleTimeRight, secondSpeed);
        if (Float.compare(right, this.paddleTimeRight) != 0) {
            this.paddleTimeRight = right;
            this.setDataProperty(new FloatEntityData(Entity.DATA_PADDLE_TIME_RIGHT, right));
        }
    }

    private static float updatePaddleTime(float time, float speed) {
        if (speed == 0) {
            return 0;
        }
        float animationSpeed = Math.max(0.01f, Math.min(0.08f, Math.abs(speed) * 0.05f));
        return time + Math.signum(speed) * animationSpeed;
    }

    /**
     * 基岩版摩擦选择：水中 0.9，空中 1.0（无空气阻力），地面取 actor 所在方块的摩擦。
     * Bedrock friction: 0.9 in water, 1.0 in air (no air resistance), block friction at the actor on ground.
     */
    private BoatFriction resolveFriction(boolean inWater) {
        if (inWater) {
            return new BoatFriction(BoatPaddlePhysics.WATER_FRICTION, true);
        }
        int x = (int) Math.floor(this.x);
        // 与客户端一致：取 actor origin（脚底 + baseOffset）所在方块，而非实体脚底。
        // Match the client: use the actor origin (feet + baseOffset), not the entity feet.
        int y = (int) Math.floor(this.y + this.getBaseOffset());
        int z = (int) Math.floor(this.z);
        Block current = this.liquidBlock(x, y, z);
        if (current.isWater()) {
            return new BoatFriction(BoatPaddlePhysics.WATER_FRICTION, true);
        }
        Block below = this.level.getBlock(x, y - 1, z, 0);
        if (current.isAir() || current.getId() == BlockID.SNOW_LAYER) {
            if (this.onGround) {
                return new BoatFriction(blockFriction(below), true);
            }
            return this.liquidBlock(x, y - 1, z).isWater()
                    ? new BoatFriction(BoatPaddlePhysics.WATER_FRICTION, true)
                    : new BoatFriction(BoatPaddlePhysics.AIR_FRICTION, false);
        }
        if (!this.onGround) {
            return new BoatFriction(BoatPaddlePhysics.AIR_FRICTION, false);
        }
        Block block = this.level.getBlock(x, y, z, 0);
        return new BoatFriction(blockFriction(hasVolume(block) ? block : below), true);
    }

    private Block liquidBlock(int x, int y, int z) {
        Block extra = this.level.getBlock(x, y, z, 1);
        return extra.isAir() ? this.level.getBlock(x, y, z, 0) : extra;
    }

    private static float blockFriction(Block block) {
        return block.isAir() ? BoatPaddlePhysics.AIR_BLOCK_FRICTION : (float) block.getFrictionFactor();
    }

    private static boolean hasVolume(Block block) {
        AxisAlignedBB box = block.getBoundingBox();
        return box != null
                && box.getMaxX() > box.getMinX()
                && box.getMaxY() > box.getMinY()
                && box.getMaxZ() > box.getMinZ();
    }

    private record BoatFriction(float value, boolean canPaddle) {
    }

    public float getGroundFriction() {
        int minX = (int) Math.floor(this.boundingBox.getMinX());
        int maxX = (int) Math.floor(this.boundingBox.getMaxX());
        int minZ = (int) Math.floor(this.boundingBox.getMinZ());
        int maxZ = (int) Math.floor(this.boundingBox.getMaxZ());
        int minY = (int) Math.floor(this.boundingBox.getMinY() + 0.05);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (this.level.getBlock(x, minY, z).isWater()) {
                    return 0.9f;
                }
            }
        }
        AxisAlignedBB boatShape = getBoundingBox().clone();
        // 0.001 high box extending downwards from the boat
        double middleY = boatShape.getMinY() - 0.0005;
        boatShape.setMaxY(middleY + 0.0001);
        boatShape.setMinY(middleY);

        int x0 = (int) Math.floor(boatShape.getMinX()) - 1;
        int x1 = (int) Math.ceil(boatShape.getMaxX()) + 1;
        int y0 = (int) Math.floor(boatShape.getMinY()) - 1;
        int y1 = (int) Math.ceil(boatShape.getMaxY()) + 1;
        int z0 = (int) Math.floor(boatShape.getMinZ()) - 1;
        int z1 = (int) Math.ceil(boatShape.getMaxZ())+ 1;

        float friction = 0.0F;
        int count = 0;

        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                int edges = ((x == x0 || x == x1 - 1) ? 1 : 0) + ((z == z0 || z == z1 - 1) ? 1 : 0);
                if (edges == 2) {
                    continue;
                }

                for (int y = y0; y < y1; y++) {
                    if (edges > 0 && !(y != y0 && y != y1 - 1)) {
                        continue;
                    }
                    final Block state = this.getLevel().getBlock(x, y, z);
                    if (state.getId() == BlockID.LILY_PAD) {
                        continue;
                    }

                    if (state.getBoundingBox() == null) {
                        continue;
                    }

                    if (state.collidesWithBB(boatShape)) {
                        friction += (float) state.getFrictionFactor();
                        count++;
                    }
                }
            }
        }

        if (friction < 0.001f) {
            // 空中无地面摩擦；基岩版船无空气阻力，不能把速度清零。
            // No ground friction in air: Bedrock boats have no air resistance, so never zero the velocity.
            return 1f;
        }
        return friction / count;
    }
}
