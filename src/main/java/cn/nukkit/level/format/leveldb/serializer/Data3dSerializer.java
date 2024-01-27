package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

/**
 * @author LT_Name
 */
public class Data3dSerializer {

    public static void serializer(WriteBatch writeBatch, Chunk chunk) {
        //TODO
        /*DimensionData dimensionData = levelDBChunk.getProvider().getLevel().getDimensionData();
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.ioBuffer();
        try {
            byte[] byArray;
            for (byte by : byArray = levelDBChunk.getHeightMapArray()) {
                byteBuf.writeShortLE(by);
            }
            for (int i2 = 0; i2 < dimensionData.getHeight() >> 4; ++i2) {
                PalettedBlockStorage palettedBlockStorage = levelDBChunk.getBiomeStorage(i2);
                palettedBlockStorage.writeToStorage(byteBuf);
            }
            byte[] byArray2 = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(byArray2);
            writeBatch.put(LevelDBKey.DATA_3D.a(levelDBChunk.getX(), levelDBChunk.getZ(), levelDBChunk.getProvider().getLevel().getDimension()), byArray2);
        }
        finally {
            byteBuf.release();
        }*/
    }

    public static void deserialize(DB db, ChunkBuilder chunkBuilder) {
        ByteBuf heightAndBiomesBuffer = null;
        try {
            DimensionData dimensionData = chunkBuilder.getDimensionData();
            byte[] bytes = db.get(LevelDBKey.DATA_3D.getKey(chunkBuilder.getChunkX(), chunkBuilder.getChunkZ(), dimensionData));
            if (bytes != null) {
                heightAndBiomesBuffer = Unpooled.wrappedBuffer(bytes);
                int[] heights = new int[256];
                for (int i = 0; i < 256; i++) {
                    heights[i] = heightAndBiomesBuffer.readUnsignedShortLE();
                }
                chunkBuilder.heightMap(heights);

                PalettedBlockStorage[] biomePalettes = new PalettedBlockStorage[dimensionData.getHeight() >> 4];
                PalettedBlockStorage last = null;
                for (int y = 0; y < biomePalettes.length; y++) {
                    PalettedBlockStorage biomePalette = readBiomePalette(heightAndBiomesBuffer);
                    if (biomePalette == null) {
                        if (last != null) {
                            biomePalette = last.copy();
                        } else {
                            throw new IllegalStateException("Invalid biome palette");
                        }
                    }
                    last = biomePalette;
                    biomePalettes[y] = biomePalette;
                }
                chunkBuilder.biomeStorage(biomePalettes);
            }
        } finally {
            if (heightAndBiomesBuffer != null) {
                heightAndBiomesBuffer.release();
            }
        }
    }

    private static PalettedBlockStorage readBiomePalette(ByteBuf byteBuf) {
        int index = byteBuf.readerIndex();
        int header = byteBuf.readUnsignedByte() >> 1;
        if (header == 127) {
            return null;
        }
        byteBuf.readerIndex(index);
        PalettedBlockStorage palette = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 0);
        palette.readFromStorage(byteBuf);
        return palette;
    }

}
