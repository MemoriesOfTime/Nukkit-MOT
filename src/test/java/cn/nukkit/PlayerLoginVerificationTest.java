package cn.nukkit;

import cn.nukkit.entity.data.Skin;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.network.encryption.LoginChainVerifier;
import cn.nukkit.network.protocol.LoginPacket;
import cn.nukkit.network.protocol.PlayStatusPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.network.session.login.NetworkSessionState;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.ClientChainData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerLoginVerificationTest {
    @Test
    void onlyLiveUnchangedLoginReceivesValidatedIdentityOnMain() throws Exception {
        MockServer.init();
        for (String state : new String[]{"live", "closed", "timeout", "phase-changed", "expired"}) {
            ClientChainData verified = mock(ClientChainData.class);
            when(verified.isAuthenticationCurrent()).thenReturn(!state.equals("expired"));
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicInteger validations = new AtomicInteger();
            LoginChainVerifier verifier = verifier(8, packet -> {
                validations.incrementAndGet();
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                return verified;
            });
            Probe player = player();
            try {
                LoginPacket packet = new LoginPacket();
                packet.setBuffer(new byte[]{1});
                player.beginLoginVerification(packet, verifier);
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                player.beginLoginVerification(packet, verifier);
                assertEquals(1, validations.get(), "duplicate login cannot create another verification");
                assertFalse(Player.isPreLoginVerifiedPacketAllowed(SessionLoginPhase.LOGIN_RECEIVED,
                        ProtocolInfo.toNewProtocolID(ProtocolInfo.LOGIN_PACKET)));
                assertNull(player.accepted);
                assertNull(player.getLoginChainData());
                assertNull(player.getUniqueId());
                assertFalse(player.loginVerified);
                if (state.equals("closed")) player.closed = true;
                if (state.equals("timeout")) player.close("", "timeout");
                if (state.equals("phase-changed")) player.getNetworkSession().getState().getLogin()
                        .setPhase(SessionLoginPhase.DISCONNECTED);
                release.countDown();
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                if (!state.equals("timeout")) {
                    while (AsyncTask.FINISHED_LIST.isEmpty() && System.nanoTime() < until) Thread.sleep(1);
                    assertFalse(AsyncTask.FINISHED_LIST.isEmpty());
                }
                while (player.accepted == null && player.reason == null && System.nanoTime() < until) {
                    AsyncTask.collectTask();
                    Thread.sleep(1);
                    if (!state.equals("live") && !state.equals("expired") && AsyncTask.FINISHED_LIST.isEmpty()) break;
                }
                if (state.equals("live")) {
                    assertSame(verified, player.accepted);
                    assertSame(Thread.currentThread(), player.continuedOn);
                } else {
                    assertNull(player.accepted, state);
                }
                if (state.equals("expired")) assertNotNull(player.reason);
            } finally {
                release.countDown();
                verifier.shutdown();
            }
        }
    }

    @Test
    void overloadSendsProtocolFailureAndNeverStartsVerificationOrAssignsIdentity() throws Exception {
        MockServer.init();
        LoginChainVerifier verifier = verifier(1, packet -> { fail("must not decode over-budget input"); return null; });
        Probe player = player();
        LoginPacket packet = new LoginPacket();
        packet.setBuffer(new byte[]{1, 2});
        try {
            player.beginLoginVerification(packet, verifier);
            assertEquals(PlayStatusPacket.LOGIN_FAILED_SERVER_FULL, player.status);
            assertNotNull(player.reason);
            assertNull(player.accepted);
            assertNull(player.getLoginChainData());
            assertNull(player.getUniqueId());
        } finally {
            verifier.shutdown();
        }
    }

    private static LoginChainVerifier verifier(long byteCapacity, Function<byte[], ClientChainData> decoder) throws Exception {
        Constructor<LoginChainVerifier> constructor = LoginChainVerifier.class
                .getDeclaredConstructor(int.class, int.class, long.class, Function.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, 1, byteCapacity, decoder);
    }

    private static Probe player() throws Exception {
        Probe player = mock(Probe.class, CALLS_REAL_METHODS);
        player.connected = true;
        NetworkSessionState state = new NetworkSessionState();
        state.getLogin().setPhase(SessionLoginPhase.LOGIN_RECEIVED);
        NetworkPlayerSession session = mock(NetworkPlayerSession.class);
        when(session.getState()).thenReturn(state);
        Field field = Player.class.getDeclaredField("networkSession");
        field.setAccessible(true);
        field.set(player, session);
        field = cn.nukkit.entity.Entity.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(player, MockServer.get());
        return player;
    }

    private static class Probe extends Player {
        ClientChainData accepted;
        Thread continuedOn;
        String reason;
        int status;

        private Probe() { super(null, null, null); }
        @Override void continueVerifiedLogin(Skin skin, ClientChainData validated) {
            accepted = validated;
            continuedOn = Thread.currentThread();
        }
        @Override protected void sendPlayStatus(int status, boolean immediate) {
            assertTrue(immediate, "overload status must precede disconnect");
            this.status = status;
        }
        @Override public String getAddress() { return "127.0.0.1"; }
        @Override public void close(String message, String reason) {
            this.reason = reason;
            connected = false;
            closed = true;
            super.close(new TextContainer(message), reason, false); // Exercises pending-job cancellation.
        }
    }
}
