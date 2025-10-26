package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

public class BlockTallDryGrass extends BlockFlowable {
    public BlockTallDryGrass() {
        this(0);
    }

    public BlockTallDryGrass(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Tall Dry Grass";
    }

    @Override
    public int getId() {
        return TALL_DRY_GRASS;
    }

    @Override
    public boolean canBeReplaced() {
        return true;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (isSupportValid()) {
            this.getLevel().setBlock(block, this, true);
            return true;
        }
        return false;
    }

    private boolean isSupportValid() {
        switch (down().getId()) {
            case SAND:
            case SUSPICIOUS_SAND:
            case MOSS_BLOCK:
            case FARMLAND:
            case TERRACOTTA:
            case MUD:
            case MUDDY_MANGROVE_ROOTS:
            case PALE_MOSS_BLOCK:
                return true;
            default: return BlockSweetBerryBush.isSupportValid(down());
        }

    }
}
