package cn.nukkit.level.generator.structure;

import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.tag.CompoundTag;

public class SwampHut extends ScatteredStructurePiece {
    public SwampHut(final BlockVector3 pos) {
        super(pos, new BlockVector3(7, 5, 9));
    }

    @Override
    public void generate(final ChunkManager level, final NukkitRandom random) {
        adjustHorizPos(level);

        final StructureBuilder builder = new StructureBuilder(level, this);
        builder.fill(new BlockVector3(1, 1, 2), new BlockVector3(5, 4, 7), BlockID.PLANKS, 1, BlockID.AIR, 0); // hut body
        builder.fill(new BlockVector3(1, 1, 1), new BlockVector3(5, 1, 1), BlockID.PLANKS, 1); // hut steps
        builder.fill(new BlockVector3(2, 1, 0), new BlockVector3(4, 1, 0), BlockID.PLANKS, 1); // hut steps
        builder.fill(new BlockVector3(4, 2, 2), new BlockVector3(4, 3, 2), BlockID.AIR); // hut door
        builder.fill(new BlockVector3(5, 3, 4), new BlockVector3(5, 3, 5), BlockID.AIR); // left window
        builder.setBlock(new BlockVector3(1, 3, 4), BlockID.AIR);

        builder.setBlock(new BlockVector3(1, 3, 5), BlockID.FLOWER_POT_BLOCK);
        builder.setTile(new BlockVector3(1, 3, 5), BlockEntity.FLOWER_POT, new CompoundTag()
            .putShort("item", BlockID.RED_MUSHROOM));

        builder.setBlock(new BlockVector3(2, 3, 2), BlockID.FENCE);
        builder.setBlock(new BlockVector3(3, 3, 7), BlockID.FENCE);

        builder.fill(new BlockVector3(0, 4, 1), new BlockVector3(6, 4, 1), BlockID.SPRUCE_WOOD_STAIRS, 2); // N
        builder.fill(new BlockVector3(6, 4, 2), new BlockVector3(6, 4, 7), BlockID.SPRUCE_WOOD_STAIRS, 1); // E
        builder.fill(new BlockVector3(0, 4, 8), new BlockVector3(6, 4, 8), BlockID.SPRUCE_WOOD_STAIRS, 3); // S
        builder.fill(new BlockVector3(0, 4, 2), new BlockVector3(0, 4, 7), BlockID.SPRUCE_WOOD_STAIRS, 0); // W

        builder.fill(new BlockVector3(1, 0, 2), new BlockVector3(1, 3, 2), BlockID.LOG);
        builder.fill(new BlockVector3(5, 0, 2), new BlockVector3(5, 3, 2), BlockID.LOG);
        builder.fill(new BlockVector3(1, 0, 7), new BlockVector3(1, 3, 7), BlockID.LOG);
        builder.fill(new BlockVector3(5, 0, 7), new BlockVector3(5, 3, 7), BlockID.LOG);

        builder.setBlock(new BlockVector3(1, 2, 1), BlockID.FENCE);
        builder.setBlock(new BlockVector3(5, 2, 1), BlockID.FENCE);

        builder.setBlock(new BlockVector3(4, 2, 6), BlockID.CAULDRON_BLOCK);
        builder.setTile(new BlockVector3(4, 2, 6), BlockEntity.CAULDRON);

        builder.setBlock(new BlockVector3(3, 2, 6), BlockID.CRAFTING_TABLE);

        builder.setBlockDownward(new BlockVector3(1, -1, 2), BlockID.LOG);
        builder.setBlockDownward(new BlockVector3(5, -1, 2), BlockID.LOG);
        builder.setBlockDownward(new BlockVector3(1, -1, 7), BlockID.LOG);
        builder.setBlockDownward(new BlockVector3(5, -1, 7), BlockID.LOG);

    }
}
