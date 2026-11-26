package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityViewerMap;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MoveEntityAbsolutePacket;
import cn.nukkit.network.protocol.MoveEntityDeltaPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockConstruction;

class EntityMovementRecipientsTest {
    private final ObjenesisStd objects = new ObjenesisStd();
    private final Level level = objects.newInstance(Level.class);
    private final Actor actor = objects.newInstance(Actor.class);

    @Test
    void emptyNativeViewSkipsEveryDeltaConstruction() {
        Player viewer = objects.newInstance(Viewer.class);
        actor.viewers = new ConcurrentHashMap<>();
        for (int id = 0; id < 512; id++) actor.viewers.put(id, viewer);
        actor.viewers.clear();
        try (var deltas = mockConstruction(MoveEntityDeltaPacket.class)) {
            for (int i = 0; i < 64; i++) move();
            assertEquals(0, deltas.constructed().size());
            actor.viewers.put(1, viewer);
            viewer.protocol = ProtocolInfo.CURRENT_PROTOCOL;
            move();
            assertEquals(1, deltas.constructed().size());
        }
        assertEquals(65, actor.idReads);
        assertEquals(65, actor.viewerReads);
    }

    @Test
    void nativeViewerRegistryDeliversFirstJoinAndRetainedEntryReinsertion() {
        var registry = new EntityViewerMap<Integer, Player>();
        actor.viewers = registry;
        Viewer viewer = objects.newInstance(Viewer.class);
        viewer.protocol = ProtocolInfo.CURRENT_PROTOCOL;
        for (int i = 0; i < 512; i++) registry.put(i, viewer);
        registry.clear();
        try (var deltas = mockConstruction(MoveEntityDeltaPacket.class)) {
            for (int i = 0; i < 64; i++) move();
            assertTrue(deltas.constructed().isEmpty());
            registry.computeIfAbsent(0, key -> viewer);
            move();
            var retained = registry.entrySet().iterator().next();
            registry.clear();
            move();
            retained.setValue(viewer);
            move();
            assertEquals(2, deltas.constructed().size());
        }
        assertEquals(2, viewer.sends);
        assertEquals(67, actor.viewerReads);
        assertEquals(67, actor.idReads);
    }

    @Test
    void everyMovementReachesAPermanentViewerDuringConcurrentJoinsAndLeaves() throws Exception {
        var registry = new EntityViewerMap<Integer, Player>();
        actor.viewers = registry;
        Viewer permanent = objects.newInstance(Viewer.class);
        permanent.protocol = ProtocolInfo.CURRENT_PROTOCOL;
        registry.put(0, permanent);
        int writers = 5;
        int rounds = 16;
        int movesPerRound = 256;
        int updatesPerRound = 512;
        var phase = new CyclicBarrier(writers + 1);
        var pool = Executors.newFixedThreadPool(writers);
        try {
            List<Future<Integer>> updates = new ArrayList<>();
            for (int index = 1; index <= writers; index++) {
                int key = index;
                Viewer transientViewer = objects.newInstance(Viewer.class);
                transientViewer.protocol = ProtocolInfo.CURRENT_PROTOCOL;
                updates.add(pool.submit(() -> {
                    int completed = 0;
                    for (int round = 0; round < rounds; round++) {
                        phase.await(10, TimeUnit.SECONDS);
                        for (int i = 0; i < updatesPerRound; i++) {
                            registry.put(key, transientViewer);
                            if ((i & 1) == 0) registry.remove(key);
                            else registry.keySet().remove(key);
                            completed++;
                        }
                    }
                    return completed;
                }));
            }
            for (int round = 0; round < rounds; round++) {
                phase.await(10, TimeUnit.SECONDS);
                for (int i = 0; i < movesPerRound; i++) move();
            }
            for (Future<Integer> update : updates) {
                assertEquals(rounds * updatesPerRound, update.get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(rounds * movesPerRound, permanent.sends);
        assertEquals(rounds * movesPerRound, actor.viewerReads);
        assertInstanceOf(MoveEntityDeltaPacket.class, permanent.packet);
    }

    @Test
    void customViewAndReentrantLegacyDeliveryPreserveDeltaSnapshot() {
        Viewer legacy = objects.newInstance(Viewer.class);
        legacy.protocol = ProtocolInfo.v1_16_100 - 1;
        Viewer modern = objects.newInstance(Viewer.class);
        modern.protocol = ProtocolInfo.v1_16_100;
        actor.viewers = new LinkedHashMap<>() {
            @Override public boolean isEmpty() {
                throw new AssertionError("custom map emptiness is not part of recipient enumeration");
            }
        };
        actor.viewers.put(1, legacy);
        actor.viewers.put(2, modern);
        actor.onGround = true;
        actor.onGetViewers = () -> {
            actor.lastX = 10;
            actor.onGround = false;
        };
        legacy.onSend = () -> actor.lastPitch = 50;
        move();

        MoveEntityAbsolutePacket absolute = assertInstanceOf(MoveEntityAbsolutePacket.class, legacy.packet);
        assertFalse(absolute.onGround);
        MoveEntityDeltaPacket delta = assertInstanceOf(MoveEntityDeltaPacket.class, modern.packet);
        assertEquals(1, delta.eid);
        assertEquals(2, absolute.eid);
        assertEquals(2, actor.idReads);
        assertEquals(1, actor.viewerReads);
        assertEquals(MoveEntityDeltaPacket.FLAG_HAS_X | MoveEntityDeltaPacket.FLAG_HAS_PITCH
                | MoveEntityDeltaPacket.FLAG_ON_GROUND, delta.flags);
        assertEquals(10, delta.x);
        assertEquals(50, delta.pitchDelta);
        assertEquals(0, delta.y);
    }

    @Test
    void firstViewerJoiningAnEmptyMapReceivesTheNextMovement() throws InterruptedException {
        actor.viewers = new ConcurrentHashMap<>();
        move();
        Viewer viewer = objects.newInstance(Viewer.class);
        viewer.protocol = ProtocolInfo.CURRENT_PROTOCOL;
        Thread join = new Thread(() -> actor.viewers.put(7, viewer));
        join.start();
        join.join();
        move();
        assertInstanceOf(MoveEntityDeltaPacket.class, viewer.packet);
        actor.viewers.remove(7);
        move();
        assertEquals(1, viewer.sends);
    }

    private void move() {
        level.addEntityMovement(actor, 10, 0, 0, 0, 50, 0);
    }

    private static final class Actor extends Entity {
        Map<Integer, Player> viewers;
        int idReads;
        int viewerReads;
        Runnable onGetViewers;

        Actor() { super(null, null); }
        @Override public int getNetworkId() { return 1; }
        @Override public long getId() { return ++idReads; }
        @Override public Map<Integer, Player> getViewers() {
            viewerReads++;
            if (onGetViewers != null) onGetViewers.run();
            return viewers;
        }
    }

    private static final class Viewer extends Player {
        DataPacket packet;
        int sends;
        Runnable onSend;

        Viewer() { super(null, 0L, null); }
        @Override public boolean dataPacket(DataPacket packet) {
            this.packet = packet;
            sends++;
            if (onSend != null) onSend.run();
            return true;
        }
    }
}
