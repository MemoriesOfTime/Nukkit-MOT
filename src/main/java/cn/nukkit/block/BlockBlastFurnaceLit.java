package cn.nukkit.block;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityBlastFurnace;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import org.jetbrains.annotations.NotNull;

public class BlockBlastFurnaceLit extends BlockFurnaceBurning {

    public BlockBlastFurnaceLit() {
        this(0);
    }

    public BlockBlastFurnaceLit(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIT_BLAST_FURNACE;
    }

    @Override
    public String getName() {
        return "Burning Blast Furnace";
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.BLAST_FURNACE;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityBlastFurnace> getBlockEntityClass() {
        return BlockEntityBlastFurnace.class;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(BLAST_FURNACE));
    }

}
