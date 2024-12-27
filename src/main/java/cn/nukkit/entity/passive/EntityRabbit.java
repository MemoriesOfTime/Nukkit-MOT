package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EntityRabbit extends EntityJumpingAnimal {

    public static final int NETWORK_ID = 18;

    public EntityRabbit(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return this.isBaby() ? 0.268f : 0.402f;
    }

    @Override
    public float getLength() {
        return this.isBaby() ? 0.268f : 0.402f;
    }

    @Override
    public float getHeight() {
        return this.isBaby() ? 0.268f : 0.402f;
    }

    @Override
    public double getSpeed() {
        return 1.2;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(3);
        super.initEntity();
        this.setScale(0.65f);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player player) {
            if (player.closed) {
                return false;
            }
            int id = Objects.requireNonNullElse(player.getInventory(), EMPTY_INVENTORY).getItemInHandFast().getId();
            return player.spawned && player.isAlive() && (id == Item.DANDELION || id == Item.CARROT || id == Item.GOLDEN_CARROT) && distance <= 49;
        }
        return super.targetOption(creature, distance);
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            drops.add(Item.get(Item.RABBIT_HIDE, 0, Utils.rand(0, 1)));
            drops.add(Item.get(this.isOnFire() ? Item.COOKED_RABBIT : Item.RAW_RABBIT, 0, Utils.rand(0, 1)));
            drops.add(Item.get(Item.RABBIT_FOOT, 0, Utils.rand(0, 101) <= 9 ? 1 : 0));
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.DANDELION || item.getId() == Item.CARROT || item.getId() == Item.GOLDEN_CARROT) {
            player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EAT);
            this.level.addParticle(new ItemBreakParticle(this.add(0,this.getMountedYOffset(),0), item));
            this.setInLove();
            return true;
        }
        return super.onInteract(player, item, clickedPos);
    }
}
