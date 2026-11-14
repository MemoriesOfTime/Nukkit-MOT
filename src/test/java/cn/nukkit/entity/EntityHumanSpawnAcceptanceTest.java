package cn.nukkit.entity;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.SimpleCommandMap;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.AddPlayerPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerListPacket;
import cn.nukkit.network.protocol.PlayerSkinPacket;
import cn.nukkit.network.protocol.RemoveEntityPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.LoginChainData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses actual Player.dataPacket and actual registered public send-event listeners. */
class EntityHumanSpawnAcceptanceTest {

    private Server server;
    private Level level;
    private Plugin plugin;
    private final List<DataPacket> delivered = new ArrayList<>();
    private final List<Runnable> delayed = new ArrayList<>();
    private Consumer<DataPacketSendEvent> onSend = event -> { };
    private Consumer<EntitySpawnEvent> onSpawn = event -> { };
    private Consumer<DataPacket> transport = packet -> { };

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        MockServer.reset();
        server = MockServer.get();
        level = server.getDefaultLevel();
        when(level.getServer()).thenReturn(server);
        when(server.getViewDistance()).thenReturn(2);
        Field converting = Level.class.getDeclaredField("isBeingConverted");
        converting.setAccessible(true);
        converting.setBoolean(level, true);
        doCallRealMethod().when(server).updatePlayerListData(
                any(UUID.class), anyLong(), anyString(), any(Skin.class), any(Player[].class));
        doCallRealMethod().when(server).updatePlayerListData(
                any(UUID.class), anyLong(), anyString(), any(Skin.class), anyString(), any(Player[].class));
        doCallRealMethod().when(server).updatePlayerListData(any(PlayerListPacket.Entry.class), any(Player[].class));
        doCallRealMethod().when(server).removePlayerListData(any(UUID.class), any(Player.class));
        doCallRealMethod().when(server).removePlayerListData(any(UUID.class), any(Player[].class));

        PluginManager manager = new PluginManager(server, mock(SimpleCommandMap.class));
        when(server.getPluginManager()).thenReturn(manager);
        server.callDataPkSendEv = true;
        plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        manager.registerEvent(DataPacketSendEvent.class, new Listener() { }, EventPriority.NORMAL,
                (listener, event) -> onSend.accept((DataPacketSendEvent) event), plugin);
        manager.registerEvent(EntitySpawnEvent.class, new Listener() { }, EventPriority.NORMAL,
                (listener, event) -> onSpawn.accept((EntitySpawnEvent) event), plugin);

        ServerScheduler scheduler = mock(ServerScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.scheduleDelayedTask(eq(InternalPlugin.INSTANCE), any(Runnable.class), anyInt()))
                .thenAnswer(invocation -> {
                    delayed.add(invocation.getArgument(1));
                    return mock(TaskHandler.class);
                });
    }

    @AfterEach
    void removeOnlyOwnListener() {
        HandlerList.unregisterAll(plugin);
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_21_124", "V1_26_10", "V1_26_40", "V1_26_50", "V1_21_124_NETEASE"})
    void refusedAddPlayerRemainsRetryable(GameVersion version) {
        EntityHuman npc = npc(version);
        Player viewer = viewer(version);
        AtomicBoolean refused = new AtomicBoolean();
        onSend = event -> {
            if (event.getPacket() instanceof AddPlayerPacket && refused.compareAndSet(false, true)) {
                event.setCancelled();
            }
        };

        npc.spawnTo(viewer);
        assertTrue(refused.get(), "the native send event must reach the public veto listener");
        assertEquals(0, adds());
        assertFalse(npc.getViewers().containsKey(viewer.getLoaderId()), "refused actor must not be cached as spawned");

        npc.spawnTo(viewer);
        assertEquals(1, adds(), "an independent retry must submit the actor exactly once");
        assertSame(viewer, npc.getViewers().get(viewer.getLoaderId()));
    }

    @Test
    void invalidSkinDoesNotPoisonLaterValidSpawn() {
        EntityHuman npc = npc(GameVersion.V1_26_10);
        Player viewer = viewer(GameVersion.V1_26_10);
        npc.setSkin(new Skin());
        assertThrows(IllegalStateException.class, () -> npc.spawnTo(viewer));
        assertFalse(npc.getViewers().containsKey(viewer.getLoaderId()));
        npc.setSkin(visibleSkin());
        npc.spawnTo(viewer);
        assertEquals(1, adds());
    }

    @Test
    void constructorSpawnEventCanSpawnInitializedHuman() {
        Player viewer = viewer(GameVersion.V1_26_10);
        AtomicReference<EntityHuman> observed = new AtomicReference<>();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        AtomicBoolean initialized = new AtomicBoolean();
        onSpawn = event -> {
            if (event.getEntity() instanceof EntityHuman human && !(human instanceof Player)) {
                observed.set(human);
                initialized.set(human.getSkin().isValid() && human.getInventory() != null
                        && human.getOffhandInventory() != null);
                try {
                    human.spawnTo(viewer);
                } catch (RuntimeException | Error failure) {
                    // Capture before PluginManager logs listener errors, so failure describes
                    // the native constructor callback rather than a mock logger dependency.
                    callbackFailure.set(failure);
                }
            }
        };
        EntityHuman human = npc(GameVersion.V1_26_10);
        assertSame(human, observed.get(), "the real constructor must publish its EntitySpawnEvent");
        assertTrue(initialized.get(), "native skin and both inventories must already be initialized");
        assertNull(callbackFailure.get(), () -> "constructor-time public spawn failed: " + callbackFailure.get());
        assertEquals(1, adds());
        assertSame(viewer, human.getViewers().get(viewer.getLoaderId()));
        human.spawnTo(viewer);
        assertEquals(1, adds(), "a normal call after construction must keep the callback's accepted actor");
        human.despawnFrom(viewer);
        human.spawnTo(viewer);
        assertEquals(2, adds(), "the constructor callback must not poison later normal lifecycle calls");
    }

    @Test
    void constructorSpawnEventCanDespawnInitializedHuman() {
        Player viewer = viewer(GameVersion.V1_26_10);
        AtomicReference<EntityHuman> observed = new AtomicReference<>();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        AtomicBoolean initialized = new AtomicBoolean();
        onSpawn = event -> {
            if (event.getEntity() instanceof EntityHuman human && !(human instanceof Player)) {
                observed.set(human);
                initialized.set(human.getSkin().isValid() && human.getInventory() != null
                        && human.getOffhandInventory() != null);
                try {
                    human.despawnFrom(viewer);
                } catch (RuntimeException | Error failure) {
                    callbackFailure.set(failure);
                }
            }
        };
        EntityHuman human = npc(GameVersion.V1_26_10);
        assertSame(human, observed.get());
        assertTrue(initialized.get());
        assertNull(callbackFailure.get(), () -> "constructor-time public despawn failed: " + callbackFailure.get());
        assertEquals(0, adds());
        assertFalse(human.getViewers().containsKey(viewer.getLoaderId()));
        human.spawnTo(viewer);
        assertEquals(1, adds());
    }

    @Test
    void transportFailurePropagatesWithoutPoisoningRetry() {
        EntityHuman npc = npc(GameVersion.V1_26_50);
        Player viewer = viewer(GameVersion.V1_26_50);
        RuntimeException failure = new IllegalStateException("native add admission failed");
        transport = packet -> {
            if (packet instanceof AddPlayerPacket) {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> npc.spawnTo(viewer)));
        assertFalse(npc.getViewers().containsKey(viewer.getLoaderId()));
        transport = packet -> { };
        npc.spawnTo(viewer);
        assertEquals(1, adds());
    }

    @Test
    void refusedPostSpawnSkinKeepsAcceptedActorAndExistingResend() {
        EntityHuman npc = npc(GameVersion.V1_26_50);
        Player viewer = viewer(GameVersion.V1_26_50);
        AtomicBoolean refused = new AtomicBoolean();
        onSend = event -> {
            if (event.getPacket() instanceof PlayerSkinPacket && refused.compareAndSet(false, true)) {
                event.setCancelled();
            }
        };
        npc.spawnTo(viewer);
        assertTrue(refused.get());
        assertEquals(1, adds());
        assertTrue(actorPresent(npc.getId()));
        assertSame(viewer, npc.getViewers().get(viewer.getLoaderId()));
        new ArrayList<>(delayed).forEach(Runnable::run);
        assertEquals(1, delivered.stream().filter(PlayerSkinPacket.class::isInstance).count());
        assertEquals(1, adds(), "the existing skin resend must not respawn the accepted actor");
        assertTrue(actorPresent(npc.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_26_10", "V1_26_50", "V1_21_124_NETEASE"})
    void inlineSameViewerSpawnIsSuppressed(GameVersion version) {
        EntityHuman npc = npc(version);
        Player viewer = viewer(version);
        AtomicBoolean reentered = new AtomicBoolean();
        onSend = event -> {
            if (event.getPacket() instanceof AddPlayerPacket && reentered.compareAndSet(false, true)) {
                npc.spawnTo(viewer);
            }
        };
        npc.spawnTo(viewer);
        assertTrue(reentered.get());
        assertEquals(1, adds());
        npc.spawnTo(viewer);
        assertEquals(1, adds());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_26_10", "V1_26_50", "V1_21_124_NETEASE"})
    void despawnDuringListRegistrationStopsTheActor(GameVersion version) {
        EntityHuman npc = npc(version);
        Player viewer = viewer(version);
        AtomicBoolean despawned = new AtomicBoolean();
        onSend = event -> {
            if (event.getPacket() instanceof PlayerListPacket list && list.type == PlayerListPacket.TYPE_ADD
                    && despawned.compareAndSet(false, true)) {
                npc.despawnFrom(viewer);
            }
        };
        npc.spawnTo(viewer);
        assertTrue(despawned.get());
        assertEquals(0, adds(), "a retired registration must not publish a later actor");
        assertFalse(npc.getViewers().containsKey(viewer.getLoaderId()));
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
        assertFalse(listEntryPresent(npc.getUniqueId()), "late list ADD must be retired after the callback");
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_26_10", "V1_26_50", "V1_21_124_NETEASE"})
    void closeDuringListRegistrationStopsTheActor(GameVersion version) {
        EntityHuman npc = npc(version);
        Player viewer = viewer(version);
        AtomicBoolean closed = new AtomicBoolean();
        onSend = event -> {
            if (event.getPacket() instanceof PlayerListPacket list && list.type == PlayerListPacket.TYPE_ADD
                    && closed.compareAndSet(false, true)) {
                npc.close();
            }
        };
        npc.spawnTo(viewer);
        assertTrue(closed.get());
        assertEquals(0, adds());
        assertFalse(npc.getViewers().containsKey(viewer.getLoaderId()));
        assertFalse(listEntryPresent(npc.getUniqueId()));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_26_10", "V1_26_50", "V1_21_124_NETEASE"})
    void despawnDuringAddDoesNotLeaveALateGhost(GameVersion version) {
        EntityHuman npc = npc(version);
        Player viewer = viewer(version);
        AtomicBoolean despawned = new AtomicBoolean();
        onSend = event -> {
            if (event.getPacket() instanceof AddPlayerPacket && despawned.compareAndSet(false, true)) {
                npc.despawnFrom(viewer);
                npc.spawnTo(viewer);
            }
        };
        npc.spawnTo(viewer);
        assertTrue(despawned.get());
        assertFalse(actorPresent(npc.getId()), "REMOVE before the admitted ADD cannot retire that later ADD");
        assertFalse(npc.getViewers().containsKey(viewer.getLoaderId()));
        onSend = event -> { };
        delivered.clear();
        npc.spawnTo(viewer);
        assertEquals(1, adds());
        assertTrue(actorPresent(npc.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_26_10", "V1_26_50", "V1_21_124_NETEASE"})
    void closeDuringAddDoesNotLeaveALateGhost(GameVersion version) {
        EntityHuman npc = npc(version);
        Player viewer = viewer(version);
        AtomicBoolean closed = new AtomicBoolean();
        onSend = event -> {
            if (event.getPacket() instanceof AddPlayerPacket && closed.compareAndSet(false, true)) {
                npc.close();
            }
        };
        npc.spawnTo(viewer);
        assertTrue(closed.get());
        assertFalse(actorPresent(npc.getId()));
        assertFalse(npc.getViewers().containsKey(viewer.getLoaderId()));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_26_50", "V1_21_124_NETEASE"})
    void delayedTasksFromRefusedAttemptCannotRemoveRetryRegistration(GameVersion version) {
        EntityHuman npc = npc(version);
        Player viewer = viewer(version);
        onSend = event -> {
            if (event.getPacket() instanceof AddPlayerPacket) {
                event.setCancelled();
            }
        };
        npc.spawnTo(viewer);
        List<Runnable> retired = new ArrayList<>(delayed);
        delayed.clear();
        onSend = event -> { };
        npc.spawnTo(viewer);
        assertEquals(1, adds());
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
        retired.forEach(Runnable::run);
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
        assertTrue(actorPresent(npc.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_26_10", "V1_21_124_NETEASE"})
    void realPlayerRetryRetainsItsExistingTabAndSkinRegistration(GameVersion version) {
        Player viewer = viewer(version);
        NativePlayer subject = (NativePlayer) viewer(version);
        AtomicBoolean refused = new AtomicBoolean();
        onSend = event -> {
            if (event.getPacket() instanceof AddPlayerPacket && refused.compareAndSet(false, true)) {
                event.setCancelled();
            }
        };
        subject.spawnTo(viewer);
        assertTrue(refused.get());
        assertTrue(viewer.sentSkins.contains(subject.getUniqueId()));
        subject.spawnTo(viewer);
        assertEquals(1, adds());
        assertEquals(1, delivered.stream().filter(packet -> packet instanceof PlayerListPacket list
                && list.type == PlayerListPacket.TYPE_ADD).count());
        assertTrue(delivered.stream().noneMatch(packet -> packet instanceof PlayerListPacket list
                && list.type == PlayerListPacket.TYPE_REMOVE));
    }

    private EntityHuman npc(GameVersion version) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        EntityHuman npc = new EntityHuman(chunk, Entity.getDefaultNBT(new Vector3(0.5, 64, 0.5))
                .putString("NameTag", "Admission NPC")
                .putCompound("Skin", new CompoundTag().putString("ModelId", "native-admission-fixture")
                        .putByteArray("Data", visibleSkin().getSkinData().data)));
        Skin skin = visibleSkin();
        skin.setPersona(!version.isNetEase() && version.getProtocol() >= GameVersion.V1_26_40.getProtocol());
        npc.setSkin(skin);
        return npc;
    }

    private Player viewer(GameVersion version) {
        NetworkPlayerSession session = mock(NetworkPlayerSession.class);
        doAnswer(invocation -> {
            DataPacket packet = invocation.getArgument(0);
            transport.accept(packet);
            delivered.add(packet);
            return null;
        }).when(session).sendPacket(any(DataPacket.class));
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(session);
        return new NativePlayer(source, version);
    }

    private long adds() {
        return delivered.stream().filter(AddPlayerPacket.class::isInstance).count();
    }

    private boolean actorPresent(long id) {
        boolean present = false;
        for (DataPacket packet : delivered) {
            if (packet instanceof AddPlayerPacket add && add.entityUniqueId == id) {
                present = true;
            } else if (packet instanceof RemoveEntityPacket remove && remove.eid == id) {
                present = false;
            }
        }
        return present;
    }

    private boolean listEntryPresent(UUID uuid) {
        boolean present = false;
        for (DataPacket packet : delivered) {
            if (packet instanceof PlayerListPacket list) {
                for (PlayerListPacket.Entry entry : list.entries) {
                    if (uuid.equals(entry.uuid)) {
                        present = list.type == PlayerListPacket.TYPE_ADD;
                    }
                }
            }
        }
        return present;
    }

    private static Skin visibleSkin() {
        Skin skin = new Skin();
        byte[] pixels = new byte[Skin.SINGLE_SKIN_SIZE];
        for (int i = 0; i < pixels.length; i += 4) {
            pixels[i] = (byte) 255;
            pixels[i + 2] = (byte) 255;
            pixels[i + 3] = (byte) 255;
        }
        skin.setSkinId("native-admission-fixture");
        skin.setSkinData(pixels);
        return skin;
    }

    private static final class NativePlayer extends Player {
        private NativePlayer(SourceInterface source, GameVersion version) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            uuid = UUID.randomUUID();
            gameVersion = version;
            protocol = version.getProtocol();
            displayName = "Native viewer";
            loginChainData = mock(LoginChainData.class);
            when(loginChainData.getXUID()).thenReturn("");
            offhandInventory = new PlayerOffhandInventory(this);
            setSkin(visibleSkin());
            spawned = true;
        }
    }
}
