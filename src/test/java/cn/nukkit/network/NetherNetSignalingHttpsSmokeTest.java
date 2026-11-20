package cn.nukkit.network;

import cn.nukkit.utils.serverconfig.category.NetherNetSettings;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPServerSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.PongData;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.netty.util.nethernet.TokenTrust;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tel.schich.libdatachannel.LibDataChannelArchDetect;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端验证内置信令的 HTTPS：证书装配（PEM 与 PKCS12）、默认 426 拒绝明文、
 * 显式放行后的同端口双模式，以及无证书时的纯明文回归。
 * <p>
 * End-to-end checks for HTTPS on the built-in signaling: certificate wiring (PEM and PKCS12),
 * the default plaintext rejection, dual-scheme serving when allowed, and the plaintext
 * regression without a certificate.
 */
class NetherNetSignalingHttpsSmokeTest {

    private static final char[] KEYSTORE_PASSWORD = "nethernet-test".toCharArray();

    @TempDir
    Path tempDir;

    private MultiThreadIoEventLoopGroup group;
    private Channel channel;
    private SSLContext httpsContext;

    @BeforeEach
    void setUp() throws Exception {
        LibDataChannelArchDetect.initialize();
        this.group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        this.httpsContext = null;
    }

    @AfterEach
    void tearDown() {
        if (this.channel != null) {
            this.channel.close().awaitUninterruptibly();
        }
        if (this.group != null) {
            this.group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(60)
    void rejectsPlaintextWhileServingHttpsByDefault() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        NetherNetSettings settings = new NetherNetSettings();
        settings.httpsCertificate(certificate.certificate().toString());
        settings.httpsPrivateKey(certificate.privateKey().toString());

        int port = this.startSignaling(settings, certificate.cert());

        assertEquals(200, this.request(port, "https").statusCode(), "HTTPS join endpoint should serve the status");
        HttpResponse<Void> plaintext = this.request(port, "http");
        assertEquals(426, plaintext.statusCode(), "plaintext should be refused by default");
        assertTrue(plaintext.headers().firstValue("Upgrade").orElse("").contains("TLS/1.2"),
                "the 426 must name the TLS upgrade");
    }

    @Test
    @Timeout(60)
    void servesBothSchemesWhenPlaintextIsAllowed() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        NetherNetSettings settings = new NetherNetSettings();
        settings.httpsCertificate(certificate.certificate().toString());
        settings.httpsPrivateKey(certificate.privateKey().toString());
        settings.httpsRejectPlaintext(false);

        int port = this.startSignaling(settings, certificate.cert());

        assertEquals(200, this.request(port, "https").statusCode(), "HTTPS join endpoint should serve the status");
        assertEquals(200, this.request(port, "http").statusCode(), "plaintext should still be served when allowed");
    }

    @Test
    @Timeout(60)
    void servesHttpsThroughThePkcs12KeystoreWithPasswordFile() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        Path keystore = this.writePkcs12(certificate);
        // 密码从文件读取，行尾换行符不属于密码本身
        // The password comes from a file, trailing line terminator excluded
        Path passwordFile = this.tempDir.resolve("https-password.txt");
        Files.writeString(passwordFile, "nethernet-test\n");

        NetherNetSettings settings = new NetherNetSettings();
        settings.httpsCertificate(keystore.toString());
        settings.httpsPassword("file:" + passwordFile);
        settings.httpsRejectPlaintext(false);

        int port = this.startSignaling(settings, certificate.cert());

        assertEquals(200, this.request(port, "https").statusCode(), "keystore HTTPS should serve the status");
        assertEquals(200, this.request(port, "http").statusCode(), "plaintext should still be served when allowed");
    }

    @Test
    @Timeout(30)
    void plaintextWithoutCertificate() throws Exception {
        int port = this.startSignaling(new NetherNetSettings(), null);

        assertEquals(200, this.request(port, "http").statusCode(), "no certificate keeps the endpoint plaintext");
    }

    @Test
    @Timeout(30)
    void badCertificateFilesFailStartupWithTheBranchName() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        Path garbage = this.tempDir.resolve("garbage.pem");
        Files.writeString(garbage, "not a certificate");

        NetherNetSettings keystore = new NetherNetSettings();
        keystore.httpsCertificate(garbage.toString());
        IllegalArgumentException keystoreError = assertThrows(IllegalArgumentException.class,
                () -> NetherNetHttps.apply(new NetherNetHTTPServerSignaling.Builder(), keystore, this.tempDir));
        assertTrue(keystoreError.getMessage().startsWith("Cannot read the TLS keystore"),
                "an empty private key selects the keystore branch: " + keystoreError.getMessage());

        NetherNetSettings pem = new NetherNetSettings();
        pem.httpsCertificate(garbage.toString());
        pem.httpsPrivateKey(certificate.privateKey().toString());
        IllegalArgumentException pemError = assertThrows(IllegalArgumentException.class,
                () -> NetherNetHttps.apply(new NetherNetHTTPServerSignaling.Builder(), pem, this.tempDir));
        assertTrue(pemError.getMessage().startsWith("Cannot use the TLS certificate chain"),
                "a private key selects the PEM branch: " + pemError.getMessage());
    }

    @Test
    void missingPasswordFileKeepsItsPathInTheReportedStartupFailure() {
        NetherNetSettings settings = new NetherNetSettings();
        settings.httpsCertificate("unused.p12");
        settings.httpsPassword("file:missing-password.txt");
        Path missing = this.tempDir.resolve("missing-password.txt");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NetherNetHttps.apply(new NetherNetHTTPServerSignaling.Builder(), settings, this.tempDir));
        Throwable reported = error;
        while (reported.getCause() != null) {
            reported = reported.getCause();
        }
        assertTrue(reported.getMessage() != null && reported.getMessage().contains(missing.toString()),
                "startup logging must retain the password file path: " + reported.getMessage());
    }

    /**
     * 绑定一个按给定设置装配的信令端点；trustCert 为 null 时不提供 HTTPS 客户端上下文。
     * Binds a signaling endpoint wired from the settings; trustCert null skips the HTTPS client context.
     */
    private int startSignaling(NetherNetSettings settings, Certificate trustCert) throws Exception {
        OperatorIdentity identity = OperatorIdentity.generate("https-smoke-test");
        PongData pong = new PongData.Builder()
                .setServerName("NetherNetHttpsSmokeTest")
                .setProtocol(1)
                .setVersion("1.26.50")
                .setNonce("0123456789abcdef")
                .build();

        for (int attempt = 0; ; attempt++) {
            NetherNetHTTPServerSignaling.Builder builder = new NetherNetHTTPServerSignaling.Builder()
                    .setIdentity(identity)
                    .setServeHttp(true)
                    .setIceOnLocalPort(false)
                    .setTokenTrust(TokenTrust.ANY)
                    .setMotdProvider((host, remoteAddress, client) -> pong)
                    .setPlayerFilter((host, player) -> null);
            boolean https = NetherNetHttps.apply(builder, settings, this.tempDir);
            assertEquals(!settings.httpsCertificate().isBlank(), https,
                    "apply must report TLS exactly when a certificate is set");
            NetherNetHTTPServerSignaling signaling = builder.build();
            if (trustCert != null) {
                this.httpsContext = trustedContext(trustCert);
            }

            int port;
            try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                port = probe.getLocalPort();
            }

            var bind = new ServerBootstrap()
                    .group(this.group)
                    .channelFactory(NetherNetChannelFactory.server(signaling))
                    // 冒烟测试走不到建连，但 bootstrap 要求必须设置
                    // No join gets this far in the smoke test, but the bootstrap insists
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.close();
                        }
                    })
                    .bind(new InetSocketAddress("127.0.0.1", port))
                    .awaitUninterruptibly();
            if (bind.isSuccess()) {
                this.channel = bind.channel();
                assertTrue(this.channel.isActive(), "signaling channel should be active");
                return port;
            }

            Throwable bindFailure = bind.cause();
            bind.channel().close().awaitUninterruptibly();
            boolean portCollision = bindFailure instanceof java.net.ConnectException
                    && bindFailure.getMessage() != null
                    && bindFailure.getMessage().startsWith("Failed to bind HTTP signaling");
            if (!portCollision || attempt >= 9) {
                throw new AssertionError("Unable to bind the test signaling endpoint", bindFailure);
            }
        }
    }

    private HttpResponse<Void> request(int port, String scheme) throws Exception {
        HttpClient.Builder client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
        if (scheme.equals("https")) {
            client.sslContext(this.httpsContext);
        }
        // 证书的 CN 是 localhost（无 SAN），客户端以 localhost 访问以通过主机名校验
        // The certificate carries CN=localhost without SANs, so the client connects as localhost
        HttpRequest request = HttpRequest.newBuilder(URI.create(scheme + "://localhost:" + port + "/v1/join"))
                .GET().build();
        return client.build().send(request, HttpResponse.BodyHandlers.discarding());
    }

    /** 客户端只信任测试自签的证书。 The client trusts exactly the test's self-signed certificate. */
    private static SSLContext trustedContext(Certificate certificate) throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("nethernet-test", certificate);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), new SecureRandom());
        return context;
    }

    private Path writePkcs12(SelfSignedCertificate certificate) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("nethernet-test", certificate.key(), KEYSTORE_PASSWORD,
                new Certificate[]{certificate.cert()});
        Path file = this.tempDir.resolve("signaling.p12");
        try (var output = Files.newOutputStream(file)) {
            store.store(output, KEYSTORE_PASSWORD);
        }
        return file;
    }
}
