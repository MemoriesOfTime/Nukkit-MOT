package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import io.netty.buffer.ByteBuf;

public class ChunkSectionSerializerV9 implements ChunkSectionSerializer {

    public static final ChunkSectionSerializer INSTANCE = new ChunkSectionSerializerV9();

    @Override
    public void serializer(ByteBuf byteBuf, StateBlockStorage[] storages, int ySection) {
        byteBuf.writeByte(storages.length);
        byteBuf.writeByte(ySection);
        for (StateBlockStorage storage : storages) {
            storage.writeTo(byteBuf);
        }
    }

    @Override
    public StateBlockStorage[] deserialize(ByteBuf byteBuf, ChunkBuilder chunkBuilder) {
        int layerCount = byteBuf.readUnsignedByte();
        byteBuf.readUnsignedByte(); //sectionY not use
        StateBlockStorage[] storages = new StateBlockStorage[Math.max(layerCount, 2)];
        for (int layer = 0; layer < layerCount; ++layer) {
            storages[layer] = new StateBlockStorage();
            storages[layer].ofBlock(byteBuf, chunkBuilder);
        }
        return storages;
    }
}

