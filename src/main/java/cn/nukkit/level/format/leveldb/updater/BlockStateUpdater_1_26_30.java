package cn.nukkit.level.format.leveldb.updater;

import org.cloudburstmc.blockstateupdater.BlockStateUpdater;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

/**
 * 1.26.30 方块状态迁移规则（potent_sulfur 补默认状态）。
 * <p>
 * Updater rules for block states introduced in 1.26.30 (potent_sulfur default state).
 * <p>
 * Adapted from BlockStateUpdaters (<a href="https://github.com/CloudburstMC/BlockStateUpdaters">BlockStateUpdaters</a>).
 * Vendored until the library publishes 1.26.50; then revert to the dependency entry.
 */
public class BlockStateUpdater_1_26_30 implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdater_1_26_30();

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext ctx) {
        ctx.addUpdater(1, 26, 30)
                .match("name", "minecraft:potent_sulfur")
                .visit("states")
                .tryAdd("potent_sulfur_state", "dry");
    }
}
