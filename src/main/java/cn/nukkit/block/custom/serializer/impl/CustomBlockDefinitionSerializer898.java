package cn.nukkit.block.custom.serializer.impl;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;

/**
 * v898+ (1.21.130): collision_box origin/size -> boxes(min/max)
 * <p>
 * Adapted from Lumi project (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 */
public class CustomBlockDefinitionSerializer898 extends CustomBlockDefinitionSerializer843 {

    public static final CustomBlockDefinitionSerializer898 INSTANCE = new CustomBlockDefinitionSerializer898();

    @Override
    protected void reSerializeCollisionBox(CompoundTag nbt) {
        CompoundTag components = nbt.getCompound("components");
        convertCollisionBox(components);

        if (nbt.contains("permutations")) {
            for (Tag tag : nbt.getList("permutations", CompoundTag.class).getAll()) {
                CompoundTag permutation = (CompoundTag) tag;
                if (permutation.contains("components")) {
                    convertCollisionBox(permutation.getCompound("components"));
                }
            }
        }
    }

    private static void convertCollisionBox(CompoundTag components) {
        if (!components.contains("minecraft:collision_box")) {
            return;
        }

        CompoundTag collisionBox = components.getCompound("minecraft:collision_box");
        if (!collisionBox.contains("origin") || !collisionBox.contains("size")) {
            return;
        }

        ListTag<FloatTag> origin = collisionBox.getList("origin", FloatTag.class);
        ListTag<FloatTag> size = collisionBox.getList("size", FloatTag.class);

        if (origin.isEmpty() || size.isEmpty()) {
            return;
        }

        float originX = origin.get(0).parseValue();
        float originY = origin.get(1).parseValue();
        float originZ = origin.get(2).parseValue();
        float sizeX = size.get(0).parseValue();
        float sizeY = size.get(1).parseValue();
        float sizeZ = size.get(2).parseValue();

        float minX = originX + 8f;
        float minY = originY;
        float minZ = originZ + 8f;
        float maxX = minX + sizeX;
        float maxY = minY + sizeY;
        float maxZ = minZ + sizeZ;

        CompoundTag box = new CompoundTag()
                .putFloat("minX", minX)
                .putFloat("minY", minY)
                .putFloat("minZ", minZ)
                .putFloat("maxX", maxX)
                .putFloat("maxY", maxY)
                .putFloat("maxZ", maxZ);

        collisionBox.remove("origin");
        collisionBox.remove("size");
        collisionBox.putList("boxes", new ListTag<CompoundTag>().add(box));
    }
}
