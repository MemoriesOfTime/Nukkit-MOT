package cn.nukkit.block.custom.serializer.impl;

import cn.nukkit.block.custom.serializer.CustomBlockDefinitionSerializer;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;

import java.util.Map;

/**
 * v843+ (1.21.110): face_dimming(boolean) -> packed_bools(byte)
 * <p>
 * Adapted from Lumi project (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 */
public class CustomBlockDefinitionSerializer843 extends CustomBlockDefinitionSerializer {

    public static final CustomBlockDefinitionSerializer843 INSTANCE = new CustomBlockDefinitionSerializer843();

    @Override
    protected void reSerializeMaterials(CompoundTag nbt) {
        CompoundTag components = nbt.getCompound("components");
        convertMaterialInstances(components);

        if (nbt.contains("permutations")) {
            for (Tag tag : nbt.getList("permutations", CompoundTag.class).getAll()) {
                CompoundTag permutation = (CompoundTag) tag;
                if (permutation.contains("components")) {
                    convertMaterialInstances(permutation.getCompound("components"));
                }
            }
        }
    }

    private static void convertMaterialInstances(CompoundTag components) {
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
                if (material.contains("face_dimming")) {
                    boolean faceDimming = material.getBoolean("face_dimming");
                    material.remove("face_dimming");
                    material.putByte("packed_bools", (byte) (faceDimming ? 0x1 : 0x0));
                }
                // 1.21.80+ material overhaul: the legacy "alpha_test" name is silently
                // ignored by new clients and the material falls back to opaque, so every
                // transparent texel renders black. Translate to the modern name here;
                // older clients keep the legacy name they understand.
                if (material.contains("render_method")
                        && material.getString("render_method").equals("alpha_test")) {
                    material.putString("render_method", "alpha_test_single_sided");
                }
            }
        }
    }
}
