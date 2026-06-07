package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.ThreadCache;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.List;

public class BlockEntitySerializer {

    private static final ByteOrder LEVELDB_ORDER = ByteOrder.LITTLE_ENDIAN;

    public static void loadBlockEntities(DB db, ChunkBuilder builder) {
        byte[] key = LevelDBKey.BLOCK_ENTITIES.getKey(
                builder.getChunkX(),
                builder.getChunkZ(),
                builder.getDimensionData().getDimensionId()
        );

        byte[] value = db.get(key);
        if (value == null) {
            return;
        }

        List<CompoundTag> blockEntities = new ObjectArrayList<>();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(value)) {
            while (stream.available() > 0) {
                blockEntities.add(NBTIO.read(stream, LEVELDB_ORDER));
            }
            builder.dataLoader((chunk, _) -> chunk.setNbtBlockEntities(blockEntities));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveBlockEntities(WriteBatch db, LevelDBChunk chunk) {
        byte[] key = LevelDBKey.BLOCK_ENTITIES.getKey(
                chunk.getX(),
                chunk.getZ(),
                chunk.getProvider().getLevel().getDimension()
        );

        Collection<BlockEntity> entities = chunk.getBlockEntities().values();

        if (entities.isEmpty()) {
            db.delete(key);
            return;
        }

        var baos = ThreadCache.fbaos.get().reset();

        try {
            for (BlockEntity blockEntity : entities) {
                if (blockEntity.canSaveToStorage()) {
                    blockEntity.saveNBT();
                    NBTIO.write(blockEntity.namedTag, baos, LEVELDB_ORDER, false);
                }
            }

            byte[] value = baos.toByteArray();

            db.put(key, value);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}