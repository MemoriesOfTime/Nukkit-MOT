package cn.nukkit.level.generator.block.state;

import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.generator.math.Rotation;

public class BlockState {
    public static final BlockState AIR = new BlockState(0);

    private final int id;
    private final int meta;

    public BlockState(final int id) {
        this(id, 0);
    }

    public BlockState(final int id, final int meta) {
        this.id = id;
        this.meta = meta;
    }

    public static BlockState fromFullId(final int fullId) {
        return new BlockState(fullId >> Block.DATA_BITS, fullId & Block.DATA_MASK);
    }

    public static BlockState fromHash(final int hash) {
        return new BlockState(hash >> 6, hash & 0x3f);
    }

    public int getId() {
        return id;
    }

    public int getMeta() {
        return meta;
    }

    public int getFullId() {
        return id << Block.DATA_BITS | meta & Block.DATA_MASK;
    }

    public int getRuntimeId() {
        return GlobalBlockPalette.getOrCreateRuntimeId(id, meta);
    }

    public Block getBlock() {
        return Block.get(id, meta);
    }

    public BlockState rotate(final Rotation rot) {
        return switch (rot) {
            case CLOCKWISE_90 -> new BlockState(id, Rotation.clockwise90(id, meta));
            case CLOCKWISE_180 -> new BlockState(id, Rotation.clockwise180(id, meta));
            case COUNTERCLOCKWISE_90 -> new BlockState(id, Rotation.counterclockwise90(id, meta));
            default -> this;
        };
    }
}
