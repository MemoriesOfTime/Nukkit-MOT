package cn.nukkit.level.format.leveldb.updater.blockstateupdater;

import cn.nukkit.level.format.leveldb.updater.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

public interface BlockStateUpdater {

    void registerUpdaters(CompoundTagUpdaterContext context);

}
