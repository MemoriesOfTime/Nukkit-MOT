package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginManager;
import org.iq80.leveldb.DBException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression from round-3 review: failed destination loads preserve source membership. */
class ReviewCheckChunksProbeTest {
    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void entityKeepsItsOldChunkWhenTheNewChunkLoadThrows() throws Exception {
        Entity entity = mock(Entity.class);
        doCallRealMethod().when(entity).checkChunks();
        doCallRealMethod().when(entity).isChunkTransitionGuarded(any(), anyInt(), anyInt());
        entity.server = mock(Server.class);
        entity.server.asyncChunkLoadCompletion = true;
        Level level = mock(Level.class);
        entity.level = level;
        entity.x = 16 * 5 + 1;   // chunk 5,0 (unloaded, corrupt)
        entity.z = 1;
        BaseFullChunk old = mock(BaseFullChunk.class);
        when(old.getX()).thenReturn(4);
        when(old.getZ()).thenReturn(0);
        entity.chunk = old;
        when(level.getChunk(eq(5), eq(0), eq(true))).thenThrow(new DBException("Failed to read chunk 5, 0"));
        assertDoesNotThrow(entity::checkChunks);
        verify(old, never()).removeEntity(entity);          // source still owns the entity
        verify(old, never()).addEntity(entity);
        assertSame(old, entity.chunk);             // remains owned by a saved chunk
        // further ticks must remain safe
        assertDoesNotThrow(entity::checkChunks);
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void rejectedMoveRestoresSavedPositionBoundsAndSourceMembership(Failure failure) throws Exception {
        Fixture f = new Fixture();
        // Refresh the snapshot inside the same chunk. Network watermarks deliberately disagree.
        f.entity.ySize = 0.25f;
        assertTrue(f.entity.setPosition(new Vector3(79.25, 65, 1.5)));
        f.entity.lastX = 90;
        f.entity.lastY = 200;
        f.entity.lastZ = 50;
        f.fail(failure);

        for (int tick = 0; tick < 2; tick++) {
            f.entity.blocksAround = new ArrayList<>();
            f.entity.collisionBlocks = new ArrayList<>();
            f.entity.ySize = 0.75f;
            assertFalse(assertDoesNotThrow(() -> f.entity.setPosition(new Vector3(81, 70, 2))));
            assertSame(f.source, f.entity.chunk);
            assertSame(f.entity, f.source.getEntities().get(f.entity.getId()));
            assertEquals(79.25, f.entity.x);
            assertEquals(65, f.entity.y);
            assertEquals(1.5, f.entity.z);
            assertEquals(0.25f, f.entity.ySize);
            double radius = f.entity.getWidth() * f.entity.scale / 2d;
            assertEquals(f.entity.x - radius, f.entity.boundingBox.getMinX());
            assertEquals(f.entity.y + 0.25, f.entity.boundingBox.getMinY());
            assertEquals(f.entity.z - radius, f.entity.boundingBox.getMinZ());
            assertNull(f.entity.blocksAround);
            assertNull(f.entity.collisionBlocks);
            f.entity.saveNBT();
            assertEquals(79.25, f.entity.namedTag.getList("Pos", DoubleTag.class).get(0).data);
        }
        // The next valid movement still succeeds; rollback does not poison the entity.
        assertTrue(f.entity.setPosition(new Vector3(78.5, 65, 1.5)));
        assertSame(f.entity, f.source.getEntities().get(f.entity.getId()));
    }

    @Test
    void rejectedTeleportReportsFailure() throws Exception {
        Fixture f = new Fixture();
        f.fail(Failure.QUARANTINE);
        assertFalse(f.entity.teleport(new Vector3(81, 70, 2), null));
        assertSame(f.source, f.entity.chunk);
        assertEquals(65, f.entity.x);
    }

    @Test
    void aliasedPositionStillReportsRejectedMove() throws Exception {
        Fixture f = new Fixture();
        f.fail(Failure.EXCEPTION);
        f.entity.x = 81;
        f.entity.y = 70;
        f.entity.z = 2;
        assertFalse(f.entity.setPosition(f.entity));
        assertSame(f.source, f.entity.chunk);
        assertSame(f.entity, f.source.getEntities().get(f.entity.getId()));
        assertEquals(65, f.entity.x);
        assertEquals(64, f.entity.y);
        assertEquals(1, f.entity.z);
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void crossWorldFailureLeavesSourceWorldAndMembershipUntouched(Failure failure) throws Exception {
        Fixture f = new Fixture();
        Server server = mock(Server.class);
        server.asyncChunkLoadCompletion = true;
        f.entity.server = server;
        Level targetLevel = mock(Level.class);
        switch (failure) {
            case EXCEPTION -> when(targetLevel.getChunk(5, 0, true)).thenThrow(new DBException("unreadable other world"));
            case NULL -> when(targetLevel.getChunk(5, 0, true)).thenReturn(null);
            case QUARANTINE -> {
                LevelDBChunk destination = LevelDBChunk.getEmptyChunk(5, 0);
                destination.markChunkReadFailure(new DBException("quarantined other world"));
                when(targetLevel.getChunk(5, 0, true)).thenReturn(destination);
            }
        }
        assertFalse(assertDoesNotThrow(() -> f.entity.setPosition(new Position(81, 70, 2, targetLevel))));
        assertSame(f.level, f.entity.level);
        assertSame(f.source, f.entity.chunk);
        assertSame(f.entity, f.source.getEntities().get(f.entity.getId()));
        assertEquals(65, f.entity.x);
        assertEquals(64, f.entity.y);
        assertEquals(1, f.entity.z);
        verify(f.level, never()).removeEntity(f.entity);
        verify(targetLevel, never()).addEntity(f.entity);
        verifyNoInteractions(server); // No switch-level event or player dimension side effects.
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void healthyCrossWorldTransferKeepsExistingSwitchFlow(boolean guarded) throws Exception {
        Fixture f = new Fixture();
        Server server = mock(Server.class);
        server.asyncChunkLoadCompletion = guarded;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        f.entity.server = server;
        Level targetLevel = mock(Level.class);
        LevelDBChunk destination = LevelDBChunk.getEmptyChunk(5, 0);
        AtomicInteger reads = new AtomicInteger();
        when(targetLevel.getChunk(5, 0, true)).thenAnswer(ignored -> {
            if (guarded && reads.getAndIncrement() == 0) {
                assertSame(f.level, f.entity.level);
                assertSame(f.entity, f.source.getEntities().get(f.entity.getId()));
            } else {
                assertSame(targetLevel, f.entity.level);
                assertFalse(f.source.getEntities().containsKey(f.entity.getId()));
            }
            return destination;
        });
        assertTrue(f.entity.setPosition(new Position(81, 70, 2, targetLevel)));
        assertSame(targetLevel, f.entity.level);
        assertSame(destination, f.entity.chunk);
        assertSame(f.entity, destination.getEntities().get(f.entity.getId()));
        assertFalse(f.source.getEntities().containsKey(f.entity.getId()));
        verify(f.level).removeEntity(f.entity);
        verify(targetLevel).addEntity(f.entity);
        verify(targetLevel, times(guarded ? 2 : 1)).getChunk(5, 0, true);
    }

    @Test
    void successfulDestinationIsAcquiredBeforeSourceRemoval() throws Exception {
        Fixture f = new Fixture();
        LevelDBChunk destination = LevelDBChunk.getEmptyChunk(5, 0, f.provider);
        when(f.level.getChunk(5, 0, true)).thenAnswer(ignored -> {
            assertSame(f.entity, f.source.getEntities().get(f.entity.getId()));
            return destination;
        });
        assertTrue(f.entity.setPosition(new Vector3(81, 70, 2)));
        assertSame(destination, f.entity.chunk);
        assertFalse(f.source.getEntities().containsKey(f.entity.getId()));
        assertSame(f.entity, destination.getEntities().get(f.entity.getId()));
        when(f.level.getChunk(6, 0, true)).thenThrow(new DBException("next destination failed"));
        assertFalse(f.entity.setPosition(new Vector3(97, 80, 3)));
        assertSame(destination, f.entity.chunk);
        assertSame(f.entity, destination.getEntities().get(f.entity.getId()));
        assertEquals(81, f.entity.x);
        assertEquals(70, f.entity.y);
        assertEquals(2, f.entity.z);
    }

    @Test
    void disabledFlagRemovesSourceBeforeReadingDestination() throws Exception {
        Fixture f = new Fixture();
        f.server.asyncChunkLoadCompletion = false;
        LevelDBChunk destination = LevelDBChunk.getEmptyChunk(5, 0, f.provider);
        when(f.level.getChunk(5, 0, true)).thenAnswer(ignored -> {
            assertFalse(f.source.getEntities().containsKey(f.entity.getId()));
            return destination;
        });
        assertTrue(f.entity.setPosition(new Vector3(81, 70, 2)));
        assertSame(destination, f.entity.chunk);
        assertSame(f.entity, destination.getEntities().get(f.entity.getId()));
    }

    @Test
    void disabledFlagKeepsLegacySuccessForNullDestination() throws Exception {
        Fixture f = new Fixture();
        f.server.asyncChunkLoadCompletion = false;
        when(f.level.getChunk(5, 0, true)).thenReturn(null);

        assertTrue(f.entity.setPosition(new Vector3(81, 70, 2)));
        assertNull(f.entity.chunk);
        assertFalse(f.source.getEntities().containsKey(f.entity.getId()));
        assertEquals(81, f.entity.x);
        assertEquals(70, f.entity.y);
        assertEquals(2, f.entity.z);
    }

    @Test
    void disabledFlagKeepsLegacyReadExceptionPropagation() throws Exception {
        Fixture f = new Fixture();
        f.server.asyncChunkLoadCompletion = false;
        DBException failure = new DBException("legacy unreadable destination");
        when(f.level.getChunk(5, 0, true)).thenThrow(failure);

        assertSame(failure, assertThrows(DBException.class,
                () -> f.entity.setPosition(new Vector3(81, 70, 2))));
        assertSame(f.source, f.entity.chunk);
        assertFalse(f.source.getEntities().containsKey(f.entity.getId()));
        assertEquals(81, f.entity.x);
        verify(f.level, never()).reportChunkLoadFailure(anyInt(), anyInt(), any());
    }

    @Test
    void lifecycleFailureOnRealDestinationDoesNotRejectMovement() throws Exception {
        Fixture f = new Fixture();
        LevelDBChunk destination = LevelDBChunk.getEmptyChunk(5, 0, f.provider);
        destination.markChunkLoadFailure(new IllegalStateException("chunk event failed"));
        when(f.level.getChunk(5, 0, true)).thenReturn(destination);

        assertTrue(f.entity.setPosition(new Vector3(81, 70, 2)));
        assertSame(destination, f.entity.chunk);
        assertSame(f.entity, destination.getEntities().get(f.entity.getId()));
        assertFalse(f.source.getEntities().containsKey(f.entity.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void switchingFlagOffStillRejectsExistingReadPlaceholder(boolean anotherWorld) throws Exception {
        Fixture f = new Fixture();
        Level target = anotherWorld ? mock(Level.class) : f.level;
        LevelDBChunk placeholder = LevelDBChunk.getEmptyChunk(5, 0);
        placeholder.markChunkReadFailure(new DBException("placeholder survived flag switch"));
        when(target.getChunkIfLoaded(5, 0)).thenReturn(placeholder);
        when(target.getChunk(5, 0, true)).thenReturn(placeholder);
        f.server.asyncChunkLoadCompletion = false;
        assertTrue(f.entity.setPosition(new Vector3(79, 65, 1.5)));
        Vector3 position = anotherWorld ? new Position(81, 70, 2, target) : new Vector3(81, 70, 2);

        assertFalse(f.entity.setPosition(position));
        assertSame(f.level, f.entity.level);
        assertSame(f.source, f.entity.chunk);
        assertSame(f.entity, f.source.getEntities().get(f.entity.getId()));
        assertTrue(placeholder.getEntities().isEmpty());
        assertEquals(79, f.entity.x);
        assertEquals(65, f.entity.y);
        assertEquals(1.5, f.entity.z);
        verify(f.level, never()).removeEntity(f.entity);
        verify(target, times(anotherWorld ? 0 : 1)).addEntity(f.entity); // Constructor owns the initial add.
    }

    @Test
    void staleSnapshotIsNotUsedForDifferentChunkIdentity() throws Exception {
        Fixture f = new Fixture();
        LevelDBChunk replacement = LevelDBChunk.getEmptyChunk(8, 0, f.provider);
        f.source.removeEntity(f.entity);
        f.entity.chunk = replacement;
        replacement.addEntity(f.entity);
        when(f.level.getChunk(9, 0, true)).thenThrow(new DBException("unreadable replacement neighbour"));
        assertFalse(f.entity.setPosition(new Vector3(145, 90, 3)));
        assertSame(replacement, f.entity.chunk);
        assertEquals(8, (int) f.entity.x >> 4);
        assertEquals(90, f.entity.y, "stale position from the previous chunk must not be restored");
        assertSame(f.entity, replacement.getEntities().get(f.entity.getId()));
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void worldEntityUpdateContinuesAfterMoverMeetsUnreadableChunk(Failure failure) throws Exception {
        Fixture f = new Fixture();
        f.fail(failure);
        Entity mover = spy(f.entity);
        f.source.addEntity(mover);
        doReturn(false).when(mover).isActivationThrottled(anyInt());
        doAnswer(ignored -> {
            assertFalse(mover.setPosition(new Vector3(81, 70, 2)));
            return true;
        }).when(mover).onUpdate(anyInt());
        Entity healthy = mock(Entity.class);
        when(healthy.onUpdate(anyInt())).thenReturn(true);
        Level tickingLevel = mock(Level.class, CALLS_REAL_METHODS);
        Method update = Level.class.getDeclaredMethod("updateEntity", Entity.class, int.class);
        update.setAccessible(true);

        for (int tick = 1; tick <= 2; tick++) {
            int currentTick = tick;
            assertDoesNotThrow(() -> {
                assertEquals(Boolean.TRUE, update.invoke(tickingLevel, mover, currentTick));
                assertEquals(Boolean.TRUE, update.invoke(tickingLevel, healthy, currentTick));
            });
            verify(healthy).onUpdate(currentTick);
            assertSame(f.source, mover.chunk);
            assertSame(mover, f.source.getEntities().get(mover.getId()));
            assertEquals(65, mover.x);
        }
    }

    private enum Failure { EXCEPTION, NULL, QUARANTINE }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final LevelProvider provider = mock(LevelProvider.class);
        final Server server = mock(Server.class);
        final LevelDBChunk source;
        final Entity entity;

        Fixture() throws Exception {
            when(level.getServer()).thenReturn(MockServer.get());
            when(level.getDimensionData()).thenReturn(new DimensionData(0, -64, 319));
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
            when(level.getNearbyEntities(any(), any(), anyBoolean(), anyBoolean())).thenReturn(new Entity[0]);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            Field converted = Level.class.getDeclaredField("isBeingConverted");
            converted.setAccessible(true);
            converted.setBoolean(level, true);
            when(provider.getLevel()).thenReturn(level);
            source = LevelDBChunk.getEmptyChunk(4, 0, provider);
            CompoundTag nbt = new CompoundTag()
                    .putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", 65)).add(new DoubleTag("", 64)).add(new DoubleTag("", 1)))
                    .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", 0)).add(new DoubleTag("", 0)).add(new DoubleTag("", 0)))
                    .putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("", 0)).add(new FloatTag("", 0)));
            entity = new EntityCow(source, nbt);
            server.asyncChunkLoadCompletion = true;
            entity.server = server;
            entity.checkChunks();
            assertSame(entity, source.getEntities().get(entity.getId()));
        }

        void fail(Failure failure) {
            switch (failure) {
                case EXCEPTION -> when(level.getChunk(5, 0, true)).thenThrow(new DBException("unreadable destination"));
                case NULL -> when(level.getChunk(5, 0, true)).thenReturn(null);
                case QUARANTINE -> {
                    LevelDBChunk destination = LevelDBChunk.getEmptyChunk(5, 0, provider);
                    destination.markChunkReadFailure(new DBException("quarantined destination"));
                    when(level.getChunk(5, 0, true)).thenReturn(destination);
                }
            }
        }
    }
}
