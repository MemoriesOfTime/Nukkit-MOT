package cn.nukkit.network.session;

import cn.nukkit.MockServer;
import cn.nukkit.network.NetherNetInterface;
import cn.nukkit.network.session.login.SessionLoginContext;
import cn.nukkit.network.session.login.SessionLoginPhase;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetherNetResourcePackGraceTest {
    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
        MockServer.get().netEaseMode = false;
    }

    @Test
    void liveResourcePackDownloadGetsExistingRakNetGrace() throws Exception {
        NetherNetPlayerSession session = session(true);
        configureClock(session, SessionLoginPhase.RESOURCE_PACK);

        assertFalse(session.isLoginPhaseTimedOut(TimeUnit.SECONDS.toNanos(120), 90_000),
                "An active transport preserves a resource transfer during the existing grace period");
        assertFalse(session.isLoginPhaseTimedOut(RakNetPlayerSession.RESOURCE_PACK_PHASE_MAX_NANOS - 1, 90_000));
    }

    @Test
    void inactiveTransportAndOtherLoginPhasesKeepTheirDeadlines() throws Exception {
        NetherNetPlayerSession inactive = session(false);
        configureClock(inactive, SessionLoginPhase.RESOURCE_PACK);
        assertTrue(inactive.isLoginPhaseTimedOut(TimeUnit.SECONDS.toNanos(120), 90_000));

        for (SessionLoginPhase phase : new SessionLoginPhase[]{
                SessionLoginPhase.CONNECTED, SessionLoginPhase.LOGGED_IN, SessionLoginPhase.DISCONNECTED}) {
            NetherNetPlayerSession session = session(true);
            configureClock(session, phase);
            assertEquals(phase == SessionLoginPhase.CONNECTED,
                    session.isLoginPhaseTimedOut(TimeUnit.SECONDS.toNanos(120), 90_000), phase.name());
        }
    }

    @Test
    void graceBoundaryDisabledTimeoutAndSubsequentActivityRemainEffective() throws Exception {
        NetherNetPlayerSession session = session(true);
        configureClock(session, SessionLoginPhase.RESOURCE_PACK);
        assertTrue(session.isLoginPhaseTimedOut(RakNetPlayerSession.RESOURCE_PACK_PHASE_MAX_NANOS, 90_000));
        assertTrue(session.isLoginPhaseTimedOut(RakNetPlayerSession.RESOURCE_PACK_PHASE_MAX_NANOS + 1, 90_000));
        assertFalse(session.isLoginPhaseTimedOut(RakNetPlayerSession.RESOURCE_PACK_PHASE_MAX_NANOS + 1, 0));

        Field activity = SessionLoginContext.class.getDeclaredField("lastActivityNanos");
        activity.setAccessible(true);
        activity.setLong(session.getState().getLogin(), TimeUnit.SECONDS.toNanos(599));
        assertFalse(session.isLoginPhaseTimedOut(TimeUnit.SECONDS.toNanos(601), 90_000),
                "Recent progress still resets the normal inactivity deadline after the added grace ends");
        assertTrue(session.isLoginPhaseTimedOut(TimeUnit.SECONDS.toNanos(689), 90_000));
    }

    private static NetherNetPlayerSession session(boolean active) {
        EventLoop loop = mock(EventLoop.class);
        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        doReturn(timer).when(loop).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        NetherNetChildChannel channel = mock(NetherNetChildChannel.class, RETURNS_DEEP_STUBS);
        when(channel.eventLoop()).thenReturn(loop);
        when(channel.isActive()).thenReturn(active);
        return new NetherNetPlayerSession(mock(NetherNetInterface.class), channel);
    }

    private static void configureClock(NetworkPlayerSession session, SessionLoginPhase phase) throws Exception {
        SessionLoginContext login = session.getState().getLogin();
        login.setPhase(phase);
        for (String name : new String[]{"lastActivityNanos", "phaseStartedNanos"}) {
            Field clock = SessionLoginContext.class.getDeclaredField(name);
            clock.setAccessible(true);
            clock.setLong(login, 0L);
        }
    }
}
