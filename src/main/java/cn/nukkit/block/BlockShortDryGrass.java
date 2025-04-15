package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockShortDryGrass extends BlockFlowable {
    public BlockShortDryGrass() {
        this(0);
    }

    public BlockShortDryGrass(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Short Dry Grass";
    }

    @Override
    public int getId() {
        return SHORT_DRY_GRASS;
    }

    @Override
    public boolean canBeReplaced() {
        return true;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (isSupportValid()) {
            this.getLevel().setBlock(block, this, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player) {
        if (item.getId() == Item.DYE && item.getDamage() == 0x0f) {
            BlockTallDryGrass tallDryGrass = new BlockTallDryGrass();

            this.level.addParticle(new BoneMealParticle(this));
            this.level.setBlock(this, tallDryGrass, true, false);
            item.count--;
            return true;
        }

        return false;
    }

    @Override
    public boolean canBeActivated() {
        return true;
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
