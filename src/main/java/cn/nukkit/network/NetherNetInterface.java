package cn.nukkit.network;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.player.PlayerCreationEvent;
import cn.nukkit.event.server.QueryRegenerateEvent;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.NetherNetPlayerSession;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.network.session.RakNetPlayerSession;
import cn.nukkit.utils.TextFormat;
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
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPSignaling.JoinRefusal;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling.PongData;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetSignaling.IceServerInfo;
import org.cloudburstmc.netty.util.nethernet.NetherNetLogging;
import org.cloudburstmc.netty.util.nethernet.ServerIdentity;
import org.cloudburstmc.netty.util.nethernet.TokenTrust;
import org.cloudburstmc.netty.util.nethernet.TransportIdentityBinding;
import tel.schich.libdatachannel.LibDataChannelArchDetect;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
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
 * 媒体 UDP 端口由 server-udp-ports 决定；单端口钉住或映射到任一 RakNet 监听端口（server-port / IPv6）时
 * 经 {@link NetherNetMediaRelay} 与 RakNet 共用。
 * <p>
 * Adapted from PowerNukkitX NetherNet support (built on CloudburstMC netty-transport-nethernet
 * and WaterdogPE's signaling work).
 */
@Log4j2
public class NetherNetInterface implements AdvancedSourceInterface {

    /** 登录超时禁用（timeout-milliseconds=0）时的握手兜底值。 Handshake fallback while the login timeout is disabled. */
    private static final int DEFAULT_HANDSHAKE_TIMEOUT_SECONDS = 30;

    /**
     * 媒体可达性告警：评估窗口内 peerConnecting 达到下限且 iceConnected 为零即告警一次，
     * 恢复（窗口内出现成功连接）再报一次，持续故障不重复。
     * Media-reachability alarm: fires once when attempts reach the floor with zero ICE
     * successes in the window, recovers once when a connection succeeds, no repeat while held.
     */
    static final int MEDIA_ALERT_WINDOW_MINUTES = 5;
    static final long MEDIA_ALERT_MIN_ATTEMPTS = 5;

    /**
     * 共用端口时自动挑选内部回环端口的起点。
     * Where the shared-port search for an internal loopback port starts.
     */
    static final int DEFAULT_MEDIA_PORT = 19134;
    /** 自动挑选内部端口时最多探测的端口数。 How many ports the internal-port search probes at most. */
    static final int MEDIA_PORT_ATTEMPTS = 16;

    private final Server server;
    private Network network;

    private final Channel channel;
    private final EventLoopGroup eventLoopGroup;
    private final NetherNetHTTPSignaling signaling;
    private final NetherNetTransportStats stats = new NetherNetTransportStats();
    /**
     * 与 RakNet 共用某个监听端口时的进程内中继（server-udp-ports 把媒体发布在 server-port 或 IPv6 监听上），否则为 null。
     * The in-process relay while media shares a RakNet listener port (server-udp-ports
     * publishes media on server-port or the IPv6 listener), null otherwise.
     */
    private final NetherNetMediaRelay relay;
    /** 告警与 /status 中展示的媒体端口描述。 Media port description shown by the alarm and /status. */
    private final String mediaDescription;
    private long nextMediaAlertMinute = -1;
    private boolean mediaAlarmRaised;
    private final Map<InetSocketAddress, NetherNetPlayerSession> sessions = new HashMap<>();
    private final Queue<NetherNetPlayerSession> sessionCreationQueue = PlatformDependent.newMpscQueue();
    private final Set<NetherNetPlayerSession> pendingSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<InetAddress, Long> blockedAddresses = new ConcurrentHashMap<>();
    /** 广告随附的 nonce（P2P 世界机制，专用服务器客户端不回传也不校验），启动时随机一次。 Rides along in the advertisement, random once at startup; a P2P-worlds mechanism, never verified. */
    private final AtomicReference<String> pongNonce = new AtomicReference<>(String.format("%016x", new SecureRandom().nextLong()));

    private volatile boolean accepting = true;
    private int inboundRoundRobinCursor;

    public NetherNetInterface(Server server, NetherNetSettings settings) {
        this(server, settings, null);
    }

    /**
     * @param rakNet RakNet 接口；server-udp-ports 把媒体发布在 server-port 上时，从它的监听 socket 中继媒体，
     *               为 null 时该配置直接中止启动。 The RakNet interface; media published on server-port
     *               is relayed from its listener sockets, null aborts startup for that configuration.
     */
    public NetherNetInterface(Server server, NetherNetSettings settings, RakNetInterface rakNet) {
        this.server = server;

        try {
            LibDataChannelArchDetect.initialize();
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to initialize NetherNet: the native libdatachannel library may be missing for this platform", t);
        }
        NetherNetLogging.setNativeLogLevel("WARN");

        ServerIdentity identity = NetherNetIdentity.load(server, settings);
        NetherNetUdpPorts mediaPorts;
        try {
            mediaPorts = resolveMediaPorts(server);
        } catch (IllegalArgumentException e) {
            log.fatal(e.getMessage());
            log.fatal(server.getLanguage().translateString("nukkit.nethernet.udpPorts.hint"));
            throw e;
        }
        boolean sharedPort = mediaPorts != null
                && (mediaPorts.sharesPort(server.getPort())
                || (server.isIpv6Enabled() && mediaPorts.sharesPort(server.getIpv6Port())));
        if (sharedPort) {
            List<Channel> listeners = rakNet == null ? List.of() : rakNet.getDatagramChannels();
            if (listeners.isEmpty()) {
                String message = server.getLanguage().translateString("nukkit.nethernet.sharedPort.noRakNet",
                        udpPortsEntry(server), server.getPort());
                log.fatal(message);
                throw new IllegalStateException(message);
            }
            DatagramSocket reservedMediaPort = reserveMediaPort(server, mediaPorts);
            if (mediaPorts.pinsOnly(server.getPort())
                    || (server.isIpv6Enabled() && mediaPorts.pinsOnly(server.getIpv6Port()))) {
                mediaPorts = mediaPorts.relayedThrough(reservedMediaPort.getLocalPort());
            }
            this.relay = new NetherNetMediaRelay(reservedMediaPort,
                    Math.max(256, server.getMaxPlayers() * 4), this::isAddressBlocked);
            listeners.forEach(this.relay::attach);
        } else {
            this.relay = null;
        }

        this.signaling = new NetherNetHTTPSignaling.Builder()
                .setIdentity(identity)
                .setServeHttp(true)
                // RakNet 已占用 server-port 的 UDP 侧，信令端口不可复用；媒体端口经 server-udp-ports
                // 钉住（默认等于 server-port，即与 RakNet 共用、由中继转发到自动挑选的回环端口；
                // 0 仍为系统自动分配）。写成映射（19132:19134）可钉住内部端口
                // RakNet holds the UDP side of server-port so the signaling port stays off-limits;
                // media is pinned via server-udp-ports (default: server-port itself, shared with
                // RakNet and relayed to an automatically picked loopback port; 0 still means
                // system-assigned). A mapping (19132:19134) pins the internal port instead
                .setIceOnLocalPort(false)
                .setIceServers(iceServers(settings))
                .setAdvertisedAddresses(mediaPorts == null ? Set.of() : mediaPorts.advertisedAddresses())
                .setTokenTrust(TokenTrust.ANY)
                .setMotdProvider((host, remoteAddress) -> this.buildPong())
                .setPlayerFilter((host, player) -> this.refusePlayer(player))
                .build();

        this.eventLoopGroup = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());

        int handshakeTimeout = handshakeTimeoutSeconds(server.networkLoginTimeoutMilliseconds);
        this.mediaDescription = describeMedia(server, mediaPorts, sharedPort);
        InetSocketAddress bindAddress = new InetSocketAddress(
                server.getIp().isBlank() ? "0.0.0.0" : server.getIp(), server.getPort());
        NetherNetServerSignaling channelSignaling = this.signaling;
        if (sharedPort) {
            NetherNetSharedPortSdp.ExternalPorts externalPorts = new NetherNetSharedPortSdp.ExternalPorts(
                    server.getPort(), server.isIpv6Enabled() ? server.getIpv6Port() : -1);
            int internalPort = mediaPorts.begin();
            channelSignaling = this.relay.decorate(this.signaling, sdp -> NetherNetSharedPortSdp.rewriteAnswer(
                    sdp, internalPort, this.publishedAddresses(), externalPorts));
        } else if (mediaPorts != null) {
            channelSignaling = mediaPorts.decorate(this.signaling);
        }
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(this.eventLoopGroup)
                .channelFactory(NetherNetChannelFactory.server(channelSignaling))
                .option(NetherChannelOption.NETHER_SERVER_RTC_HANDSHAKE_TIMEOUT_SECONDS, handshakeTimeout)
                .option(NetherChannelOption.NETHER_METRICS, this.stats)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        NetherNetPlayerSession nukkitSession = new NetherNetPlayerSession(NetherNetInterface.this, (NetherNetChildChannel) channel);
                        NetherNetInterface.this.pendingSessions.add(nukkitSession);
                        channel.pipeline().addLast("nukkit-handler", nukkitSession);
                    }
                });
        if (sharedPort) {
            // 媒体 socket 绑在回环：只有中继 leg 能到达它，也不会从内部端口向客户端发起探测；
            // 对端地址由 leg 以 prflx 带入，库内按信令来源猜测候选的逻辑同样无用
            // The media socket binds to loopback: only relay legs reach it and no probes leave the
            // internal port toward clients; the peer arrives as prflx through the leg, so the
            // library's guessing from the signaling address is pointless too
            bootstrap.option(NetherChannelOption.NETHER_PEER_CONNECTION_CONFIG, mediaPorts.peerConfig(this.relay.mediaEndpoint()));
            bootstrap.option(NetherChannelOption.NETHER_INFER_PEER_CANDIDATES, false);
        } else if (mediaPorts != null) {
            bootstrap.option(NetherChannelOption.NETHER_PEER_CONNECTION_CONFIG, mediaPorts.peerConfig(bindAddress));
        }
        var bindFuture = bootstrap
                .bind(bindAddress)
                .awaitUninterruptibly();
        if (!bindFuture.isSuccess()) {
            if (this.relay != null) {
                this.relay.shutdown();
            }
            this.eventLoopGroup.shutdownGracefully();
            throw new RuntimeException("Failed to bind NetherNet signaling on " + bindAddress, bindFuture.cause());
        }
        this.channel = bindFuture.channel();

        if (mediaPorts != null) {
            log.info(server.getLanguage().translateString("nukkit.nethernet.listening.pinned",
                    bindAddress.getPort(), this.mediaDescription));
        } else {
            log.info(server.getLanguage().translateString("nukkit.nethernet.listening.auto",
                    bindAddress.getPort()));
        }
    }

    private static String describeMedia(Server server, NetherNetUdpPorts mediaPorts, boolean sharedPort) {
        if (mediaPorts == null) {
            return server.getLanguage().translateString("nukkit.nethernet.media.systemAssigned");
        }
        if (!sharedPort) {
            return mediaPorts.toString();
        }
        // 共用形态下单端口映射总是成立（pinsOnly 已先经 relayedThrough 转换），外部端口 = 内部端口 + 偏移
        // In shared mode a single-port mapping always holds (pinsOnly was relayedThrough first),
        // the external port being the internal one plus the offset
        int externalPort = mediaPorts.begin() + mediaPorts.offset();
        String description = server.getLanguage().translateString("nukkit.nethernet.media.sharedPort",
                externalPort, mediaPorts.begin());
        return mediaPorts.advertisedAddresses().isEmpty()
                ? description : description + " via " + mediaPorts.advertisedAddresses();
    }

    /**
     * 共用端口应答里发布的地址：每次应答重新枚举（加入是低频事件，网卡变化即时生效）。
     * Addresses published in a shared-port answer, enumerated per answer (joins are rare and
     * interface changes take effect at once).
     */
    private List<InetAddress> publishedAddresses() {
        List<InetAddress> addresses = NetherNetSharedPortSdp.localAddresses(
                this.server.getIp(), this.server.getIpv6Address(), this.server.isIpv6Enabled());
        if (addresses.isEmpty()) {
            log.warn(this.server.getLanguage().translateString("nukkit.nethernet.sharedPort.noAddress"));
        }
        return addresses;
    }

    private boolean isAddressBlocked(InetAddress address) {
        Long expiry = this.blockedAddresses.get(address);
        return expiry != null && expiry >= System.currentTimeMillis();
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

    /**
     * server-udp-ports 的条目值，默认 19132（与 server-port 相等即共用 RakNet 端口）。生产的 Config 加载时即以
     * 该字面默认补齐缺失键，server-port 回退仅覆盖绕过默认加载的读取。
     * The server-udp-ports entry, 19132 by default (equal to server-port it shares RakNet's port).
     * Production Config auto-fills a missing key with that literal default at load time, so the
     * server-port fallback only covers reads bypassing default loading.
     */
    private static String udpPortsEntry(Server server) {
        return server.getPropertyString("server-udp-ports", String.valueOf(server.getPort()));
    }

    /**
     * server-port 的 UDP 侧归 RakNet，IPv6 监听同理；server.properties 是关键配置，
     * 解析错误或窗口覆盖任一监听端口（含外部映射窗口）时抛本地化错误中止启动，而非回退自动分配。
     * 直接写某个监听端口（如 19132，内部回环端口由 {@link #reserveMediaPort} 自动挑选）或单端口映射到它
     * （如 19132:19134、19133:19134）都表示媒体经进程内中继与 RakNet 共用该端口，两种监听一视同仁。
     * The UDP side of server-port belongs to RakNet, likewise the IPv6 listener;
     * server.properties is critical config, so parse errors or windows covering either listener
     * (external mapping windows included) abort startup with a localized error instead of falling
     * back to auto ports. Writing a listener port itself (19132, say, with the internal loopback
     * port picked by {@link #reserveMediaPort}) or a single-port mapping onto one (19132:19134,
     * 19133:19134) both share that port with RakNet through the in-process relay; the two
     * listeners are treated alike.
     */
    private static NetherNetUdpPorts resolveMediaPorts(Server server) {
        String value = udpPortsEntry(server);
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse(value, server.getLanguage());
        boolean ipv6Enabled = server.isIpv6Enabled();
        if (ports == null || ports.pinsOnly(server.getPort())
                || (ipv6Enabled && ports.pinsOnly(server.getIpv6Port()))) {
            return ports;
        }
        if (ports.contains(server.getPort())) {
            throw new IllegalArgumentException(server.getLanguage()
                    .translateString("nukkit.nethernet.udpPorts.coversServerPort", value, server.getPort()));
        }
        if (ipv6Enabled && ports.contains(server.getIpv6Port())) {
            throw new IllegalArgumentException(server.getLanguage()
                    .translateString("nukkit.nethernet.udpPorts.coversIpv6Port", value, server.getIpv6Port()));
        }
        if (ports.offset() != 0) {
            int externalBegin = ports.begin() + ports.offset();
            int externalEnd = ports.end() + ports.offset();
            // 覆盖监听端口的外部窗口无法中继（中继只挂在真实的监听 socket 上），仅单端口映射例外
            // An external window over a listener cannot be relayed (the relay sits only on the
            // real listener sockets); the single-port mapping is the one exception
            if (!ports.publishesOn(server.getPort())
                    && externalBegin <= server.getPort() && server.getPort() <= externalEnd) {
                throw new IllegalArgumentException(server.getLanguage().translateString(
                        "nukkit.nethernet.udpPorts.sharedWindow", value, server.getPort(), ports.begin()));
            }
            if (ipv6Enabled && !ports.publishesOn(server.getIpv6Port())
                    && externalBegin <= server.getIpv6Port() && server.getIpv6Port() <= externalEnd) {
                throw new IllegalArgumentException(server.getLanguage().translateString(
                        "nukkit.nethernet.udpPorts.sharedWindowIpv6", value, server.getIpv6Port(), ports.begin()));
            }
        }
        return ports;
    }

    /**
     * 共用端口时预留内部回环端口（{@link NetherNetMediaRelay#reserveMediaPort}）：钉住的内部端口被占即中止启动；
     * 自动挑选从 {@link #DEFAULT_MEDIA_PORT} 起向上探测，跳过 RakNet 的监听端口，取第一个空闲端口——
     * 固定段不与系统临时端口段重叠，同机的第二个实例也会因预留而自然错开。
     * Reserves the internal loopback port for the shared port ({@link NetherNetMediaRelay#reserveMediaPort}):
     * a pinned internal port already in use aborts startup; the automatic pick probes upward from
     * {@link #DEFAULT_MEDIA_PORT}, skipping RakNet's listener ports, and takes the first free one —
     * a fixed range stays clear of the ephemeral range, and a second instance on the host lands on
     * the next port thanks to the reservation.
     */
    private static DatagramSocket reserveMediaPort(Server server, NetherNetUdpPorts mediaPorts) {
        String value = udpPortsEntry(server);
        if (mediaPorts.publishesOn(server.getPort())
                || (server.isIpv6Enabled() && mediaPorts.publishesOn(server.getIpv6Port()))) {
            try {
                return NetherNetMediaRelay.reserveMediaPort(mediaPorts.begin());
            } catch (IOException e) {
                String message = server.getLanguage().translateString(
                        "nukkit.nethernet.sharedPort.internalInUse", value, mediaPorts.begin());
                log.fatal(message);
                throw new IllegalStateException(message, e);
            }
        }
        int last = DEFAULT_MEDIA_PORT + MEDIA_PORT_ATTEMPTS - 1;
        IOException lastFailure = null;
        for (int port = DEFAULT_MEDIA_PORT; port <= last; port++) {
            if (port == server.getPort() || (server.isIpv6Enabled() && port == server.getIpv6Port())) {
                continue;
            }
            try {
                return NetherNetMediaRelay.reserveMediaPort(port);
            } catch (IOException e) {
                lastFailure = e;
            }
        }
        String message = server.getLanguage().translateString("nukkit.nethernet.sharedPort.noFreeInternalPort",
                value, server.getPort(), DEFAULT_MEDIA_PORT, last);
        log.fatal(message);
        throw new IllegalStateException(message, lastFailure);
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

    private JoinRefusal refusePlayer(org.cloudburstmc.netty.util.nethernet.PlayerInfo player) {
        if (!this.accepting) {
            return JoinRefusal.REJECTED;
        }
        if (this.server.getOnlinePlayers().size() >= this.server.getMaxPlayers()) {
            return JoinRefusal.FULL;
        }
        InetSocketAddress remote = player.remoteAddress();
        if (remote != null) {
            Long expiry = this.blockedAddresses.get(remote.getAddress());
            if (expiry != null) {
                if (expiry < System.currentTimeMillis()) {
                    this.blockedAddresses.remove(remote.getAddress());
                } else {
                    return JoinRefusal.REJECTED;
                }
            }
        }
        this.stats.onJoinAccepted();
        return null;
    }

    @Override
    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public boolean process() {
        this.expireLoginSessions();
        this.sampleStatsAndEvaluateMediaAlarm();

        NetherNetPlayerSession session;
        while ((session = this.sessionCreationQueue.poll()) != null) {
            this.pendingSessions.remove(session);
            if (session.getDisconnectReason() != null || !session.getChannel().isActive()) {
                continue;
            }
            // 远端地址自会话构造首次读取起被 Netty 缓存为库给出的信令来源（真实客户端地址），
            // ICE 连上后也不会变成 leg 地址，玩家创建、封禁与日志直接使用即可
            // Netty caches the remote from the session's first read as the library's signaling
            // source (the real client); it never turns into the leg address once ICE connects,
            // so player creation, bans and logs can use it as is
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
                    if (player != null) {
                        player.close(player.getLeaveMessage(), nukkitSession.getDisconnectReason(), false);
                    }
                } catch (Exception e) {
                    player.getNetworkSession().disconnect("Internal error");
                    log.error("Exception closing player " + player.getName(), e);
                }
                iterator.remove();
            } else if (!nukkitSession.getChannel().isActive()) {
                // 会话层信号（closeFuture/tick/channelInactive）全部缺失时的最终防线：
                // 已入表的会话在建表时必然活跃，失活即远端关闭，本 tick 置因、下 tick 回收
                // Last resort when every session-level signal went missing: a session in the map
                // was active when it entered, so an inactive channel is a remote close
                nukkitSession.disconnect("transport:closed_by_remote_peer");
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
            this.stats.onLoginTimeout();
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
            this.stats.onLoginTimeout();
            session.disconnect("disconnectionScreen.timeout");
        }
    }

    /**
     * 主线程每 tick 驱动：分钟采样推进历史环形数组，并按告警周期评估媒体可达性
     * （首评估定在启动后一个完整窗口，避免用不完整数据触发）。
     * Main-thread tick hook: advances the minute ring, then evaluates media reachability
     * per alarm cadence (the first check waits one full window so it never fires on partial data).
     */
    private void sampleStatsAndEvaluateMediaAlarm() {
        long nowMillis = System.currentTimeMillis();
        this.stats.sampleMinute(nowMillis);

        long minute = Math.floorDiv(nowMillis, 60_000L);
        if (this.nextMediaAlertMinute < 0) {
            this.nextMediaAlertMinute = minute + MEDIA_ALERT_WINDOW_MINUTES;
            return;
        }
        if (minute < this.nextMediaAlertMinute) {
            return;
        }
        this.nextMediaAlertMinute = minute + MEDIA_ALERT_WINDOW_MINUTES;

        NetherNetTransportStats.TransportSnapshot snapshot =
                this.stats.snapshot(MEDIA_ALERT_WINDOW_MINUTES, nowMillis);
        if (mediaUnreachable(snapshot)) {
            if (!this.mediaAlarmRaised) {
                this.mediaAlarmRaised = true;
                log.warn(this.server.getLanguage().translateString("nukkit.nethernet.stats.mediaUnreachable",
                        snapshot.peerConnecting(), snapshot.iceConnected(), MEDIA_ALERT_WINDOW_MINUTES, this.mediaDescription));
            }
        } else if (this.mediaAlarmRaised) {
            this.mediaAlarmRaised = false;
            // 窗口内确有成功连接才算恢复；流量枯竭（没人再试）只静默解除。
            // Only a real success reports recovery; traffic drying up just clears silently.
            if (snapshot.iceConnected() > 0) {
                log.info(this.server.getLanguage().translateString("nukkit.nethernet.stats.mediaRecovered",
                        snapshot.iceConnected(), MEDIA_ALERT_WINDOW_MINUTES));
            }
        }
    }

    /**
     * 媒体不可达判定：窗口内尝试量达到下限且 ICE 零连通（零是精确值，见 stats 类注）。
     * Unreachable verdict: attempts at the floor with zero ICE successes in the window
     * (zero stays exact, see the stats class notes).
     */
    static boolean mediaUnreachable(NetherNetTransportStats.TransportSnapshot snapshot) {
        return snapshot.peerConnecting() >= MEDIA_ALERT_MIN_ATTEMPTS && snapshot.iceConnected() == 0;
    }

    /**
     * /status 渲染行（走 lang.ini；simple 两行、full 四行，共用端口时 full 多一行中继状态；
     * 会话计数仅主线程命令路径读取）。
     * /status lines localized through lang.ini; two in simple mode, four in full plus a relay line
     * while the port is shared, session counts are read from the main-thread command path only.
     */
    public List<String> buildStatusLines(int windowMinutes, boolean full) {
        NetherNetTransportStats.TransportSnapshot snapshot =
                this.stats.snapshot(windowMinutes, System.currentTimeMillis());
        List<String> lines = new ArrayList<>(5);
        lines.add(TextFormat.GOLD + this.server.getLanguage().translateString("nukkit.nethernet.stats.summary",
                this.mediaDescription, this.sessions.size(), this.pendingSessions.size()));
        lines.add(TextFormat.GOLD + this.server.getLanguage().translateString("nukkit.nethernet.stats.funnel",
                snapshot.joinAccepted(), snapshot.iceConnected(), snapshot.peerConnecting(),
                snapshot.rtcConnected(), windowMinutes));
        if (full) {
            lines.add(TextFormat.GOLD + this.server.getLanguage().translateString("nukkit.nethernet.stats.traffic",
                    formatBytes(snapshot.bytesIn()), formatBytes(snapshot.bytesOut())));
            lines.add(TextFormat.GOLD + this.server.getLanguage().translateString("nukkit.nethernet.stats.dropped",
                    snapshot.postConnectDrops(), snapshot.loginTimeouts(), windowMinutes));
            if (this.relay != null) {
                NetherNetMediaRelay.Snapshot relaySnapshot = this.relay.snapshot();
                lines.add(TextFormat.GOLD + this.server.getLanguage().translateString("nukkit.nethernet.stats.relay",
                        relaySnapshot.legs(), relaySnapshot.legsOpened(),
                        formatBytes(relaySnapshot.bytesIn()), formatBytes(relaySnapshot.bytesOut()),
                        relaySnapshot.rejectedUfrag(), relaySnapshot.rejectedLimit() + relaySnapshot.rejectedBlocked()));
            }
        }
        return lines;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024f);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / 1024f / 1024f);
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / 1024f / 1024f / 1024f);
    }

    public NetherNetTransportStats getTransportStats() {
        return this.stats;
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
        if (this.relay != null) {
            this.relay.shutdown();
        }
        this.eventLoopGroup.shutdownGracefully();
    }

    @Override
    public void emergencyShutdown() {
        this.accepting = false;
        this.pendingSessions.forEach(session -> session.disconnect("Shutdown"));
        this.sessions.values().forEach(session -> session.disconnect("Shutdown"));
        this.channel.close().awaitUninterruptibly();
        if (this.relay != null) {
            this.relay.shutdown();
        }
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
