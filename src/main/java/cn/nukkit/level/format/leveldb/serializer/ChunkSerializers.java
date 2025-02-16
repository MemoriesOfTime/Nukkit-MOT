package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

public class ChunkSerializers {

    private static final IntObjectMap<ChunkSerializer> serializers = new IntObjectHashMap<>();

    static {
        /*serializers.put(0, ChunkSerializerV1.INSTANCE);
        serializers.put(1, ChunkSerializerV1.INSTANCE);
        serializers.put(2, ChunkSerializerV1.INSTANCE);*/

        serializers.put(3, ChunkSerializerV3.INSTANCE);
        serializers.put(4, ChunkSerializerV3.INSTANCE);
        serializers.put(5, ChunkSerializerV3.INSTANCE);
        serializers.put(6, ChunkSerializerV3.INSTANCE);
        serializers.put(7, ChunkSerializerV3.INSTANCE);
        serializers.put(8, ChunkSerializerV3.INSTANCE);
        serializers.put(9, ChunkSerializerV3.INSTANCE);
        serializers.put(10, ChunkSerializerV3.INSTANCE);
        serializers.put(11, ChunkSerializerV3.INSTANCE);
        serializers.put(12, ChunkSerializerV3.INSTANCE);
        serializers.put(13, ChunkSerializerV3.INSTANCE);
        serializers.put(14, ChunkSerializerV3.INSTANCE);
        serializers.put(15, ChunkSerializerV3.INSTANCE);
        serializers.put(16, ChunkSerializerV3.INSTANCE);
        serializers.put(17, ChunkSerializerV3.INSTANCE);
        serializers.put(18, ChunkSerializerV3.INSTANCE);
        serializers.put(19, ChunkSerializerV3.INSTANCE);
        serializers.put(20, ChunkSerializerV3.INSTANCE);
        serializers.put(21, ChunkSerializerV3.INSTANCE);
        serializers.put(22, ChunkSerializerV3.INSTANCE);
        serializers.put(23, ChunkSerializerV3.INSTANCE);
        serializers.put(24, ChunkSerializerV3.INSTANCE);
        serializers.put(25, ChunkSerializerV3.INSTANCE);
        serializers.put(26, ChunkSerializerV3.INSTANCE);
        serializers.put(27, ChunkSerializerV3.INSTANCE);
        serializers.put(28, ChunkSerializerV3.INSTANCE);
        serializers.put(29, ChunkSerializerV3.INSTANCE);
        serializers.put(30, ChunkSerializerV3.INSTANCE);
        serializers.put(31, ChunkSerializerV3.INSTANCE);
        serializers.put(32, ChunkSerializerV3.INSTANCE);
        serializers.put(33, ChunkSerializerV3.INSTANCE);
        serializers.put(34, ChunkSerializerV3.INSTANCE);
        serializers.put(35, ChunkSerializerV3.INSTANCE);
        serializers.put(36, ChunkSerializerV3.INSTANCE);
        serializers.put(37, ChunkSerializerV3.INSTANCE);
        serializers.put(38, ChunkSerializerV3.INSTANCE);
        serializers.put(39, ChunkSerializerV3.INSTANCE);
        serializers.put(40, ChunkSerializerV3.INSTANCE);
        serializers.put(41, ChunkSerializerV3.INSTANCE);
    }

    private static ChunkSerializer getChuckSerializer(int chunkVersion) {
        ChunkSerializer chunkSerializer = serializers.get(chunkVersion);
        if (chunkSerializer == null) {
            throw new IllegalArgumentException("Unknown chunk version " + chunkVersion);
        }
        return chunkSerializer;
    }

    public static void serializeChunk(WriteBatch writeBatch, Chunk chunk, int chunkVersion) {
        getChuckSerializer(chunkVersion).serializer(writeBatch, chunk);
    }

    public static void deserializeChunk(DB db, ChunkBuilder chunkBuilder, int chunkVersion) {
        getChuckSerializer(chunkVersion).deserialize(db, chunkBuilder);
    }

}
