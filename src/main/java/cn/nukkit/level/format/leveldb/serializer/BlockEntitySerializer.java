package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.stream.NBTInputStream;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.ChunkException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

import static cn.nukkit.level.format.leveldb.LevelDBKey.BLOCK_ENTITIES;

public class BlockEntitySerializer {

    public static void serializer(WriteBatch writeBatch, LevelDBChunk chunk) {
        List<CompoundTag> blockEntities = new ObjectArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!blockEntity.closed) {
                blockEntity.saveNBT();
                blockEntities.add(blockEntity.namedTag);
            }
        }

        byte[] blockEntitiesKey = BLOCK_ENTITIES.getKey(chunk.getX(), chunk.getZ(), chunk.getProvider().getLevel().getDimension());
        if (blockEntities.isEmpty()) {
            writeBatch.delete(blockEntitiesKey);
        } else {
            try {
                writeBatch.put(blockEntitiesKey, NBTIO.write(blockEntities, ByteOrder.LITTLE_ENDIAN));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void deserialize(DB db, ChunkBuilder chunkBuilder) {
        List<CompoundTag> blockEntities = new ObjectArrayList<>();
        byte[] blockEntityData = db.get(BLOCK_ENTITIES.getKey(chunkBuilder.getChunkX(), chunkBuilder.getChunkZ(), chunkBuilder.getDimensionData().getDimensionId()));
        if (blockEntityData != null && blockEntityData.length != 0) {
            try (NBTInputStream nbtStream = new NBTInputStream(new ByteArrayInputStream(blockEntityData), ByteOrder.LITTLE_ENDIAN, false)) {
                while (nbtStream.available() > 0) {
                    Tag tag = Tag.readNamedTag(nbtStream);
                    if (!(tag instanceof CompoundTag)) {
                        throw new IOException("Root tag must be a compound tag");
                    }
                    blockEntities.add((CompoundTag) tag);
                }
            } catch (IOException e) {
                throw new ChunkException("Corrupted block entity data", e);
            }
        }
        chunkBuilder.blockEntities(blockEntities);
    }
}
