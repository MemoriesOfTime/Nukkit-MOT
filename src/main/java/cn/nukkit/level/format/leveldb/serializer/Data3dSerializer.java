package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

public class Data3dSerializer {

    public static void serialize(WriteBatch db, LevelDBChunk chunk) {
        DimensionData dimensionData = chunk.getProvider().getLevel().getDimensionData();

        ByteBuf buffer  = ByteBufAllocator.DEFAULT.ioBuffer();
        try {
            byte[] heightMap = chunk.getHeightMapArray();
            for (int height : heightMap) {
                buffer.writeShortLE(height);
            }

            for (int i = 0; i < dimensionData.getHeight() >> 4; i++) {
                PalettedBlockStorage storage = chunk.getBiomeStorage(i);
                storage.writeToStorage(buffer);
            }

            db.put(LevelDBKey.DATA_3D.getKey(chunk.getX(), chunk.getZ(), chunk.getProvider().getLevel().getDimension()), Utils.convertByteBuf2Array(buffer));
        } finally {
            buffer.release();
        }
    }

    public static void deserialize(DB db, ChunkBuilder builder) {
        DimensionData dimensionData = builder.getDimensionData();

        byte[] data3d = db.get(LevelDBKey.DATA_3D.getKey(builder.getChunkX(), builder.getChunkZ(), dimensionData.getDimensionId()));
        if (data3d == null || data3d.length < 1) {
            return;
        }


        int[] heightMap = new int[512];
        PalettedBlockStorage[] biomes = new PalettedBlockStorage[dimensionData.getHeight() >> 4];

        ByteBuf buffer = ByteBufAllocator.DEFAULT.ioBuffer(data3d.length);
        try {
            buffer.writeBytes(data3d);
            for (int i = 0; i < 256; i++) {
                heightMap[i] = buffer.readUnsignedShortLE();
            }

            for (int i = 0; i < biomes.length; i++) {
                PalettedBlockStorage storage = readPalettedBiomes(buffer);
                if (storage == null && i == 0) {
                    throw new IllegalStateException("First biome palette can not point to previous!");
                }

                if (storage == null) {
                    storage = biomes[i - 1].copy();
                }
                biomes[i] = storage;
            }
        } finally {
            buffer.release();
        }

        builder.heightMap(heightMap);
        builder.biomes3d(biomes);
    }

    public static PalettedBlockStorage readPalettedBiomes(ByteBuf buffer) {
        int index = buffer.readerIndex();
        int size = buffer.readUnsignedByte() >> 1;
        if (size == 127) {
            // This means this paletted storage had the flag pointing to the previous one
            return null;
        }

        buffer.readerIndex(index);
        PalettedBlockStorage storage = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 0);
        storage.readFromStorage(buffer);
        return storage;
    }

}
