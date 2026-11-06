package cn.nukkit.block;

import cn.nukkit.entity.item.EntityFallingBlock;
import cn.nukkit.event.block.BlockFallEvent;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.*;

/**
 * @author rcsuperman
 * Nukkit Project
 */
public abstract class BlockFallable extends BlockSolid {

    protected BlockFallable() {
    }

    @Override
    public int onUpdate(int type) {
        if (type != Level.BLOCK_UPDATE_NORMAL) {
            return type;
        }

        Block down = this.down();
        if (this.canFallInto(down)) {
            BlockFallEvent event = new BlockFallEvent(this);
            this.level.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return type;
            }

            this.level.setBlock(this, Block.get(Block.AIR), true, true);
            EntityFallingBlock fall = this.createFallingEntity(new CompoundTag());
            fall.spawnToAll();
        }
        return type;
    }

    protected boolean canFallInto(Block block) {
        return BlockFallable.isFallThrough(this, block);
    }

    /**
     * Whether an unsupported gravity block falls into {@code below}.
     * <p>
     * Vanilla Bedrock lets sand, gravel, concrete powder and anvils fall through every block it would
     * replace on landing (the minecraft:replaceable tag): seagrass, short and tall grass, ferns, dead
     * bushes, vines, glow lichen, roots, sprouts and thin snow. The landing branch already replaces
     * exactly these cells through {@link Block#canBeReplaced()}, so the start of the fall uses the same
     * predicate; otherwise the block hangs over them. Flowers, kelp, torches and other non-replaceable
     * plants keep supporting it, as in vanilla. A bubble column still needs its liquid layer, and a snow
     * layer never falls into another snow layer, which the landing branch merges instead.
     */
    static boolean isFallThrough(Block falling, Block below) {
        if (below.getId() == AIR || below instanceof BlockLiquid || below instanceof BlockFire) {
            return true;
        }
        if (below instanceof BlockBubbleColumn) {
            return below.getLevelBlockAtLayer(1) instanceof BlockLiquid;
        }
        if (falling instanceof BlockSnowLayer && below instanceof BlockSnowLayer) {
            return false;
        }
        return below.canBeReplaced() && !below.isSolid();
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
