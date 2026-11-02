package cn.nukkit.network;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.network.session.NetherNetPlayerSession;
import cn.nukkit.utils.serverconfig.category.NetherNetSettings;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
import org.cloudburstmc.netty.util.nethernet.PlayerInfo;
import org.cloudburstmc.netty.util.nethernet.TransportIdentityBinding;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NetherNet skips the Bedrock encryption handshake, the only thing that ties a login chain to the
 * peer on RakNet. Upstream binds the chain to the signaling assertion only with xbox-auth on; with
 * it off the binding was released unchecked, so a chain captured on another server and replayed here
 * looked exactly like its holder's. These cover the offline path reporting the binding instead.
 */
class NetherNetOfflineIdentityBindingTest {

    @TempDir
    Path tempDir;

    private Server server;
    private NetherNetInterface netherNet;

    @BeforeEach
    void setUp() {
        this.server = MockServer.get();
        lenient().when(server.getIp()).thenReturn("127.0.0.1");
        lenient().when(server.getPort()).thenReturn(0);
        lenient().when(server.getDataPath()).thenReturn(tempDir.toString());
        lenient().when(server.getMotd()).thenReturn("NetherNetOfflineIdentityBindingTest");
        lenient().when(server.getLanguage()).thenReturn(new BaseLang("eng"));
        this.netherNet = new NetherNetInterface(server, new NetherNetSettings());
    }

    @AfterEach
    void tearDown() {
        if (this.netherNet != null) {
            this.netherNet.shutdown();
        }
        this.server.xboxAuth = false;
        MockServer.reset();
    }

    private static PublicKey key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        return generator.generateKeyPair().getPublic();
    }

    private static String encoded(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static NetherNetChildChannel channel() {
        return new NetherNetChildChannel(null, null,
                new InetSocketAddress("127.0.0.1", 51234), new InetSocketAddress("127.0.0.1", 19132));
    }

    private static void admit(NetherNetChildChannel channel, PublicKey key) {
        JwtClaims claims = new JwtClaims();
        claims.setClaim("cpk", encoded(key));
        TransportIdentityBinding.install(channel, TransportIdentityBinding.forPlayer(new PlayerInfo(
                "2535000000000000", "Tester", "1234567890", new InetSocketAddress("127.0.0.1", 19132), null, claims)));
    }

    private static NetherNetPlayerSession session(NetherNetChildChannel channel) {
        NetherNetPlayerSession session = mock(NetherNetPlayerSession.class);
        when(session.getChannel()).thenReturn(channel);
        return session;
    }

    @Test
    void offlineHolderOfTheAdmittedKeyIsBound() throws Exception {
        PublicKey identity = key();
        NetherNetChildChannel channel = channel();
        admit(channel, identity);
        NetherNetPlayerSession session = session(channel);

        assertNull(this.netherNet.checkIdentityBinding(session, encoded(identity)));
        verify(session).setIdentityKeyBound(true);
    }

    @Test
    void offlineReplayedChainIsAdmittedUnbound() throws Exception {
        NetherNetChildChannel channel = channel();
        admit(channel, key());
        NetherNetPlayerSession session = session(channel);

        // Offline mode still lets the player in, to authenticate some other way, but never as the chain's owner
        assertNull(this.netherNet.checkIdentityBinding(session, encoded(key())));
        verify(session).setIdentityKeyBound(false);
    }

    @Test
    void offlineTransportWithoutAssertionIsUnbound() throws Exception {
        NetherNetPlayerSession session = session(channel());

        assertNull(this.netherNet.checkIdentityBinding(session, encoded(key())));
        verify(session).setIdentityKeyBound(false);
    }

    @Test
    void offlineChainWithoutUsableKeyIsUnbound() throws Exception {
        NetherNetChildChannel channel = channel();
        admit(channel, key());
        NetherNetPlayerSession session = session(channel);

        assertNull(this.netherNet.checkIdentityBinding(session, "not a key"));
        verify(session).setIdentityKeyBound(false);
    }

    @Test
    void onlineReplayedChainIsStillRefused() throws Exception {
        this.server.xboxAuth = true;
        NetherNetChildChannel channel = channel();
        admit(channel, key());
        NetherNetPlayerSession session = session(channel);

        assertNotNull(this.netherNet.checkIdentityBinding(session, encoded(key())));
        verify(session).setIdentityKeyBound(false);
    }

    @Test
    void bindingIsSpentByTheFirstLogin() throws Exception {
        PublicKey identity = key();
        NetherNetChildChannel channel = channel();
        admit(channel, identity);
        NetherNetPlayerSession first = session(channel);
        NetherNetPlayerSession second = session(channel);

        assertNull(this.netherNet.checkIdentityBinding(first, encoded(identity)));
        assertNull(this.netherNet.checkIdentityBinding(second, encoded(identity)));
        verify(first).setIdentityKeyBound(true);
        verify(second).setIdentityKeyBound(false);
    }
}
