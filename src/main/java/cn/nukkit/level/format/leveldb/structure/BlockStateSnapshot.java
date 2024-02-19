package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import org.cloudburstmc.nbt.NbtMap;


public class BlockStateSnapshot {

    private final NbtMap vanillaState;
    private final int runtimeId;
    private final int version;
    private boolean custom;
    private int legacyId;
    private int legacyData;
    private Block block;

    private BlockStateSnapshot(NbtMap vanillaState, int runtimeId, int version, boolean custom, int legacyId, int legacy) {
        this.vanillaState = vanillaState;
        this.runtimeId = runtimeId;
        this.version = version;
        this.custom = custom;
        this.legacyId = legacyId;
        this.legacyData = legacy;
    }

    public NbtMap getVamillaState() {
        return this.vanillaState;
    }

    public int getRuntimeId() {
        return this.runtimeId;
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isCustom() {
        return this.custom;
    }

    public int getLegacyId() {
        if (this.legacyId != -1) {
            return this.legacyId;
        }

        int id = BlockStateMapping.get().getLegacyId(this.runtimeId);
        if (this.version == BlockStateMapping.get().getProtocol()) {
            this.legacyId = id;
        }
        return id;
    }

    public int getLegacyData() {
        if (this.legacyData != -1) {
            return this.legacyData;
        }

        int meta = BlockStateMapping.get().getLegacyData(this.runtimeId);
        if (this.version == BlockStateMapping.get().getProtocol()) {
            this.legacyData = meta;
        }
        return meta;
    }

    public Block getBlock() {
        if (block == null) {
            return Block.get(this.getLegacyId(), this.getLegacyData());
        }
        return block;
    }

    public static BlockStateSnapshotBuilder builder() {
        return new BlockStateSnapshotBuilder();
    }

    public static class BlockStateSnapshotBuilder {
        private NbtMap vanillaState;
        private int runtimeId;
        private int version;
        private boolean custom;
        private int legacyId;
        private int legacyData;

        public BlockStateSnapshotBuilder() {
        }

        public BlockStateSnapshotBuilder vanillaState(NbtMap vanillaState) {
            this.vanillaState = vanillaState;
            return this;
        }

        public BlockStateSnapshotBuilder runtimeId(int runtimeId) {
            this.runtimeId = runtimeId;
            return this;
        }

        public BlockStateSnapshotBuilder version(int version) {
            this.version = version;
            return this;
        }

        public BlockStateSnapshotBuilder custom(boolean custom) {
            this.custom = custom;
            return this;
        }

        public BlockStateSnapshotBuilder legacyId(int legacyId) {
            this.legacyId = legacyId;
            return this;
        }

        public BlockStateSnapshotBuilder legacyData(int legacyData) {
            this.legacyData = legacyData;
            return this;
        }

        public BlockStateSnapshot build() {
            return new BlockStateSnapshot(this.vanillaState, this.runtimeId, this.version, this.custom, this.legacyId, this.legacyData);
        }
    }
}
