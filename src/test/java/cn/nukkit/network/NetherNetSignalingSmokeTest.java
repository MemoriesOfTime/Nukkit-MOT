package cn.nukkit.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling.PongData;
import org.cloudburstmc.netty.util.nethernet.ServerIdentity;
import org.cloudburstmc.netty.util.nethernet.TokenTrust;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tel.schich.libdatachannel.LibDataChannelArchDetect;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端绑定内置 NetherNet HTTP 信令端点：验证原生库加载、TCP 监听与 {@code GET /v1/join} 状态应答。
 * Binds the builtin HTTP signaling end to end: native load, TCP listener, and the join status response.
 */
@Log4j2
class NetherNetSignalingSmokeTest {

    private MultiThreadIoEventLoopGroup group;
    private Channel channel;

    @BeforeEach
    void setUp() {
        LibDataChannelArchDetect.initialize();
        this.group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
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
    @Timeout(30)
    void joinEndpointServesStatus() throws Exception {
        ServerIdentity identity = ServerIdentity.generate("smoke-test");
        PongData pong = new PongData.Builder()
                .setServerName("NetherNetSmokeTest")
                .setProtocol(1)
                .setVersion("1.26.50")
                .setNonce("0123456789abcdef")
                .build();

        NetherNetHTTPSignaling signaling = new NetherNetHTTPSignaling.Builder()
                .setIdentity(identity)
                .setServeHttp(true)
                .setIceOnLocalPort(false)
                .setTokenTrust(TokenTrust.ANY)
                .setMotdProvider((host, remoteAddress) -> pong)
                .setPlayerFilter((host, player) -> null)
                .build();

        // 通道回报的是请求地址，端口需在此自取
        // The channel reports the requested address, so pick the ephemeral port here
        java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        int port = probe.getLocalPort();
        probe.close();

        this.channel = new ServerBootstrap()
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
                .awaitUninterruptibly()
                .channel();
        assertTrue(this.channel.isActive(), "signaling channel should be active");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/join")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "join endpoint body: " + response.body());
        assertTrue(response.body().contains("NetherNetSmokeTest"), "body should carry the server name: " + response.body());
        assertTrue(response.body().contains("\"transportLayer\""), "body should expose the transport layer field");
    }
}
