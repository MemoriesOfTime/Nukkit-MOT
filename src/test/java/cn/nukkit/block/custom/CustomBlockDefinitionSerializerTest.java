package cn.nukkit.block.custom;

import cn.nukkit.GameVersion;
import cn.nukkit.block.custom.container.data.Materials;
import cn.nukkit.block.custom.serializer.CustomBlockDefinitionSerializer;
import cn.nukkit.nbt.tag.ByteTag;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自定义方块定义序列化器的多版本边界测试：face_dimming -> packed_bools (843+)、ambient_occlusion -> float (975+)。
 * <p>
 * Multi-version boundary tests for the custom block definition serializer: face_dimming -> packed_bools (843+), ambient_occlusion -> float (975+).
 */
class CustomBlockDefinitionSerializerTest {

    @Test
    void legacyBelow843KeepsBooleanFields() {
        CompoundTag out = CustomBlockDefinitionSerializer.serialize(buildNbt(), ProtocolInfo.v1_21_100);

        CompoundTag star = material(out, "components", "*");
        assertInstanceOf(ByteTag.class, star.get("ambient_occlusion"));
        assertTrue(star.getBoolean("ambient_occlusion"));
        assertTrue(star.contains("face_dimming"));
        assertFalse(star.contains("packed_bools"));

        CompoundTag up = material(out, "components", "up");
        assertInstanceOf(ByteTag.class, up.get("ambient_occlusion"));
        assertFalse(up.getBoolean("ambient_occlusion"));
    }

    @Test
    void v843PacksFaceDimmingButKeepsBooleanAmbientOcclusion() {
        CompoundTag out = CustomBlockDefinitionSerializer.serialize(buildNbt(), ProtocolInfo.v1_21_110_26);

        CompoundTag star = material(out, "components", "*");
        assertEquals(1, star.getByte("packed_bools"));
        assertFalse(star.contains("face_dimming"));
        assertInstanceOf(ByteTag.class, star.get("ambient_occlusion"));

        CompoundTag up = material(out, "components", "up");
        assertEquals(0, up.getByte("packed_bools"));
    }

    @Test
    void v898InheritsMaterialConversion() {
        CompoundTag out = CustomBlockDefinitionSerializer.serialize(buildNbt(), ProtocolInfo.v1_21_130);

        CompoundTag star = material(out, "components", "*");
        assertTrue(star.contains("packed_bools"));
        assertInstanceOf(ByteTag.class, star.get("ambient_occlusion"));
    }

    @Test
    void v975ConvertsAmbientOcclusionToFloat() {
        CompoundTag nbt = buildNbt();
        CompoundTag out = CustomBlockDefinitionSerializer.serialize(nbt, ProtocolInfo.v1_26_20);

        // 不得修改调用方持有的原始 NBT / caller-owned NBT must stay untouched
        assertNotSame(nbt, out);
        assertInstanceOf(ByteTag.class, material(nbt, "components", "*").get("ambient_occlusion"));

        CompoundTag star = material(out, "components", "*");
        assertInstanceOf(FloatTag.class, star.get("ambient_occlusion"));
        assertEquals(1.0f, star.getFloat("ambient_occlusion"));
        assertTrue(star.contains("packed_bools"));

        CompoundTag up = material(out, "components", "up");
        assertEquals(0.0f, up.getFloat("ambient_occlusion"));

        CompoundTag permutationMaterial = material(out, "permutationComponents", "*");
        assertInstanceOf(FloatTag.class, permutationMaterial.get("ambient_occlusion"));
        assertEquals(1.0f, permutationMaterial.getFloat("ambient_occlusion"));
    }

    @Test
    void latestProtocolsConvertAmbientOcclusionToFloat() {
        for (int protocol : new int[]{ProtocolInfo.v1_26_20, ProtocolInfo.v1_26_40, ProtocolInfo.v1_26_44, ProtocolInfo.CURRENT_PROTOCOL}) {
            CompoundTag star = material(CustomBlockDefinitionSerializer.serialize(buildNbt(), protocol), "components", "*");
            assertInstanceOf(FloatTag.class, star.get("ambient_occlusion"), "protocol " + protocol);
        }
    }

    @Test
    void netEaseProtocolsKeepLegacyBooleanAmbientOcclusion() {
        for (GameVersion version : new GameVersion[]{GameVersion.V1_20_50_NETEASE, GameVersion.V1_21_124_NETEASE}) {
            CompoundTag out = CustomBlockDefinitionSerializer.serialize(buildNbt(), version.getProtocol());
            CompoundTag star = material(out, "components", "*");
            assertInstanceOf(ByteTag.class, star.get("ambient_occlusion"), version.toString());
        }
    }

    @Test
    void preexistingFloatAmbientOcclusionIsNotClobbered() {
        CompoundTag nbt = buildNbt();
        material(nbt, "components", "*").putFloat("ambient_occlusion", 0.5f);

        CompoundTag star = material(CustomBlockDefinitionSerializer.serialize(nbt, ProtocolInfo.v1_26_20), "components", "*");

        assertEquals(0.5f, star.getFloat("ambient_occlusion"));
    }

    private static CompoundTag buildNbt() {
        Materials materials = Materials.builder()
                .any(Materials.RenderMethod.OPAQUE, "stone")
                .up(Materials.RenderMethod.OPAQUE, false, false, "stone_top");

        CompoundTag components = new CompoundTag()
                .putCompound("minecraft:material_instances", new CompoundTag()
                        .putCompound("mappings", new CompoundTag())
                        .putCompound("materials", materials.toCompoundTag()));

        CompoundTag permutationComponents = new CompoundTag()
                .putCompound("minecraft:material_instances", new CompoundTag()
                        .putCompound("materials", Materials.builder()
                                .any(Materials.RenderMethod.BLEND, "stone_alt")
                                .toCompoundTag()));

        return new CompoundTag()
                .putCompound("components", components)
                .putList("permutations", new ListTag<CompoundTag>()
                        .add(new CompoundTag()
                                .putString("condition", "q.block_state('state') == 'alt'")
                                .putCompound("components", permutationComponents)));
    }

    private static CompoundTag material(CompoundTag root, String componentsKey, String face) {
        CompoundTag container = "permutationComponents".equals(componentsKey)
                ? ((CompoundTag) root.getList("permutations", CompoundTag.class).get(0)).getCompound("components")
                : root.getCompound("components");
        CompoundTag instance = container.getCompound("minecraft:material_instances");
        assertTrue(instance.contains("materials"), "missing materials under " + componentsKey);
        CompoundTag faceTag = instance.getCompound("materials").getCompound(face);
        assertTrue(faceTag != null && !faceTag.getTags().isEmpty(), "missing face " + face);
        return faceTag;
    }
}
