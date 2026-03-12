package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityControllable;
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
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.Objects;

import static cn.nukkit.network.protocol.SetEntityLinkPacket.TYPE_RIDE;

public class EntityHappyGhast extends EntityFlyingAnimal implements InventoryHolder, EntityRideable, EntityControllable {

    public static final int NETWORK_ID = 147;

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
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_BODY_ROTATION_ALWAYS_FOLLOWS_HEAD, true);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_COLLIDABLE, true);

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_WASD_AIR_CONTROLLED, true);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_DOES_SERVER_AUTH_ONLY_DISMOUNT, true);

        this.setMovementSpeed(0.05f);
        this.armorInventory = new EntityArmorInventory(this);
        if (this.namedTag.contains("Armor")) {
            ListTag<CompoundTag> armorList = this.namedTag.getList("Armor", CompoundTag.class);
            for (CompoundTag armorTag : armorList.getAll()) {
                this.armorInventory.setItem(armorTag.getByte("Slot"), NBTIO.getItemHelper(armorTag));
            }
        }
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
        if (item.getNamespaceId().equals("minecraft:name_tag") && !player.isAdventure()) {
            return applyNameTag(player, item);
        }

        if (item instanceof ItemHarness harness) {
            if (this.armorInventory.getBody().isNull()) {
                Item harnessEquipped = harness.clone();
                if (harnessEquipped.getCount() > 1) {
                    harnessEquipped.setCount(1);
                }
                this.armorInventory.setBody(harnessEquipped);
                player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
                this.armorInventory.sendContents(player);
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
                this.armorInventory.sendContents(player);
            }
            return true;
        }

        this.mountEntity(player);

        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);
        this.armorInventory.sendContents(player);
    }

    public Item getHarness() {
        return this.armorInventory.getBody();
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(2, 3);
    }

    @Override
    public void onPlayerInput(Player player, double strafe, double forward) {
        // todo: control logic
    }

    @Override
    public void updatePassengers() {
        if (this.passengers.isEmpty()) {
            return;
        }

        for (Entity passenger : new ArrayList<>(this.passengers)) {
            if (!passenger.isAlive() || (this.getNetworkId() != EntitySkeletonHorse.NETWORK_ID && this.isInsideOfWater())) {
                this.dismountEntity(passenger);
                continue;
            }
            this.updatePassengerPosition(passenger);
        }
    }

    @Override
    public boolean mountEntity(Entity entity, byte mode) {
        Objects.requireNonNull(entity, "The target of the mounting entity can't be null");

        if (entity.riding != null) {
            this.dismountEntity(entity);
            this.motionX = 0;
            this.motionZ = 0;
            this.stayTime = 20;
        } else {
            if (entity instanceof Player && ((Player) entity).isSleeping()) {
                return false;
            }

            if (this.isPassenger(entity)) {
                return false;
            }

            this.broadcastLinkPacket(entity, TYPE_RIDE);

            entity.riding = this;
            entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
            this.passengers.add(entity);

            entity.setSeatPosition(getMountedOffset(entity));
            updatePassengerPosition(entity);
        }

        return true;
    }

    @Override
    public Vector3f getMountedOffset(Entity entity) {
        return new Vector3f(0, 5.22f, 0);
    }
}
