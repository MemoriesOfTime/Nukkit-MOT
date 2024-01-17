package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import io.netty.buffer.ByteBuf;

public interface ChunkSectionSerializer {

    void serializer(ByteBuf byteBuf, StateBlockStorage[] storages, int ySection);

    StateBlockStorage[] deserialize(ByteBuf byteBuf, ChunkBuilder chunkBuilder);

}
