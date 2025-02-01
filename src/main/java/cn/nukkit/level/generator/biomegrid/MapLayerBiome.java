package cn.nukkit.level.generator.biomegrid;

import cn.nukkit.level.biome.EnumBiome;

public class MapLayerBiome extends MapLayer {

    private static final int[] WARM = new int[]{EnumBiome.DESERT.id, EnumBiome.DESERT.id, EnumBiome.DESERT.id, EnumBiome.SAVANNA.id, EnumBiome.SAVANNA.id, EnumBiome.PLAINS.id};
    private static final int[] WET = new int[]{EnumBiome.PLAINS.id, EnumBiome.PLAINS.id, EnumBiome.FOREST.id, EnumBiome.BIRCH_FOREST.id, EnumBiome.ROOFED_FOREST.id, EnumBiome.EXTREME_HILLS.id, EnumBiome.SWAMP.id};
    private static final int[] DRY = new int[]{EnumBiome.PLAINS.id, EnumBiome.FOREST.id, EnumBiome.TAIGA.id, EnumBiome.EXTREME_HILLS.id};
    private static final int[] COLD = new int[]{EnumBiome.ICE_PLAINS.id, EnumBiome.ICE_PLAINS.id, EnumBiome.COLD_TAIGA.id};
    private static final int[] WARM_LARGE = new int[]{EnumBiome.MESA_PLATEAU_F.id, EnumBiome.MESA_PLATEAU_F.id, EnumBiome.MESA_PLATEAU.id};
    private static final int[] DRY_LARGE = new int[]{EnumBiome.MEGA_TAIGA.id};
    private static final int[] WET_LARGE = new int[]{EnumBiome.JUNGLE.id};

    private final MapLayer belowLayer;

    public MapLayerBiome(long seed, MapLayer belowLayer) {
        super(seed);
        this.belowLayer = belowLayer;
    }

    @Override
    public int[] generateValues(int x, int z, int sizeX, int sizeZ) {
        int[] values = this.belowLayer.generateValues(x, z, sizeX, sizeZ);

        int[] finalValues = new int[sizeX * sizeZ];
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                int val = values[j + i * sizeX];
                if (val != 0) {
                    setCoordsSeed(x + j, z + i);
                    switch (val) {
                        case 1:
                            val = DRY[nextInt(DRY.length)];
                            break;
                        case 2:
                            val = WARM[nextInt(WARM.length)];
                            break;
                        case 3:
                        case 1003:
                            val = COLD[nextInt(COLD.length)];
                            break;
                        case 4:
                            val = WET[nextInt(WET.length)];
                            break;
                        case 1001:
                            val = DRY_LARGE[nextInt(DRY_LARGE.length)];
                            break;
                        case 1002:
                            val = WARM_LARGE[nextInt(WARM_LARGE.length)];
                            break;
                        case 1004:
                            val = WET_LARGE[nextInt(WET_LARGE.length)];
                            break;
                        default:
                            break;
                    }
                }
                finalValues[j + i * sizeX] = val;
            }
        }
        return finalValues;
    }
}
