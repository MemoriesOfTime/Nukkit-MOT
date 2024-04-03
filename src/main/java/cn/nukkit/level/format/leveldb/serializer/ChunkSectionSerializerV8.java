package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import io.netty.buffer.ByteBuf;

public class ChunkSectionSerializerV8 implements ChunkSectionSerializer {

    public static final ChunkSectionSerializer INSTANCE = new ChunkSectionSerializerV8();

    @Override
    public void serializer(ByteBuf byteBuf, StateBlockStorage[] storages, int ySection) {
        byteBuf.writeByte(storages.length);
        for (StateBlockStorage storage : storages) {
            storage.writeToStorage(byteBuf);
        }
    }

    @Override
    public StateBlockStorage[] deserialize(ByteBuf byteBuf, ChunkBuilder chunkBuilder) {
        int layerCount = byteBuf.readUnsignedByte();
        StateBlockStorage[] storages = new StateBlockStorage[Math.max(layerCount, 2)];
        for (int layer = 0; layer < layerCount; ++layer) {
            storages[layer] = new StateBlockStorage();
            storages[layer].readFromStorage(byteBuf, chunkBuilder);
        }
        return storages;
    }
}

