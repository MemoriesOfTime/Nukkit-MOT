package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.block.o;
import cn.nukkit.level.format.anvil.util.NibbleArray;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.serializer.a;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import io.netty.buffer.ByteBuf;

import java.lang.invoke.MethodHandles;

public class ChunkSectionSerializerV7 implements ChunkSectionSerializer {

    public static final ChunkSectionSerializer INSTANCE = new ChunkSectionSerializerV7();
    private static final long b;

    @Override
    public void a(ByteBuf byteBuf, StateBlockStorage[] stateBlockStorageArray, int n2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StateBlockStorage[] a(ByteBuf byteBuf, ChunkBuilder chunkBuilder, long l2) {
        long l3 = l2 ^ 0x28374F8EB048L;
        byte[] byArray = new byte[4096];
        byteBuf.readBytes(byArray);
        byte[] byArray2 = new byte[2048];
        try {
            byteBuf.readBytes(byArray2);
            if (byteBuf.isReadable(4096)) {
                byteBuf.skipBytes(4096);
            }
        }
        catch (UnsupportedOperationException unsupportedOperationException) {
            throw ChunkSectionSerializerV7.a(unsupportedOperationException);
        }
        StateBlockStorage[] stateBlockStorageArray = new StateBlockStorage[2];
        stateBlockStorageArray[0] = ChunkSectionSerializerV7.a(byArray, byArray2, l3);
        return stateBlockStorageArray;
    }

    private static StateBlockStorage a(byte[] byArray, byte[] byArray2, long l2) {
        long l3 = l2 = b ^ l2;
        long l4 = l3 ^ 0x2D3D7EE67622L;
        long l5 = l3 ^ 0x6A752300A303L;
        NibbleArray nibbleArray = new NibbleArray(byArray2);
        StateBlockStorage stateBlockStorage = new StateBlockStorage();
        try {
            for (int i2 = 0; i2 < 4096; ++i2) {
                stateBlockStorage.a(i2, BlockStateMapping.get().b(byArray[i2], nibbleArray.get(i2), l5), l4);
            }
        }
        catch (UnsupportedOperationException unsupportedOperationException) {
            throw ChunkSectionSerializerV7.a(unsupportedOperationException);
        }
        return stateBlockStorage;
    }

    static {
        b = o.a(-2719066120239241990L, 3081627049135516087L, MethodHandles.lookup().lookupClass()).a(56374457200807L);
        a = new ChunkSectionSerializerV7();
    }

    private static UnsupportedOperationException a(UnsupportedOperationException unsupportedOperationException) {
        return unsupportedOperationException;
    }
}

