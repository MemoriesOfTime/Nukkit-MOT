package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.data.ByteEntityData;
import cn.nukkit.entity.passive.EntityFox;
import cn.nukkit.entity.passive.EntityRabbit;
import cn.nukkit.entity.passive.EntitySheep;
import cn.nukkit.entity.passive.EntityTurtle;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.EntityArmorInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.*;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.DyeColor;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EntityWolf extends EntityTameableMob implements InventoryHolder {

    public static final int NETWORK_ID = 14;

    private static final String NBT_KEY_ANGRY = "Angry";
    private static final String NBT_KEY_COLLAR_COLOR = "CollarColor";
    private static final String NBT_KEY_ARMOR = "Armor";
    private static final int WOLF_ARMOR_REPAIR_DURABILITY = 8;
    private static final int[] WOLF_ARMOR_CRACK_THRESHOLDS = {60, 44, 20};

    private final Vector3 tempVector = new Vector3();
    private DyeColor collarColor = DyeColor.RED;
    private boolean angry;
    private int angryDuration;
    private int afterInWater = -1;
    private EntityArmorInventory armorInventory;

    public EntityWolf(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.6f;
    }

    @Override
    public float getHeight() {
        return 0.8f;
    }

    @Override
    public double getSpeed() {
        return 1.2;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(8);

        super.initEntity();

        this.setFriendly(true);
        this.armorInventory = new EntityArmorInventory(this);

        if (this.namedTag.containsList(NBT_KEY_ARMOR)) {
            ListTag<CompoundTag> armorList = this.namedTag.getList(NBT_KEY_ARMOR, CompoundTag.class);
            for (CompoundTag armorTag : armorList.getAll()) {
                this.armorInventory.setItem(armorTag.getByte("Slot"), NBTIO.getItemHelper(armorTag));
            }
        }

        if (this.namedTag.contains(NBT_KEY_ANGRY) && this.namedTag.getByte(NBT_KEY_ANGRY) == 1) {
            this.setAngry(true);
        }

        if (this.namedTag.contains(NBT_KEY_COLLAR_COLOR)) {
            this.collarColor = DyeColor.getByDyeData(this.namedTag.getByte(NBT_KEY_COLLAR_COLOR));
            if (this.collarColor == null) {
                this.collarColor = DyeColor.RED;
            }

            this.setDataProperty(new ByteEntityData(DATA_COLOUR, collarColor.getWoolData()));
        }

        this.setDamage(new int[]{0, 3, 4, 6});
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putBoolean(NBT_KEY_ANGRY, this.angry);
        this.namedTag.putByte(NBT_KEY_COLLAR_COLOR, this.collarColor.getDyeData());

        if (this.armorInventory != null) {
            ListTag<CompoundTag> armorTag = new ListTag<>(NBT_KEY_ARMOR);
            for (int i = 0; i < this.armorInventory.getSize(); i++) {
                armorTag.add(NBTIO.putItemHelper(this.armorInventory.getItem(i), i));
            }
            this.namedTag.putList(armorTag);
        }
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (!creature.isAlive() || creature.closed || distance > 256) {
            return false;
        }

        if (this.isAngry() && this.isAngryTo == creature.getId()) {
            return true;
        }

        if (creature instanceof Player && !this.isInLove()) {
            if (distance <= 64 && this.isBeggingItem(((Player) creature).getInventory().getItemInHandFast())) {
                // TODO: Begging
                if (distance <= 9) {
                    stayTime = 40;
                }
                return true;
            }

            if (this.isOwner(creature)) {
                if (distance <= 4) {
                    return false;
                }

                if (distance <= 100) {
                    return true;
                }
            }
        }

        if (!this.hasOwner() && distance <= 256 && (
            creature instanceof EntitySkeleton && !creature.isInsideOfWater() ||
                creature instanceof EntitySheep ||
                creature instanceof EntityRabbit ||
                creature instanceof EntityFox ||
                creature instanceof EntityTurtle && ((EntityTurtle) creature).isBaby() && !creature.isInsideOfWater()
        )) {
            this.isAngryTo = creature.getId();
            this.setAngry(true);
            return true;
        }

        if (this.hasOwner() && distance <= 256 && creature instanceof EntitySkeleton) {
            this.isAngryTo = creature.getId();
            this.setAngry(true);
            return true;
        }

        if (this.isInLove()) {
            return creature instanceof BaseEntity && ((BaseEntity) creature).isInLove() && creature.isAlive() && !creature.closed && creature.getNetworkId() == this.getNetworkId() && distance <= 100;
        }

        return false;
    }

    public boolean isAngry() {
        return this.angry;
    }

    public void setAngry(boolean angry) {
        this.angry = angry;
        if (!this.hasOwner()) {
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_ANGRY, angry);
        }
        this.angryDuration = angry ? 500 : 0;
        this.setFriendly(!angry);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        int healable = this.getHealableItem(item);

        if (item.getId() == ItemID.BONE) {
            if (!this.hasOwner() && !this.isAngry()) {
                if (Utils.rand(1, 3) == 3) {
                    EntityEventPacket packet = new EntityEventPacket();
                    packet.eid = this.getId();
                    packet.event = EntityEventPacket.TAME_SUCCESS;
                    player.dataPacket(packet);

                    this.setMaxHealth(20);
                    this.setHealth(20);
                    this.setOwner(player);
                    this.setCollarColor(DyeColor.RED);
                    this.getLevel().dropExpOrb(this, Utils.rand(1, 7));
                }

                EntityEventPacket packet = new EntityEventPacket();
                packet.eid = this.getId();
                packet.event = EntityEventPacket.TAME_FAIL;
                player.dataPacket(packet);

                return true;
            }
        } else if (item instanceof ItemWolfArmor && this.canOwnerEditArmor(player) && !this.isBaby()) {
            if (this.getWolfArmor().isNull()) {
                Item armor = item.clone();
                armor.setCount(1);
                this.getArmorInventory().setBody(armor);
                this.getArmorInventory().sendContents(this.getViewers().values());
                this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EQUIP_WOLF);
                if (!player.isCreative()) {
                    player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
                }
            }
            return false;
        } else if (item instanceof ItemShears && this.canOwnerEditArmor(player) && !this.getWolfArmor().isNull()) {
            Item armor = this.getWolfArmor();
            this.getArmorInventory().setBody(Item.AIR_ITEM);
            if (player.getInventory().canAddItem(armor)) {
                player.getInventory().addItem(armor);
            } else {
                this.getLevel().dropItem(this, armor);
            }
            this.getArmorInventory().sendContents(this.getViewers().values());
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_UNEQUIP_WOLF);
            this.damageHeldTool(player, item);
            return false;
        } else if (item instanceof ItemArmadilloScute && this.canOwnerEditArmor(player)) {
            if (this.repairWolfArmor()) {
                this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ARMOR_REPAIR_WOLF);
                if (!player.isCreative()) {
                    player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
                }
            }
            return false;
        } else if (item.getId() == Item.DYE) {
            if (this.hasOwner() && player.equals(this.getOwner())) {
                this.setCollarColor(((ItemDye) item).getDyeColor());
                return true;
            }
        } else if (this.isBreedingItem(item)) {
            if (!this.isInLove() || healable != 0 && this.getHealth() < this.getMaxHealth()) {
                this.getLevel().addSound(this, Sound.RANDOM_EAT);
                this.getLevel().addParticle(new ItemBreakParticle(this.add(0, this.getHeight() * 0.75F, 0), Item.get(item.getId(), 0, 1)));
                if (!this.isInLoveCooldown()) {
                    this.setInLove();
                }

                if (healable != 0) {
                    this.setHealth(Math.max(this.getMaxHealth(), this.getHealth() + healable));
                }

                return true;
            }
        } else if (this.hasOwner() && player.equals(this.getOwner()) && !this.isInsideOfWater()) {
            this.setSitting(!this.isSitting());
            return false;
        }

        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        if (!this.getWolfArmor().isNull() && ev.canBeReducedByArmor() && ev.getCause() != EntityDamageEvent.DamageCause.THORNS) {
            int armorDamage = applyWolfArmorAbsorption(ev);
            boolean attacked = super.attack(ev);
            if (attacked) {
                this.afterSuccessfulAttack(ev);
                this.damageWolfArmor(armorDamage);
            }
            return attacked;
        }

        if (super.attack(ev)) {
            this.afterSuccessfulAttack(ev);
            return true;
        }

        return false;
    }

    static int applyWolfArmorAbsorption(EntityDamageEvent ev) {
        float finalDamage = ev.getFinalDamage();
        ev.setDamage(ev.getDamage(EntityDamageEvent.DamageModifier.ARMOR) - finalDamage,
                EntityDamageEvent.DamageModifier.ARMOR);
        return Math.max(1, (int) Math.ceil(Math.max(0, finalDamage)));
    }

    @Override
    public Inventory getInventory() {
        return this.getArmorInventory();
    }

    public EntityArmorInventory getArmorInventory() {
        if (this.armorInventory == null) {
            this.armorInventory = new EntityArmorInventory(this);
        }
        return this.armorInventory;
    }

    public Item getWolfArmor() {
        return this.getArmorInventory().getBody();
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);
        this.getArmorInventory().sendContents(player);
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();
        Item armor = this.getWolfArmor();
        if (!armor.isNull()) {
            drops.add(armor);
        }
        return drops.toArray(Item.EMPTY_ARRAY);
    }

    private boolean canOwnerEditArmor(Player player) {
        return this.hasOwner() && player.equals(this.getOwner());
    }

    private boolean repairWolfArmor() {
        Item armor = this.getWolfArmor();
        if (!(armor instanceof ItemWolfArmor) || armor.getDamage() <= 0) {
            return false;
        }

        armor.setDamage(Math.max(0, armor.getDamage() - WOLF_ARMOR_REPAIR_DURABILITY));
        this.getArmorInventory().setBody(armor);
        this.getArmorInventory().sendContents(this.getViewers().values());
        return true;
    }

    private void damageWolfArmor(int amount) {
        Item armor = this.getWolfArmor();
        if (armor.isNull() || armor.isUnbreakable() || armor.getMaxDurability() < 0) {
            return;
        }

        Enchantment durability = armor.getEnchantment(Enchantment.ID_DURABILITY);
        if (durability != null && durability.getLevel() > 0
                && (100 / (durability.getLevel() + 1)) <= Utils.random.nextInt(100)) {
            return;
        }

        int oldDamage = armor.getDamage();
        int maxDurability = armor.getMaxDurability();
        armor.setDamage(oldDamage + amount);
        if (armor.getDamage() >= maxDurability) {
            this.getArmorInventory().setBody(Item.AIR_ITEM);
            this.getArmorInventory().sendContents(this.getViewers().values());
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ARMOR_BREAK_WOLF);
            return;
        }

        this.getArmorInventory().setBody(armor);
        this.getArmorInventory().sendContents(this.getViewers().values());
        this.playWolfArmorCrackSoundIfNeeded(maxDurability - oldDamage, maxDurability - armor.getDamage());
    }

    private void playWolfArmorCrackSoundIfNeeded(int oldRemaining, int newRemaining) {
        for (int threshold : WOLF_ARMOR_CRACK_THRESHOLDS) {
            if (oldRemaining > threshold && newRemaining <= threshold) {
                this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ARMOR_CRACK_WOLF);
                return;
            }
        }
    }

    private void damageHeldTool(Player player, Item item) {
        if (player.isCreative()) {
            return;
        }

        item.useOn((Entity) null);
        if (item.getDamage() >= item.getMaxDurability()) {
            this.level.addSoundToViewers(player, Sound.RANDOM_BREAK);
            this.level.addParticle(new ItemBreakParticle(player, item));
            player.getInventory().setItemInHand(Item.get(Item.AIR));
        } else {
            player.getInventory().setItemInHand(item);
        }
    }

    private void afterSuccessfulAttack(EntityDamageEvent ev) {
        this.setSitting(false);
        if (ev instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            if (damageByEntityEvent.getDamager() instanceof Player player) {
                if (!(player.isSurvival() || player.isAdventure()) || (this.hasOwner() && player.equals(this.getOwner()))) {
                    return;
                }
            }
            this.isAngryTo = damageByEntityEvent.getDamager().getId();
            this.setAngry(true);
        }
    }

    @Override
    public void attackEntity(Entity entity) {
        if (entity instanceof Player && (
            !this.isAngry() && this.isBeggingItem(((Player) entity).getInventory().getItemInHandFast()) ||
                this.hasOwner() && entity.equals(this.getOwner())
        )) return;

        if (this.attackDelay > 23 && this.distanceSquared(entity) < 1.5) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());

            if (entity instanceof Player) {
                float points = 0;
                for (Item i : ((Player) entity).getInventory().getArmorContents()) {
                    points += this.getArmorPoints(i.getId());
                }

                damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                    (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f) - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
            }

            this.setMotion(tempVector.setComponents(0, this.getGravity() * 5, 0)); // TODO: Jump before attack

            entity.attack(new EntityDamageByEntityEvent(this, entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
        }
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean hasUpdate = super.entityBaseTick(tickDiff);

        if (this.angryDuration == 1) {
            this.setAngry(false);
        } else if (this.angryDuration > 0) {
            this.angryDuration--;
        }

        if (this.isInsideOfWater()) {
            afterInWater = 0;
        } else if (afterInWater != -1) {
            afterInWater++;
        }

        if (afterInWater > 60) {
            afterInWater = -1;

            this.stayTime = 40;

            EntityEventPacket packet = new EntityEventPacket();
            packet.eid = this.getId();
            packet.event = EntityEventPacket.SHAKE_WET;
            Server.broadcastPacket(this.getViewers().values(), packet);
        }

        return hasUpdate;
    }

    @Override
    protected void checkTarget() {
        if (!this.isSitting() && this.hasOwner() && this.distanceSquared(this.getOwner()) > 144) {
            this.setAngry(false);
            // TODO: Safe teleport (on ground)
            this.teleport(this.getOwner(), null);
            this.move(0, 0.0001, 0); // To fix floating problem
            return;
        }

        super.checkTarget();
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : 3;
    }

    public void setCollarColor(DyeColor color) {
        this.namedTag.putByte(NBT_KEY_COLLAR_COLOR, color.getDyeData());
        this.setDataProperty(new ByteEntityData(DATA_COLOUR, color.getWoolData()));
        this.collarColor = color;
    }

    @Override
    public boolean canDespawn() {
        if (this.hasOwner(false)) {
            return false;
        }
        return super.canDespawn();
    }

    public boolean isBeggingItem(Item item) {
        return item.getId() == ItemID.BONE ||
            item.getId() == ItemID.RAW_CHICKEN ||
            item.getId() == ItemID.COOKED_CHICKEN ||
            item.getId() == ItemID.RAW_BEEF ||
            item.getId() == ItemID.COOKED_BEEF ||
            item.getId() == ItemID.RAW_MUTTON ||
            item.getId() == ItemID.COOKED_MUTTON ||
            item.getId() == ItemID.RAW_PORKCHOP ||
            item.getId() == ItemID.COOKED_PORKCHOP ||
            item.getId() == ItemID.RAW_RABBIT ||
            item.getId() == ItemID.COOKED_RABBIT ||
            item.getId() == ItemID.ROTTEN_FLESH;
    }

    public boolean isBreedingItem(Item item) {
        return item.getId() == ItemID.RAW_CHICKEN ||
            item.getId() == ItemID.COOKED_CHICKEN ||
            item.getId() == ItemID.RAW_BEEF ||
            item.getId() == ItemID.COOKED_BEEF ||
            item.getId() == ItemID.RAW_MUTTON ||
            item.getId() == ItemID.COOKED_MUTTON ||
            item.getId() == ItemID.RAW_PORKCHOP ||
            item.getId() == ItemID.COOKED_PORKCHOP ||
            item.getId() == ItemID.RAW_RABBIT ||
            item.getId() == ItemID.COOKED_RABBIT ||
            item.getId() == ItemID.ROTTEN_FLESH;
    }

    public int getHealableItem(Item item) {
        switch (item.getId()) {
            case ItemID.RAW_PORKCHOP:
            case ItemID.RAW_BEEF:
            case ItemID.RAW_RABBIT:
                return 3;
            case ItemID.COOKED_PORKCHOP:
            case ItemID.COOKED_BEEF:
                return 8;
            case ItemID.RAW_FISH:
            case ItemID.RAW_SALMON:
            case ItemID.RAW_CHICKEN:
            case ItemID.RAW_MUTTON:
                return 2;
            case ItemID.CLOWNFISH:
            case ItemID.PUFFERFISH:
                return 1;
            case ItemID.COOKED_FISH:
            case ItemID.COOKED_RABBIT:
                return 5;
            case ItemID.COOKED_SALMON:
            case ItemID.COOKED_CHICKEN:
            case ItemID.COOKED_MUTTON:
                return 6;
            case ItemID.ROTTEN_FLESH:
                return 4;
            case ItemID.RABBIT_STEW:
                return 10;
            default:
                return 0;
        }
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Wolf";
    }

    @Override
    public boolean canTarget(Entity entity) {
        return entity.canBeFollowed();
    }

    @Override
    public boolean isMeetAttackConditions(Vector3 target) {
        if (target instanceof BaseEntity baseEntity) {
            if (this.isInLove() && baseEntity.getNetworkId() == this.getNetworkId() && baseEntity.isFriendly() == this.isFriendly()) {
                return false;
            }
        }
        return super.isMeetAttackConditions(target);
    }
}
