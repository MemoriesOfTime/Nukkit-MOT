package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import com.nukkitx.network.util.Preconditions;
import io.netty.buffer.ByteBuf;

public class ChunkSectionSerializers {
    private static final ChunkSectionSerializer[] serializers;

    static {
        serializers = new ChunkSectionSerializer[10];
        serializers[0] = ChunkSectionSerializerV7.INSTANCE;

        serializers[1] = ChunkSectionSerializerV1.INSTANCE;
        serializers[2] = ChunkSectionSerializerV7.INSTANCE;
        serializers[3] = ChunkSectionSerializerV7.INSTANCE;
        serializers[4] = ChunkSectionSerializerV7.INSTANCE;
        serializers[5] = ChunkSectionSerializerV7.INSTANCE;
        serializers[6] = ChunkSectionSerializerV7.INSTANCE;
        serializers[7] = ChunkSectionSerializerV7.INSTANCE;
        serializers[8] = ChunkSectionSerializerV8.INSTANCE;
        serializers[9] = ChunkSectionSerializerV9.INSTANCE;
    }

    public static void serializer(ByteBuf byteBuf, StateBlockStorage[] stateBlockStorageArray, int ySection, int version) {
        ChunkSectionSerializers.getChunkSectionSerializer(version).serializer(byteBuf, stateBlockStorageArray, ySection);
    }

    public static StateBlockStorage[] deserialize(ByteBuf byteBuf, ChunkBuilder chunkBuilder, int version) {
        return ChunkSectionSerializers.getChunkSectionSerializer(version).deserialize(byteBuf, chunkBuilder);
    }

    public static ChunkSectionSerializer getChunkSectionSerializer(int version) {
        Preconditions.checkElementIndex(version, serializers.length, "ChunkSectionSerializers invalid version: " + version);
        ChunkSectionSerializer serializer = serializers[version];
        Preconditions.checkNotNull(serializer, "ChunkSectionSerializers invalid version: " + version);
        return serializer;
    }
}

