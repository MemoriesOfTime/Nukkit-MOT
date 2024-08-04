package cn.nukkit.block.customblock.container;

import cn.nukkit.level.GlobalBlockPalette;

public interface BlockContainer {

    String getIdentifier();

    int getNukkitId();

    default int getNukkitDamage() {
        return 0;
    }

    default int getRuntimeId() {
        return GlobalBlockPalette.getOrCreateRuntimeId(this.getNukkitId(), this.getNukkitDamage());
    }
}
