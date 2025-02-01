package cn.nukkit.level.generator.biomegrid;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.biome.EnumBiome;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class MapLayerBiomeVariation extends MapLayer {

    private static final int[] ISLANDS = new int[]{EnumBiome.PLAINS.id, EnumBiome.FOREST.id};
    private static final Int2ObjectMap<int[]> VARIATIONS = new Int2ObjectOpenHashMap<>();

    static {
        VARIATIONS.put(EnumBiome.DESERT.id, new int[]{EnumBiome.DESERT_HILLS.id});
        VARIATIONS.put(EnumBiome.FOREST.id, new int[]{EnumBiome.FOREST_HILLS.id});
        VARIATIONS.put(EnumBiome.BIRCH_FOREST.id, new int[]{EnumBiome.BIRCH_FOREST_HILLS.id});
        VARIATIONS.put(EnumBiome.ROOFED_FOREST.id, new int[]{EnumBiome.PLAINS.id});
        VARIATIONS.put(EnumBiome.TAIGA.id, new int[]{EnumBiome.TAIGA_HILLS.id});
        VARIATIONS.put(EnumBiome.MEGA_TAIGA.id, new int[]{EnumBiome.MEGA_TAIGA_HILLS.id});
        VARIATIONS.put(EnumBiome.COLD_TAIGA.id, new int[]{EnumBiome.COLD_TAIGA_HILLS.id});
        VARIATIONS.put(EnumBiome.PLAINS.id, new int[]{EnumBiome.FOREST.id, EnumBiome.FOREST.id, EnumBiome.FOREST_HILLS.id});
        //VARIATIONS.put(EnumBiome.ICE_PLAINS.id, new int[]{EnumBiome.ICE_MOUNTAINS.id});
        VARIATIONS.put(EnumBiome.JUNGLE.id, new int[]{EnumBiome.JUNGLE_HILLS.id});
        VARIATIONS.put(EnumBiome.OCEAN.id, new int[]{EnumBiome.DEEP_OCEAN.id});
        VARIATIONS.put(EnumBiome.EXTREME_HILLS.id, new int[]{EnumBiome.EXTREME_HILLS_PLUS.id});
        VARIATIONS.put(EnumBiome.SAVANNA.id, new int[]{EnumBiome.SAVANNA_PLATEAU.id});
        VARIATIONS.put(EnumBiome.MESA_PLATEAU_F.id, new int[]{EnumBiome.MESA.id});
        VARIATIONS.put(EnumBiome.MESA_PLATEAU.id, new int[]{EnumBiome.MESA.id});
        VARIATIONS.put(EnumBiome.MESA.id, new int[]{EnumBiome.MESA.id});
    }

    private final MapLayer belowLayer;
    private final MapLayer variationLayer;

    /**
     * Creates an instance with no variation layer.
     * @param seed the PRNG seed
     * @param belowLayer the layer below this one
     */
    public MapLayerBiomeVariation(long seed, MapLayer belowLayer) {
        this(seed, belowLayer, null);
    }

    /**
     * Creates an instance with the given variation layer.
     * @param seed the PRNG seed
     * @param belowLayer the layer below this one
     * @param variationLayer the variation layer, or null to use no variation layer
     */
    public MapLayerBiomeVariation(long seed, MapLayer belowLayer, MapLayer variationLayer) {
        super(seed);
        this.belowLayer = belowLayer;
        this.variationLayer = variationLayer;
    }

    @Override
    public int[] generateValues(int x, int z, int sizeX, int sizeZ) {
        if (this.variationLayer == null) {
            return generateRandomValues(x, z, sizeX, sizeZ);
        }
        return mergeValues(x, z, sizeX, sizeZ);
    }

    /**
     * Generates a rectangle, replacing all the positive values in the previous layer with random
     * values from 2 to 31 while leaving zero and negative values unchanged.
     *
     * @param x the lowest x coordinate
     * @param z the lowest z coordinate
     * @param sizeX the x coordinate range
     * @param sizeZ the z coordinate range
     * @return a flattened array of generated values
     */
    public int[] generateRandomValues(int x, int z, int sizeX, int sizeZ) {
        int[] values = this.belowLayer.generateValues(x, z, sizeX, sizeZ);

        int[] finalValues = new int[sizeX * sizeZ];
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                int val = values[j + i * sizeX];
                if (val > 0) {
                    setCoordsSeed(x + j, z + i);
                    val = nextInt(30) + 2;
                }
                finalValues[j + i * sizeX] = val;
            }
        }
        return finalValues;
    }

    /**
     * Generates a rectangle using the previous layer and the variation layer.
     *
     * @param x the lowest x coordinate
     * @param z the lowest z coordinate
     * @param sizeX the x coordinate range
     * @param sizeZ the z coordinate range
     * @return a flattened array of generated values
     */
    public int[] mergeValues(int x, int z, int sizeX, int sizeZ) {
        int gridX = x - 1;
        int gridZ = z - 1;
        int gridSizeX = sizeX + 2;
        int gridSizeZ = sizeZ + 2;

        int[] values = this.belowLayer.generateValues(gridX, gridZ, gridSizeX, gridSizeZ);
        int[] variationValues = this.variationLayer.generateValues(gridX, gridZ, gridSizeX, gridSizeZ);

        int[] finalValues = new int[sizeX * sizeZ];
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                setCoordsSeed(x + j, z + i);
                int centerValue = values[j + 1 + (i + 1) * gridSizeX];
                int variationValue = variationValues[j + 1 + (i + 1) * gridSizeX];
                if (centerValue != 0 && variationValue == 3 && centerValue < 128) {
                    finalValues[j + i * sizeX] = Biome.biomes[centerValue + 128] != null ? centerValue + 128 : centerValue;
                } else if (variationValue == 2 || nextInt(3) == 0) {
                    int val = centerValue;
                    if (VARIATIONS.containsKey(centerValue)) {
                        val = VARIATIONS.get(centerValue)[nextInt(VARIATIONS.get(centerValue).length)];
                    } else if (centerValue == EnumBiome.DEEP_OCEAN.id && nextInt(3) == 0) {
                        val = ISLANDS[nextInt(ISLANDS.length)];
                    }
                    if (variationValue == 2 && val != centerValue) {
                        val = Biome.biomes[val + 128] != null ? val + 128 : centerValue;
                    }
                    if (val != centerValue) {
                        int count = 0;
                        if (values[j + 1 + i * gridSizeX] == centerValue) { // upper value
                            count++;
                        }
                        if (values[j + 1 + (i + 2) * gridSizeX] == centerValue) { // lower value
                            count++;
                        }
                        if (values[j + (i + 1) * gridSizeX] == centerValue) { // left value
                            count++;
                        }
                        if (values[j + 2 + (i + 1) * gridSizeX] == centerValue) { // right value
                            count++;
                        }
                        // spread mountains if not too close from an edge
                        finalValues[j + i * sizeX] = count < 3 ? centerValue : val;
                    } else {
                        finalValues[j + i * sizeX] = val;
                    }
                } else {
                    finalValues[j + i * sizeX] = centerValue;
                }
            }
        }
        return finalValues;
    }
}
