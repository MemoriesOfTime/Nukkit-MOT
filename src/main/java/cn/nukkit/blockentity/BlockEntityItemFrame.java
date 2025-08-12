package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.event.block.ItemFrameDropItemEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Pub4Game on 03.07.2016.
 */
public class BlockEntityItemFrame extends BlockEntitySpawnable {

    private Item item_;

    public BlockEntityItemFrame(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        if (!namedTag.contains("Item")) {
            namedTag.putCompound("Item", NBTIO.putItemHelper(item_ = new ItemBlock(Block.get(BlockID.AIR))));
        }

        if (!namedTag.contains("ItemRotation")) {
            namedTag.putByte("ItemRotation", 0);
        }

        if (!namedTag.contains("ItemDropChance")) {
            namedTag.putFloat("ItemDropChance", 1.0f);
        }

        this.level.updateComparatorOutputLevel(this);

        super.initBlockEntity();
    }

    @Override
    public String getName() {
        return "Item Frame";
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.getBlock().getId() == Block.ITEM_FRAME_BLOCK;
    }

    public int getItemRotation() {
        return this.namedTag.getByte("ItemRotation");
    }

    public void setItemRotation(int itemRotation) {
        this.namedTag.putByte("ItemRotation", itemRotation);
        this.level.updateComparatorOutputLevel(this);
        this.setDirty();
    }

    public Item getItem() {
        if (item_ == null) {
            CompoundTag NBTTag = this.namedTag.getCompound("Item");
            item_ = NBTIO.getItemHelper(NBTTag);
        }
        return item_;
    }

    public void setItem(Item item) {
        this.setItem(item, true);
    }

    public void setItem(Item item, boolean setChanged) {
        item_ = null;
        this.namedTag.putCompound("Item", NBTIO.putItemHelper(item));
        if (setChanged) {
            this.setDirty();
        }

        this.level.updateComparatorOutputLevel(this);
    }

    public float getItemDropChance() {
        return this.namedTag.getFloat("ItemDropChance");
    }

    public void setItemDropChance(float chance) {
        this.namedTag.putFloat("ItemDropChance", chance);
    }

    @Override
    public void setDirty() {
        this.spawnToAll();
        super.setDirty();
    }

    @Override
    public CompoundTag getSpawnCompound() {
        return this.getSpawnCompound(ProtocolInfo.CURRENT_PROTOCOL);
    }

    @Override
    public CompoundTag getSpawnCompound(int protocol) {
        if (!this.namedTag.contains("Item")) {
            this.setItem(new ItemBlock(Block.get(BlockID.AIR)), false);
        }

        CompoundTag itemOriginal = namedTag.getCompound("Item");

        CompoundTag tag = new CompoundTag()
                .putString("id", BlockEntity.ITEM_FRAME)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z);

        int itemId = itemOriginal.getShort("id");
        String itemName = itemOriginal.getString("Name");
        if (itemId != Item.AIR || (itemName != null && !itemName.equals("minecraft:air"))) {
            CompoundTag item = itemOriginal.copy();
            item.setName("Item");
            if (protocol >= ProtocolInfo.v1_16_0) {
                if (!item.contains("Name")) {
                    item.remove("id");
                    int damage = itemOriginal.getShort("Damage");
                    String namespaceId;
                    if (id == 0) {
                        namespaceId = "minecraft:air";
                    } else {
                        try {
                            namespaceId = RuntimeItems.getMapping(protocol).toRuntime(itemId, damage).getIdentifier();
                            if (namespaceId == null || namespaceId.isBlank()) {
                                throw new Exception("Empty namespaceId");
                            }
                        } catch (Exception e) {
                            namespaceId = "minecraft:unknown";
                            Server.getInstance().getLogger().error("Failed to get namespaceId of " + itemId + ":" + damage, e);
                        }
                    }
                    item.putString("Name", namespaceId);
                }
            } else { // < 1.16 used item id
                if (!item.contains("id")) {
                    item.putShort("id", Item.INFO_UPDATE);
                }
            }

            if (itemId == Item.MAP) {
                item.getCompound("tag").remove("Colors");
            } else {
                item.getCompound("tag").remove("Items");
            }

            tag.putCompound("Item", item)
                    .putByte("ItemRotation", this.getItemRotation());
        }
        return tag;
    }

    public int getAnalogOutput() {
        return this.getItem() == null || this.getItem().getId() == 0 ? 0 : this.getItemRotation() % 8 + 1;
    }

    public boolean dropItem(Player player) {
        Item item = this.getItem();
        if (item != null && item.getId() != Item.AIR) {
            if (player != null) {
                ItemFrameDropItemEvent event = new ItemFrameDropItemEvent(player, this.getBlock(), this, item);
                this.level.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    this.spawnTo(player);
                    return true;
                }
            }
            this.setItem(Item.get(Item.AIR));
            this.setItemRotation(0);
            if (this.getItemDropChance() > ThreadLocalRandom.current().nextFloat()) {
                this.level.dropItem(this.add(0.5, 0, 0.5), item);
            }
            this.level.addLevelEvent(this, LevelEventPacket.EVENT_SOUND_ITEM_FRAME_ITEM_REMOVED);
            return true;
        }
        return false;
    }
}
