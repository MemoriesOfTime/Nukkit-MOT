package cn.nukkit.level.biome.impl.plains;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFlower;
import cn.nukkit.block.BlockSapling;
import cn.nukkit.level.biome.type.GrassyBiome;
import cn.nukkit.level.generator.populator.impl.PopulatorFlower;
import cn.nukkit.level.generator.populator.impl.PopulatorTree;

/**
 * @author Kanelucky
 */
public class MeadowBiome extends GrassyBiome {

    public MeadowBiome() {
        super();
        PopulatorFlower flower = new PopulatorFlower();
        flower.setRandomAmount(5);
        flower.addType(DANDELION, 0);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_POPPY);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_AZURE_BLUET);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_RED_TULIP);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_ORANGE_TULIP);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_WHITE_TULIP);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_PINK_TULIP);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_OXEYE_DAISY);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_CORNFLOWER);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_ALLIUM);
        this.addPopulator(flower);

        PopulatorTree oakTree = new PopulatorTree(BlockSapling.OAK);
        oakTree.setRandomAmount(0);
        oakTree.setRandomAmount(1);
        this.addPopulator(oakTree);
        PopulatorTree birchTree = new PopulatorTree(BlockSapling.BIRCH);
        birchTree.setBaseAmount(0);
        birchTree.setRandomAmount(1);
        this.addPopulator(birchTree);

        this.setBaseHeight(0.8F);
        this.setHeightVariation(0.25F);
    }

    @Override
    public int getSurfaceId(int x, int y, int z) {
        return GRASS << Block.DATA_BITS;
    }

    @Override
    public int getGroundId(int x, int y, int z) {
        return DIRT << Block.DATA_BITS;
    }

    @Override
    public String getName() {
        return "Meadow";
    }
}
