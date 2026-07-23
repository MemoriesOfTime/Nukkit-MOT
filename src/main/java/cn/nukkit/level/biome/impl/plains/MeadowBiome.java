package cn.nukkit.level.biome.impl.plains;

import cn.nukkit.block.BlockFlower;
import cn.nukkit.block.BlockSapling;
import cn.nukkit.level.biome.type.GrassyBiome;
import cn.nukkit.level.generator.populator.impl.PopulatorFlower;
import cn.nukkit.level.generator.populator.impl.PopulatorTree;

/**
 * Flowery meadow at the foot of mountains, rich in flowers with occasional oak and birch trees.
 *
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
        flower.addType(RED_FLOWER, BlockFlower.TYPE_OXEYE_DAISY);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_CORNFLOWER);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_ALLIUM);
        this.addPopulator(flower);

        PopulatorTree oakTree = new PopulatorTree(BlockSapling.OAK);
        oakTree.setBaseAmount(0);
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
    public String getName() {
        return "Meadow";
    }
}
