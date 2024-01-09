package cn.nukkit.blockentity;

import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;

import java.util.List;


public class BlockEntityDecoratedPot extends BlockEntitySpawnable {

    public static final List<String> emptyDecoration = List.of("minecraft:brick", "minecraft:brick", "minecraft:brick", "minecraft:brick");

    public BlockEntityDecoratedPot(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        ListTag<StringTag> listTag = new ListTag<>();
        for (String s : emptyDecoration) {
            listTag.add(new StringTag(s));
        }
        this.namedTag.putList(listTag);
    }

    @Override
    public boolean isBlockEntityValid() {
        return getBlock().getId() == Block.DECORATED_POT;
    }

    @Override
    public CompoundTag getSpawnCompound() {
        return new CompoundTag()
                .putString("id", BlockEntity.DECORATED_POT)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putList("sherds", namedTag.getList("sherds"));
    }

    public enum DecoratedFace {
        BACK,
        LEFT,
        RIGHT,
        FRONT
    }
}