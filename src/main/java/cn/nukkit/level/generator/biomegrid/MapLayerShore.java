package cn.nukkit.level.generator.biomegrid;

import cn.nukkit.level.biome.EnumBiome;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class MapLayerShore extends MapLayer {

    private static final IntSet OCEANS = new IntOpenHashSet();
    private static final Int2IntMap SPECIAL_SHORES = new Int2IntOpenHashMap();

    static {
        OCEANS.add(EnumBiome.OCEAN.id);
        OCEANS.add(EnumBiome.DEEP_OCEAN.id);

        SPECIAL_SHORES.put(EnumBiome.EXTREME_HILLS.id, EnumBiome.STONE_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.EXTREME_HILLS_PLUS.id, EnumBiome.STONE_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.EXTREME_HILLS_M.id, EnumBiome.STONE_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.EXTREME_HILLS_PLUS_M.id, EnumBiome.STONE_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.ICE_PLAINS.id, EnumBiome.COLD_BEACH.id);
        //SPECIAL_SHORES.put(EnumBiome.ICE_MOUNTAINS.id, EnumBiome.COLD_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.ICE_PLAINS_SPIKES.id, EnumBiome.COLD_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.COLD_TAIGA.id, EnumBiome.COLD_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.COLD_TAIGA_HILLS.id, EnumBiome.COLD_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.COLD_TAIGA_M.id, EnumBiome.COLD_BEACH.id);
        SPECIAL_SHORES.put(EnumBiome.MUSHROOM_ISLAND.id, EnumBiome.MUSHROOM_ISLAND_SHORE.id);
        SPECIAL_SHORES.put(EnumBiome.SWAMP.id, EnumBiome.SWAMP.id);
        SPECIAL_SHORES.put(EnumBiome.MESA.id, EnumBiome.MESA.id);
        SPECIAL_SHORES.put(EnumBiome.MESA_PLATEAU_F.id, EnumBiome.MESA_PLATEAU_F.id);
        SPECIAL_SHORES.put(EnumBiome.MESA_PLATEAU_F_M.id, EnumBiome.MESA_PLATEAU_F_M.id);
        SPECIAL_SHORES.put(EnumBiome.MESA_PLATEAU.id, EnumBiome.MESA_PLATEAU.id);
        SPECIAL_SHORES.put(EnumBiome.MESA_PLATEAU_M.id, EnumBiome.MESA_PLATEAU_M.id);
        SPECIAL_SHORES.put(EnumBiome.MESA_BRYCE.id, EnumBiome.MESA_BRYCE.id);
    }

    private final MapLayer belowLayer;

    public MapLayerShore(long seed, MapLayer belowLayer) {
        super(seed);
        this.belowLayer = belowLayer;
    }

    @Override
    public int[] generateValues(int x, int z, int sizeX, int sizeZ) {
        int gridX = x - 1;
        int gridZ = z - 1;
        int gridSizeX = sizeX + 2;
        int gridSizeZ = sizeZ + 2;
        int[] values = this.belowLayer.generateValues(gridX, gridZ, gridSizeX, gridSizeZ);

        int[] finalValues = new int[sizeX * sizeZ];
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                // This applies shores using Von Neumann neighborhood
                // it takes a 3x3 grid with a cross shape and analyzes values as follow
                // OXO
                // XxX
                // OXO
                // the grid center value decides how we are proceeding:
                // - if it's not ocean and it's surrounded by at least 1 ocean cell it turns the center value into beach.
                int upperVal = values[j + 1 + i * gridSizeX];
                int lowerVal = values[j + 1 + (i + 2) * gridSizeX];
                int leftVal = values[j + (i + 1) * gridSizeX];
                int rightVal = values[j + 2 + (i + 1) * gridSizeX];
                int centerVal = values[j + 1 + (i + 1) * gridSizeX];
                if (!OCEANS.contains(centerVal) && (OCEANS.contains(upperVal) || OCEANS.contains(lowerVal) || OCEANS.contains(leftVal) || OCEANS.contains(rightVal))) {
                    finalValues[j + i * sizeX] = SPECIAL_SHORES.containsKey(centerVal) ? SPECIAL_SHORES.get(centerVal) : EnumBiome.BEACH.id;
                } else {
                    finalValues[j + i * sizeX] = centerVal;
                }
            }
        }
        return finalValues;
    }
}
