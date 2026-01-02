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
import cn.nukkit.event.vehicle.VehicleMoveEvent;
import cn.nukkit.event.vehicle.VehicleUpdateEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Location;
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

    protected float deltaRotation;
    protected boolean sinking = true;
    public int woodID;
    private boolean inWater;

    public double paddleMotionX = 0d;
    public double paddleMotionY = 0d;

    public float paddleTimeLeft = 0f;
    public float paddleTimeRight = 0f;

    public EntityBoat(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);

        this.setMaxHealth(40);
        this.setHealth(40);
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
            source.setDamage(source.getDamage() * 2);

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

        double waterDiff = getWaterLevel();

        boolean isPlayerOfNewerVersion = getPassenger() instanceof Player player && player.protocol >= ProtocolInfo.v1_21_130_28;
        if (isPlayerOfNewerVersion) {
            this.rotateVehicle();
        }

        boolean simulateWaves = (!(getPassenger() instanceof Player) || isPlayerOfNewerVersion) && !getDataFlag(DATA_FLAGS, DATA_FLAG_OUT_OF_CONTROL);

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

        double groundFriction = this.getGroundFriction();
        this.motionX *= groundFriction;
        this.motionZ *= groundFriction;


        if (simulateWaves) {
            if (waterDiff > SINKING_DEPTH || this.sinking) {
                this.motionY = waterDiff > 0.5 ? this.motionY - this.getGravity() : (this.motionY - SINKING_SPEED < -0.005 ? this.motionY : this.motionY - SINKING_SPEED);
            }
        }

        Location from = new Location(lastX, lastY, lastZ, lastYaw, lastPitch, level);
        Location to = new Location(this.x, this.y, this.z, this.yaw, this.pitch, level);

        this.getServer().getPluginManager().callEvent(new VehicleUpdateEvent(this));

        if (!from.equals(to)) {
            this.getServer().getPluginManager().callEvent(new VehicleMoveEvent(this, from, to));
        }

        this.move(this.motionX, this.motionY, this.motionZ);

        if (this.age % 5 == 0) {
            if (!this.passengers.isEmpty() && this.passengers.get(0) instanceof Player) {
                Block[] blocks = this.level.getCollisionBlocks(this.getBoundingBox().grow(0.1, 0.3, 0.1));
                for (Block b : blocks) {
                    if (b.getId() == Block.LILY_PAD) {
                        this.level.setBlockAt((int) b.x, (int) b.y, (int) b.z, 0, 0);
                        this.level.dropItem(b, Item.get(Item.LILY_PAD, 0, 1));
                    }
                }
            }
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

    public double getWaterLevel() {
        double maxY = this.boundingBox.getMinY() + getBaseOffset();
        AxisAlignedBB.BBConsumer<Double> consumer = new AxisAlignedBB.BBConsumer<>() {

            private double diffY = Double.MAX_VALUE;

            @Override
            public void accept(int x, int y, int z) {
                Block block = EntityBoat.this.level.getBlock(EntityBoat.this.temporalVector.setComponents(x, y, z));

                if (block instanceof BlockWater) {
                    double level = block.getMaxY();

                    diffY = Math.min(maxY - level, diffY);
                    inWater = true;
                }
            }

            @Override
            public Double get() {
                return diffY;
            }
        };

        this.boundingBox.forEach(consumer);

        return consumer.get();
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
            if (passengers.indexOf(player) == RIDER_INDEX && player.protocol < ProtocolInfo.v1_21_130_28) {
                continue;
            }
            player.dataPacket(pk);
        }
    }

    /**
     * Since 1.21.130, boat movement turns server-controlled
     */
    public void onPlayerInput(Player player, double motionX, double motionY) {
        if (player.protocol < ProtocolInfo.v1_21_130_28) {
            return;
        }
        this.paddleMotionX = motionX;
        this.paddleMotionY = motionY;
        this.moveVehicle(motionX, motionY);
    }

    public void rotateVehicle() {
        boolean simulateMove = ((getPassenger() instanceof Player player)
                && player.protocol >= ProtocolInfo.v1_21_130_28);
        if (simulateMove) {
            boolean inputLeft = this.paddleMotionX > 0.35;
            boolean inputRight = this.paddleMotionX < -0.35;
            boolean inputDown = this.paddleMotionY < -0.35;

            float delta = inputDown ? 0.1f : 1;
            if (inputLeft) {
                this.deltaRotation -= delta;
            } else if (inputRight) {
                this.deltaRotation += delta;
            }
            this.deltaRotation *= this.getGroundFriction();
            this.deltaRotation = NukkitMath.clamp(this.deltaRotation, -5, 5);
            this.yaw += this.deltaRotation;
            this.setHeadYaw(this.getYaw());

            this.paddleMotionX = 0;
            this.paddleMotionY = 0;
        }
    }

    public void moveVehicle(double mx, double my) {
        boolean simulateMove = ((getPassenger() instanceof Player player)
                && player.protocol >= ProtocolInfo.v1_21_130_28);
        if (simulateMove) {
            boolean inputLeft = mx > 0.35;
            boolean inputRight = mx < -0.35;
            boolean inputUp = my > 0.35;
            boolean inputDown = my < -0.35;

            float animationSpeed = (float) Math.max(0.01,
                    Math.min(0.08, Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ) * 0.05));
            if (inputUp) {
                this.paddleTimeLeft += animationSpeed;
                this.paddleTimeRight += animationSpeed;
            } else if (inputDown) {
                this.paddleTimeLeft -= animationSpeed;
                this.paddleTimeRight -= animationSpeed;
            } else {
                if (!inputLeft) {
                    this.paddleTimeLeft = 0;
                }
                if (!inputRight) {
                    this.paddleTimeRight = 0;
                }
            }

            this.setDataProperty(new FloatEntityData(Entity.DATA_PADDLE_TIME_LEFT, this.paddleTimeLeft));
            this.setDataProperty(new FloatEntityData(Entity.DATA_PADDLE_TIME_RIGHT, this.paddleTimeRight));

            this.sendData(this.getViewers().values().toArray(Player.EMPTY_ARRAY));

            float acceleration = 0;
            if (inputRight != inputLeft && !inputUp && !inputDown) {
                acceleration += 0.005f;
            }
            if (inputUp) {
                acceleration += 0.04f;
            } else if (inputDown) {
                acceleration -= 0.005f;
            }
            float rad = ((float) yaw - 90) * (NukkitMath.DEG_TO_RAD);
            this.motionX += NukkitMath.sin(-rad) * acceleration;
            this.motionZ += NukkitMath.cos(rad) * acceleration;
        }
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
                    this.inWater = true;
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
            return 0f;
        }
        return friction / count;
    }
}