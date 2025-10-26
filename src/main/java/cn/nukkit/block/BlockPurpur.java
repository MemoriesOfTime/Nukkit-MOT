package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

public class BlockPurpur extends BlockSolidMeta {

    public static final int PURPUR_NORMAL = 0;
    public static final int PURPUR_PILLAR = 2;

    private static final short[] faces = new short[]{
            0,
            0,
            0b1000,
            0b1000,
            0b0100,
            0b0100
    };

    public BlockPurpur() {
        this(PURPUR_NORMAL);
    }

    public BlockPurpur(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        String[] names = new String[]{
                "Purpur Block",
                "",
                "Purpur Pillar",
                ""
        };

        return names[this.getDamage() & 0x03];
    }

    @Override
    public int getId() {
        return PURPUR_BLOCK;
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 30;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (this.getDamage() != PURPUR_NORMAL) {
            this.setDamage(((this.getDamage() & 0x03) | faces[face.getIndex()]));
        }
        this.getLevel().setBlock(block, this, true, true);

        return true;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(Block.PURPUR_BLOCK), this.getDamage() & 0x03, 1);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.MAGENTA_BLOCK_COLOR;
    }
}
