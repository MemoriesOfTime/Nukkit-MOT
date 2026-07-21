package cn.nukkit.level.biome.impl.taiga;

/**
 * @author Kanelucky
 */
public class GroveBiome extends TaigaHillsBiome {

    public GroveBiome() {
        super();
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
