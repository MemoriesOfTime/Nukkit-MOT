package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityControllable;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.EntityRideable;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

import static cn.nukkit.network.protocol.SetEntityLinkPacket.TYPE_RIDE;

/**
 * @author PetteriM1
 */
public class EntityHorseBase extends EntityWalkingAnimal implements EntityRideable, EntityControllable {

    private static final String NBT_KEY_ARMOR_ITEM = "ArmorItem";

    private boolean saddled;
    private Item horseArmor = Item.AIR_ITEM;

    public EntityHorseBase(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return -1;
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        if (this.namedTag.contains("Saddle")) {
            this.setSaddled(this.namedTag.getBoolean("Saddle"));
        }

        if (this.namedTag.containsCompound(NBT_KEY_ARMOR_ITEM)) {
            this.setHorseArmor(NBTIO.getItemHelper(this.namedTag.getCompound(NBT_KEY_ARMOR_ITEM)), false);
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putBoolean("Saddle", this.isSaddled());
        if (this.hasHorseArmor()) {
            this.namedTag.putCompound(NBT_KEY_ARMOR_ITEM, NBTIO.putItemHelper(this.horseArmor));
        } else {
            this.namedTag.remove(NBT_KEY_ARMOR_ITEM);
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
            entity.setDataProperty(new Vector3fEntityData(DATA_RIDER_SEAT_POSITION, new Vector3f(0, this instanceof EntityDonkey ? 2.1f : 2.3f, 0)));
            this.passengers.add(entity);
        }

        return true;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (this.isFeedItem(item) && !this.isInLoveCooldown()) {
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EAT);
            this.level.addParticle(new ItemBreakParticle(this.add(0, this.getMountedYOffset(), 0), Item.get(item.getId(), 0, 1)));
            this.setInLove();
            return true;
        } else if (this.canBeSaddled() && !this.isSaddled() && item.getId() == Item.SADDLE) {
            player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_SADDLE);
            this.setSaddled(true);
        } else if (this.canWearHorseArmor() && !this.hasHorseArmor() && item.isHorseArmor()) {
            Item armor = item.clone();
            armor.setCount(1);
            this.setHorseArmor(armor);
            if (!player.isCreative()) {
                player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            }
        } else if (this.passengers.isEmpty() && !this.isBaby() && !player.isSneaking() && (!this.canBeSaddled() || this.isSaddled())) {
            if (player.riding == null) {
                this.mountEntity(player);
            }
        }

        return super.onInteract(player, item, clickedPos);
    }

    public boolean canBeSaddled() {
        return !this.isBaby();
    }

    public boolean canWearHorseArmor() {
        return false;
    }

    public boolean isSaddled() {
        return this.saddled;
    }

    public void setSaddled(boolean saddled) {
        if (this.canBeSaddled()) {
            this.saddled = saddled;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SADDLED, saddled);
        }
    }

    public boolean hasHorseArmor() {
        return !this.horseArmor.isNull();
    }

    public Item getHorseArmor() {
        return this.horseArmor;
    }

    public void setHorseArmor(Item armor) {
        this.setHorseArmor(armor, true);
    }

    private void setHorseArmor(Item armor, boolean send) {
        if (this.canWearHorseArmor() && armor != null && armor.isHorseArmor()) {
            this.horseArmor = armor.clone();
            this.horseArmor.setCount(1);
            if (send) {
                this.level.addSound(this, Sound.MOB_HORSE_ARMOR);
            }
        } else {
            this.horseArmor = Item.AIR_ITEM;
        }

        if (send) {
            this.sendHorseArmor(this.getViewers().values().toArray(Player.EMPTY_ARRAY));
        }
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (this.hasHorseArmor() && source.canBeReducedByArmor()) {
            float reduction = source.getFinalDamage() * this.horseArmor.getArmorPoints() * 0.04f;
            source.setDamage(-reduction, EntityDamageEvent.DamageModifier.ARMOR);
        }
        return super.attack(source);
    }

    @Override
    public boolean isFeedItem(Item item) {
        return item.getId() == Item.WHEAT ||
                item.getId() == Item.APPLE ||
                item.getId() == Item.HAY_BALE ||
                item.getId() == Item.GOLDEN_APPLE ||
                item.getId() == Item.SUGAR ||
                item.getId() == Item.BREAD ||
                item.getId() == Item.GOLDEN_CARROT;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        Iterator<Entity> linkedIterator = this.passengers.iterator();

        while (linkedIterator.hasNext()) {
            cn.nukkit.entity.Entity linked = linkedIterator.next();

            if (!linked.isAlive()) {
                if (linked.riding == this) {
                    linked.riding = null;
                }

                linkedIterator.remove();
            }
        }

        return super.onUpdate(currentTick);
    }

    @Override
    public void onPlayerInput(Player player, double strafe, double forward) {
        this.stayTime = 0;
        this.moveTime = 10;
        this.target = null;
        this.setBothYaw(player.yaw);

        if (forward < 0) {
            forward = forward / 2;
        }

        strafe *= 0.4;

        double f = strafe * strafe + forward * forward;
        double friction = 0.6;

        if (f >= 1.0E-4) {
            f = Math.sqrt(f);

            if (f < 1) {
                f = 1;
            }

            f = friction / f;
            strafe = strafe * f;
            forward = forward * f;
            double f1 = Math.sin(this.yaw * 0.017453292);
            double f2 = Math.cos(this.yaw * 0.017453292);
            this.motionX = (strafe * f2 - forward * f1);
            this.motionZ = (forward * f2 + strafe * f1);
        } else {
            this.motionX = 0;
            this.motionZ = 0;
        }
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        return this.passengers.isEmpty();
    }

    @Override
    public boolean canDespawn() {
        if (this.isSaddled() || this.hasHorseArmor()) {
            return false;
        }

        return super.canDespawn();
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);
        this.sendHorseArmor(player);
    }

    private void sendHorseArmor(Player... players) {
        if (players.length == 0) {
            return;
        }

        Item armor = this.hasHorseArmor() ? this.horseArmor : Item.AIR_ITEM;
        MobArmorEquipmentPacket packet = new MobArmorEquipmentPacket();
        packet.eid = this.getId();
        packet.slots = new Item[]{Item.AIR_ITEM, armor, Item.AIR_ITEM, Item.AIR_ITEM};
        packet.body = armor;

        for (Player player : players) {
            player.dataPacket(packet);
        }
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
}
