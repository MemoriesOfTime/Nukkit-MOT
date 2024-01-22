package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import io.netty.buffer.ByteBuf;

public class ChunkSectionSerializerV1 implements ChunkSectionSerializer {

    public static final ChunkSectionSerializer INSTANCE = new ChunkSectionSerializerV1();

    @Override
    public void serializer(ByteBuf byteBuf, StateBlockStorage[] storages, int ySection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StateBlockStorage[] deserialize(ByteBuf byteBuf, ChunkBuilder chunkBuilder) {
        StateBlockStorage[] storages = new StateBlockStorage[2];
        storages[0] = StateBlockStorage.ofBlock(byteBuf);
        return storages;
    }
}

