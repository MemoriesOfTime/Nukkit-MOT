package cn.nukkit.level.biome.impl.jungle;

import cn.nukkit.level.generator.populator.impl.PopulatorBambooForest;
import cn.nukkit.level.generator.populator.impl.PopulatorMelon;
import cn.nukkit.level.generator.populator.impl.tree.JungleBigTreePopulator;

/**
 * @author Alemiz112
 */
public class BambooJungleBiome extends JungleBiome {

    public BambooJungleBiome() {
        /*JungleTreePopulator trees = new JungleTreePopulator();
        trees.setBaseAmount(3);
        this.addPopulator(trees);*/

        PopulatorBambooForest bamboo = new PopulatorBambooForest();
        bamboo.setBaseAmount(80);
        bamboo.setRandomAmount(30);
        this.addPopulator(bamboo);

        JungleBigTreePopulator bigTrees = new JungleBigTreePopulator();
        bigTrees.setBaseAmount(-1);
        bigTrees.setRandomAmount(2);
        this.addPopulator(bigTrees);

        PopulatorMelon melon = new PopulatorMelon();
        melon.setRandomAmount(2);
        this.addPopulator(melon);
    }

    @Override
    public String getName() {
        return "Bamboo Jungle";
    }
}
