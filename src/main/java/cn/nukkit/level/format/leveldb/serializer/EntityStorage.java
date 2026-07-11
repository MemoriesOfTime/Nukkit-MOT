package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

interface EntityStorage {

    void loadEntities(DB db, ChunkBuilder builder);

    void saveEntities(@Nullable DB sourceDb, WriteBatch writeBatch, LevelDBChunk chunk);

    void deleteEntities(@Nullable DB sourceDb, WriteBatch writeBatch, LevelDBChunk chunk);

    static byte[] writeNbt(CompoundTag nbt) {
        try {
            return NBTIO.write(nbt, ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write entity NBT", e);
        }
    }

    static void writeNbt(CompoundTag nbt, OutputStream stream) {
        try {
            NBTIO.write(nbt, stream, ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write entity NBT", e);
        }
    }

    static void writeBytes(byte[] bytes, OutputStream stream) {
        try {
            stream.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write entity storage key", e);
        }
    }
}
