package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
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

import static cn.nukkit.level.format.leveldb.LevelDBKey.ENTITIES;

public class EntitySerializer {

    public static void serializer(WriteBatch writeBatch, LevelDBChunk chunk) {
        List<CompoundTag> entities = new ObjectArrayList<>();
        for (Entity entity : chunk.getEntities().values()) {
            if (!(entity instanceof Player) && !entity.closed && entity.canBeSavedWithChunk()) {
                entity.saveNBT();
                entities.add(entity.namedTag);
            }
        }
        byte[] entitiesKey = ENTITIES.getKey(chunk.getX(), chunk.getZ(), chunk.getProvider().getLevel().getDimension());
        if (entities.isEmpty()) {
            writeBatch.delete(entitiesKey);
        } else {
            try {
                writeBatch.put(entitiesKey, NBTIO.write(entities, ByteOrder.LITTLE_ENDIAN));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void deserialize(DB db, ChunkBuilder chunkBuilder) {
        List<CompoundTag> entities = new ObjectArrayList<>();
        byte[] entityData = db.get(ENTITIES.getKey(chunkBuilder.getChunkX(), chunkBuilder.getChunkZ(), chunkBuilder.getDimensionData().getDimensionId()));
        if (entityData != null && entityData.length != 0) {
            try (NBTInputStream nbtStream = new NBTInputStream(new ByteArrayInputStream(entityData), ByteOrder.LITTLE_ENDIAN, false)) {
                while (nbtStream.available() > 0) {
                    Tag tag = Tag.readNamedTag(nbtStream);
                    if (!(tag instanceof CompoundTag)) {
                        throw new IOException("Root tag must be a compound tag");
                    }
                    entities.add((CompoundTag) tag);
                }
            } catch (IOException e) {
                throw new ChunkException("Corrupted entity data", e);
            }
        }
        chunkBuilder.entities(entities);
    }

}
