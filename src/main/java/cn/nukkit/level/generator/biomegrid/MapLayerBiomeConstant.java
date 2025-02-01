package cn.nukkit.level.generator.biomegrid;

public class MapLayerBiomeConstant extends MapLayer {

    private final int biome;

    public MapLayerBiomeConstant(long seed, int biome) {
        super(seed);
        this.biome = biome;
    }

    @Override
    public int[] generateValues(int x, int z, int sizeX, int sizeZ) {
        int[] values = new int[sizeX * sizeZ];
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                values[j + i * sizeX] = biome;
            }
        }
        return values;
    }
}
