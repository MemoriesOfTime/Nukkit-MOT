
package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.mob.EntityWither;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * @author Justin
 */
public class BlockSkullWitherSkeleton extends BlockSkullSkeleton {

    public BlockSkullWitherSkeleton() {
        this(0);
    }

    public BlockSkullWitherSkeleton(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WITHER_SKELETON_SKULL;
    }

    @Override
    public String getName() {
        return "Wither Skeleton Skull";
    }

    @Override
    public Item toItem() {
        return Item.get(Item.SKULL, 1);
    }

    @Override
    public SkullType getSkullType() {
        return SkullType.WITHER_SKELETON;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @NotNull Player player) {
        if (super.place(item, block, target, face, fx, fy, fz, player)) {
            if (EntityWither.checkAndSpawnWither(this)) {
                player.awardAchievement("spawnWither");
            }
            return true;
        }
        return false;
    }
}
