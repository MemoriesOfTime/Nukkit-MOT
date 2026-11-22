package cn.nukkit.level.structure;

import cn.nukkit.math.BlockVector3;

/**
 * 结构放置选项（位置、旋转/镜像、完整度、实体与方块开关）。
 * <p>
 * Placement options: destination, rotation/mirror, integrity and block/entity toggles.
 */
public final class StructurePlacementOptions {

    public final BlockVector3 position;
    public int rotation;
    public int mirror;
    public float integrity = 100f;
    public int integritySeed;
    public boolean includeEntities;
    public boolean ignoringBlocks;
    /**
     * 0=瞬放 1=逐层 2=逐方块（与原版动画模式一致）。
     * <p>
     * 0=instant 1=layer 2=blocks, matching vanilla animation modes.
     */
    public int animationMode;
    public float animationSeconds;

    public StructurePlacementOptions(BlockVector3 position) {
        this.position = position;
    }
}
