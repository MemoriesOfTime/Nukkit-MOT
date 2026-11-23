package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockMagma;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityLivingEyeAndEffectsFastPathTest {

    @BeforeAll
    static void init() {
        MockServer.init();
        Effect.init();
    }

    @AfterAll
    static void cleanup() {
        MockServer.reset();
    }

    @Test
    void trackedEffectMapKeepsEmptyFastPathConsistentAcrossDirectAndViewMutations() {
        Fixture fixture = new Fixture();
        PlainLiving actor = fixture.actor;
        Map<Integer, Effect> effects = actor.getEffects();
        assertTrue(effects.isEmpty());
        assertFalse(actor.hasEffect(Effect.LEVITATION));
        assertNull(actor.getEffect(Effect.LEVITATION));
        assertSame(effects.values(), effects.values());
        assertFalse(effects.values().iterator().hasNext());

        Effect speed = Effect.getEffect(Effect.SPEED);
        effects.put(Effect.SPEED, speed);
        assertFalse(effects.isEmpty());
        assertTrue(actor.hasEffect(Effect.SPEED));
        assertSame(speed, actor.getEffect(Effect.SPEED));

        Iterator<Integer> keyIter = effects.keySet().iterator();
        assertTrue(keyIter.hasNext());
        assertEquals(Effect.SPEED, keyIter.next());
        keyIter.remove();
        assertTrue(effects.isEmpty());
        assertFalse(actor.hasEffect(Effect.SPEED));

        effects.computeIfAbsent(Effect.REGENERATION, Effect::getEffect);
        assertFalse(effects.isEmpty());
        effects.values().removeIf(e -> e.getId() == Effect.REGENERATION);
        assertTrue(effects.isEmpty());

        for (int i = 0; i < 5_000; i++) {
            probeEmptyEffects(effects);
        }
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        int sink = 0;
        for (int i = 0; i < 5_000; i++) {
            sink += probeEmptyEffects(effects);
        }
        long allocated = mx.getThreadAllocatedBytes(tid) - before;
        assertEquals(0, sink);
        assertTrue(allocated <= 256L, "Empty effect checks should allocate 0 B, got " + allocated + " B");
    }

    private static int probeEmptyEffects(Map<Integer, Effect> effects) {
        int acc = 0;
        if (effects.containsKey(Effect.LEVITATION)) acc++;
        if (effects.containsKey(Effect.SLOW_FALLING)) acc++;
        if (!effects.isEmpty()) acc++;
        for (Effect e : effects.values()) {
            acc += e.getId();
        }
        return acc;
    }

    @Test
    void completedEffectMutationsAreVisibleDuringConcurrentEmptyChecks() throws Exception {
        Map<Integer, Effect> effects = new Fixture().actor.getEffects();
        Effect speed = Effect.getEffect(Effect.SPEED);
        var stop = new AtomicBoolean();
        var started = new CountDownLatch(1);
        var reader = new FutureTask<>(() -> {
            started.countDown();
            while (!stop.get()) {
                effects.isEmpty();
            }
            return null;
        });
        new Thread(reader, "effect-empty-reader").start();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 20_000; i++) {
                effects.put(Effect.SPEED, speed);
                assertFalse(effects.isEmpty(), "Completed insertion must invalidate an empty result");
                Map.Entry<Integer, Effect> entry = effects.entrySet().iterator().next();
                effects.clear();
                assertTrue(effects.isEmpty(), "Completed clear must invalidate a populated result");
                entry.setValue(speed);
                assertFalse(effects.isEmpty(), "An entry can reinsert its mapping after a clear");
                effects.remove(Effect.SPEED);
                assertTrue(effects.isEmpty(), "Completed removal must invalidate a populated result");
            }
        } finally {
            stop.set(true);
            reader.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void tickKeepsCapturedEffectsWhenCallbackMutatesTrackedMap() {
        for (int count : new int[]{2, 17}) {
            PlainLiving actor = new Fixture().actor;
            actor.noClip = true;
            actor.setAirTicks(300);
            Map<Integer, Effect> activeEffects = actor.getEffects();
            Effect removed = new Effect(Effect.SPEED, "removed", 0, 0, 0).setDuration(100);
            Effect replacement = new Effect(Effect.SPEED, "replacement", 0, 0, 0).setDuration(100);
            Effect mutator = new Effect(Effect.SPEED, "mutator", 0, 0, 0) {
                @Override
                public boolean canTick() {
                    return true;
                }

                @Override
                public void applyEffect(Entity entity) {
                    assertSame(removed, activeEffects.put(count - 1, replacement));
                }
            }.setDuration(100);
            activeEffects.put(0, mutator);
            for (int key = 1; key < count - 1; key++) {
                activeEffects.put(key, new Effect(Effect.SPEED, "witness", 0, 0, 0).setDuration(100));
            }
            activeEffects.put(count - 1, removed);

            actor.entityBaseTick(1);

            assertEquals(99, removed.getDuration());
            assertEquals(100, replacement.getDuration());
            assertSame(replacement, activeEffects.get(count - 1));
        }
    }

    @Test
    void dryLivingEntitySkipsBubbleColumnAndNonDamagingFloorBlockMaterialization() {
        Fixture fixture = new Fixture();
        PlainLiving dry = spy(fixture.actor);
        dry.age = 9; // age advances to 10 in super.entityBaseTick -> floorProbeDue = true
        dry.setAirTicks(300);
        doReturn(false).when(dry).canDespawn();
        doReturn(true).when(dry).isAlive();
        doReturn(false).when(dry).isInsideOfSolid();
        doReturn(false).when(dry).isInsideOfLava();
        doReturn(false).when(dry).isInsideOfFire();
        doNothing().when((Entity) dry).checkBlockCollision();

        dry.entityBaseTick(1);

        verify(dry, never()).isInsideBubbleColumn();
        verify(fixture.level, never()).getBlock(same(fixture.chunk), eq(2), eq(63), eq(2), eq(false));
        assertEquals(305, dry.getAirTicks());

        // Now place MAGMA under feet and verify getBlock IS called for magma collision
        fixture.s0.set(2, 15, 2, BlockStateMapping.get().getState(Block.MAGMA, 0));
        BlockMagma magma = mock(BlockMagma.class);
        when(fixture.level.getBlock(fixture.chunk, 2, 63, 2, false)).thenReturn(magma);
        dry.age = 19; // advances to 20 -> floorProbeDue = true
        dry.entityBaseTick(1);
        verify(fixture.level, times(1)).getBlock(fixture.chunk, 2, 63, 2, false);
        verify(magma, times(1)).onEntityCollide(dry);
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final StateBlockStorage s0 = new StateBlockStorage();
        final LevelDBChunk chunk;
        final PlainLiving actor;

        Fixture() {
            when(level.getServer()).thenReturn(Server.getInstance());
            when(level.isYInRange(anyInt())).thenReturn(true);
            when(level.getDimensionData()).thenReturn(DimensionData.LEGACY_DIMENSION);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            lenient().when(level.getNearbyRideables(any(), any())).thenReturn(new Entity[0]);
            level.isBeingConverted = true;

            LevelDBProvider provider = mock(LevelDBProvider.class);
            when(provider.getLevel()).thenReturn(level);

            // Floor at y=63 is STONE (non-damaging), eye at y=65 is AIR
            s0.set(2, 15, 2, BlockStateMapping.get().getState(Block.STONE, 0));
            ChunkSection[] sections = new ChunkSection[16];
            sections[3] = new LevelDBChunkSection(3, new StateBlockStorage[]{s0, new StateBlockStorage()}, false);
            sections[4] = new LevelDBChunkSection(4, new StateBlockStorage[]{new StateBlockStorage(), new StateBlockStorage()}, false);
            chunk = new LevelDBChunk(provider, 0, 0, sections, null, null, null, null, null, ChunkState.FINISHED);
            when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
            when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenReturn(chunk);
            when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);

            actor = new PlainLiving(chunk, Entity.getDefaultNBT(new Vector3(2.5, 64.0, 2.5)));
        }
    }

    static class PlainLiving extends cn.nukkit.entity.mob.EntityZombie {
        PlainLiving(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
            this.dataProperties.putLong(DATA_FLAGS_EXTENDED, 0L);
        }
    }
}
