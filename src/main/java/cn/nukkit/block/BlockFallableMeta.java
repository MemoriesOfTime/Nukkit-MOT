package cn.nukkit.block;

import cn.nukkit.entity.item.EntityFallingBlock;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.*;

/**
 * @author rcsuperman
 * Nukkit Project
 */
public abstract class BlockFallableMeta extends BlockSolidMeta {

    protected BlockFallableMeta(int meta) {
        super(meta);
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (!this.getLevel().randomTickingEnabled()) return type;
            Block down = this.down();
            if (this.canFallInto(down)) {
                EntityFallingBlock.startFalling(this, () -> this.createFallingEntity(new CompoundTag()));
            }
        }
        return type;
    }

    protected boolean canFallInto(Block block) {
        return block.getId() == AIR
                || block instanceof BlockLiquid
                || block instanceof BlockFire
                || block instanceof BlockBubbleColumn && block.getLevelBlockAtLayer(1) instanceof BlockLiquid;
    }

    protected EntityFallingBlock createFallingEntity(CompoundTag customNbt) {
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", this.x + 0.5))
                        .add(new DoubleTag("", this.y))
                        .add(new DoubleTag("", this.z + 0.5)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)))
                .putInt("TileID", this.getId())
                .putByte("Data", this.getDamage());

        for (Tag customTag : customNbt.getAllTags()) {
            nbt.put(customTag.getName(), customTag.copy());
        }

        return new EntityFallingBlock(this.getLevel().getChunk((int) this.x >> 4, (int) this.z >> 4), nbt);
    }
}
