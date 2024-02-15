package cn.nukkit.level.format.leveldb.updater.blockstateupdater;

import cn.nukkit.level.format.leveldb.updater.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

public class BlockStateUpdater_1_17_40 implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdater_1_17_40();

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext context) {
        context.addUpdater(1, 16, 210, true) // Palette version wasn't bumped so far
                .match("name", "minecraft:sculk_catalyst")
                .visit("states")
                .tryAdd("bloom", (byte) 0);
    }
}
