package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import java.util.function.Consumer;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

public interface ChunkSerializer {

    void serializer(WriteBatch writeBatch, Chunk chunk);

    default void serializer(WriteBatch writeBatch, Chunk chunk, Consumer<LevelDBChunkSection.SaveToken> snapshots) {
        serializer(writeBatch, chunk);
    }

    void deserialize(DB db, ChunkBuilder chunkBuilder);

}
