package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.data.ByteEntityData;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.DyeColor;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class EntitySheep extends EntityWalkingAnimal {

    public static final int NETWORK_ID = 13;

    protected boolean sheared = false;
    protected int color;
    protected int unshearTicks = -1;

    public EntitySheep(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.45f;
        }
        return 0.9f;
    }

    @Override
    public float getHeight() {
        if (isBaby()) {
            return 0.65f;
        }
        return 1.3f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(8);

        super.initEntity();

        if (!this.namedTag.contains("Color")) {
            this.setColor(randomColor());
        } else {
            this.setColor(this.namedTag.getByte("Color"));
        }

        if (!this.namedTag.contains("Sheared")) {
            this.namedTag.putBoolean("Sheared", false);
        } else if (this.namedTag.getBoolean("Sheared")) {
            this.sheared = true;
            this.unshearTicks = Utils.rand(2400, 4800);
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SHEARED, true);
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putByte("Color", this.color);
        this.namedTag.putBoolean("Sheared", this.sheared);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.DYE) {
            this.setColor(((ItemDye) item).getDyeColor().getWoolData());
            return true;
        } else if (item.getId() == Item.WHEAT && !this.isBaby() && !this.isInLoveCooldown()) {
            player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EAT);
            this.level.addParticle(new ItemBreakParticle(this.add(0, this.getMountedYOffset(), 0), Item.get(Item.WHEAT)));
            this.setInLove();
            return true;
        } else if (item.getId() == Item.SHEARS && !this.isBaby() && !this.sheared) {
            this.shear(true);
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_SHEAR);
            player.getInventory().getItemInHand().setDamage(item.getDamage() + 1);
            return true;
        }
        return super.onInteract(player, item, clickedPos);
    }

    public void shear(boolean shear) {
        this.sheared = shear;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SHEARED, shear);
        if (shear) {
            this.level.dropItem(this, Item.get(Item.WOOL, getColor(), Utils.rand(1, 3)));
            this.unshearTicks = Utils.rand(2400, 4800);
        } else {
            this.unshearTicks = -1;
        }
    }

    @Override
    public boolean isFeedItem(Item item) {
        return item.getId() == Item.WHEAT;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            if (!this.isSheared()) {
                drops.add(Item.get(Item.WOOL, this.getColor(), 1));
            }
            drops.add(Item.get(this.isOnFire() ? Item.COOKED_MUTTON : Item.RAW_MUTTON, 0, Utils.rand(1, 2)));
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }


    public void setColor(int woolColor) {
        this.color = woolColor;
        this.namedTag.putByte("Color", woolColor);
        this.setDataProperty(new ByteEntityData(DATA_COLOUR, woolColor));
    }

    public int getColor() {
        return this.color;
    }

    private int randomColor() {
        int rand = Utils.rand(1, 200);

        if (rand == 1) {
            return DyeColor.PINK.getWoolData();
        } else if (rand < 8) {
            return DyeColor.BROWN.getWoolData();
        } else if (rand < 18) {
            return DyeColor.GRAY.getWoolData();
        } else if (rand < 28) {
            return DyeColor.LIGHT_GRAY.getWoolData();
        } else if (rand < 38) {
            return DyeColor.BLACK.getWoolData();
        } else {
            return DyeColor.WHITE.getWoolData();
        }
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.sheared && this.unshearTicks > 0) {
            if (this.unshearTicks == 40) {
                if (this.stayTime <= 0) {
                    this.stayTime = 50;
                }

                EntityEventPacket pk = new EntityEventPacket();
                pk.eid = this.getId();
                pk.event = EntityEventPacket.EAT_GRASS_ANIMATION;
                Server.broadcastPacket(this.getViewers().values(), pk);
            }

            this.unshearTicks--;
        } else if (this.sheared && this.unshearTicks == 0) {
            shear(false);
        }

        return super.entityBaseTick(tickDiff);
    }

    public boolean isSheared() {
        return this.sheared;
    }
}
