package cn.nukkit.level.format.leveldb.updater.blockstateupdater;

import cn.nukkit.level.format.leveldb.updater.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockStateUpdater_1_10_0 implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdater_1_10_0();

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext context) {
        // TODO: mapped types. (I'm not sure if these are needed)
    }
}
