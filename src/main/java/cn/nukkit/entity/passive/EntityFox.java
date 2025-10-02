package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

public class EntityFox extends EntityWalkingAnimal {

    public static final int NETWORK_ID = 121;

    public EntityFox(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.7f;
    }

    @Override
    public float getHeight() {
        return 0.6f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);

        super.initEntity();
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 2);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.SWEET_BERRIES && !this.isBaby() && !this.isInLoveCooldown()) {
            player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EAT);
            this.level.addParticle(new ItemBreakParticle(this.add(0, this.getMountedYOffset(), 0), Item.get(Item.SWEET_BERRIES)));
            this.setInLove();
            return true;
        }
        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public boolean isFeedItem(Item item) {
        return item.getId() == Item.SWEET_BERRIES;
    }

    @Override
    protected boolean canBeDamagedBySweetBerryBush() {
        return false;
    }
}
