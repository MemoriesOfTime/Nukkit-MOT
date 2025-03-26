package cn.nukkit.level.biome.impl.jungle;

import cn.nukkit.block.BlockFlower;
import cn.nukkit.level.biome.type.GrassyBiome;
import cn.nukkit.level.generator.populator.impl.PopulatorBambooForest;
import cn.nukkit.level.generator.populator.impl.PopulatorFlower;
import cn.nukkit.level.generator.populator.impl.PopulatorMelon;
import cn.nukkit.level.generator.populator.impl.tree.JungleBigTreePopulator;
import cn.nukkit.level.generator.populator.impl.tree.JungleTreePopulator;

/**
 * @author DaPorkchop_
 */
public class JungleBiome extends GrassyBiome {
    public JungleBiome() {
        super();

        JungleTreePopulator trees = new JungleTreePopulator();
        trees.setBaseAmount(10);
        this.addPopulator(trees);

        JungleBigTreePopulator bigTrees = new JungleBigTreePopulator();
        bigTrees.setBaseAmount(7);
        this.addPopulator(bigTrees);

        PopulatorMelon melon = new PopulatorMelon();
        melon.setRandomAmount(2);
        this.addPopulator(melon);

        PopulatorBambooForest bamboo = new PopulatorBambooForest();
        bamboo.setRandomAmount(2);
        this.addPopulator(bamboo);

        PopulatorFlower flower = new PopulatorFlower();
        flower.setRandomAmount(3);
        flower.addType(DANDELION, 0);
        flower.addType(RED_FLOWER, BlockFlower.TYPE_POPPY);
        this.addPopulator(flower);
    }

    @Override
    public String getName() {
        return "Jungle";
    }
}
