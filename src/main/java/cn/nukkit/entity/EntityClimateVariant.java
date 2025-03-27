package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.level.biome.EnumBiome;
import lombok.Getter;
import java.util.Arrays;
import java.util.List;

/**
 * Ported from PowerNukkitX
 */
public class EntityClimateVariant {

    private static String PROPERTY_STATE = "minecraft:climate_variant";

    private static List<EnumBiome> coldTags = Arrays.asList(EnumBiome.TAIGA, EnumBiome.TAIGA_M, EnumBiome.TAIGA_HILLS, EnumBiome.COLD_TAIGA_HILLS, EnumBiome.COLD_TAIGA, EnumBiome.MEGA_TAIGA, EnumBiome.MEGA_TAIGA_HILLS, EnumBiome.COLD_TAIGA_M, EnumBiome.MEGA_SPRUCE_TAIGA, EnumBiome.MEGA_SPRUCE_TAIGA_HILLS, EnumBiome.EXTREME_HILLS, EnumBiome.EXTREME_HILLS_EDGE, EnumBiome.EXTREME_HILLS_PLUS, EnumBiome.FROZEN_OCEAN, EnumBiome.FROZEN_RIVER, EnumBiome.ICE_PLAINS, EnumBiome.ICE_MOUNTAINS, EnumBiome.COLD_BEACH, EnumBiome.EXTREME_HILLS_M, EnumBiome.EXTREME_HILLS_PLUS_M);
    private static List<EnumBiome> warmTags = Arrays.asList(EnumBiome.SAVANNA, EnumBiome.JUNGLE, EnumBiome.MESA, EnumBiome.DESERT, EnumBiome.SWAMP, EnumBiome.SWAMPLAND_M, EnumBiome.SAVANNA_PLATEAU, EnumBiome.SAVANNA_M, EnumBiome.SAVANNA_PLATEAU_M, EnumBiome.JUNGLE_HILLS, EnumBiome.JUNGLE_EDGE, EnumBiome.JUNGLE_M, EnumBiome.JUNGLE_EDGE_M, EnumBiome.MESA_PLATEAU_F, EnumBiome.MESA_PLATEAU, EnumBiome.MESA_PLATEAU_F_M, EnumBiome.MESA_PLATEAU_M, EnumBiome.WARM_OCEAN, EnumBiome.DEEP_WARM_OCEAN, EnumBiome.LUKEWARM_OCEAN, EnumBiome.DEEP_LUKEWARM_OCEAN, EnumBiome.DESERT_HILLS, EnumBiome.DESERT_M);

    public static Variant getBiomeVariant(int biomeId) {
        if(coldTags.contains(EnumBiome.getBiome(biomeId))) return Variant.COLD;
        if(warmTags.contains(EnumBiome.getBiome(biomeId))) return Variant.WARM;
        return Variant.TEMPERATE;
    }

    public static Variant getVariant(Entity entity) {
        String var = entity.getEnumEntityProperty(PROPERTY_STATE);
        if(var == null) return null;
        return Arrays.stream(Variant.VALUES).filter(variant -> variant.getName().equals(var)).findFirst().get();
    }

    public static void setVariant(Entity entity, Variant variant) {
        entity.setEnumEntityProperty(PROPERTY_STATE, variant.getName());
        entity.sendData(entity.getViewers().values().toArray(Player[]::new));
        entity.namedTag.putString("variant", variant.getName());
    }

    public enum Variant {

        TEMPERATE("temperate"),
        WARM("warm"),
        COLD("cold");

        @Getter private final String name;
        Variant(String s) {
            name = s;
        }

        public static Variant get(String name) {
            return Arrays.stream(Variant.VALUES).filter(variant -> variant.getName().equals(name)).findFirst().get();
        }

        public final static Variant[] VALUES = values();
    }

}