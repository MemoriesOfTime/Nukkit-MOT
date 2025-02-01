package cn.nukkit.level.generator.biome;

import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.generator.noise.bukkit.SimplexOctaveGenerator;
import cn.nukkit.math.NukkitRandom;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class BiomeClimate {

    private static final Int2ObjectMap<Climate> CLIMATE_MAP = new Int2ObjectOpenHashMap<>();
    private static final SimplexOctaveGenerator noiseGen;

    static {
        int[] biomes = new int[Biome.unorderedBiomes.size()];
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = Biome.unorderedBiomes.get(i).getId();
        }
        setBiomeClimate(Climate.DEFAULT, biomes);
        setBiomeClimate(Climate.PLAINS, EnumBiome.PLAINS.id, EnumBiome.SUNFLOWER_PLAINS.id, EnumBiome.BEACH.id);
        setBiomeClimate(Climate.DESERT, EnumBiome.DESERT.id, EnumBiome.DESERT_HILLS.id, EnumBiome.DESERT_M.id, EnumBiome.MESA.id, EnumBiome.MESA_BRYCE.id, EnumBiome.MESA_PLATEAU.id, EnumBiome.MESA_PLATEAU_F.id, EnumBiome.MESA_PLATEAU_M.id, EnumBiome.MESA_PLATEAU_F_M.id, EnumBiome.HELL.id);
        setBiomeClimate(Climate.EXTREME_HILLS, EnumBiome.EXTREME_HILLS.id, EnumBiome.EXTREME_HILLS_PLUS.id, EnumBiome.EXTREME_HILLS_M.id, EnumBiome.EXTREME_HILLS_PLUS_M.id, EnumBiome.STONE_BEACH.id, EnumBiome.EXTREME_HILLS_EDGE.id);
        setBiomeClimate(Climate.FOREST, EnumBiome.FOREST.id, EnumBiome.FOREST_HILLS.id, EnumBiome.FLOWER_FOREST.id, EnumBiome.ROOFED_FOREST.id, EnumBiome.ROOFED_FOREST_M.id);
        setBiomeClimate(Climate.BIRCH_FOREST, EnumBiome.BIRCH_FOREST.id, EnumBiome.BIRCH_FOREST_HILLS.id, EnumBiome.BIRCH_FOREST_M.id, EnumBiome.BIRCH_FOREST_HILLS_M.id);
        setBiomeClimate(Climate.TAIGA, EnumBiome.TAIGA.id, EnumBiome.TAIGA_HILLS.id, EnumBiome.TAIGA_M.id, EnumBiome.MEGA_SPRUCE_TAIGA.id);//, EnumBiome.MEGA_SPRUCE_TAIGA_HILLS.id
        setBiomeClimate(Climate.SWAMPLAND, EnumBiome.SWAMP.id, EnumBiome.SWAMPLAND_M.id);
        setBiomeClimate(Climate.ICE_PLAINS, EnumBiome.ICE_PLAINS.id, EnumBiome.ICE_PLAINS_SPIKES.id, EnumBiome.FROZEN_RIVER.id, EnumBiome.FROZEN_OCEAN.id);//, EnumBiome.ICE_MOUNTAINS.id
        setBiomeClimate(Climate.MUSHROOM, EnumBiome.MUSHROOM_ISLAND.id, EnumBiome.MUSHROOM_ISLAND_SHORE.id);
        setBiomeClimate(Climate.COLD_BEACH, EnumBiome.COLD_BEACH.id);
        setBiomeClimate(Climate.JUNGLE, EnumBiome.JUNGLE.id, EnumBiome.JUNGLE_HILLS.id, EnumBiome.JUNGLE_M.id);
        setBiomeClimate(Climate.JUNGLE_EDGE, EnumBiome.JUNGLE_EDGE.id, EnumBiome.JUNGLE_EDGE_M.id);
        setBiomeClimate(Climate.COLD_TAIGA, EnumBiome.COLD_TAIGA.id, EnumBiome.COLD_TAIGA_HILLS.id, EnumBiome.COLD_TAIGA_M.id);
        setBiomeClimate(Climate.MEGA_TAIGA, EnumBiome.MEGA_TAIGA.id, EnumBiome.MEGA_TAIGA_HILLS.id);
        setBiomeClimate(Climate.SAVANNA, EnumBiome.SAVANNA.id);
        setBiomeClimate(Climate.SAVANNA_MOUNTAINS, EnumBiome.SAVANNA_M.id);
        setBiomeClimate(Climate.SAVANNA_PLATEAU, EnumBiome.SAVANNA_PLATEAU.id);
        setBiomeClimate(Climate.SAVANNA_PLATEAU_MOUNTAINS, EnumBiome.SAVANNA_PLATEAU_M.id);
        setBiomeClimate(Climate.SKY);//, EnumBiome.THE_END.id, EnumBiome.SMALL_END_ISLANDS.id, EnumBiome.END_MIDLANDS.id, EnumBiome.END_HIGHLANDS.id, EnumBiome.END_BARRENS.id

        noiseGen = new SimplexOctaveGenerator(new NukkitRandom(1234), 1);
        noiseGen.setScale(1 / 8.0D);
    }

    public static double getTemperature(int biome) {
        return CLIMATE_MAP.get(biome).getTemperature();
    }

    public static double getHumidity(int biome) {
        return CLIMATE_MAP.get(biome).getHumidity();
    }

    public static boolean isWet(int biome) {
        return getHumidity(biome) > 0.85D;
    }

    public static boolean isCold(int biome, int x, int y, int z) {
        return getVariatedTemperature(biome, x, y, z) < 0.15D;
    }

    public static boolean isRainy(int biome, int x, int y, int z) {
        boolean rainy = CLIMATE_MAP.get(biome).isRainy();
        return rainy && !isCold(biome, x, y, z);
    }

    public static boolean isSnowy(int biome, int x, int y, int z) {
        boolean rainy = CLIMATE_MAP.get(biome).isRainy();
        return rainy && isCold(biome, x, y, z);
    }

    private static double getVariatedTemperature(int biome, int x, int y, int z) {
        double temp = CLIMATE_MAP.get(biome).getTemperature();
        if (y > 64) {
            double variation = noiseGen.noise(x, z, 0.5D, 2.0D) * 4.0D;
            return temp - (variation + (y - 64)) * 0.05D / 30.0D;
        } else {
            return temp;
        }
    }

    private static void setBiomeClimate(Climate temp, int... biomes) {
        for (int biome : biomes) {
            CLIMATE_MAP.put(biome, temp);
        }
    }

    private static class Climate {

        public static final Climate DEFAULT = new Climate(0.5D, 0.5D, true);
        public static final Climate PLAINS = new Climate(0.8D, 0.4D, true);
        public static final Climate DESERT = new Climate(2.0D, 0.0D, false);
        public static final Climate EXTREME_HILLS = new Climate(0.2D, 0.3D, true);
        public static final Climate FOREST = new Climate(0.7D, 0.8D, true);
        public static final Climate BIRCH_FOREST = new Climate(0.6D, 0.6D, true);
        public static final Climate TAIGA = new Climate(0.25D, 0.8D, true);
        public static final Climate SWAMPLAND = new Climate(0.8D, 0.9D, true);
        public static final Climate ICE_PLAINS = new Climate(0.0D, 0.5D, true);
        public static final Climate MUSHROOM = new Climate(0.9D, 1.0D, true);
        public static final Climate COLD_BEACH = new Climate(0.05D, 0.3D, true);
        public static final Climate JUNGLE = new Climate(0.95D, 0.9D, true);
        public static final Climate JUNGLE_EDGE = new Climate(0.95D, 0.8D, true);
        public static final Climate COLD_TAIGA = new Climate(-0.5D, 0.4D, true);
        public static final Climate MEGA_TAIGA = new Climate(0.3D, 0.8D, true);
        public static final Climate SAVANNA = new Climate(1.2D, 0.0D, false);
        public static final Climate SAVANNA_MOUNTAINS = new Climate(1.1D, 0.0D, false);
        public static final Climate SAVANNA_PLATEAU = new Climate(1.0D, 0.0D, false);
        public static final Climate SAVANNA_PLATEAU_MOUNTAINS = new Climate(0.5D, 0.0D, false);
        public static final Climate SKY = new Climate(0.5D, 0.5D, false);

        private final double temperature;
        private final double humidity;
        private final boolean rainy;

        Climate(double temperature, double humidity, boolean rainy) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.rainy = rainy;
        }

        public double getTemperature() {
            return this.temperature;
        }

        public double getHumidity() {
            return this.humidity;
        }

        public boolean isRainy() {
            return this.rainy;
        }
    }
}
