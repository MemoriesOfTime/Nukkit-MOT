package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import lombok.Getter;

@Getter
public class BlockFenceCrimson extends BlockFence {
    private final int id = CRIMSON_FENCE;
    private final int burnChance = 0;
    private final int burnAbility= 0;

    private final String name = "Crimson Fence";

    public BlockFenceCrimson() {
        this(0);
    }

    public BlockFenceCrimson(int meta) {
        super(meta);
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}