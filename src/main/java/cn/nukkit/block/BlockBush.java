package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

public class BlockBush extends BlockFlowable {
    public BlockBush() {
        this(0);
    }

    public BlockBush(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Bush";
    }

    @Override
    public int getId() {
        return BUSH;
    }

    @Override
    public boolean canBeReplaced() {
        return true;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (BlockSweetBerryBush.isSupportValid(down())) {
            this.getLevel().setBlock(block, this, true);
            return true;
        }
        return false;
    }
}
