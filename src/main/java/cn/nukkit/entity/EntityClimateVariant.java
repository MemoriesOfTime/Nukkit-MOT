package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.level.biome.EnumBiome;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;

/**
 * Ported from PowerNukkitX
 */
@Log4j2
public class EntityClimateVariant {

    private static final String PROPERTY_STATE = "minecraft:climate_variant";

    private static final List<Integer> coldTags = Arrays.asList(EnumBiome.TAIGA.id, EnumBiome.TAIGA_M.id, EnumBiome.TAIGA_HILLS.id, EnumBiome.COLD_TAIGA_HILLS.id, EnumBiome.COLD_TAIGA.id, EnumBiome.MEGA_TAIGA.id, EnumBiome.MEGA_TAIGA_HILLS.id, EnumBiome.COLD_TAIGA_M.id, EnumBiome.MEGA_SPRUCE_TAIGA.id, EnumBiome.MEGA_SPRUCE_TAIGA_HILLS.id, EnumBiome.EXTREME_HILLS.id, EnumBiome.EXTREME_HILLS_EDGE.id, EnumBiome.EXTREME_HILLS_PLUS.id, EnumBiome.FROZEN_OCEAN.id, EnumBiome.FROZEN_RIVER.id, EnumBiome.ICE_PLAINS.id, EnumBiome.ICE_MOUNTAINS.id, EnumBiome.COLD_BEACH.id, EnumBiome.EXTREME_HILLS_M.id, EnumBiome.EXTREME_HILLS_PLUS_M.id);
    private static final List<Integer> warmTags = Arrays.asList(EnumBiome.SAVANNA.id, EnumBiome.JUNGLE.id, EnumBiome.MESA.id, EnumBiome.DESERT.id, EnumBiome.SWAMP.id, EnumBiome.SWAMPLAND_M.id, EnumBiome.SAVANNA_PLATEAU.id, EnumBiome.SAVANNA_M.id, EnumBiome.SAVANNA_PLATEAU_M.id, EnumBiome.JUNGLE_HILLS.id, EnumBiome.JUNGLE_EDGE.id, EnumBiome.JUNGLE_M.id, EnumBiome.JUNGLE_EDGE_M.id, EnumBiome.MESA_PLATEAU_F.id, EnumBiome.MESA_PLATEAU.id, EnumBiome.MESA_PLATEAU_F_M.id, EnumBiome.MESA_PLATEAU_M.id, EnumBiome.WARM_OCEAN.id, EnumBiome.DEEP_WARM_OCEAN.id, EnumBiome.LUKEWARM_OCEAN.id, EnumBiome.DEEP_LUKEWARM_OCEAN.id, EnumBiome.DESERT_HILLS.id, EnumBiome.DESERT_M.id);

    public static Variant getBiomeVariant(int biomeId) {
        if(coldTags.contains(biomeId)) {
            return Variant.COLD;
        }
        if(warmTags.contains(biomeId)) {
            return Variant.WARM;
        }
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