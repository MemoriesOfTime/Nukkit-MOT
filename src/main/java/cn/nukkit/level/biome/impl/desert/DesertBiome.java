package cn.nukkit.level.biome.impl.desert;

import cn.nukkit.level.biome.type.SandyBiome;
import cn.nukkit.level.generator.populator.impl.PopulatorCactus;
import cn.nukkit.level.generator.populator.impl.PopulatorDeadBush;
import cn.nukkit.level.generator.populator.impl.PopulatorSugarcane;
import cn.nukkit.level.generator.populator.impl.PopulatorWell;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class DesertBiome extends SandyBiome {
    public DesertBiome() {
        PopulatorSugarcane sugarcane = new PopulatorSugarcane();
        sugarcane.setBaseAmount(10);
        sugarcane.setRandomAmount(40);
        this.addPopulator(sugarcane);

        PopulatorCactus cactus = new PopulatorCactus();
        cactus.setBaseAmount(2);
        this.addPopulator(cactus);

        PopulatorDeadBush deadbush = new PopulatorDeadBush();
        deadbush.setBaseAmount(2);
        this.addPopulator(deadbush);

        this.addPopulator(new PopulatorWell());

        this.setBaseHeight(0.125f);
        this.setHeightVariation(0.05f);
    }

    @Override
    public String getName() {
        return "Desert";
    }

    @Override
    public boolean canRain() {
        return false;
    }
}
