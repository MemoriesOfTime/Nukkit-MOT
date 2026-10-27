package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.serverconfig.ServerConfig;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage using the native spawn and movement lifecycle.
 * Constructor, Entity.init, doFirstSpawn, chunk-ready retirement, rotation,
 * fastMove, PlayerMoveEvent dispatch, cancellation and rollback remain native.
 * Only world/server/network I/O is fake. No assignment to lastXYZ or firstMove.
 * Login's non-positional AdventureSettings/food/loggedIn prerequisites are supplied;
 * authentication, resource-pack negotiation and onUpdate tick scheduling are outside scope.
 */
class PlayerBootstrapMovementRollbackTest {
    private static Server server;
    private static Server previous;

    @BeforeAll static void initializeSdk() throws Exception {
        MockServer.init();
        var field = Server.class.getDeclaredField("instance");
        field.setAccessible(true);
        previous = (Server) field.get(null);
        server = mock(Server.class);
        field.set(null, server);
        defaults();
    }

    @AfterAll static void restoreServer() throws Exception {
        var field = Server.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, previous);
    }

    private static void defaults() {
        server.maxPendingChunkWrites = 128;
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        when(server.getGamemode()).thenReturn(0);
        when(server.getDifficulty()).thenReturn(2);
        when(server.isPrimaryThread()).thenReturn(true);
        when(server.getViewDistance()).thenReturn(1);
        when(server.getDataPath()).thenReturn(System.getProperty("java.io.tmpdir"));
        when(server.getOnlinePlayers()).thenReturn(new HashMap<>());
        var config = new ServerConfig();
        config.customBlockSettings().autoDownloadVanillaPalette(false);
        when(server.getServerConfig()).thenReturn(config);
    }

    @ParameterizedTest @CsvSource({"1,2168", "-1,2168", "0,2168", "1,786"})
    void yawFirstThenHeldSmallMoveRetainsAuthoritativeSpawn(int sign, int protocol) throws Exception {
        try (Fixture f = new Fixture(sign, 76, 76, protocol)) {
            Vector3 before = f.player.getPosition();
            f.player.rotateStationary();

            assertPoint(before, f.player);
            assertFalse(f.player.first());
            f.restricted = true;
            f.player.packets.clear();
            f.player.input(before.add(0.125, 0, 0));

            assertEquals(1, f.moves.size(), "The real native dispatch must reach the registered listener");
            assertEquals(1, f.cancelled);
            assertAll(
                    () -> assertPoint(before, f.moves.get(0).getFrom()),
                    () -> assertPoint(before, f.player),
                    () -> f.assertCorrectionAt(before));
        }
    }

    @Test void yawFirstThenVerticalMoveIsNotMistakenForRestrictedHorizontalMove() throws Exception {
        try (Fixture f = new Fixture(1)) {
            Vector3 before = f.player.getPosition();
            f.player.rotateStationary();
            f.restricted = true;
            f.player.input(before.add(0, 0.125, 0));

            assertEquals(1, f.moves.size());
            assertAll(
                    () -> assertEquals(0, f.cancelled, "Actual X/Z did not change"),
                    () -> assertPoint(before.add(0, 0.125, 0), f.player));
        }
    }

    @Test void actualInitAndFirstSpawnCanReachStationaryRotationWithoutPositionalMove() throws Exception {
        try (Fixture f = new Fixture(1)) {
            assertTrue(f.player.first());
            assertTrue(f.player.spawned);
            assertNull(f.player.teleportPosition);
            assertPoint(f.start, f.player);
            assertEquals(0, f.moves.size());

            f.player.rotateStationary();
            assertFalse(f.player.first());
            assertPoint(f.start, f.player);
            assertEquals(0, f.moves.size());

        }
    }

    @Test void acceptedFirstPositionalMoveSeedsNextCancellation() throws Exception {
        try (Fixture f = new Fixture(1)) {
            Vector3 accepted = f.start.add(0.125, 0, 0);
            f.player.input(accepted);
            assertPoint(accepted, f.player);
            assertFalse(f.player.first());
            f.restricted = true;
            f.player.input(accepted.add(0.125, 0, 0));
            assertEquals(1, f.cancelled);
            assertPoint(accepted, f.player);
        }
    }

    @Test void acceptedInitialDropSeedsRollbackBeforeYawAndCancellation() throws Exception {
        try (Fixture f = new Fixture(1, 80, 80)) {
            Vector3 dropped = new Vector3(f.start.x, 76.5, f.start.z);
            f.player.input(dropped);
            assertPoint(dropped, f.player);
            f.player.rotateStationary();
            f.restricted = true;
            f.player.input(dropped.add(0.125, 0, 0));
            assertEquals(1, f.cancelled);
            assertPoint(dropped, f.player);
        }
    }

    @Test void realSpawnSurfaceRelocationThenYawMustNotRollBackToOrigin() throws Exception {
        try (Fixture f = new Fixture(1, 80, 76.5)) {

            f.player.rotateStationary();
            f.restricted = true;
            f.player.input(f.start.add(0.125, 0, 0));
            assertEquals(1, f.cancelled);
            assertPoint(f.start, f.player);
        }
    }

    @Test void noRestrictionAcceptsSmallMoveAfterInitialYaw() throws Exception {
        try (Fixture f = new Fixture(1)) {
            f.player.rotateStationary();
            Vector3 wanted = f.start.add(0.125, 0, 0);
            f.player.input(wanted);
            assertEquals(1, f.moves.size());
            assertEquals(0, f.cancelled);
            assertPoint(wanted, f.player);
        }
    }

    @Test void realTeleportBeforeCancellationSeedsRollbackPosition() throws Exception {
        try (Fixture f = new Fixture(1)) {
            Location destination = new Location(f.start.x, 76.25, f.start.z, 0, 0, f.level);
            assertTrue(f.player.teleport(destination, null));
            f.player.retireReadyTeleport();
            f.player.rotateStationary();
            f.restricted = true;
            f.player.input(destination.add(0.125, 0, 0));
            assertEquals(1, f.cancelled);
            assertPoint(destination, f.player);
        }
    }

    @Test void realMovementPublicationSeedPreventsOriginRollback() throws Exception {
        try (Fixture f = new Fixture(1)) {
            f.player.updateMovement();
            f.player.rotateStationary();
            f.restricted = true;
            f.player.input(f.start.add(0.125, 0, 0));
            assertEquals(1, f.cancelled);
            assertPoint(f.start, f.player);
        }
    }

    private static void assertPoint(Vector3 expected, Vector3 actual) {
        assertAll(() -> assertEquals(expected.x, actual.x, 1.0e-7, "x"),
                () -> assertEquals(expected.y, actual.y, 1.0e-7, "y"),
                () -> assertEquals(expected.z, actual.z, 1.0e-7, "z"));
    }

    private static final class Fixture implements AutoCloseable {
        final Vector3 start;
        final Level level = mock(Level.class);
        final Plugin plugin = mock(Plugin.class);
        final PluginManager manager;
        final NativePlayer player;
        final List<PlayerMoveEvent> moves = new ArrayList<>();
        boolean restricted;
        int cancelled;

        Fixture(int sign) throws Exception { this(sign, 76, 76); }

        Fixture(int sign, double savedY, double spawnY) throws Exception {
            this(sign, savedY, spawnY, ProtocolInfo.v1_26_40);
        }

        Fixture(int sign, double savedY, double spawnY, int protocol) throws Exception {
            reset(server);
            defaults();
            start = new Vector3(sign * 32.5, spawnY, sign * 48.5);
            manager = new PluginManager(server, null);
            when(server.getPluginManager()).thenReturn(manager);
            when(plugin.isEnabled()).thenReturn(true);
            when(server.getDefaultLevel()).thenReturn(level);
            when(level.getServer()).thenReturn(server);
            when(level.getName()).thenReturn("bootstrap-fixture");
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getSafeSpawn(any(Vector3.class))).thenAnswer(c ->
                    Position.fromObject(start, level));
            when(level.getSafeSpawn()).thenReturn(Position.fromObject(start, level));
            when(level.getVibrationManager()).thenReturn(mock(cn.nukkit.level.vibration.VibrationManager.class));
            var updates = Level.class.getDeclaredField("updateEntities");
            updates.setAccessible(true);
            updates.set(level, new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<Entity>());
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            when(level.getProvider()).thenReturn(provider);
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(chunk.isInitialized()).thenReturn(true);
            when(chunk.isGenerated()).thenReturn(true);
            when(chunk.isPopulated()).thenReturn(true);
            when(chunk.getX()).thenReturn(start.getChunkX());
            when(chunk.getZ()).thenReturn(start.getChunkZ());
            when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenReturn(chunk);
            when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenAnswer(c -> new HashMap<Integer, Player>());
            when(level.getChunkEntities(anyInt(), anyInt())).thenReturn(new HashMap<>());
            when(level.getChunkEntities(anyInt(), anyInt(), anyBoolean())).thenReturn(new HashMap<>());
            when(level.getNearbyEntities(any(AxisAlignedBB.class), any(Entity.class))).thenReturn(new Entity[0]);
            when(level.getNearbyEntities(any(AxisAlignedBB.class))).thenReturn(new Entity[0]);
            when(level.getCollidingEntities(any(AxisAlignedBB.class))).thenReturn(new Entity[0]);
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> block(c.getArgument(0)));
            when(level.getBlock(any(Vector3.class), anyBoolean())).thenAnswer(c -> block(c.getArgument(0)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(c ->
                    block(new Vector3(c.getArgument(0, Integer.class), c.getArgument(1, Integer.class), c.getArgument(2, Integer.class))));
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c ->
                    block(new Vector3(c.getArgument(0, Integer.class), c.getArgument(1, Integer.class), c.getArgument(2, Integer.class))));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(c ->
                    block(new Vector3(c.getArgument(0, Integer.class), c.getArgument(1, Integer.class), c.getArgument(2, Integer.class))));
            SourceInterface source = mock(SourceInterface.class);
            when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
            player = new NativePlayer(source, protocol);
            manager.registerEvent(PlayerMoveEvent.class, new Listener() { }, EventPriority.LOWEST,
                    (listener, event) -> {
                        PlayerMoveEvent move = (PlayerMoveEvent) event;
                        if (move.getPlayer() != player) return;
                        moves.add(move);
                        // Model a plugin that cancels horizontal movement.
                        if (restricted && (move.getFrom().x != move.getTo().x || move.getFrom().z != move.getTo().z)) {
                            move.setCancelled();
                            cancelled++;
                        }
                    }, plugin);
            Vector3 saved = new Vector3(start.x, savedY, start.z);
            player.initialize(chunk, saved);
            assertTrue(player.first(), "No fixture manipulation retires native bootstrap");
            assertPoint(saved, player);
            player.spawnNative();
            assertPoint(start, player);
            player.retireReadyTeleport();
        }

        private Block block(Vector3 p) {
            return Block.get(p.getFloorY() < 76 ? Block.STONE : Block.AIR, 0, level,
                    p.getFloorX(), p.getFloorY(), p.getFloorZ());
        }
        void assertCorrectionAt(Vector3 point) {
            List<MovePlayerPacket> corrections = player.packets.stream()
                    .filter(MovePlayerPacket.class::isInstance).map(MovePlayerPacket.class::cast).toList();
            assertEquals(1, corrections.size(), "Exactly the cancellation correction is captured after input");
            MovePlayerPacket packet = corrections.get(0);
            assertEquals(MovePlayerPacket.MODE_NORMAL, packet.mode);
            assertEquals(player.getId(), packet.eid);
            assertEquals((float) point.x, packet.x, 1.0e-5f);
            assertEquals((float) (point.y + 0.00001 + player.wireOffset()), packet.y, 1.0e-5f);
            assertEquals((float) point.z, packet.z, 1.0e-5f);
        }
        @Override public void close() { HandlerList.unregisterAll(plugin); }
    }

    private static final class NativePlayer extends Player {
        final List<DataPacket> packets = new ArrayList<>();
        NativePlayer(SourceInterface source, int selectedProtocol) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19199));
            protocol = selectedProtocol;
            gameVersion = GameVersion.byProtocol(protocol, false);
            // Same non-positional prerequisite established by processLogin before Entity.init.
            adventureSettings = new AdventureSettings(this);
        }
        void initialize(BaseFullChunk chunk, Vector3 point) {
            init(chunk, Entity.getDefaultNBT(point));
            foodData = new cn.nukkit.PlayerFood(this, 20, 20);
            loggedIn = true;
        }
        void spawnNative() { doFirstSpawn(); }
        void retireReadyTeleport() {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                usedChunks.put(Level.chunkHash(getChunkX() + dx, getChunkZ() + dz), Boolean.TRUE);
            if (teleportPosition != null) assertTrue(checkTeleportPosition());
            assertNull(teleportPosition);
        }
        void rotateStationary() { setRotation(45, 0); handleMovement(getPosition(), 1); }
        void input(Vector3 point) { handleMovement(point, 1); }
        boolean first() { return firstMove; }
        float wireOffset() { return getBaseOffset(); }
        @Override public boolean dataPacket(DataPacket packet) { packets.add(packet); return true; }
    }
}
