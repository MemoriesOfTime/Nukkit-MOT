package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/** Constructor-isolated fixture: real native getters, chunks and sections; no mocking in queries. */
final class CollisionGrowFixture {
    final Level level;
    final LevelDBProvider provider;
    final LevelDBChunk chunk;
    final ProbeEntity entity;
    final CollisionHelper helper;

    CollisionGrowFixture(boolean customBox) throws Exception {
        this(customBox, ProbeEntity.class);
    }

    CollisionGrowFixture(boolean customBox, Class<? extends ProbeEntity> entityType) throws Exception {
        MockServer.init();
        Field singleton = Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Unsafe unsafe = (Unsafe) singleton.get(null);
        level = (Level) unsafe.allocateInstance(Level.class);
        provider = (LevelDBProvider) unsafe.allocateInstance(LevelDBProvider.class);
        set(Level.class, level, "provider", provider);
        set(Level.class, level, "dimensionData", new DimensionData(0, 0, 255));
        set(LevelDBProvider.class, provider, "level", level);
        StateBlockStorage storage = new StateBlockStorage();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                storage.set(x, 15, z, BlockStateSnapshot.builder().legacyId(BlockID.STONE).legacyData(0).build());
            }
        }
        ChunkSection[] sections = new ChunkSection[16];
        sections[3] = new LevelDBChunkSection(3, new StateBlockStorage[]{storage}, false);
        chunk = new LevelDBChunk(provider, 0, 0, sections, null, null, null, null, null,
                ChunkState.FINISHED);
        entity = (ProbeEntity) unsafe.allocateInstance(entityType);
        set(Position.class, entity, "level", level);
        entity.chunk = chunk;
        entity.box(customBox ? new CustomBox() : new SimpleAxisAlignedBB(5, 64, 5, 6, 65.6, 6));
        helper = new CollisionHelper(entity);
        if (level.getClass() != Level.class || provider.getClass() != LevelDBProvider.class
                || chunk.getClass() != LevelDBChunk.class) throw new AssertionError("fixture classes");
        if (helper.getCollisionBlocks().length != 0) throw new AssertionError("fixture collision answer");
    }

    static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    void write(int x, int y, int z, int id) {
        ChunkSection section = chunk.getSection(y >> 4);
        LevelDBChunkSection mutable;
        if (section instanceof LevelDBChunkSection existing) {
            mutable = existing;
        } else {
            mutable = new LevelDBChunkSection(y >> 4, new StateBlockStorage[]{new StateBlockStorage()}, false);
            mutable.setParent(chunk);
            chunk.getSections()[y >> 4] = mutable;
        }
        mutable.getStorages()[0].set(x & 15, y & 15, z & 15,
                BlockStateSnapshot.builder().legacyId(id).legacyData(0).build());
    }

    static class ProbeEntity extends Entity {
        ProbeEntity(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }
        @Override public int getNetworkId() { return -1; }
        void box(AxisAlignedBB box) { this.boundingBox = box; }
    }

    static final class CustomBox extends SimpleAxisAlignedBB {
        long grows;
        CustomBox() { super(5, 64, 5, 6, 65.6, 6); }
        @Override public AxisAlignedBB grow(double x, double y, double z) {
            grows++;
            return super.grow(x + .1, y, z);
        }
    }
}
