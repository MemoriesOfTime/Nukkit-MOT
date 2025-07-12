package cn.nukkit.block.custom.container;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.level.GlobalBlockPalette;

public interface BlockContainer {

    String getIdentifier();

    int getNukkitId();

    default int getNukkitDamage() {
        return 0;
    }

    default int getRuntimeId() {
        Server.mvw("BlockContainer#getRuntimeId()");
        return getRuntimeId(GameVersion.getLastVersion());
    }

    default int getRuntimeId(GameVersion version) {
        return GlobalBlockPalette.getOrCreateRuntimeId(version, this.getNukkitId(), this.getNukkitDamage());
    }
}
