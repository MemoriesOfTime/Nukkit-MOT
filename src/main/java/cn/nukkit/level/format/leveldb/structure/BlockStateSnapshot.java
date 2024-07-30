package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.cloudburstmc.nbt.NbtMap;

@Getter
@Builder
@ToString
public class BlockStateSnapshot {

    private final NbtMap vanillaState;
    private final int runtimeId;
    private final int version;

    @Builder.Default
    private boolean custom = false;

    @Builder.Default
    private int legacyId = -1;
    @Builder.Default
    private int legacyData = -1;

    private Block block;

    public int getLegacyId() {
        if (this.legacyId != -1) {
            return this.legacyId;
        }

        int id = BlockStateMapping.get().getLegacyId(this.runtimeId);
        if (this.version == BlockStateMapping.get().getVersion()) {
            this.legacyId = id;
        }
        return id;
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

    public Block getBlock() {
        if (this.block == null) {
            this.block = Block.get(this.getLegacyId(), this.getLegacyData());
        }
        return this.block;
    }
}
