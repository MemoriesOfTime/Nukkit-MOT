package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.cloudburstmc.nbt.NbtMap;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Getter
@Builder(toBuilder = true)
@ToString
public class BlockStateSnapshot {

    private static final VarHandle LEGACY_ID;

    static {
        try {
            LEGACY_ID = MethodHandles.lookup().findVarHandle(BlockStateSnapshot.class, "legacyId", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final NbtMap vanillaState;
    private final int runtimeId;
    private final GameVersion version;

    @Builder.Default
    private boolean custom = false;

    @Builder.Default
    private int legacyId = -1;
    @Builder.Default
    private int legacyData = -1;

    public int getLegacyId() {
        int id = this.legacyId;
        if (id != -1) {
            return id;
        }

        BlockStateMapping mapping = BlockStateMapping.get();
        id = mapping.getLegacyId(this.runtimeId);
        if (id != -1 && this.version == mapping.getVersion()) {
            int pinned = (int) LEGACY_ID.compareAndExchange(this, -1, id);
            return pinned == -1 ? id : pinned;
        }
        return id;
    }

    // Palette memos may reuse a legacy ID only once it can no longer change.
    int getPinnedLegacyId() {
        return (int) LEGACY_ID.getAcquire(this);
    }

    public int getLegacyData() {
        if (this.legacyData != -1) {
            return this.legacyData;
        }

        int meta = BlockStateMapping.get().getLegacyData(this.runtimeId);
        if (this.version == BlockStateMapping.get().getVersion()) {
            this.legacyData = meta;
        }
        return meta;
    }

    @Deprecated
    public Block getBlock() {
        return Block.get(this.getLegacyId(), this.getLegacyData());
    }
}
