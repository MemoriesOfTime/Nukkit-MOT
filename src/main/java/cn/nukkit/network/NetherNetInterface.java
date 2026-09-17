package cn.nukkit.network;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.player.PlayerCreationEvent;
import cn.nukkit.event.server.QueryRegenerateEvent;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.NetherNetPlayerSession;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.network.session.RakNetPlayerSession;
import cn.nukkit.utils.serverconfig.category.NetherNetSettings;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling.PongData;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetSignaling.IceServerInfo;
import org.cloudburstmc.netty.util.nethernet.NetherNetLogging;
import org.cloudburstmc.netty.util.nethernet.ServerIdentity;
import org.cloudburstmc.netty.util.nethernet.TokenTrust;
import org.cloudburstmc.netty.util.nethernet.TransportIdentityBinding;
import tel.schich.libdatachannel.LibDataChannelArchDetect;

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NetherNet（WebRTC）传输，与 RakNet 并行服务受限网络下的新客户端。
 * <p>
 * HTTP 信令端点接收 SDP offer，接受的每个对端成为普通 {@link NetherNetChildChannel}，载荷与 RakNet
 * 的 batch 体一致；DTLS 已加密，Bedrock 加密保持关闭，登录链改由信令身份绑定（见 {@link #checkIdentityBinding}）。
 * <p>
 * Adapted from PowerNukkitX NetherNet support (built on CloudburstMC netty-transport-nethernet
 * and WaterdogPE's signaling work).
 */
@Log4j2
public class NetherNetInterface implements AdvancedSourceInterface {

    /** 登录超时禁用（timeout-milliseconds=0）时的握手兜底值。 Handshake fallback while the login timeout is disabled. */
    private static final int DEFAULT_HANDSHAKE_TIMEOUT_SECONDS = 30;

    private final Server server;
    private Network network;

    private final Channel channel;
    private final EventLoopGroup eventLoopGroup;
    private final NetherNetHTTPSignaling signaling;
    private final Map<InetSocketAddress, NetherNetPlayerSession> sessions = new HashMap<>();
    private final Queue<NetherNetPlayerSession> sessionCreationQueue = PlatformDependent.newMpscQueue();
    private final Set<NetherNetPlayerSession> pendingSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<InetAddress, Long> blockedAddresses = new ConcurrentHashMap<>();
    private final AtomicReference<String> pongNonce = new AtomicReference<>(String.format("%016x", new java.security.SecureRandom().nextLong()));

    private volatile boolean accepting = true;
    private int inboundRoundRobinCursor;

    public NetherNetInterface(Server server, NetherNetSettings settings) {
        this.server = server;

        try {
            LibDataChannelArchDetect.initialize();
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to initialize NetherNet: the native libdatachannel library may be missing for this platform", t);
        }
        NetherNetLogging.setNativeLogLevel("WARN");

        ServerIdentity identity = NetherNetIdentity.load(server, settings);

        this.signaling = new NetherNetHTTPSignaling.Builder()
                .setIdentity(identity)
                .setServeHttp(true)
                // RakNet 已占用 server-port 的 UDP 侧，媒体端口由系统按对端自动分配
                // RakNet holds the UDP side of server-port, so media uses system-assigned ports
                .setIceOnLocalPort(false)
                .setIceServers(iceServers(settings))
                .setAdvertisedAddresses(settings.advertiseAddresses())
                .setTokenTrust(TokenTrust.ANY)
                .setMotdProvider((host, remoteAddress) -> this.buildPong())
                .setPlayerFilter((host, player) -> this.acceptsPlayer(player))
                .build();

        this.eventLoopGroup = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());

        int handshakeTimeout = handshakeTimeoutSeconds(server.networkLoginTimeoutMilliseconds);
        InetSocketAddress bindAddress = new InetSocketAddress(
                server.getIp().isBlank() ? "0.0.0.0" : server.getIp(), server.getPort());
        var bindFuture = new ServerBootstrap()
                .group(this.eventLoopGroup)
                .channelFactory(NetherNetChannelFactory.server(this.signaling))
                .option(NetherChannelOption.NETHER_SERVER_RTC_HANDSHAKE_TIMEOUT_SECONDS, handshakeTimeout)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        NetherNetPlayerSession nukkitSession = new NetherNetPlayerSession(NetherNetInterface.this, (NetherNetChildChannel) channel);
                        NetherNetInterface.this.pendingSessions.add(nukkitSession);
                        channel.pipeline().addLast("nukkit-handler", nukkitSession);
                    }
                })
                .bind(bindAddress)
                .awaitUninterruptibly();
        if (!bindFuture.isSuccess()) {
            this.eventLoopGroup.shutdownGracefully();
            throw new RuntimeException("Failed to bind NetherNet signaling on " + bindAddress, bindFuture.cause());
        }
        this.channel = bindFuture.channel();

        log.info("NetherNet (WebRTC) signaling listening on tcp/{}, WebRTC media on system-assigned udp ports", bindAddress.getPort());
    }

    private static List<IceServerInfo> iceServers(NetherNetSettings settings) {
        List<String> urls = settings.iceServers();
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        return List.of(new IceServerInfo.Builder().setUrls(List.copyOf(urls)).build());
    }

    /**
     * 握手预算与登录超时同源（同一时间窗口），避免两个时钟取小导致配置被截断；
     * 登录超时禁用时用兜底值，废弃握手仍需回收。
     * The handshake budget shares the login timeout's window so neither clock truncates the
     * other; a disabled login timeout falls back so abandoned handshakes are still reaped.
     */
    static int handshakeTimeoutSeconds(int loginTimeoutMillis) {
        return loginTimeoutMillis <= 0
                ? DEFAULT_HANDSHAKE_TIMEOUT_SECONDS
                : Math.max(loginTimeoutMillis / 1000, 1);
    }

    private PongData buildPong() {
        QueryRegenerateEvent info = this.server.getQueryInformation();
        return new PongData.Builder()
                .setServerName(this.server.getMotd())
                .setProtocol(ProtocolInfo.CURRENT_PROTOCOL)
                .setVersion(ProtocolInfo.MINECRAFT_VERSION_NETWORK)
                .setLevelName(this.server.getSubMotd())
                .setPlayerCount(info.getPlayerCount())
                .setMaxPlayerCount(info.getMaxPlayerCount())
                .setGameType(this.server.getDefaultGamemode() == 1 ? 1 : 0)
                .setOnlineAuth(this.server.xboxAuth)
                .setSelfSignedAuth(!this.server.xboxAuth)
                .setNonce(this.pongNonce.get())
                .build();
    }

    /**
     * 是否允许对端建立传输：停机中、超出人数上限或来源地址被封禁时拒绝。
     * Whether a peer may open a transport: not while shutting down, past the player cap, or from a blocked address.
     */
    private boolean acceptsPlayer(org.cloudburstmc.netty.util.nethernet.PlayerInfo player) {
        if (!this.accepting) {
            return false;
        }
        if (this.server.getOnlinePlayers().size() >= this.server.getMaxPlayers()) {
            return false;
        }
        InetSocketAddress remote = player.remoteAddress();
        if (remote != null) {
            Long expiry = this.blockedAddresses.get(remote.getAddress());
            if (expiry != null) {
                if (expiry < System.currentTimeMillis()) {
                    this.blockedAddresses.remove(remote.getAddress());
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public boolean process() {
        this.expireLoginSessions();

        NetherNetPlayerSession session;
        while ((session = this.sessionCreationQueue.poll()) != null) {
            this.pendingSessions.remove(session);
            if (session.getDisconnectReason() != null || !session.getChannel().isActive()) {
                continue;
            }
            InetSocketAddress address = (InetSocketAddress) session.getChannel().remoteAddress();
            try {
                PlayerCreationEvent event = new PlayerCreationEvent(this, Player.class, Player.class, null, address);
                this.server.getPluginManager().callEvent(event);

                NetherNetPlayerSession replaced = this.sessions.put(event.getSocketAddress(), session);
                if (replaced != null && replaced != session && replaced.getDisconnectReason() == null) {
                    replaced.disconnect("Logged in from another location");
                }

                Constructor<? extends Player> constructor = event.getPlayerClass().getConstructor(SourceInterface.class, Long.class, InetSocketAddress.class);
                session.getState().getConnection().setPlayerCreatedNanos(System.nanoTime());
                Player player = constructor.newInstance(this, event.getClientId(), event.getSocketAddress());
                session.getState().getConnection().setPlayerCreated(true);
                player.raknetProtocol = NetherNetPlayerSession.RAKNET_PROTOCOL;
                session.setPlayer(player);
                this.server.addPlayer(address, player);
            } catch (Exception e) {
                Server.getInstance().getLogger().error("Failed to create Player", e);
                session.disconnect("Internal Server Error");
                this.sessions.remove(address);
            }
        }

        List<NetherNetPlayerSession> activeSessions = new ArrayList<>(this.sessions.size());
        Iterator<NetherNetPlayerSession> iterator = this.sessions.values().iterator();
        while (iterator.hasNext()) {
            NetherNetPlayerSession nukkitSession = iterator.next();
            Player player = nukkitSession.getPlayer();
            if (nukkitSession.getDisconnectReason() != null) {
                try {
                    player.close(player.getLeaveMessage(), nukkitSession.getDisconnectReason(), false);
                } catch (Exception e) {
                    player.getNetworkSession().disconnect("Internal error");
                    log.error("Exception closing player " + player.getName(), e);
                }
                iterator.remove();
            } else {
                activeSessions.add(nukkitSession);
            }
        }
        this.drainInboundFairly(activeSessions);
        return true;
    }

    private void drainInboundFairly(List<NetherNetPlayerSession> activeSessions) {
        if (activeSessions.isEmpty()) {
            this.inboundRoundRobinCursor = 0;
            return;
        }
        int size = activeSessions.size();
        int start = Math.floorMod(this.inboundRoundRobinCursor, size);
        int remainingPackets = RakNetInterface.MAX_INBOUND_PACKETS_PER_INTERFACE_TICK;
        long remainingBytes = RakNetInterface.MAX_INBOUND_BYTES_PER_INTERFACE_TICK;
        int visited = 0;
        while (visited < size && remainingPackets > 0 && remainingBytes > 0L) {
            NetherNetPlayerSession nukkitSession = activeSessions.get((start + visited) % size);
            RakNetPlayerSession.InboundDrain drained =
                    nukkitSession.serverTick(remainingPackets, remainingBytes);
            remainingPackets -= drained.packets();
            remainingBytes -= drained.bytes();
            visited++;
        }
        this.inboundRoundRobinCursor = (start + (visited < size ? visited : 1)) % size;
    }

    public void queueSessionForPlayerCreation(NetherNetPlayerSession session) {
        session.getState().getConnection().setQueuedForPlayerCreation(true);
        session.getState().getConnection().setQueuedForPlayerCreationNanos(System.nanoTime());
        this.pendingSessions.add(session);
        this.sessionCreationQueue.offer(session);
    }

    private void expireLoginSessions() {
        int timeoutMillis = this.server.networkLoginTimeoutMilliseconds;

        long nowNanos = System.nanoTime();
        this.pendingSessions.removeIf(session -> {
            if (session.getDisconnectReason() != null || !session.getChannel().isOpen()) {
                return true;
            }
            if (timeoutMillis <= 0) {
                return false;
            }
            if (!session.isPendingLoginTimedOut(nowNanos, timeoutMillis)) {
                return false;
            }

            log.warn("Disconnecting timed out pending NetherNet session {} in phase {}", session.getChannel().remoteAddress(), session.getState().getLogin().getPhase());
            session.disconnect("disconnectionScreen.timeout");
            return true;
        });

        if (timeoutMillis <= 0) {
            return;
        }

        for (NetherNetPlayerSession session : this.sessions.values()) {
            if (session.getDisconnectReason() != null || !session.isLoginPhaseTimedOut(nowNanos, timeoutMillis)) {
                continue;
            }
            log.warn("Disconnecting timed out NetherNet session {} in phase {}", session.getChannel().remoteAddress(), session.getState().getLogin().getPhase());
            session.disconnect("disconnectionScreen.timeout");
        }
    }

    /**
     * 把登录链绑定到建立传输的身份：RakNet 靠加密握手绑定，NetherNet 跳过了它，未绑定的链可被重放，
     * 改用信令断言绑定（对端在建连前已证明持有断言中的密钥）。
     * Ties the login chain to the identity that opened the transport: RakNet's encryption handshake
     * does this on its own, NetherNet skips it, so the signaling assertion binds it instead.
     *
     * @return why the login must be refused, or null when the two agree (or nothing is enforced)
     */
    public String checkIdentityBinding(NetworkPlayerSession session, String identityPublicKeyBase64) {
        if (!(session instanceof NetherNetPlayerSession netherNetSession)) {
            return null;
        }
        Channel channel = netherNetSession.getChannel();
        if (!this.server.xboxAuth) {
            // 离线模式本就不证明身份，没有可比对的密钥
            // Offline mode has given up on proving identity, no key worth comparing
            return TransportIdentityBinding.acceptForwardedIdentity(channel);
        }

        PublicKey identityKey = parseIdentityKey(identityPublicKeyBase64);
        if (identityKey == null) {
            return "the login chain carries no usable identity key";
        }
        return TransportIdentityBinding.mismatch(channel, identityKey);
    }

    private static PublicKey parseIdentityKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            return KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getNetworkLatency(Player player) {
        return (int) player.getNetworkSession().getPing();
    }

    @Override
    public NetworkPlayerSession getSession(InetSocketAddress address) {
        return this.sessions.get(address);
    }

    @Override
    public void close(Player player) {
        this.close(player, "unknown reason");
    }

    @Override
    public void close(Player player, String reason) {
        NetworkPlayerSession playerSession = this.getSession(player.getSocketAddress());
        if (playerSession != null) {
            playerSession.disconnect(reason);
        }
    }

    @Override
    public void shutdown() {
        this.accepting = false;
        this.pendingSessions.forEach(session -> session.disconnect("Shutdown"));
        this.sessions.values().forEach(session -> session.disconnect("Shutdown"));
        this.channel.close().awaitUninterruptibly();
        this.eventLoopGroup.shutdownGracefully();
    }

    @Override
    public void emergencyShutdown() {
        this.accepting = false;
        this.pendingSessions.forEach(session -> session.disconnect("Shutdown"));
        this.sessions.values().forEach(session -> session.disconnect("Shutdown"));
        this.channel.close().awaitUninterruptibly();
        this.eventLoopGroup.shutdownGracefully();
    }

    @Override
    public void blockAddress(InetAddress address) {
        this.blockedAddresses.put(address, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(100));
    }

    @Override
    public void blockAddress(InetAddress address, int timeout) {
        this.blockedAddresses.put(address, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(Math.max(timeout, 1)));
    }

    @Override
    public void unblockAddress(InetAddress address) {
        this.blockedAddresses.remove(address);
    }

    /**
     * NetherNet 是面向连接的，无原始数据报通道可写；查询等原始包继续走 RakNet 监听。
     * Connection-oriented: no raw datagram channel to write to, raw packets keep flowing over RakNet.
     */
    @Override
    public void sendRawPacket(InetSocketAddress socketAddress, ByteBuf payload) {
        // Network.sendPacket 把同一个 buffer 依次交给所有接口，RakNet 的 pipeline 写入消耗
        // 唯一一次引用计数并负责释放；此处不得 release，否则查询应答会双重释放
        // Network.sendPacket hands one buffer to every interface and RakNet's pipeline write
        // consumes its only refcount; releasing here would double-free the query reply
    }

    /**
     * 广告由信令 motd provider 按请求动态构建，此处无需刷新缓存。
     * The advertisement is built per request through the motd provider, nothing cached to refresh.
     */
    @Override
    public void setName(String name) {
    }

    public Network getNetwork() {
        return this.network;
    }
}
