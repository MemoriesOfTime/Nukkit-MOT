package cn.nukkit;

import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.EntityMovementSpeedModifier;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SetEntityDataPacket;
import cn.nukkit.network.protocol.UpdateAttributesPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native metadata publication, real event dispatch and setters; only transport is replaced. */
class PlayerSprintPublicationReentryTest {
    @BeforeAll static void initialize() { MockServer.init(); Attribute.init(); }

    @Test void ordinaryStartStopRetainsTheExistingMultiplierAndBaseSpeed() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.setSprinting(true);
            f.assertState(true);
            f.player.setSprinting(false);
            f.assertState(false);
            assertEquals(List.of(0.13f, 0.1f), f.attributeValues());
        }
    }

    @Test void metadataListenerResetCannotLeaveAnOrphanSprintModifier() throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicInteger callbacks = f.once(SetEntityDataPacket.class::isInstance,
                    event -> event.getPlayer().setSprinting(false));
            f.player.setSprinting(true);
            assertEquals(1, callbacks.get(), "The real packet event must perform the nested reset");
            f.assertState(false);
        }
    }

    @Test void metadataListenerRestartKeepsItsOwnSprintModifier() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.setSprinting(true);
            AtomicInteger callbacks = f.once(SetEntityDataPacket.class::isInstance,
                    event -> event.getPlayer().setSprinting(true));
            f.player.setSprinting(false);
            assertEquals(1, callbacks.get());
            f.assertState(true);
        }
    }

    @Test void sameValueMetadataCallbackDoesNotStackTheModifier() throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicInteger callbacks = f.once(SetEntityDataPacket.class::isInstance,
                    event -> event.getPlayer().setSprinting(true));
            f.player.setSprinting(true);
            assertEquals(1, callbacks.get());
            f.assertState(true);
            assertEquals(1, f.player.getMovementSpeedModifiers().size());
        }
    }

    @Test void attributeListenerResetAfterAcceptedModifierWriteKeepsItsOwnState() throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicInteger callbacks = f.once(UpdateAttributesPacket.class::isInstance,
                    event -> event.getPlayer().setSprinting(false));
            f.player.setSprinting(true);
            assertEquals(1, callbacks.get());
            f.assertState(false);
        }
    }

    @Test void attributeListenerRestartAfterRemovalKeepsItsOwnState() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.setSprinting(true);
            AtomicInteger callbacks = f.once(UpdateAttributesPacket.class::isInstance,
                    event -> event.getPlayer().setSprinting(true));
            f.player.setSprinting(false);
            assertEquals(1, callbacks.get());
            f.assertState(true);
        }
    }

    @Test void metadataCallbackCanChangeAwayAndBackWithoutLosingTheFinalState() throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicInteger callbacks = f.once(SetEntityDataPacket.class::isInstance, event -> {
                event.getPlayer().setSprinting(false);
                event.getPlayer().setSprinting(true);
            });
            f.player.setSprinting(true);
            assertEquals(1, callbacks.get());
            f.assertState(true);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Server server = mock(Server.class);
        final PluginManager plugins = new PluginManager(server, null);
        final Plugin plugin = mock(Plugin.class);
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final NetworkPlayerSession transport = mock(NetworkPlayerSession.class);
        final List<DataPacket> packets = new ArrayList<>();

        Fixture() throws Exception {
            when(plugin.isEnabled()).thenReturn(true);
            when(server.getPluginManager()).thenReturn(plugins);
            server.callDataPkSendEv = true;
            set(Entity.class, player, "server", server);
            set(Entity.class, player, "id", 27L);
            set(Entity.class, player, "dataProperties", new EntityMetadata().putLong(Entity.DATA_FLAGS, 0));
            set(Entity.class, player, "hasSpawned", new ConcurrentHashMap<Integer, Player>());
            set(Entity.class, player, "intProperties", new LinkedHashMap<String, Integer>());
            set(Entity.class, player, "floatProperties", new LinkedHashMap<String, Float>());
            set(EntityLiving.class, player, "movementSpeed", Player.DEFAULT_SPEED);
            set(EntityLiving.class, player, "movementSpeedModifiers", new HashMap<String, EntityMovementSpeedModifier>());
            set(Player.class, player, "speedToSend", Player.DEFAULT_SPEED);
            set(Player.class, player, "networkSession", transport);
            player.connected = true;
            player.spawned = false;
            player.protocol = ProtocolInfo.v1_26_50;
            player.gameVersion = GameVersion.V1_26_50;
            doAnswer(call -> { packets.add(call.getArgument(0)); return null; })
                    .when(transport).sendPacket(any(DataPacket.class));
        }

        AtomicInteger once(Predicate<DataPacket> selected, Consumer<DataPacketSendEvent> callback) {
            AtomicBoolean invoked = new AtomicBoolean();
            AtomicInteger calls = new AtomicInteger();
            plugins.registerEvent(DataPacketSendEvent.class, new Listener() { }, EventPriority.NORMAL,
                    (listener, event) -> {
                        DataPacketSendEvent packetEvent = (DataPacketSendEvent) event;
                        if (packetEvent.getPlayer() == player && selected.test(packetEvent.getPacket())
                                && invoked.compareAndSet(false, true)) {
                            calls.incrementAndGet();
                            callback.accept(packetEvent);
                        }
                    }, plugin);
            return calls;
        }

        void assertState(boolean sprinting) throws Exception {
            assertEquals(sprinting, player.isSprinting());
            assertEquals(sprinting, player.getMovementSpeedModifiers().containsKey(EntityMovementSpeedModifier.SPRINTING),
                    "The final sprint flag and actual speed modifier must describe the same state");
            float expected = sprinting ? Player.DEFAULT_SPEED * 1.3f : Player.DEFAULT_SPEED;
            assertEquals(Player.DEFAULT_SPEED, player.getMovementSpeed(), 1e-7f, "Base speed is unchanged");
            assertEquals(expected, player.recalculateMovementSpeedToSend(), 1e-7f);
            assertEquals(expected, (float) get(Player.class, player, "speedToSend"), 1e-7f);
        }

        List<Float> attributeValues() {
            List<Float> values = new ArrayList<>();
            for (DataPacket packet : packets) {
                if (packet instanceof UpdateAttributesPacket attributes) {
                    for (Attribute attribute : attributes.entries) {
                        if (attribute.getId() == Attribute.MOVEMENT_SPEED) values.add(attribute.getValue());
                    }
                }
            }
            return values;
        }

        @Override public void close() { HandlerList.unregisterAll(plugin); }
    }

    private static Object get(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
