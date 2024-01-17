package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import io.netty.buffer.ByteBuf;

public class ChunkSectionSerializerV8 implements ChunkSectionSerializer {

    public static final ChunkSectionSerializer INSTANCE = new ChunkSectionSerializerV8();

    @Override
    public void a(ByteBuf byteBuf, StateBlockStorage[] stateBlockStorageArray, int n2) {
        byteBuf.writeByte(stateBlockStorageArray.length);
        for (StateBlockStorage stateBlockStorage : stateBlockStorageArray) {
            stateBlockStorage.writeTo(byteBuf);
        }
    }

    @Override
    public StateBlockStorage[] a(ByteBuf byteBuf, ChunkBuilder chunkBuilder, long l2) {
        long l3 = l2 ^ 0x21DF810E46E0L;
        int n2 = byteBuf.readUnsignedByte();
        StateBlockStorage[] stateBlockStorageArray = new StateBlockStorage[Math.max(n2, 2)];
        for (int i2 = 0; i2 < n2; ++i2) {
            stateBlockStorageArray[i2] = new StateBlockStorage();
            stateBlockStorageArray[i2].a(byteBuf, chunkBuilder, l3);
        }
        return stateBlockStorageArray;
    }
}

