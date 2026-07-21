package cn.nukkit.level.biome.impl.taiga;

/**
 * @author Kanelucky
 */
public class GroveBiome extends TaigaHillsBiome {

    public GroveBiome() {
        super();

        this.setBaseHeight(1.0F);
        this.setHeightVariation(0.30F);
    }

    @Override
    public boolean isFreezing() {
        return true;
    }

    @Override
    public String getName() {
        return "Grove";
    }
}
