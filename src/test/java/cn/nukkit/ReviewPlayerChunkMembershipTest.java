package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import org.iq80.leveldb.DBException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Player has its own checkChunks override; entity regressions alone do not cover it. */
class ReviewPlayerChunkMembershipTest {
    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void rejectedDestinationRetainsPlayerAndRestoresPosition(Failure failure) throws Exception {
        Fixture f = new Fixture();
        assertTrue(f.player.setPosition(new Vector3(79.25, 65, 1.5)));
        // Network coordinates are not the last safe position.
        f.player.lastX = 99;
        f.player.lastY = 100;
        f.player.lastZ = 99;
        f.fail(failure);
        for (int tick = 0; tick < 2; tick++) {
            f.player.blocksAround = new ArrayList<>();
            f.player.collisionBlocks = new ArrayList<>();
            assertFalse(assertDoesNotThrow(() -> f.player.setPosition(new Vector3(81, 70, 2))));
            assertSame(f.source, f.player.chunk);
            assertSame(f.player, f.source.getEntities().get(f.player.getId()));
            assertEquals(79.25, f.player.x);
            assertEquals(65, f.player.y);
            assertEquals(1.5, f.player.z);
            assertEquals(79.25 - 0.6f / 2d, f.player.boundingBox.getMinX());
            assertEquals(65, f.player.boundingBox.getMinY());
            assertEquals(1.5 - 0.6f / 2d, f.player.boundingBox.getMinZ());
            assertNull(f.player.blocksAround);
            assertNull(f.player.collisionBlocks);
            assertTrue(f.destination.getEntities().isEmpty());
        }
        assertTrue(f.player.setPosition(new Vector3(78.5, 65, 1.5)));
        assertSame(f.player, f.source.getEntities().get(f.player.getId()));
    }

    @Test
    void healthyTransferLoadsBeforeRemovingPlayerAndRefreshesRollbackPosition() throws Exception {
        Fixture f = new Fixture();
        when(f.level.getChunk(5, 0, true)).thenAnswer(ignored -> {
            assertSame(f.player, f.source.getEntities().get(f.player.getId()));
            return f.destination;
        });
        assertTrue(f.player.setPosition(new Vector3(81, 70, 2)));
        assertFalse(f.source.getEntities().containsKey(f.player.getId()));
        assertSame(f.destination, f.player.chunk);
        assertSame(f.player, f.destination.getEntities().get(f.player.getId()));
        when(f.level.getChunk(6, 0, true)).thenThrow(new DBException("next destination failed"));
        assertFalse(f.player.setPosition(new Vector3(97, 80, 3)));
        assertSame(f.player, f.destination.getEntities().get(f.player.getId()));
        assertEquals(81, f.player.x);
        assertEquals(70, f.player.y);
        assertEquals(2, f.player.z);
    }

    @Test
    void negativeFractionCrossWorldPreflightChecksPlayersFloorChunk() throws Exception {
        Fixture f = new Fixture();
        Server server = mock(Server.class);
        server.asyncChunkLoadCompletion = true;
        Field serverField = Entity.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(f.player, server);
        Level targetLevel = mock(Level.class);
        LevelDBChunk quarantined = LevelDBChunk.getEmptyChunk(-1, 0);
        quarantined.markChunkReadFailure(new DBException("quarantined negative target"));
        when(targetLevel.getChunk(-1, 0, true)).thenReturn(quarantined);

        assertFalse(f.player.setPosition(new Position(-0.5, 70, 2, targetLevel)));
        assertSame(f.level, f.player.level);
        assertSame(f.source, f.player.chunk);
        assertSame(f.player, f.source.getEntities().get(f.player.getId()));
        assertEquals(65, f.player.x);
        assertEquals(64, f.player.y);
        assertEquals(1, f.player.z);
        verify(targetLevel).getChunk(-1, 0, true);
        verify(targetLevel, never()).getChunk(0, 0, true);
        verify(f.level, never()).removeEntity(f.player);
        verify(targetLevel, never()).addEntity(f.player);
        verifyNoInteractions(server);
    }

    @Test
    void healthyNegativeFractionMoveReportsSuccessForPlayersFloorChunk() throws Exception {
        Fixture f = new Fixture();
        LevelDBChunk destination = LevelDBChunk.getEmptyChunk(-1, 0);
        when(f.level.getChunk(-1, 0, true)).thenReturn(destination);
        assertTrue(f.player.setPosition(new Vector3(-0.5, 70, 2)));
        assertSame(destination, f.player.chunk);
        assertSame(f.player, destination.getEntities().get(f.player.getId()));
        assertFalse(f.source.getEntities().containsKey(f.player.getId()));
    }

    @Test
    void disabledFlagRemovesPlayerBeforeReadingDestination() throws Exception {
        Fixture f = new Fixture();
        f.server.asyncChunkLoadCompletion = false;
        when(f.level.getChunk(5, 0, true)).thenAnswer(ignored -> {
            assertFalse(f.source.getEntities().containsKey(f.player.getId()));
            return f.destination;
        });

        assertTrue(f.player.setPosition(new Vector3(81, 70, 2)));
        assertSame(f.destination, f.player.chunk);
        assertSame(f.player, f.destination.getEntities().get(f.player.getId()));
    }

    @Test
    void disabledFlagKeepsLegacySuccessForNullDestination() throws Exception {
        Fixture f = new Fixture();
        f.server.asyncChunkLoadCompletion = false;
        when(f.level.getChunk(5, 0, true)).thenReturn(null);

        assertTrue(f.player.setPosition(new Vector3(81, 70, 2)));
        assertNull(f.player.chunk);
        assertFalse(f.source.getEntities().containsKey(f.player.getId()));
        assertEquals(81, f.player.x);
        assertEquals(70, f.player.y);
        assertEquals(2, f.player.z);
    }

    @Test
    void disabledFlagKeepsLegacyReadExceptionPropagation() throws Exception {
        Fixture f = new Fixture();
        f.server.asyncChunkLoadCompletion = false;
        DBException failure = new DBException("legacy unreadable destination");
        when(f.level.getChunk(5, 0, true)).thenThrow(failure);

        assertSame(failure, assertThrows(DBException.class,
                () -> f.player.setPosition(new Vector3(81, 70, 2))));
        assertSame(f.source, f.player.chunk);
        assertFalse(f.source.getEntities().containsKey(f.player.getId()));
        assertEquals(81, f.player.x);
    }

    @Test
    void lifecycleFailureOnRealDestinationDoesNotRejectPlayerMovement() throws Exception {
        Fixture f = new Fixture();
        f.destination.markChunkLoadFailure(new IllegalStateException("chunk event failed"));
        when(f.level.getChunk(5, 0, true)).thenReturn(f.destination);

        assertTrue(f.player.setPosition(new Vector3(81, 70, 2)));
        assertSame(f.destination, f.player.chunk);
        assertSame(f.player, f.destination.getEntities().get(f.player.getId()));
        assertFalse(f.source.getEntities().containsKey(f.player.getId()));
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
        assertTrue(f.player.setPosition(new Vector3(79, 65, 1.5)));
        Vector3 position = anotherWorld ? new Position(81, 70, 2, target) : new Vector3(81, 70, 2);

        assertFalse(f.player.setPosition(position));
        assertSame(f.level, f.player.level);
        assertSame(f.source, f.player.chunk);
        assertSame(f.player, f.source.getEntities().get(f.player.getId()));
        assertTrue(placeholder.getEntities().isEmpty());
        assertEquals(79, f.player.x);
        assertEquals(65, f.player.y);
        assertEquals(1.5, f.player.z);
        verify(f.level, never()).removeEntity(f.player);
        verify(target, never()).addEntity(f.player);
    }

    private enum Failure { EXCEPTION, NULL, QUARANTINE }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final LevelDBChunk source = LevelDBChunk.getEmptyChunk(4, 0);
        final LevelDBChunk destination = LevelDBChunk.getEmptyChunk(5, 0);
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);

        Fixture() throws Exception {
            server.asyncChunkLoadCompletion = true;
            Field serverField = Entity.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(player, server);
            player.level = level;
            player.chunk = source;
            player.x = 65;
            player.y = 64;
            player.z = 1;
            player.scale = 1;
            player.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0, 0, 0);
            doReturn(0.6f).when(player).getWidth();
            doReturn(1.8f).when(player).getHeight();
            // Exercise the established-player branch, including the viewer-map update.
            player.justCreated = false;
            Field spawned = Entity.class.getDeclaredField("hasSpawned");
            spawned.setAccessible(true);
            spawned.set(player, new ConcurrentHashMap<Integer, Player>());
            when(level.getChunkPlayers(anyInt(), anyInt())).thenAnswer(ignored -> new HashMap<Integer, Player>());
            source.addEntity(player);
            player.checkChunks();
        }

        void fail(Failure failure) {
            switch (failure) {
                case EXCEPTION -> when(level.getChunk(5, 0, true)).thenThrow(new DBException("unreadable destination"));
                case NULL -> when(level.getChunk(5, 0, true)).thenReturn(null);
                case QUARANTINE -> {
                    destination.markChunkReadFailure(new DBException("quarantined destination"));
                    when(level.getChunk(5, 0, true)).thenReturn(destination);
                }
            }
        }
    }
}
