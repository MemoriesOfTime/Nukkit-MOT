package cn.nukkit.network;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.player.PlayerCreationEvent;
import cn.nukkit.event.server.QueryRegenerateEvent;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.proxy.ProxyProtocolHandler;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.network.session.RakNetPlayerSession;
import cn.nukkit.utils.Utils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter;

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public class RakNetInterface implements AdvancedSourceInterface {

    private final Server server;
    private Network network;

    private final Channel channel;
    private final ProxyProtocolHandler proxyProtocolHandler;
    private final Map<InetSocketAddress, RakNetPlayerSession> sessions = new HashMap<>();
    private final Queue<RakNetPlayerSession> sessionCreationQueue = PlatformDependent.newMpscQueue();
    private final Set<RakNetPlayerSession> pendingSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final long serverId = ThreadLocalRandom.current().nextLong();

    public RakNetInterface(Server server) {
        this.server = server;
        this.proxyProtocolHandler = server.enableProxyProtocol ? new ProxyProtocolHandler(server.proxyProtocolWhitelist) : null;

        boolean disableNative = Boolean.parseBoolean(System.getProperty("disableNativeEventLoop"));

        Transport transport;
        if (!disableNative && Epoll.isAvailable()) {
            transport = new Transport(EpollDatagramChannel.class, EpollEventLoopGroup::new);
        } else {
            transport = new Transport(NioDatagramChannel.class, NioEventLoopGroup::new);
        }

        EventLoopGroup group = transport.eventLoopGroupFactory.apply(Runtime.getRuntime().availableProcessors());

        ServerBootstrap bootstrap = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(transport.datagramChannel))
                .group(group)
                .option(RakChannelOption.RAK_GUID, this.serverId)
                .option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, new int[]{8, 9, 10, 11})
                .childOption(RakChannelOption.RAK_ORDERING_CHANNELS, 1)
                .option(RakChannelOption.RAK_SERVER_COOKIE_MODE, this.server.rakCookieMode)
                .option(RakChannelOption.RAK_PACKET_LIMIT, this.server.rakPacketLimit)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        if (proxyProtocolHandler != null) {
                            channel.parent().pipeline().addFirst("proxy-protocol-handler", proxyProtocolHandler);
                        }
                        if (server.getPropertyBoolean("enable-query", true)) {
                            channel.pipeline().addLast("query-handler", new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                                    server.handlePacket(packet.sender(), packet.content());
                                }
                            });
                        }
                    }
                })
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        RakNetPlayerSession nukkitSession = new RakNetPlayerSession(RakNetInterface.this, (RakChildChannel) channel);
                        RakNetInterface.this.pendingSessions.add(nukkitSession);
                        channel.pipeline().addLast("nukkit-handler", nukkitSession);
                    }
                });

        String address = this.server.getIp().isBlank() ? "0.0.0.0" : this.server.getIp();

        this.channel = bootstrap.bind(address, this.server.getPort()).awaitUninterruptibly().channel();

        try {
            RakServerRateLimiter rakServerRateLimiter = this.channel.pipeline().get(RakServerRateLimiter.class);
            rakServerRateLimiter.addException(InetAddress.getLocalHost());
            rakServerRateLimiter.addException(InetAddress.getByName("127.0.0.1"));
        } catch (UnknownHostException e) {
            log.error("Failed to add localhost to exception list", e);
        }
    }

    @Override
    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public boolean process() {
        this.expireLoginSessions();

        RakNetPlayerSession session;
        while ((session = this.sessionCreationQueue.poll()) != null) {
            this.pendingSessions.remove(session);
            if (session.getDisconnectReason() != null || !session.getChannel().isActive()) {
                continue;
            }
            InetSocketAddress address = session.getChannel().remoteAddress();
            try {
                PlayerCreationEvent event = new PlayerCreationEvent(this, Player.class, Player.class, null, address);
                this.server.getPluginManager().callEvent(event);

                this.sessions.put(event.getSocketAddress(), session);

                Constructor<? extends Player> constructor = event.getPlayerClass().getConstructor(SourceInterface.class, Long.class, InetSocketAddress.class);
                session.getState().getConnection().setPlayerCreatedNanos(System.nanoTime());
                Player player = constructor.newInstance(this, event.getClientId(), event.getSocketAddress());
                session.getState().getConnection().setPlayerCreated(true);
                player.raknetProtocol = session.getChannel().config().getProtocolVersion();
                session.setPlayer(player);
                this.server.addPlayer(address, player);
            } catch (Exception e) {
                Server.getInstance().getLogger().error("Failed to create Player", e);
                session.disconnect("Internal Server Error");
                this.sessions.remove(address);
            }
        }

        Iterator<RakNetPlayerSession> iterator = this.sessions.values().iterator();
        while (iterator.hasNext()) {
            RakNetPlayerSession nukkitSession = iterator.next();
            Player player = nukkitSession.getPlayer();
            if (nukkitSession.getDisconnectReason() != null) {
                try {
                    player.close(player.getLeaveMessage(), nukkitSession.getDisconnectReason(), false);
                } catch (Exception e) {
                    player.getNetworkSession().disconnect("Internal error");
                    log.error("Exception closing player " + player.getName(), e);
                }
                if (this.proxyProtocolHandler != null) {
                    this.proxyProtocolHandler.clearMappingByRealAddress(player.getSocketAddress());
                }
                iterator.remove();
            } else {
                nukkitSession.serverTick();
            }
        }
        return true;
    }

    public void queueSessionForPlayerCreation(RakNetPlayerSession session) {
        session.getState().getConnection().setQueuedForPlayerCreation(true);
        session.getState().getConnection().setQueuedForPlayerCreationNanos(System.nanoTime());
        this.pendingSessions.add(session);
        this.sessionCreationQueue.offer(session);
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
        this.pendingSessions.forEach(session -> session.disconnect("Shutdown"));
        this.sessions.values().forEach(session -> session.disconnect("Shutdown"));
        this.channel.close().awaitUninterruptibly();
    }

    @Override
    public void emergencyShutdown() {
        this.pendingSessions.forEach(session -> session.disconnect("Shutdown"));
        this.sessions.values().forEach(session -> session.disconnect("Shutdown"));
        this.channel.close().awaitUninterruptibly();
    }

    @Override
    public void blockAddress(InetAddress address) {
        this.channel.pipeline().get(RakServerRateLimiter.class).blockAddress(address, 100, TimeUnit.DAYS);
    }

    @Override
    public void blockAddress(InetAddress address, int timeout) {
        this.channel.pipeline().get(RakServerRateLimiter.class).blockAddress(address, timeout, TimeUnit.SECONDS);
    }

    @Override
    public void unblockAddress(InetAddress address) {
        this.channel.pipeline().get(RakServerRateLimiter.class).unblockAddress(address);
    }

    private void expireLoginSessions() {
        int timeoutMillis = this.server.networkLoginTimeoutMilliseconds;

        // Always clean up disconnected pending sessions and expire stale pending sessions.
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

            log.warn("Disconnecting timed out pending session {} in phase {}", session.getChannel().remoteAddress(), session.getState().getLogin().getPhase());
            session.disconnect("disconnectionScreen.timeout");
            return true;
        });

        if (timeoutMillis <= 0) {
            return;
        }

        for (RakNetPlayerSession session : this.sessions.values()) {
            if (session.getDisconnectReason() != null || !session.isLoginPhaseTimedOut(nowNanos, timeoutMillis)) {
                continue;
            }
            log.warn("Disconnecting timed out session {} in phase {}", session.getChannel().remoteAddress(), session.getState().getLogin().getPhase());
            session.disconnect("disconnectionScreen.timeout");
        }
    }

    @Override
    public void sendRawPacket(InetSocketAddress socketAddress, ByteBuf payload) {
        this.channel.write(new DatagramPacket(payload, socketAddress));
    }

    @Override
    public void setName(String name) {
        QueryRegenerateEvent info = this.server.getQueryInformation();
        String[] names = name.split("!@#"); // Split double names within the program
        String motd = Utils.rtrim(names[0].replace(";", "\\;"), '\\');
        String subMotd = names.length > 1 ? Utils.rtrim(names[1].replace(";", "\\;"), '\\') : "";
        String port = Integer.toString(this.server.getPort());
        StringJoiner joiner = new StringJoiner(";", "", ";")
                .add("MCPE")
                .add(motd)
                .add(Integer.toString(ProtocolInfo.CURRENT_PROTOCOL))
                .add(ProtocolInfo.MINECRAFT_VERSION_NETWORK)
                .add(Integer.toString(info.getPlayerCount()))
                .add(Integer.toString(info.getMaxPlayerCount()))
                .add(Long.toString(this.serverId))
                .add(subMotd)
                .add(this.server.getDefaultGamemode() == 1 ? "Creative" : "Survival")
                .add("1") // not nintendo limited
                .add(port) // ipv4 port
                .add(port); // ipv6 port

        byte[] advertisement = joiner.toString().getBytes(StandardCharsets.UTF_8);

        this.channel.config().setOption(RakChannelOption.RAK_ADVERTISEMENT, Unpooled.wrappedBuffer(advertisement));
    }

    public Network getNetwork() {
        return this.network;
    }

    private static class Transport {

        private final Class<? extends DatagramChannel> datagramChannel;
        private final IntFunction<EventLoopGroup> eventLoopGroupFactory;

        private Transport(Class<? extends DatagramChannel> datagramChannel, IntFunction<EventLoopGroup> eventLoopGroupFactory) {
            this.datagramChannel = datagramChannel;
            this.eventLoopGroupFactory = eventLoopGroupFactory;
        }
    }

}
