package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.anvil.util.NibbleArray;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import io.netty.buffer.ByteBuf;

import static cn.nukkit.level.format.leveldb.LevelDbConstants.SUB_CHUNK_SIZE;

public class ChunkSectionSerializerV7 implements ChunkSectionSerializer {

    public static final ChunkSectionSerializer INSTANCE = new ChunkSectionSerializerV7();

    @Override
    public void serializer(ByteBuf byteBuf, StateBlockStorage[] storages, int ySection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StateBlockStorage[] deserialize(ByteBuf byteBuf, ChunkBuilder chunkBuilder) {
        byte[] blocks = new byte[4096];
        byteBuf.readBytes(blocks);

        byte[] blockData = new byte[2048];
        byteBuf.readBytes(blockData);

        if (byteBuf.isReadable(4096)) {
            byteBuf.skipBytes(4096);
        }

        StateBlockStorage[] storages = new StateBlockStorage[2];
        storages[0] = ChunkSectionSerializerV7.deserialize(blocks, blockData);
        return storages;
    }

    private static StateBlockStorage deserialize(byte[] blocks, byte[] blockDataArray) {
        NibbleArray blockData = new NibbleArray(blockDataArray);
        StateBlockStorage storage = new StateBlockStorage();
        for (int i = 0; i < SUB_CHUNK_SIZE; ++i) {
            storage.set(i, BlockStateMapping.get().getBlockState(blocks[i] << Block.DATA_BITS, blockData.get(i)));
        }
        return storage;
    }
}

