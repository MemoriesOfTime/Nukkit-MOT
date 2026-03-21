package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityControllable;
import cn.nukkit.entity.EntityMoveable;
import cn.nukkit.entity.EntityRideable;
import cn.nukkit.inventory.EntityArmorInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemHarness;
import cn.nukkit.item.ItemShears;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.SetEntityLinkPacket;
import cn.nukkit.utils.Utils;

import java.util.Objects;

public class EntityHappyGhast extends EntityFlyingAnimal implements InventoryHolder, EntityRideable, EntityControllable, EntityMoveable {

    public static final int NETWORK_ID = 147;
    public static final int MAX_PASSENGERS = 4;

    private static final Vector3f[] SEAT_OFFSETS = {
            new Vector3f(0, 5.22f, 0.75f),      // 座位0: 驾驶员（前方）
            new Vector3f(-0.85f, 5.22f, -0.5f),  // 座位1: 左后
            new Vector3f(0.85f, 5.22f, -0.5f),   // 座位2: 右后
            new Vector3f(0, 5.22f, -0.75f)       // 座位3: 后方
    };

    private EntityArmorInventory armorInventory;

    public EntityHappyGhast(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);
        super.initEntity();
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_CAN_FLY, true);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_WALKER, true);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_TAMED, true);

        this.setGenericFlag(DATA_FLAG_BODY_ROTATION_ALWAYS_FOLLOWS_HEAD, true);

        this.setGenericFlag(DATA_FLAG_GRAVITY, true);
        this.setGenericFlag(DATA_FLAG_COLLIDABLE, true);

        this.setGenericFlag(DATA_FLAG_WASD_AIR_CONTROLLED, true);
        this.setGenericFlag(DATA_FLAG_DOES_SERVER_AUTH_ONLY_DISMOUNT, true);

        this.setMoveable(true);

        this.setMovementSpeed(0.05f);
        this.armorInventory = new EntityArmorInventory(this);
        if (this.namedTag.contains("Armor")) {
            ListTag<CompoundTag> armorList = this.namedTag.getList("Armor", CompoundTag.class);
            for (CompoundTag armorTag : armorList.getAll()) {
                this.armorInventory.setItem(armorTag.getByte("Slot"), NBTIO.getItemHelper(armorTag));
            }
        }
        this.updateHarnessState();
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        if (this.armorInventory != null) {
            ListTag<CompoundTag> armorTag = new ListTag<>();
            for (int i = 0; i < 5; i++) {
                armorTag.add(NBTIO.putItemHelper(this.armorInventory.getItem(i), i));
            }
            this.namedTag.putList("Armor", armorTag);
        }
    }

    @Override
    public float getWidth() {
        return isBaby() ? 0.95f : 4f;
    }

    @Override
    public float getHeight() {
        return isBaby() ? 0.95f : 4f;
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{
                getHarness()
        };
    }

    @Override
    public Inventory getInventory() {
        return this.armorInventory;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (super.onInteract(player, item, clickedPos)) {
            return true;
        }

        if (this.isBaby()) {
            return super.onInteract(player, item, clickedPos);
        }

        if (item instanceof ItemHarness harness) {
            if (this.armorInventory.getBody().isNull()) {
                Item harnessEquipped = harness.clone();
                if (harnessEquipped.getCount() > 1) {
                    harnessEquipped.setCount(1);
                }
                this.armorInventory.setBody(harnessEquipped);
                this.updateHarnessState();
                player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
                this.armorInventory.sendContents(this.getViewers().values());
            }
            return true;
        }

        if (item instanceof ItemShears) {
            if (!this.armorInventory.getBody().isNull()) {
                Item body = this.armorInventory.getBody();
                if (player.getInventory().canAddItem(body)) {
                    player.getInventory().addItem(body);
                } else {
                    this.getLevel().dropItem(clickedPos, body);
                }
                this.armorInventory.setBody(Item.AIR_ITEM);
                this.updateHarnessState();
                this.armorInventory.sendContents(this.getViewers().values());
                player.getInventory().getItemInHand().setDamage(item.getDamage() + 1);
            }
            return true;
        }

        if (this.isHarnessed() && !player.isSneaking()) {
            this.mountEntity(player);
        }

        return false;
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);
        this.armorInventory.sendContents(player);
    }

    @Override
    public boolean mountEntity(Entity entity, byte mode) {
        Objects.requireNonNull(entity, "The target of the mounting entity can't be null");

        if (this.isPassenger(entity) || entity.riding != null && !entity.riding.dismountEntity(entity, false)) {
            return false;
        }

        if (entity instanceof Player && ((Player) entity).isSleeping()) {
            return false;
        }

        if (this.passengers.size() >= MAX_PASSENGERS) {
            return false;
        }

        if (!this.isHarnessed()) {
            return false;
        }

        byte linkMode = this.passengers.isEmpty() ? SetEntityLinkPacket.TYPE_RIDE : SetEntityLinkPacket.TYPE_PASSENGER;
        this.broadcastLinkPacket(entity, linkMode);

        entity.riding = this;
        entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
        this.passengers.add(entity);
        entity.setSeatPosition(getSeatOffset(this.passengers.size() - 1));
        this.updatePassengerPosition(entity);

        return true;
    }

    private Vector3f getSeatOffset(int seatIndex) {
        if (seatIndex >= 0 && seatIndex < SEAT_OFFSETS.length) {
            return SEAT_OFFSETS[seatIndex];
        }
        return SEAT_OFFSETS[0];
    }

    public Item getHarness() {
        return this.armorInventory.getBody();
    }

    public boolean isHarnessed() {
        return !this.armorInventory.getBody().isNull();
    }

    private void updateHarnessState() {
        boolean harnessed = this.isHarnessed();
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SADDLED, harnessed);
        this.setMoveable(harnessed);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(2, 3);
    }

    public boolean isControllable() {
        return !this.armorInventory.getBody().isNull() && !this.passengers.isEmpty();
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.isAlive()) {
            if (++this.deadTicks >= 23) {
                this.close();
                return false;
            }
            return true;
        }

        int tickDiff = currentTick - this.lastUpdate;
        this.lastUpdate = currentTick;
        this.entityBaseTick(tickDiff);

        if (this.isControllable()) {
            this.move(this.motionX, this.motionY, this.motionZ);
            this.updateMovement();
        } else {
            this.updateMove(tickDiff);
        }

        return true;
    }

    @Override
    public void onPlayerInput(Player player, double strafe, double forward) {
        if (!this.isControllable()) {
            return;
        }

        // 只有第一个乘客（驾驶员）能控制方向
        if (this.passengers.isEmpty() || this.passengers.get(0) != player) {
            return;
        }

        this.stayTime = 0;
        this.moveTime = 10;
        this.target = null;
        this.followTarget = null;

        // 平滑转向
        double yawDiff = player.yaw - this.yaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        this.yaw += yawDiff * 0.08;
        this.headYaw = this.yaw;

        // 基于玩家pitch分解垂直分量
        double up = 0;
        if (forward != 0) {
            double pitchRad = player.pitch * (Math.PI / 180.0);
            double forwardLook = Math.cos(pitchRad);
            double upLook = -Math.sin(pitchRad);
            if (forward < 0) {
                // 后退减速50%
                forwardLook *= -0.5;
                upLook *= -0.5;
            }
            up = upLook;
            forward = forwardLook;
        }

        // 水平运动计算
        strafe *= 0.4;
        double f = strafe * strafe + forward * forward;
        double speed = 0.195;

        if (f >= 1.0E-4 || Math.abs(up) >= 1.0E-4) {
            if (f >= 1.0E-4) {
                f = Math.sqrt(f);
                if (f < 1) {
                    f = 1;
                }
                f = speed / f;
                strafe *= f;
                forward *= f;
            } else {
                strafe = 0;
                forward = 0;
            }

            double yawRad = this.yaw * (Math.PI / 180.0);
            double sin = Math.sin(yawRad);
            double cos = Math.cos(yawRad);
            this.motionX = strafe * cos - forward * sin;
            this.motionZ = forward * cos + strafe * sin;
        } else {
            this.motionX = 0;
            this.motionZ = 0;
        }

        this.motionY = up * speed;
    }

    @Override
    public boolean dismountEntity(Entity entity, boolean sendLinks) {
        boolean result = super.dismountEntity(entity, sendLinks);
        if (result) {
            reassignPassengerSeats();
        }
        return result;
    }

    /**
     * 乘客离开后重新分配座位位置和 link 类型，
     * 确保新的 index 0 乘客获得驾驶员座位和 TYPE_RIDE 权限。
     */
    private void reassignPassengerSeats() {
        for (int i = 0; i < this.passengers.size(); i++) {
            Entity passenger = this.passengers.get(i);
            passenger.setSeatPosition(getSeatOffset(i));
            this.updatePassengerPosition(passenger);
            broadcastLinkPacket(passenger, i == 0 ? SetEntityLinkPacket.TYPE_RIDE : SetEntityLinkPacket.TYPE_PASSENGER);
        }
    }

    @Override
    public Vector3f getMountedOffset(Entity entity) {
        return new Vector3f(0, 5.22f, 0);
    }
}
