package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import io.netty.buffer.ByteBuf;

public class ChunkSectionSerializerV1 implements ChunkSectionSerializer {

    public static final ChunkSectionSerializer INSTANCE = new ChunkSectionSerializerV1();

    @Override
    public void a(ByteBuf byteBuf, StateBlockStorage[] stateBlockStorageArray, int n2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StateBlockStorage[] a(ByteBuf byteBuf, ChunkBuilder chunkBuilder, long l2) {
        long l3 = l2 ^ 0x21DF810E46E0L;
        StateBlockStorage[] stateBlockStorageArray = new StateBlockStorage[2];
        stateBlockStorageArray[0] = new StateBlockStorage();
        stateBlockStorageArray[0].a(byteBuf, chunkBuilder, l3);
        return stateBlockStorageArray;
    }
}

