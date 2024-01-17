package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockUnknown;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import lombok.ToString;
import org.cloudburstmc.nbt.NbtMap;

@ToString
public class BlockStateSnapshot {

    private final NbtMap vanillaState;
    private final int runtimeId;
    private final int version;
    private boolean custom;
    private int legacyId;
    private int legacyData;
    private Block block;

    private Block b() {
        long l2;
        long l3 = l2 = h ^ 0x2EBE82482C84L;
        long l4 = l3 ^ 0x7344719D74B8L;
        long l5 = l3 ^ 0x12A72E981A20L;
        if (this.block == null) {
            this.block = Block.get(this.k(l4), this.h(l5));
        }
        return this.block;
    }

    public int k(long l2) {
        long l3 = (l2 = h ^ l2) ^ 0x331EEC484333L;
        if (this.legacyId != -1) {
            return this.legacyId;
        }
        int n2 = BlockStateMapping.get().c(this.runtimeId, l3);
        if (this.version == BlockStateMapping.get().d()) {
            this.legacyId = n2;
        }
        return n2;
    }

    public int h(long l2) {
        long l3 = (l2 = h ^ l2) ^ 0x76335709E128L;
        if (this.legacyData != -1) {
            return this.legacyData;
        }
        int n2 = BlockStateMapping.get().a(this.runtimeId, l3);
        if (this.version == BlockStateMapping.get().d()) {
            this.legacyData = n2;
        }
        return n2;
    }

    public int b(int n2) {
        long l2 = h ^ 0x197BD60722B7L;
        long l3 = l2 ^ 0x448125D27A8BL;
        if (n2 >= this.version || this.b() instanceof BlockUnknown) {
            return this.k(l3);
        }
        return this.b().getAlternateBlock(n2).getLegacyId();
    }

    public int a(int n2) {
        long l2 = h ^ 0x72D2488AB514L;
        long l3 = l2 ^ 0x4ECBE45A83B0L;
        if (n2 >= this.version || this.b() instanceof BlockUnknown) {
            return this.h(l3);
        }
        return this.b().getAlternateMeta(n2);
    }

    private static boolean i() {
        return false;
    }

    private static int d() {
        return -1;
    }

    private static int j() {
        return -1;
    }

    private static Block e() {
        return null;
    }

    BlockStateSnapshot(NbtMap nbtMap, int n2, int n3, boolean bl, int n4, int n5, Block block) {
        this.vanillaState = nbtMap;
        this.runtimeId = n2;
        this.version = n3;
        this.custom = bl;
        this.legacyId = n4;
        this.legacyData = n5;
        this.block = block;
    }

    public static BlockStateSnapshotBuilder l() {
        return new BlockStateSnapshotBuilder();
    }

    public NbtMap getVanillaState() {
        return this.vanillaState;
    }

    public int getRuntimeId() {
        return this.runtimeId;
    }

    public int c() {
        return this.version;
    }

    public boolean f() {
        return this.custom;
    }

    @ToString
    public static class BlockStateSnapshotBuilder {
        private NbtMap vanillaState;
        private int runtimeId;
        private int version;
        private boolean e;
        private boolean f;
        private boolean j;
        private int h;
        private boolean k;
        private int i;
        private boolean g;
        private Block block;

        BlockStateSnapshotBuilder() {
        }

        public BlockStateSnapshotBuilder a(NbtMap vanillaState) {
            this.vanillaState = vanillaState;
            return this;
        }

        public BlockStateSnapshotBuilder a(int n2) {
            this.runtimeId = n2;
            return this;
        }

        public BlockStateSnapshotBuilder b(int n2) {
            this.version = n2;
            return this;
        }

        public BlockStateSnapshotBuilder a(boolean bl) {
            this.f = bl;
            this.e = true;
            return this;
        }

        public BlockStateSnapshotBuilder d(int n2) {
            this.h = n2;
            this.j = true;
            return this;
        }

        public BlockStateSnapshotBuilder c(int n2) {
            this.i = n2;
            this.k = true;
            return this;
        }

        public BlockStateSnapshotBuilder a(Block block) {
            this.block1 = block;
            this.g = true;
            return this;
        }

        public BlockStateSnapshot a() {
            boolean bl = this.f;
            if (!this.e) {
                bl = BlockStateSnapshot.i();
            }
            int n2 = this.h;
            if (!this.j) {
                n2 = BlockStateSnapshot.d();
            }
            int n3 = this.i;
            if (!this.k) {
                n3 = BlockStateSnapshot.j();
            }
            Block block = this.block1;
            if (!this.g) {
                block = BlockStateSnapshot.e();
            }
            return new BlockStateSnapshot(this.vanillaState, this.runtimeId, this.version, bl, n2, n3, block);
        }
    }
}

