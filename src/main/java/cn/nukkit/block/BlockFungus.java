package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadLocalRandom;

public abstract class BlockFungus extends BlockFlowable {

    protected BlockFungus() {
        super(0);
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (!isValidSupport(down())) {
            return false;
        }
        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL && !isValidSupport(down())) {
            level.useBreakOn(this);
            return type;
        }

        return 0;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.isNull() || !(item.getId() == Item.DYE && item.getDamage() == 0x0F)) {
            return false;
        }

        level.addParticle(new BoneMealParticle(this));

        if (player != null && !player.isCreative()) {
            item.count--;
        }

        Block down = down();
        if (!isValidSupport(down)) {
            level.useBreakOn(this);
            return true;
        }

        if (!canGrowOn(down) || ThreadLocalRandom.current().nextFloat() >= 0.4) {
            return true;
        }

        grow(player);

        return true;
    }

    protected abstract boolean canGrowOn(Block support);

    protected boolean isValidSupport(Block support) {
        switch (support.getId()) {
            case GRASS:
            case DIRT:
            case PODZOL:
            case FARMLAND:
            case CRIMSON_NYLIUM:
            case WARPED_NYLIUM:
            case SOUL_SOIL:
            case MYCELIUM:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    public abstract boolean grow(@Nullable Player cause);
}
