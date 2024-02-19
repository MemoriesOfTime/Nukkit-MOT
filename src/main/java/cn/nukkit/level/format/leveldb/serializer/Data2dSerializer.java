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
        byte[] byArray = new byte[768];
        ByteBuf byteBuf = Unpooled.wrappedBuffer(byArray);
        byteBuf.writerIndex(0);
        byte[] heightmap = levelDBChunk.getHeightMapArray();
        byte[] biomeIdArray = levelDBChunk.getBiomeIdArray();
        for (byte b : heightmap) {
            byteBuf.writeShortLE(b);
        }
        byteBuf.writeBytes(biomeIdArray);
        writeBatch.put(LevelDBKey.DATA_2D.getKey(levelDBChunk.getX(), levelDBChunk.getZ(), levelDBChunk.getProvider().getLevel().getDimensionData()), byArray);
    }

    public static void deserialize(DB dB, ChunkBuilder chunkBuilder) {
        byte[] maps2d = dB.get(LevelDBKey.DATA_2D.getKey(chunkBuilder.getChunkX(), chunkBuilder.getChunkZ(), chunkBuilder.getDimensionData()));
        int[] heightmap = new int[512];
        byte[] biomeIdArray = new byte[256];
        if (maps2d != null) {
            ByteBuf byteBuf = Unpooled.wrappedBuffer(maps2d);
            for (int i2 = 0; i2 < 256; ++i2) {
                heightmap[i2] = byteBuf.readUnsignedShortLE();
            }
            byteBuf.readBytes(biomeIdArray);
        }
        chunkBuilder.heightMap(heightmap);
        chunkBuilder.biome2d(biomeIdArray);
    }
}

