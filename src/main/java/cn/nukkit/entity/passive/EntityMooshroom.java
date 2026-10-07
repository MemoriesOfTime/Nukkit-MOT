package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.block.BlockFlower;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class EntityMooshroom extends EntityWalkingAnimal {

    public static final int NETWORK_ID = 16;

    private static final int NO_STEW_EFFECT = -1;
    private static final int MAX_STEW_EFFECT = 12;
    private boolean interacting;

    public EntityMooshroom(FullChunk chunk, CompoundTag nbt) {
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
        if (this.isBaby()) {
            return 0.7f;
        }
        return 1.4f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(10);

        super.initEntity();

        if (this.namedTag.contains("Variant")) {
            this.setBrown(this.namedTag.getInt("Variant") == 1);
        }
        int savedEffect = this.namedTag.contains("MarkVariant") ? this.namedTag.getInt("MarkVariant") : NO_STEW_EFFECT;
        this.setStewEffect(this.isBrown() && savedEffect >= 0 && savedEffect <= MAX_STEW_EFFECT
                ? savedEffect : NO_STEW_EFFECT);
    }

    @Override
    public boolean isFeedItem(Item item) {
        return item.getId() == Item.WHEAT;
    }

    @Override
    public boolean isBreedingItem(Item item) {
        return this.isFeedItem(item);
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            drops.add(Item.get(Item.LEATHER, 0, Utils.rand(0, 2)));
            drops.add(Item.get(this.isOnFire() ? Item.STEAK : Item.RAW_BEEF, 0, Utils.rand(1, 3)));
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }
    
    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (this.interacting || player.isSpectator()) {
            return false;
        }
        if ((item.getId() == Item.BOWL || item.getId() == Item.BUCKET) && item.getDamage() == 0) {
            if (this.isBaby()) {
                return false;
            }
            boolean bowl = item.getId() == Item.BOWL;
            int effect = this.getStewEffect();
            Item result = bowl
                    ? Item.get(this.isBrown() && effect >= 0 ? Item.SUSPICIOUS_STEW : Item.MUSHROOM_STEW,
                    this.isBrown() && effect >= 0 ? effect : 0, 1)
                    : Item.get(Item.BUCKET, 1, 1);
            if (this.exchange(player, item, result)) {
                if (bowl) {
                    this.setStewEffect(NO_STEW_EFFECT);
                    this.level.addSoundToViewers(this, Sound.MOB_MOOSHROOM_SUSPICIOUS_MILK);
                } else {
                    this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_MILK);
                }
            }
            // exchangeItemInHand already consumed the input; Player must not do so again.
            return false;
        }
        int flowerEffect = flowerStewEffect(item);
        if (flowerEffect >= 0 && this.isBrown() && !this.isBaby()) {
            if (flowerEffect != this.getStewEffect() && this.exchange(player, item, null)) {
                this.setStewEffect(flowerEffect);
                this.level.addSoundToViewers(this, Sound.MOB_MOOSHROOM_EAT);
                this.level.addParticle(new SmokeParticle(this.add(0, 0.25, 0)));
            }
            return false;
        }
        if (this.isBreedingItem(item) && !this.isBaby() && !this.isInLoveCooldown()) {
            if (!player.isCreative()) {
                player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            }
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EAT);
            this.level.addParticle(new ItemBreakParticle(this.add(0, this.getMountedYOffset(), 0), Item.get(Item.WHEAT)));
            this.setInLove();
            return false;
        }
        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putInt("Variant", this.isBrown() ? 1 : 0);
        this.namedTag.putInt("MarkVariant", this.isBrown() ? this.getStewEffect() : NO_STEW_EFFECT);
    }

    @Override
    public void onStruckByLightning(Entity entity) {
        this.setBrown(!this.isBrown());
        super.onStruckByLightning(entity);
    }

    public boolean isBrown() {
        return this.getDataPropertyInt(DATA_VARIANT) == 1;
    }

    public void setBrown(boolean brown) {
        boolean changed = this.isBrown() != brown;
        this.setDataProperty(new IntEntityData(DATA_VARIANT, brown ? 1 : 0));
        if (changed || !brown) {
            this.setStewEffect(NO_STEW_EFFECT);
        }
    }

    private int getStewEffect() {
        int effect = this.getDataPropertyInt(DATA_MARK_VARIANT);
        return effect >= 0 && effect <= MAX_STEW_EFFECT ? effect : NO_STEW_EFFECT;
    }

    private void setStewEffect(int effect) {
        this.setDataProperty(new IntEntityData(DATA_MARK_VARIANT, effect));
    }

    private boolean exchange(Player player, Item item, Item result) {
        if (this.closed || !this.isAlive() || player.getLevel() != this.level) {
            return false;
        }
        boolean brown = this.isBrown();
        int effect = this.getStewEffect();
        var originalLevel = this.level;
        this.interacting = true;
        try {
            return player.getInventory().exchangeItemInHand(item, result,
                    player.isSurvival() || player.isAdventure(),
                    () -> !this.closed && this.isAlive() && !this.isBaby()
                            && this.level == originalLevel && player.getLevel() == originalLevel
                            && this.isBrown() == brown && this.getStewEffect() == effect);
        } finally {
            this.interacting = false;
        }
    }

    private static int flowerStewEffect(Item item) {
        int blockId = item instanceof ItemBlock ? item.getBlock().getId() : item.getId();
        if (blockId == BlockID.FLOWER) {
            return switch (item.getDamage()) {
                case BlockFlower.TYPE_POPPY -> 0;
                case BlockFlower.TYPE_CORNFLOWER -> 1;
                case BlockFlower.TYPE_RED_TULIP, BlockFlower.TYPE_ORANGE_TULIP,
                        BlockFlower.TYPE_WHITE_TULIP, BlockFlower.TYPE_PINK_TULIP -> 2;
                case BlockFlower.TYPE_AZURE_BLUET -> 3;
                case BlockFlower.TYPE_LILY_OF_THE_VALLEY -> 4;
                case BlockFlower.TYPE_BLUE_ORCHID -> 6;
                case BlockFlower.TYPE_ALLIUM -> 7;
                case BlockFlower.TYPE_OXEYE_DAISY -> 8;
                default -> NO_STEW_EFFECT;
            };
        }
        if (item.getDamage() != 0) {
            return NO_STEW_EFFECT;
        }
        return switch (blockId) {
            case BlockID.DANDELION -> 5;
            case BlockID.WITHER_ROSE -> 9;
            case BlockID.TORCHFLOWER -> 10;
            case BlockID.OPEN_EYEBLOSSOM -> 11;
            case BlockID.CLOSED_EYEBLOSSOM -> 12;
            default -> NO_STEW_EFFECT;
        };
    }
}
