package cn.nukkit.level.format.leveldb.updater;

import org.cloudburstmc.blockstateupdater.BlockStateUpdater;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

/**
 * @author LT_Name
 */
public class BlockStateUpdaterVanilla implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdaterVanilla();

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext ctx) {

    }

}
