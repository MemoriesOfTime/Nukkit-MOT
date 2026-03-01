package cn.nukkit.block.custom.serializer;

import cn.nukkit.block.custom.serializer.impl.CustomBlockDefinitionSerializer843;
import cn.nukkit.block.custom.serializer.impl.CustomBlockDefinitionSerializer898;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * Base class for multi-version protocol serializers of custom block definitions.
 * <p>
 * Adapted from Lumi project (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 */
public class CustomBlockDefinitionSerializer {

    public CompoundTag serialize(CompoundTag nbt) {
        reSerializeMaterials(nbt);
        reSerializeCollisionBox(nbt);
        return nbt;
    }

    protected void reSerializeMaterials(CompoundTag nbt) {
    }

    protected void reSerializeCollisionBox(CompoundTag nbt) {
    }

    public static CompoundTag serialize(CompoundTag nbt, int protocol) {
        if (protocol >= ProtocolInfo.v1_21_130) {
            return CustomBlockDefinitionSerializer898.INSTANCE.serialize(nbt.clone());
        }
        if (protocol >= ProtocolInfo.v1_21_110_26) {
            return CustomBlockDefinitionSerializer843.INSTANCE.serialize(nbt.clone());
        }
        return nbt;
    }
}
