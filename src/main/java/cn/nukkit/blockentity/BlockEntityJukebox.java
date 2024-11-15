package cn.nukkit.blockentity;

import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemRecord;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.Objects;

/**
 * @author CreeperFace
 */
public class BlockEntityJukebox extends BlockEntitySpawnable {

    private Item recordItem;

    public BlockEntityJukebox(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        if (namedTag.contains("RecordItem")) {
            this.recordItem = NBTIO.getItemHelper(namedTag.getCompound("RecordItem"));
        } else {
            this.recordItem = Item.get(0);
        }

        super.initBlockEntity();
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.getLevel().getBlockIdAt(chunk, getFloorX(), getFloorY(), getFloorZ()) == Block.JUKEBOX;
    }

    public void setRecordItem(Item recordItem) {
        Objects.requireNonNull(recordItem, "Record item cannot be null");
        this.recordItem = recordItem;
    }

    public Item getRecordItem() {
        return recordItem;
    }

    public void play() {
        if (this.recordItem instanceof ItemRecord) {
            this.getLevel().addLevelSoundEvent(this, switch (this.recordItem.getId()) {
                case Item.RECORD_13 -> LevelSoundEventPacket.SOUND_RECORD_13;
                case Item.RECORD_CAT -> LevelSoundEventPacket.SOUND_RECORD_CAT;
                case Item.RECORD_BLOCKS -> LevelSoundEventPacket.SOUND_RECORD_BLOCKS;
                case Item.RECORD_CHIRP -> LevelSoundEventPacket.SOUND_RECORD_CHIRP;
                case Item.RECORD_FAR -> LevelSoundEventPacket.SOUND_RECORD_FAR;
                case Item.RECORD_MALL -> LevelSoundEventPacket.SOUND_RECORD_MALL;
                case Item.RECORD_MELLOHI -> LevelSoundEventPacket.SOUND_RECORD_MELLOHI;
                case Item.RECORD_STAL -> LevelSoundEventPacket.SOUND_RECORD_STAL;
                case Item.RECORD_STRAD -> LevelSoundEventPacket.SOUND_RECORD_STRAD;
                case Item.RECORD_WARD -> LevelSoundEventPacket.SOUND_RECORD_WARD;
                case Item.RECORD_11 -> LevelSoundEventPacket.SOUND_RECORD_11;
                case Item.RECORD_WAIT -> LevelSoundEventPacket.SOUND_RECORD_WAIT;
                case Item.RECORD_PIGSTEP -> LevelSoundEventPacket.SOUND_RECORD_PIGSTEP;
                case Item.RECORD_OTHERSIDE -> LevelSoundEventPacket.SOUND_RECORD_OTHERSIDE;
                case Item.RECORD_5 -> LevelSoundEventPacket.SOUND_RECORD_5;
                case Item.RECORD_RELIC -> LevelSoundEventPacket.SOUND_RECORD_RELIC;
                case 255 -> {
                    switch (this.recordItem.getNamespaceId()) {
                        case "minecraft:music_disc_creator":
                            yield LevelSoundEventPacket.SOUND_RECORD_CREATOR;
                        case "minecraft:music_disc_creator_music_box":
                            yield LevelSoundEventPacket.SOUND_RECORD_CREATOR_MUSIC_BOX;
                        case "minecraft:music_disc_precipice":
                            yield LevelSoundEventPacket.SOUND_RECORD_PRECIPICE;
                    }
                    throw new IllegalStateException("Sound is not implemented for item: " + this.recordItem.getNamespaceId());
                }
                default ->
                    throw new IllegalStateException("Sound is not implemented for item: " + this.recordItem.getId());
            });
        }
    }

    public void stop() {
        this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_STOP_RECORD);
    }

    public void dropItem() {
        if (this.recordItem.getId() != 0) {
            stop();
            this.level.dropItem(this.up(), this.recordItem);
            this.recordItem = Item.get(0);
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putCompound("RecordItem", NBTIO.putItemHelper(this.recordItem));
    }

    @Override
    public CompoundTag getSpawnCompound() {
        return getDefaultCompound(this, JUKEBOX);
    }

    @Override
    public void onBreak() {
        this.dropItem();
    }
}