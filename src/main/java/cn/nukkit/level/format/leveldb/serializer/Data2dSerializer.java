package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

public class Data2dSerializer {
    public static void serializer(WriteBatch writeBatch, LevelDBChunk levelDBChunk) {
        byte[] bytes = new byte[768];
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        byteBuf.writerIndex(0);
        byte[] heightmap = levelDBChunk.getHeightMapArray();
        byte[] biomeIdArray = levelDBChunk.getBiomeIdArray();
        for (byte b : heightmap) {
            byteBuf.writeShortLE(b);
        }
        byteBuf.writeBytes(biomeIdArray);
        writeBatch.put(LevelDBKey.DATA_2D.getKey(levelDBChunk.getX(), levelDBChunk.getZ(), levelDBChunk.getProvider().getLevel().getDimension()), bytes);
    }

    public static void deserialize(DB dB, ChunkBuilder chunkBuilder) {
        byte[] maps2d = dB.get(LevelDBKey.DATA_2D.getKey(chunkBuilder.getChunkX(), chunkBuilder.getChunkZ(), chunkBuilder.getDimensionData().getDimensionId()));
        int[] heightmap = new int[512];
        byte[] biomeIdArray = new byte[256];
        if (maps2d != null) {
            ByteBuf byteBuf = Unpooled.wrappedBuffer(maps2d);
            for (int i = 0; i < 256; ++i) {
                heightmap[i] = byteBuf.readUnsignedShortLE();
            }
            byteBuf.readBytes(biomeIdArray);
        }
        chunkBuilder.heightMap(heightmap);
        chunkBuilder.biome2d(biomeIdArray);
    }
}

