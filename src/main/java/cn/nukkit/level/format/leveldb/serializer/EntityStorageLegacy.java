package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.List;

/**
 * Reads and writes legacy chunk entity records stored under the default entities key.
 */
final class EntityStorageLegacy implements EntityStorage {

    static final EntityStorageLegacy INSTANCE = new EntityStorageLegacy();

    private EntityStorageLegacy() {
    }

    @Override
    public void loadEntities(DB db, ChunkBuilder builder) {
        List<CompoundTag> entityTags = readEntityTags(
                db,
                builder.getChunkX(),
                builder.getChunkZ(),
                builder.getDimensionData().getDimensionId()
        );
        if (entityTags == null) {
            return;
        }
        builder.dataLoader((chunk, provider) -> chunk.setNbtEntities(entityTags));
    }

    @Override
    public void saveEntities(@Nullable DB sourceDb, WriteBatch writeBatch, LevelDBChunk chunk) {
        byte[] key = getKey(chunk);
        Collection<Entity> entities = chunk.getEntities().values();
        if (entities.isEmpty()) {
            writeBatch.delete(key);
            return;
        }

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            for (Entity entity : entities) {
                if (EntityNbtAdapter.isSavable(entity)) {
                    entity.saveNBT();
                    EntityStorage.writeNbt(entity.namedTag, stream);
                }
            }

            byte[] value = stream.toByteArray();
            if (value.length == 0) {
                writeBatch.delete(key);
            } else {
                writeBatch.put(key, value);
            }
        } catch (IOException e) {
            throw new RuntimeException("Can not create out stream", e);
        }
    }

    @Override
    public void deleteEntities(@Nullable DB sourceDb, WriteBatch writeBatch, LevelDBChunk chunk) {
        writeBatch.delete(getKey(chunk));
    }

    List<CompoundTag> readEntityTags(DB db, int chunkX, int chunkZ, int dimension) {
        byte[] key = LevelDBKey.ENTITIES.getKey(chunkX, chunkZ, dimension);
        byte[] value = db.get(key);
        if (value == null) {
            return null;
        }

        List<CompoundTag> entityTags = new ObjectArrayList<>();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(value)) {
            while (stream.available() > 0) {
                CompoundTag nbt = NBTIO.read(stream, ByteOrder.LITTLE_ENDIAN);
                if (EntityNbtAdapter.prepareForLoad(nbt)) {
                    entityTags.add(nbt);
                }
            }
            return entityTags;
        } catch (IOException e) {
            throw new RuntimeException("Unable to deserialize entity NBT", e);
        }
    }

    private static byte[] getKey(LevelDBChunk chunk) {
        return LevelDBKey.ENTITIES.getKey(chunk.getX(), chunk.getZ(), chunk.getProvider().getLevel().getDimension());
    }
}
