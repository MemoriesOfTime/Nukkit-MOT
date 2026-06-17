package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityPotentSulfur;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

public class BlockSulfurPotent extends BlockSulfur implements BlockEntityHolder<BlockEntityPotentSulfur> {

    public static final int DRY = 0;
    public static final int WET = 1;
    public static final int DORMANT = 2;
    public static final int ERUPTING = 3;

    public BlockSulfurPotent() {
    }

    @Override
    public int getId() {
        return POTENT_SULFUR;
    }

    @Override
    @NotNull
    public Class<? extends BlockEntityPotentSulfur> getBlockEntityClass() {
        return BlockEntityPotentSulfur.class;
    }

    @Override
    @NotNull
    public String getBlockEntityType() {
        return BlockEntity.POTENT_SULFUR;
    }

    @Override
    public String getName() {
        return "Potent Sulfur";
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (!super.place(item, block, target, face, fx, fy, fz, player)) {
            return false;
        }
        createBlockEntity();
        return true;
    }
}
