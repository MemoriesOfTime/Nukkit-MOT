package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes Bedrock's modern actor storage introduced in protocol 503 (MCBE 1.18.30).
 * <p>
 * Adapted from Chunker (<a href="https://github.com/HiveGamesOSS/Chunker">Chunker</a>).
 */
final class EntityStorageV1_18_30 implements EntityStorage {

    static final EntityStorageV1_18_30 INSTANCE = new EntityStorageV1_18_30();
    private static final int ACTOR_STORAGE_KEY_LENGTH = 8;

    private EntityStorageV1_18_30() {
    }

    boolean hasData(DB db, ChunkBuilder builder) {
        return db.get(getDigpKey(builder)) != null;
    }

    @Override
    public void loadEntities(DB db, ChunkBuilder builder) {
        EntityReadResult readResult = readEntities(
                db,
                builder.getChunkX(),
                builder.getChunkZ(),
                builder.getDimensionData().getDimensionId()
        );
        if (readResult == null) {
            return;
        }
        builder.dataLoader((chunk, provider) -> {
            chunk.setNbtEntities(readResult.getLoadableEntities());
            chunk.setPreservedEntityActors(readResult.getPreservedActors());
        });
    }

    @Override
    public void saveEntities(@Nullable DB sourceDb, WriteBatch writeBatch, LevelDBChunk chunk) {
        int dimension = chunk.getProvider().getLevel().getDimension();
        byte[] digpKey = getDigpKey(chunk.getX(), chunk.getZ(), dimension);

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            Set<Long> writtenStorageKeys = new HashSet<>();
            for (Entity entity : chunk.getEntities().values()) {
                if (!EntityNbtAdapter.isSavable(entity)) {
                    continue;
                }

                entity.saveNBT();
                EntityNbtAdapter.prepareForSave(entity, entity.namedTag);

                long uniqueEntityId = entity.namedTag.getLong("UniqueID");
                byte[] storageKey = generateStorageKeyForEntity(uniqueEntityId);
                writtenStorageKeys.add(storageKeyToLong(storageKey));
                EntityStorage.writeBytes(storageKey, stream);
                writeBatch.put(getActorKey(storageKey), EntityStorage.writeNbt(entity.namedTag));
            }

            List<LevelDBChunk.PreservedEntityActor> preservedActors = chunk.getPreservedEntityActors();
            if (preservedActors != null) {
                for (LevelDBChunk.PreservedEntityActor preservedActor : preservedActors) {
                    byte[] storageKey = preservedActor.getStorageKey();
                    long key = storageKeyToLong(storageKey);
                    if (writtenStorageKeys.add(key)) {
                        EntityStorage.writeBytes(storageKey, stream);
                        writeBatch.put(getActorKey(storageKey), preservedActor.getRawNbt());
                    }
                }
            }

            byte[] value = stream.toByteArray();
            if (value.length == 0) {
                deleteEntities(sourceDb, writeBatch, chunk);
                return;
            }

            writeBatch.put(digpKey, value);
            deleteStaleActors(sourceDb, writeBatch, chunk.getX(), chunk.getZ(), dimension, writtenStorageKeys);
        } catch (IOException e) {
            throw new RuntimeException("Can not create out stream", e);
        }
    }

    @Override
    public void deleteEntities(@Nullable DB sourceDb, WriteBatch writeBatch, LevelDBChunk chunk) {
        int dimension = chunk.getProvider().getLevel().getDimension();
        deleteStaleActors(sourceDb, writeBatch, chunk.getX(), chunk.getZ(), dimension, Set.of());
        writeBatch.delete(getDigpKey(chunk.getX(), chunk.getZ(), dimension));
    }

    List<CompoundTag> readEntityTags(DB db, int chunkX, int chunkZ, int dimension) {
        EntityReadResult result = readEntities(db, chunkX, chunkZ, dimension);
        return result == null ? null : result.getLoadableEntities();
    }

    EntityReadResult readEntities(DB db, int chunkX, int chunkZ, int dimension) {
        byte[] entityStorageKeys = db.get(getDigpKey(chunkX, chunkZ, dimension));
        if (entityStorageKeys == null) {
            return null;
        }

        EntityReadResult result = new EntityReadResult();
        for (byte[] storageKey : readStorageKeys(entityStorageKeys)) {
            byte[] value = db.get(getActorKey(storageKey));
            if (value == null) {
                continue;
            }

            try (ByteArrayInputStream stream = new ByteArrayInputStream(value)) {
                CompoundTag nbt = NBTIO.read(stream, ByteOrder.LITTLE_ENDIAN);
                EntityNbtLoadStatus status = EntityNbtAdapter.normalizeForNukkitLoad(nbt);
                if (status == EntityNbtLoadStatus.LOADABLE) {
                    result.getLoadableEntities().add(nbt);
                } else {
                    result.getPreservedActors().add(new LevelDBChunk.PreservedEntityActor(storageKey, value));
                }
            } catch (IOException | RuntimeException e) {
                result.getPreservedActors().add(new LevelDBChunk.PreservedEntityActor(storageKey, value));
            }
        }
        return result;
    }

    private void deleteStaleActors(@Nullable DB sourceDb, WriteBatch writeBatch, int chunkX, int chunkZ, int dimension, Set<Long> retainedKeys) {
        if (sourceDb == null) {
            return;
        }

        byte[] oldDigp = sourceDb.get(getDigpKey(chunkX, chunkZ, dimension));
        if (oldDigp == null) {
            return;
        }

        for (byte[] oldStorageKey : readStorageKeys(oldDigp)) {
            long key = storageKeyToLong(oldStorageKey);
            if (!retainedKeys.contains(key)) {
                writeBatch.delete(getActorKey(oldStorageKey));
            }
        }
    }

    private static byte[] getDigpKey(ChunkBuilder builder) {
        return getDigpKey(builder.getChunkX(), builder.getChunkZ(), builder.getDimensionData().getDimensionId());
    }

    private static byte[] getDigpKey(int chunkX, int chunkZ, int dimension) {
        return LevelDBKey.getKey(LevelDBKey.DIGP_PREFIX, chunkX, chunkZ, dimension);
    }

    private static byte[] getActorKey(byte[] storageKey) {
        return LevelDBKey.getKey(LevelDBKey.ACTOR_PREFIX, storageKey);
    }

    static byte[] generateStorageKeyForEntity(long uniqueEntityId) {
        long storageKey = 0x100000000L + (uniqueEntityId ^ 0xFFFFFFFF00000000L);
        return storageKeyToBytes(storageKey);
    }

    private static List<byte[]> readStorageKeys(byte[] entityStorageKeys) {
        List<byte[]> keys = new ArrayList<>();
        for (int offset = 0; offset + ACTOR_STORAGE_KEY_LENGTH <= entityStorageKeys.length; offset += ACTOR_STORAGE_KEY_LENGTH) {
            byte[] storageKey = new byte[ACTOR_STORAGE_KEY_LENGTH];
            System.arraycopy(entityStorageKeys, offset, storageKey, 0, ACTOR_STORAGE_KEY_LENGTH);
            keys.add(storageKey);
        }
        return keys;
    }

    private static long storageKeyToLong(byte[] storageKey) {
        return ByteBuffer.wrap(storageKey).order(ByteOrder.BIG_ENDIAN).getLong();
    }

    private static byte[] storageKeyToBytes(long storageKey) {
        ByteBuffer buffer = ByteBuffer.allocate(ACTOR_STORAGE_KEY_LENGTH).order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(storageKey);
        return buffer.array();
    }

    static final class EntityReadResult {

        private final List<CompoundTag> loadableEntities = new ArrayList<>();
        private final List<LevelDBChunk.PreservedEntityActor> preservedActors = new ArrayList<>();

        List<CompoundTag> getLoadableEntities() {
            return this.loadableEntities;
        }

        List<LevelDBChunk.PreservedEntityActor> getPreservedActors() {
            return this.preservedActors;
        }
    }
}
