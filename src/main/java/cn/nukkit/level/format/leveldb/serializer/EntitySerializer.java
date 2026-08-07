package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

import java.util.List;

public class EntitySerializer {

    public static void loadEntities(DB db, ChunkBuilder builder) {
        if (!EntityStorageV1_18_30.INSTANCE.hasData(db, builder)) {
            EntityStorageLegacy.INSTANCE.loadEntities(db, builder);
            return;
        }

        EntityStorageV1_18_30.EntityReadResult readResult = EntityStorageV1_18_30.INSTANCE.readEntities(
                db,
                builder.getChunkX(),
                builder.getChunkZ(),
                builder.getDimensionData().getDimensionId()
        );
        if (readResult == null) {
            EntityStorageLegacy.INSTANCE.loadEntities(db, builder);
            return;
        }

        List<CompoundTag> loadableEntities = readResult.getLoadableEntities();
        if (loadableEntities.isEmpty()) {
            List<CompoundTag> legacyEntities = EntityStorageLegacy.INSTANCE.readEntityTags(
                    db,
                    builder.getChunkX(),
                    builder.getChunkZ(),
                    builder.getDimensionData().getDimensionId()
            );
            if (legacyEntities != null && !legacyEntities.isEmpty()) {
                loadableEntities = legacyEntities;
            }
        }

        List<CompoundTag> finalLoadableEntities = loadableEntities;
        builder.dataLoader((chunk, provider) -> {
            chunk.setNbtEntities(finalLoadableEntities);
            chunk.setPreservedEntityActors(readResult.getPreservedActors());
        });
    }

    public static void saveEntities(DB sourceDb, WriteBatch writeBatch, LevelDBChunk chunk) {
        EntityStorageV1_18_30.INSTANCE.saveEntities(sourceDb, writeBatch, chunk);
        EntityStorageLegacy.INSTANCE.deleteEntities(sourceDb, writeBatch, chunk);
    }

    public static void saveEntities(WriteBatch writeBatch, LevelDBChunk chunk) {
        saveEntities(null, writeBatch, chunk);
    }
}
