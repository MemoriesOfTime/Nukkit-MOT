package cn.nukkit.blockentity;

import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.Random;

/**
 * @author glorydark
 */
public class BlockEntitySuspiciousBlock extends BlockEntitySpawnable implements BlockEntityNameable {

    private Item item;

    private byte brushDirection;

    private int brushCount;

    private boolean rare = false;

    public BlockEntitySuspiciousBlock(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public void setBrushDirection(BlockFace face) {
        this.brushDirection = (byte) face.getIndex();
        this.namedTag.putByte("brush_direction", this.brushDirection);
    }

    public BlockFace getBrushDirection() {
        return BlockFace.fromIndex(this.brushDirection);
    }

    public void setBrushCount(int brushCount) {
        this.brushCount = brushCount;
        this.namedTag.putInt("brush_count", brushCount);
    }

    public int getBrushCount() {
        return brushCount;
    }

    public void setRare(boolean rare) {
        this.rare = rare;
        this.namedTag.putString("LootTable", getLootTablePath());
    }

    public boolean isRare() {
        return rare;
    }

    @Override
    protected void initBlockEntity() {
        if (this.namedTag.contains("item")) {
            this.item = NBTIO.getItemHelper(this.namedTag.getCompound("item"));
        } else {
            this.item = Item.AIR_ITEM.clone();
        }

        super.initBlockEntity();
    }

    @Override
    public String getName() {
        return this.hasName() ? this.namedTag.getString("CustomName") : "Enchanting Table";
    }

    @Override
    public void setName(String name) {
        if (name == null || name.isEmpty()) {
            this.namedTag.remove("CustomName");
            return;
        }
        this.namedTag.putString("CustomName", name);
    }

    @Override
    public boolean hasName() {
        return this.namedTag.contains("CustomName");
    }

    @Override
    public boolean isBlockEntityValid() {
        return getBlock().isSuspiciousBlock();
    }

    @Override
    public CompoundTag getSpawnCompound() {
        return new CompoundTag()
                .putString("id", BlockEntity.DECORATED_POT)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putCompound("item", NBTIO.putItemHelper(this.item))
                .putList("type", namedTag.getList(getBlock().toItem().getNamespaceId()))
                .putInt("LootTableSeed", new Random().nextInt())
                .putString("LootTable", getLootTablePath());
    }

    // todo: check if paths are correct.
    public String getLootTablePath() {
        if (rare) {
            return "loot_tables/entities/trail_ruins_brushable_block_rare.json";
        } else {
            return "loot_tables/entities/trail_ruins_brushable_block_common.json";
        }
    }
}