package cn.nukkit;

import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerLegacyMovementViewersP03Test {
    private final ObjenesisStd objects = new ObjenesisStd();

    @Test
    void keepsLegacyThresholdAndViewerOrder() throws Exception {
        Subject subject = subject();
        Player first = viewer(ProtocolInfo.v1_7_0 - 1);
        Player boundary = viewer(ProtocolInfo.v1_7_0);
        Player second = viewer(ProtocolInfo.v1_7_0 - 2);
        subject.viewers.put(4, first);
        subject.viewers.put(1, boundary);
        subject.viewers.put(8, viewer(ProtocolInfo.v1_20_0));
        subject.viewers.put(3, second);

        assertEquals(List.of(first, second), snapshot(subject));
        assertEquals(1, subject.viewerReads);
    }

    @Test
    void keepsEmptyResultMutableAndFresh() throws Exception {
        Subject subject = subject();
        List<Player> first = snapshot(subject);
        List<Player> second = snapshot(subject);
        assertNotSame(first, second);
        Player legacy = viewer(ProtocolInfo.v1_7_0 - 1);
        first.add(legacy);
        assertEquals(List.of(legacy), first);
        assertTrue(second.isEmpty());
        assertTrue(subject.viewers.isEmpty());
    }

    @Test
    void supportedViewersKeepIndependentMutableEmptySnapshot() throws Exception {
        Subject subject = subject();
        subject.viewers.put(1, viewer(ProtocolInfo.v1_20_0));
        subject.viewers.put(2, viewer(ProtocolInfo.CURRENT_PROTOCOL));
        List<Player> snapshot = snapshot(subject);
        assertTrue(snapshot.isEmpty());
        snapshot.add(viewer(ProtocolInfo.v1_7_0 - 1));
        assertEquals(2, subject.viewers.size());
    }

    @Test
    void viewerMapChangesDoNotAlterPreviousSnapshot() throws Exception {
        Subject subject = subject();
        Player first = viewer(ProtocolInfo.v1_7_0 - 1);
        subject.viewers.put(1, first);
        List<Player> saved = snapshot(subject);
        subject.viewers.clear();
        subject.viewers.put(2, viewer(ProtocolInfo.v1_7_0 - 1));
        assertEquals(List.of(first), saved);
        saved.clear();
        assertEquals(1, subject.viewers.size());
    }

    @Test
    void separateCallsKeepCurrentProtocolAndViewerIdentity() throws Exception {
        Subject subject = subject();
        Player viewer = viewer(ProtocolInfo.v1_7_0 - 1);
        subject.viewers.put(1, viewer);
        List<Player> first = snapshot(subject);
        viewer.protocol = ProtocolInfo.v1_7_0;
        assertTrue(snapshot(subject).isEmpty());
        assertSame(viewer, first.get(0));
        assertEquals(2, subject.viewerReads);
    }

    private Subject subject() {
        Subject subject = objects.newInstance(Subject.class);
        subject.viewers = new LinkedHashMap<>();
        return subject;
    }

    private Player viewer(int protocol) {
        Player viewer = objects.newInstance(Player.class);
        viewer.protocol = protocol;
        return viewer;
    }

    // Exercise the original public rotation tick and stop at the original virtual send callback.
    private static List<Player> snapshot(Subject player) throws Exception {
        Field rotation = Player.class.getDeclaredField("needSendRotation"); rotation.setAccessible(true);
        rotation.set(player, true); player.loggedIn = true; player.spawned = true;
        assertThrows(Captured.class, () -> player.onUpdate(++player.tick));
        return player.snapshot;
    }
    private static final class Captured extends RuntimeException {}

    private static final class Subject extends Player {
        private Map<Integer, Player> viewers;
        private int viewerReads;
        private int tick;
        private List<Player> snapshot;
        @Override public boolean isAlive() { return true; }
        @Override public void addMovement(double x, double y, double z, double yaw,
                double pitch, double headYaw, Collection<Player> recipients) {
            snapshot = (List<Player>) recipients; throw new Captured();
        }

        private Subject() {
            super(null, 0L, null);
        }

        @Override
        public Map<Integer, Player> getViewers() {
            viewerReads++;
            return viewers;
        }
    }
}
