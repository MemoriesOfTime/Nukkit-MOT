package cn.nukkit.block.custom.serializer.impl;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.Tag;

import java.util.Map;

/**
 * v975+ (1.26.20): ambient_occlusion(boolean) -> float(0.0-10.0)
 */
public class CustomBlockDefinitionSerializer975 extends CustomBlockDefinitionSerializer898 {

    public static final CustomBlockDefinitionSerializer975 INSTANCE = new CustomBlockDefinitionSerializer975();

    @Override
    protected void reSerializeMaterials(CompoundTag nbt) {
        super.reSerializeMaterials(nbt);
        convertAmbientOcclusion(nbt.getCompound("components"));

        if (nbt.contains("permutations")) {
            for (Tag tag : nbt.getList("permutations", CompoundTag.class).getAll()) {
                CompoundTag permutation = (CompoundTag) tag;
                if (permutation.contains("components")) {
                    convertAmbientOcclusion(permutation.getCompound("components"));
                }
            }
        }
    }

    private static void convertAmbientOcclusion(CompoundTag components) {
        if (!components.contains("minecraft:material_instances")) {
            return;
        }

        CompoundTag materialInstances = components.getCompound("minecraft:material_instances");
        if (!materialInstances.contains("materials")) {
            return;
        }

        CompoundTag materials = materialInstances.getCompound("materials");
        for (Map.Entry<String, Tag> entry : materials.getTags().entrySet()) {
            if (entry.getValue() instanceof CompoundTag material) {
                if (material.contains("ambient_occlusion") && !material.contains("ambient_occlusion", FloatTag.class)) {
                    material.putFloat("ambient_occlusion", material.getBoolean("ambient_occlusion") ? 1.0f : 0.0f);
                }
            }
        }
    }
}
