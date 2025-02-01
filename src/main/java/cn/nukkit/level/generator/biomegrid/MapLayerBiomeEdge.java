package cn.nukkit.level.generator.biomegrid;

import cn.nukkit.level.biome.EnumBiome;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Map;
import java.util.Map.Entry;

public class MapLayerBiomeEdge extends MapLayer {

    private static final Int2IntMap MESA_EDGES = new Int2IntOpenHashMap();
    private static final Int2IntMap MEGA_TAIGA_EDGES = new Int2IntOpenHashMap();
    private static final Int2IntMap DESERT_EDGES = new Int2IntOpenHashMap();
    private static final Int2IntMap SWAMP1_EDGES = new Int2IntOpenHashMap();
    private static final Int2IntMap SWAMP2_EDGES = new Int2IntOpenHashMap();
    private static final Map<Int2IntMap, IntList> EDGES = Maps.newHashMap();

    static {
        MESA_EDGES.put(EnumBiome.MESA_PLATEAU_F.id, EnumBiome.MESA.id);
        MESA_EDGES.put(EnumBiome.MESA_PLATEAU.id, EnumBiome.MESA.id);

        MEGA_TAIGA_EDGES.put(EnumBiome.MEGA_TAIGA.id, EnumBiome.TAIGA.id);

        DESERT_EDGES.put(EnumBiome.DESERT.id, EnumBiome.EXTREME_HILLS_PLUS.id);

        SWAMP1_EDGES.put(EnumBiome.SWAMP.id, EnumBiome.PLAINS.id);
        SWAMP2_EDGES.put(EnumBiome.SWAMP.id, EnumBiome.JUNGLE_EDGE.id);

        EDGES.put(MESA_EDGES, null);
        EDGES.put(MEGA_TAIGA_EDGES, null);
        EDGES.put(DESERT_EDGES, IntArrayList.wrap(new int[]{EnumBiome.ICE_PLAINS.id}));
        EDGES.put(SWAMP1_EDGES, IntArrayList.wrap(new int[]{EnumBiome.DESERT.id, EnumBiome.COLD_TAIGA.id, EnumBiome.ICE_PLAINS.id}));
        EDGES.put(SWAMP2_EDGES, IntArrayList.wrap(new int[]{EnumBiome.JUNGLE.id}));
    }

    private final MapLayer belowLayer;

    public MapLayerBiomeEdge(long seed, MapLayer belowLayer) {
        super(seed);
        this.belowLayer = belowLayer;
    }

    @Override
    public int[] generateValues(int x, int z, int sizeX, int sizeZ) {
        int gridX = x - 1;
        int gridZ = z - 1;
        int gridSizeX = sizeX + 2;
        int gridSizeZ = sizeZ + 2;
        int[] values = belowLayer.generateValues(gridX, gridZ, gridSizeX, gridSizeZ);

        int[] finalValues = new int[sizeX * sizeZ];
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                // This applies biome large edges using Von Neumann neighborhood
                int centerVal = values[j + 1 + (i + 1) * gridSizeX];
                int val = centerVal;
                for (Entry<Int2IntMap, IntList> entry : EDGES.entrySet()) {
                    Int2IntMap map = entry.getKey();
                    if (map.containsKey(centerVal)) {
                        int upperVal = values[j + 1 + i * gridSizeX];
                        int lowerVal = values[j + 1 + (i + 2) * gridSizeX];
                        int leftVal = values[j + (i + 1) * gridSizeX];
                        int rightVal = values[j + 2 + (i + 1) * gridSizeX];
                        IntList entryValue = entry.getValue();
                        if (entryValue == null && (!map.containsKey(upperVal) || !map.containsKey(lowerVal) || !map.containsKey(leftVal) || !map.containsKey(rightVal))) {
                            val = map.get(centerVal);
                            break;
                        } else if (entryValue != null && (entryValue.contains(upperVal) || entryValue.contains(lowerVal) || entryValue.contains(leftVal) || entryValue.contains(rightVal))) {
                            val = map.get(centerVal);
                            break;
                        }
                    }
                }

                finalValues[j + i * sizeX] = val;
            }
        }
        return finalValues;
    }
}
