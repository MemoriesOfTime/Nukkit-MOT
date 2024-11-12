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
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.utils.DyeColor;
import cn.nukkit.utils.Utils;

import java.util.HashMap;

public class EntityWolf extends EntityTameableMob {

    public static final int NETWORK_ID = 14;

    private static final String NBT_KEY_ANGRY = "Angry";
    private static final String NBT_KEY_COLLAR_COLOR = "CollarColor";

    private final Vector3 tempVector = new Vector3();
    private DyeColor collarColor = DyeColor.RED;
    private boolean angry;
    private int angryDuration;
    private int afterInWater = -1;

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

            if (this.hasOwner() && creature.equals(this.getOwner())) {
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
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ANGRY, angry);
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
        if (super.attack(ev)) {
            this.setSitting(false);
            if (ev instanceof EntityDamageByEntityEvent) {
                if (((EntityDamageByEntityEvent) ev).getDamager() instanceof Player player) {
                    if (!(player.isSurvival() || player.isAdventure()) || (this.hasOwner() && player.equals(this.getOwner()))) {
                        return true;
                    }
                }
                this.isAngryTo = ((EntityDamageByEntityEvent) ev).getDamager().getId();
                this.setAngry(true);
            }
            return true;
        }

        return false;
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
