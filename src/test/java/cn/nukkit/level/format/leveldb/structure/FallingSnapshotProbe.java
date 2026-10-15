package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import org.cloudburstmc.nbt.NbtMap;

/** An ordinary legacy subclass passed through the public StateBlockStorage.set API. */
public final class FallingSnapshotProbe extends BlockStateSnapshot {
    private final Runnable read;
    public FallingSnapshotProbe(Runnable read) {
        super(NbtMap.EMPTY, 0, GameVersion.getFeatureVersion(), false, Block.AIR, 0);
        this.read = read;
    }
    @Override public int getLegacyId() { read.run(); return Block.AIR; }
}
