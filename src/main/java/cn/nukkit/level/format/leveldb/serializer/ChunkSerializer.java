package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

public interface ChunkSerializer {

    LevelDBChunkSection deserialize(DB db);

    void serializer(WriteBatch writeBatch, LevelDBChunkSection subChunk);

}
