package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockNetherPortal;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityPortalEnterEvent;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.utils.CollisionHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerNetherPortalTransferTest {

    private Player player;
    private CollisionHelper collisions;
    private MockedStatic<BlockNetherPortal> portals;
    private final Queue<AsyncTask> lookups = new ArrayDeque<>();
    private final Queue<Runnable> transfers = new ArrayDeque<>();

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        Server server = mock(Server.class);
        server.vanillaPortals = true;
        server.portalTicks = 80;
        when(server.isNetherAllowed()).thenReturn(true);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));

        ServerScheduler scheduler = mock(ServerScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> {
            lookups.add(invocation.getArgument(1));
            return null;
        }).when(scheduler).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), any(AsyncTask.class));
        doAnswer(invocation -> {
            transfers.add(invocation.getArgument(1));
            return null;
        }).when(scheduler).scheduleTask(eq(InternalPlugin.INSTANCE), any(Runnable.class));

        player = mock(Player.class);
        Field serverField = Entity.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(player, server);
        when(player.getServer()).thenReturn(server);
        when(player.isOnline()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);
        player.level = mock(Level.class);
        player.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 0.6, 65.8, 0.6);
        collisions = mock(CollisionHelper.class);
        when(player.getCollisionHelper()).thenReturn(collisions);
        doCallRealMethod().when(player).checkBlockCollision();
        portals = mockStatic(BlockNetherPortal.class);
    }

    @AfterEach
    void tearDown() {
        if (portals != null) {
            portals.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leavingPortalInSameWorldCancelsPendingTransfer(boolean lookupAlreadyFinished) {
        Position target = enterPortal();
        scheduleTransfer();
        if (lookupAlreadyFinished) {
            lookups.remove().onRun();
        }

        leavePortal();
        if (!lookupAlreadyFinished) {
            lookups.remove().onRun();
        }
        transfers.remove().run();

        portals.verify(() -> BlockNetherPortal.findNearestPortal(target));
        portals.verify(() -> BlockNetherPortal.spawnPortal(any()), never());
        verify(player, never()).teleport(any(Position.class), eq(TeleportCause.NETHER_PORTAL));
        assertNull(player.portalPos);
        assertEquals(0, player.inPortalTicks);
    }

    @ParameterizedTest
    @CsvSource({
            "false, false, false", "true, false, false",
            "false, true, false", "true, true, false",
            "false, true, true", "true, true, true"
    })
    void successfulTeleportCancelsTransferBeforeCollisionUpdate(boolean immediate, boolean lookupFinished, boolean noEvent) {
        enableRealTeleports();
        enterPortal();
        scheduleTransfer();
        if (lookupFinished) {
            lookups.remove().onRun();
        }

        teleportAway(immediate, noEvent ? null : TeleportCause.PLUGIN);
        assertEquals(100, player.x);
        if (!lookupFinished) {
            lookups.remove().onRun();
        }
        transfers.remove().run();

        portals.verify(() -> BlockNetherPortal.spawnPortal(any()), never());
        verify(player, never()).teleport(any(Position.class), eq(TeleportCause.NETHER_PORTAL));
        assertNull(player.portalPos);
        assertEquals(0, player.inPortalTicks);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void teleportFromPortalEnterEventDoesNotConsumeAnotherCountdown(boolean immediate) {
        enableRealTeleports();
        enterPortal();
        PluginManager pluginManager = player.getServer().getPluginManager();
        doAnswer(invocation -> {
            teleportAway(immediate, TeleportCause.PLUGIN);
            return null;
        }).when(pluginManager).callEvent(any(EntityPortalEnterEvent.class));

        player.inPortalTicks = 79;
        player.checkBlockCollision();

        assertEquals(100, player.x);
        assertNull(player.portalPos);
        assertEquals(0, player.inPortalTicks);
        assertTrue(lookups.isEmpty());
        assertTrue(transfers.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "false, true", "true, true"})
    void unsuccessfulTeleportPreservesPendingAttempt(boolean immediate, boolean cancelledByEvent) {
        enableRealTeleports();
        Position target = enterPortal();
        scheduleTransfer();
        if (cancelledByEvent) {
            PluginManager pluginManager = player.getServer().getPluginManager();
            doAnswer(invocation -> {
                invocation.<Event>getArgument(0).setCancelled();
                return null;
            }).when(pluginManager).callEvent(any(Event.class));
        } else {
            when(player.setPositionAndRotation(any(Vector3.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(false);
        }

        teleportAway(immediate, TeleportCause.PLUGIN);

        assertEquals(0, player.x);
        assertSame(target, player.portalPos);
        assertEquals(81, player.inPortalTicks);
        finishLookup();
        portals.verify(() -> BlockNetherPortal.spawnPortal(target));
        verify(player).teleport(target.add(1.5, 1, 0.5), TeleportCause.NETHER_PORTAL);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldCallbackPreservesNewPortalAttempt(boolean changeWorld) {
        enterPortal();
        scheduleTransfer();
        leavePortal();
        if (changeWorld) {
            player.level = mock(Level.class);
        }
        Position nextTarget = enterPortal();

        finishLookup();

        assertSame(nextTarget, player.portalPos);
        assertEquals(40, player.inPortalTicks);
        portals.verify(() -> BlockNetherPortal.spawnPortal(any()), never());
        verify(player, never()).teleport(any(Position.class), eq(TeleportCause.NETHER_PORTAL));

        scheduleTransfer();
        finishLookup();

        portals.verify(() -> BlockNetherPortal.spawnPortal(nextTarget));
        verify(player).teleport(nextTarget.add(1.5, 1, 0.5), TeleportCause.NETHER_PORTAL);
        assertNull(player.portalPos);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlyNewestScheduledAttemptTransfersPlayer(boolean newerCompletesFirst) {
        enterPortal();
        scheduleTransfer();
        leavePortal();
        Position nextTarget = enterPortal();
        scheduleTransfer();
        lookups.remove().onRun();
        Runnable oldTransfer = transfers.remove();

        if (newerCompletesFirst) {
            finishLookup();
            oldTransfer.run();
        } else {
            oldTransfer.run();
            assertSame(nextTarget, player.portalPos);
            assertEquals(81, player.inPortalTicks);
            finishLookup();
        }

        portals.verify(() -> BlockNetherPortal.spawnPortal(any()), times(1));
        verify(player, times(1)).teleport(any(Position.class), eq(TeleportCause.NETHER_PORTAL));
        verify(player).teleport(nextTarget.add(1.5, 1, 0.5), TeleportCause.NETHER_PORTAL);
        assertNull(player.portalPos);
        assertEquals(81, player.inPortalTicks);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void activeAttemptTransfersToExistingOrNewPortal(boolean portalExists) {
        enableRealTeleports();
        Position target = enterPortal();
        when(target.level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        doCallRealMethod().when(player).teleport(any(Position.class), eq(TeleportCause.NETHER_PORTAL));
        Position found = new Position(20, 70, 20, target.level);
        Position safe = new Position(20, 65, 20, target.level);
        if (portalExists) {
            portals.when(() -> BlockNetherPortal.findNearestPortal(target)).thenReturn(found);
            portals.when(() -> BlockNetherPortal.getSafePortal(found)).thenReturn(safe);
        }

        scheduleTransfer();
        finishLookup();

        portals.verify(() -> BlockNetherPortal.findNearestPortal(target));
        if (portalExists) {
            portals.verify(() -> BlockNetherPortal.spawnPortal(any()), never());
            verify(player).teleport(safe, TeleportCause.NETHER_PORTAL);
        } else {
            portals.verify(() -> BlockNetherPortal.spawnPortal(target));
            verify(player).teleport(target.add(1.5, 1, 0.5), TeleportCause.NETHER_PORTAL);
        }
        assertNull(player.portalPos);
        assertSame(target.level, player.level);
        assertEquals(81, player.inPortalTicks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"offline", "dead", "changed-world"})
    void invalidPlayerCancelsCurrentAttempt(String state) {
        enterPortal();
        scheduleTransfer();
        switch (state) {
            case "offline" -> when(player.isOnline()).thenReturn(false);
            case "dead" -> when(player.isAlive()).thenReturn(false);
            case "changed-world" -> player.level = mock(Level.class);
            default -> throw new AssertionError(state);
        }

        finishLookup();

        portals.verify(() -> BlockNetherPortal.spawnPortal(any()), never());
        verify(player, never()).teleport(any(Position.class), eq(TeleportCause.NETHER_PORTAL));
        assertNull(player.portalPos);
        assertEquals(0, player.inPortalTicks);
    }

    private Position enterPortal() {
        Position target = new Position(10, 64, 10, mock(Level.class));
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.isGenerated()).thenReturn(true);
        when(target.level.getChunk(anyInt(), anyInt(), eq(false))).thenReturn(chunk);
        when(player.level.calculatePortalMirror(player)).thenReturn(target);
        when(collisions.getCollisionBlocks()).thenReturn(new Block[]{Block.get(Block.NETHER_PORTAL)});
        player.inPortalTicks = 39;
        player.checkBlockCollision();
        assertSame(target, player.portalPos);
        return target;
    }

    private void teleportAway(boolean immediate, TeleportCause cause) {
        Location destination = new Location(100, 80.5, 100, player.level);
        if (immediate) {
            player.teleportImmediate(destination, cause);
        } else {
            player.teleport(destination, cause);
        }
    }

    private void enableRealTeleports() {
        player.temporalVector = new Vector3();
        player.dummyBossBars = new HashMap<>();
        when(player.getLocation()).thenAnswer(invocation -> new Location(player.x, player.y, player.z, player.level));
        when(player.getLevel()).thenAnswer(invocation -> player.level);
        when(player.level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        when(player.setPositionAndRotation(any(Vector3.class), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    Position to = invocation.getArgument(0);
                    player.x = to.x;
                    player.y = to.y;
                    player.z = to.z;
                    player.level = to.level;
                    return true;
                });
        doCallRealMethod().when(player).teleport(any(Location.class), nullable(TeleportCause.class));
        doCallRealMethod().when(player).teleportImmediate(any(Location.class), nullable(TeleportCause.class));
    }

    private void scheduleTransfer() {
        player.inPortalTicks = 79;
        player.checkBlockCollision();
        assertFalse(lookups.isEmpty());
    }

    private void leavePortal() {
        when(collisions.getCollisionBlocks()).thenReturn(new Block[0]);
        player.checkBlockCollision();
    }

    private void finishLookup() {
        lookups.remove().onRun();
        transfers.remove().run();
    }
}
