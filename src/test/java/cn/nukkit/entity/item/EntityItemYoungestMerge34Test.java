package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native merge, item conservation and saved Age; terrain/network are a finite test registry. */
@Timeout(10)
@Execution(ExecutionMode.SAME_THREAD)
class EntityItemYoungestMerge34Test {
    @BeforeAll static void initialize() { MockServer.init(); }
    @BeforeEach void prepare() { MockServer.reset(); }

    @Test void freshDonorRetainsAllUnitsAndYoungestAgeInOlderReceiverAndSaveReload() throws Exception {
        World world = new World();
        EntityItem old = world.drop(0, 1_199);
        EntityItem fresh = world.drop(0, 0);
        world.merge(old, fresh);

        assertEquals(2, old.getItem().getCount());
        assertTrue(fresh.isClosed());
        assertFalse(old.isClosed());
        assertEquals(0, old.age);
        EntityItem loaded = world.reload(old);
        assertNotEquals(old.getId(), loaded.getId());
        assertTrue(old.isClosed());
        assertFalse(loaded.isClosed());
        assertEquals(2, loaded.getItem().getCount());
        assertEquals(0, loaded.age);
    }

    @Test void olderDonorKeepsYoungerReceiverAgeAndAllUnits() {
        World world = new World();
        EntityItem young = world.drop(0, 199);
        EntityItem old = world.drop(0, 1_199);
        world.merge(young, old);

        assertEquals(2, young.getItem().getCount());
        assertTrue(old.isClosed());
        assertFalse(young.isClosed());
        assertEquals(200, young.age);
    }

    /** Core item code stays real; the fixture supplies only a finite world registry. */
    private static final class World {
        final Level level = mock(Level.class);
        final LevelProvider provider = mock(LevelProvider.class);
        final Map<Long, FullChunk> chunks = new LinkedHashMap<>();
        final Map<Long, Map<Long, Entity>> registries = new LinkedHashMap<>();
        final Server server = MockServer.get();

        World() {
            level.isBeingConverted = true;
            when(level.getServer()).thenReturn(server);
            when(level.getChunkPlayers(org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt())).thenReturn(Map.of());
            when(level.getFolderName()).thenReturn("age34");
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(new BlockAir());
            when(level.getBlock(any(Vector3.class), anyInt())).thenReturn(new BlockAir());
            doReturn(chunks).when(level).getChunks();
            when(level.getEntities()).thenAnswer(call -> registries.values().stream()
                    .flatMap(map -> map.values().stream()).toArray(Entity[]::new));
            when(provider.getLevel()).thenReturn(level);
            when(server.getLevels()).thenReturn(Map.of(1, level));
            when(server.getLevelByName("age34")).thenReturn(level);
        }

        EntityItem drop(int x, int age) {
            return load(Entity.getDefaultNBT(new Vector3(x, 64, 0))
                    .putShort("Health", 5)
                    .putShort("Age", age)
                    .putCompound("Item", NBTIO.putItemHelper(Item.get(Item.DIAMOND))));
        }

        EntityItem reload(EntityItem original) throws Exception {
            original.saveNBT();
            CompoundTag saved = NBTIO.read(NBTIO.write(original.namedTag, ByteOrder.LITTLE_ENDIAN),
                    ByteOrder.LITTLE_ENDIAN);
            original.close();
            return load(saved);
        }

        void merge(EntityItem receiver, EntityItem donor) {
            receiver.onGround = true;
            donor.onGround = true;
            when(level.getNearbyEntities(any(cn.nukkit.math.AxisAlignedBB.class),
                    eq(receiver), eq(false))).thenReturn(new Entity[] {donor});
            receiver.onUpdate(1);
        }

        private EntityItem load(CompoundTag tag) {
            int x = (int) Math.floor(tag.getList("Pos", cn.nukkit.nbt.tag.DoubleTag.class)
                    .get(0).data);
            long hash = Level.chunkHash(x >> 4, 0);
            FullChunk chunk = chunks.computeIfAbsent(hash, ignored -> {
                FullChunk result = mock(FullChunk.class);
                Map<Long, Entity> entities = new LinkedHashMap<>();
                registries.put(hash, entities);
                when(result.getProvider()).thenReturn(provider);
                when(result.getX()).thenReturn(x >> 4);
                when(result.getZ()).thenReturn(0);
                when(result.getEntities()).thenReturn(entities);
                return result;
            });
            EntityItem item = new FiniteItem(chunk, tag);
            registries.get(hash).put(item.getId(), item);
            return item;
        }
    }

    private static final class FiniteItem extends EntityItem {
        FiniteItem(FullChunk chunk, CompoundTag tag) {
            super(chunk, tag);
        }

        @Override
        public void checkBlockCollision() {
            // No terrain in this registry: native item age/tick/save/close stay unchanged.
        }

        @Override
        public boolean isInsideOfFire() {
            return false;
        }

        @Override
        protected boolean checkObstruction(double x, double y, double z) {
            return false;
        }

        @Override
        public boolean move(double x, double y, double z) {
            return false;
        }

        @Override
        public void updateMovement() {}

        @Override
        public Block getLevelBlock() {
            return new BlockAir();
        }
    }
}
