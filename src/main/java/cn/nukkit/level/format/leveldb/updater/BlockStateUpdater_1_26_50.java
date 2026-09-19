package cn.nukkit.level.format.leveldb.updater;

import org.cloudburstmc.blockstateupdater.BlockStateUpdater;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

/**
 * 1.26.50 方块状态迁移规则：楼梯补 corner 默认值，栅栏/玻璃板/铁栏补 connection_* 默认值。
 * <p>
 * Updater rules for block states introduced in 1.26.50: stairs get a default corner state,
 * fences/panes/bars get default connection_* states.
 * <p>
 * Adapted from BlockStateUpdaters (<a href="https://github.com/CloudburstMC/BlockStateUpdaters">BlockStateUpdaters</a>).
 * Vendored until the library publishes 1.26.50; then revert to the dependency entry.
 */
public class BlockStateUpdater_1_26_50 implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdater_1_26_50();

    // 名单唯一事实源在 BlockStateUpdaterVanilla（常驻类），便于后续切回依赖库内置实现时零迁移
    // Single source of truth lives in BlockStateUpdaterVanilla (permanent class) so switching
    // back to the library's built-in updater later requires no migration here
    public static final String[] STAIRS = BlockStateUpdaterVanilla.STAIRS_WITH_CORNER_STATE;
    public static final String[] CONNECTABLES = BlockStateUpdaterVanilla.BLOCKS_WITH_CONNECTION_STATE;

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext ctx) {
        // 1.26.50 started storing stair corners and horizontal connections as states
        // instead of inferring them from neighbours at runtime; old states get the defaults.
        for (String stairs : STAIRS) {
            ctx.addUpdater(1, 26, 50)
                    .match("name", "minecraft:" + stairs)
                    .visit("states")
                    .tryAdd("minecraft:corner", "none");
        }

        for (String connectable : CONNECTABLES) {
            ctx.addUpdater(1, 26, 50)
                    .match("name", "minecraft:" + connectable)
                    .visit("states")
                    .tryAdd("minecraft:connection_north", (byte) 0)
                    .tryAdd("minecraft:connection_east", (byte) 0)
                    .tryAdd("minecraft:connection_south", (byte) 0)
                    .tryAdd("minecraft:connection_west", (byte) 0);
        }
    }
}
