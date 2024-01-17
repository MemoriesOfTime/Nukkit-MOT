package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

/**
 * @author LT_Name
 */
public class ChunkSerializerV1 implements ChunkSerializer {

    public static final ChunkSerializer INSTANCE = new ChunkSerializerV1();

    @Override
    public void serializer(WriteBatch writeBatch, Chunk chunk) {
        //TODO
    }

    @Override
    public void deserialize(DB db, ChunkBuilder chunkBuilder) {
        //TODO
    }

}
