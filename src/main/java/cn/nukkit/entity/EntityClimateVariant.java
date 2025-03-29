package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Ported from PowerNukkitX
 */
public interface EntityClimateVariant {

    String PROPERTY_STATE = "minecraft:climate_variant";

    StringTag TAG_SPAWNS_WARM_VARIANT_FARM_ANIMALS = new StringTag("", "spawns_warm_variant_farm_animals");
    StringTag TAG_SPAWNS_COLD_VARIANT_FARM_ANIMALS = new StringTag("", "spawns_cold_variant_farm_animals");

    default Variant getBiomeVariant(int biomeId) {
        CompoundTag biomeDefinitions = Biome.getBiomeDefinitions(biomeId);
        if (biomeDefinitions != null) {
            ListTag<StringTag> tags = biomeDefinitions.getList("tags", StringTag.class);
            if (tags.contains(TAG_SPAWNS_WARM_VARIANT_FARM_ANIMALS)) {
                return Variant.WARM;
            } else if (tags.contains(TAG_SPAWNS_COLD_VARIANT_FARM_ANIMALS)) {
                return Variant.COLD;
            }
        }

        return Variant.TEMPERATE;
    }

    @Nullable
    default Variant getVariant() {
        if (this instanceof Entity entity) {
            String var = entity.getEnumEntityProperty(PROPERTY_STATE);
            if (var == null) return null;
            return Arrays.stream(Variant.VALUES).filter(variant -> variant.getName().equals(var)).findFirst().get();
        }
        return null;
    }

    default void setVariant(Variant variant) {
        if (this instanceof Entity entity) {
            entity.setEnumEntityProperty(PROPERTY_STATE, variant.getName());
            entity.sendData(entity.getViewers().values().toArray(Player[]::new));
            entity.namedTag.putString("variant", variant.getName());
        }
    }

    enum Variant {

        TEMPERATE("temperate"),
        WARM("warm"),
        COLD("cold");

        @Getter
        private final String name;

        Variant(String s) {
            name = s;
        }

        public static Variant get(String name) {
            return Arrays.stream(Variant.VALUES).filter(variant -> variant.getName().equals(name)).findFirst().get();
        }

        public final static Variant[] VALUES = values();
    }

}