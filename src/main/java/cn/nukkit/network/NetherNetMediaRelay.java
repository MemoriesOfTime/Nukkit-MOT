package cn.nukkit.network;

import cn.nukkit.network.proxy.ProxyProtocolHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * 进程内 UDP 中继，让 NetherNet 媒体与 RakNet 共用 server-port。
 * <p>
 * libjuice 自持 socket、没有外部注入接口，两个传输无法共享同一个 UDP socket；这里把 RakNet 监听 socket 收到的
 * STUN/DTLS 数据报（RFC 7983 首字节分流，STUN 再校验 magic cookie 以区分 RakNet 的 0x01/0x02 ping）按来源
 * 地址经"每客户端一条回环 leg"转发到 libjuice 的 ICE mux 内部端口，libjuice 的回包沿 leg 回到 RakNet socket
 * 再发给客户端。libjuice 只按来源地址与 ufrag 分流、不看目的地址，所以把 leg 当作对端即可完成 ICE/DTLS/SCTP。
 * <p>
 * 首个 STUN Binding Request 必须携带某个待建连接的本端 ufrag（来自应答 SDP）才会开 leg，未知来源的其它数据报
 * 一律丢弃；leg 有总数与每 IP 上限，空闲超时回收。子 channel 连上后看到的远端是 leg 的回环地址，
 * 由 {@link #clientFor} 换回真实客户端地址。内部端口可经 {@link #reserveMediaPort} 预留到首个 offer 为止。
 * <p>
 * In-process UDP relay so NetherNet media shares server-port with RakNet. libjuice owns its own
 * socket with no injection API, so the two transports cannot share one UDP socket; instead STUN/DTLS
 * datagrams arriving on the RakNet listener (RFC 7983 first-byte demux, STUN additionally checked
 * for the magic cookie to tell it from RakNet's 0x01/0x02 pings) are forwarded per client over a
 * loopback "leg" to libjuice's ICE mux port, and its replies travel back the same leg onto the
 * RakNet socket. libjuice demultiplexes by source address and ufrag only, never by destination, so
 * the leg passes as the peer. A leg opens only for a STUN Binding Request carrying the local ufrag
 * of a pending answer; anything else from an unknown source is dropped. Legs are capped in total
 * and per address and reaped when idle. The child channel reports the leg's loopback address as
 * its remote once connected, {@link #clientFor} maps it back. The internal port can be held through
 * {@link #reserveMediaPort} until the first offer.
 */
@Log4j2
public final class NetherNetMediaRelay {

    static final String HANDLER_NAME = "nethernet-media-relay";
    static final long DEFAULT_IDLE_TTL_NANOS = TimeUnit.SECONDS.toNanos(60);
    static final long SWEEP_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(10);
    /**
     * 一个 IP 后面可能是一整个 LAN 派对，且每次 ICE 重试都会换源端口；ufrag 门禁已挡住滥用，这里只是兜底。
     * One address may hide a LAN party and every ICE retry changes the source port; the ufrag gate
     * already stops abuse, this is only a backstop.
     */
    static final int MAX_LEGS_PER_ADDRESS = 64;
    /** 应答发出到客户端首个 STUN 到达的窗口，含握手超时余量。 Window from the answer leaving to the first STUN arriving, with handshake headroom. */
    static final long UFRAG_TTL_NANOS = TimeUnit.SECONDS.toNanos(120);

    private static final int STUN_MAGIC_COOKIE = 0x2112A442;
    private static final int STUN_HEADER_LENGTH = 20;
    private static final int STUN_BINDING_REQUEST = 0x0001;
    private static final int STUN_ATTR_USERNAME = 0x0006;
    private static final int STUN_MAX_USERNAME_LENGTH = 512;
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    /** RFC 7983 §7 的首字节分流结果。 The RFC 7983 §7 first-byte verdict. */
    enum Kind {
        STUN, DTLS, OTHER
    }

    /** 中继状态快照，供 /status 展示。 Relay snapshot for /status. */
    public record Snapshot(int legs, long legsOpened, long packetsIn, long bytesIn, long packetsOut, long bytesOut,
                           long rejectedUfrag, long rejectedLimit, long rejectedBlocked, long droppedNoLeg) {
    }

    private final InetSocketAddress mediaEndpoint;
    private final int maxLegs;
    private final long idleTtlNanos;
    private final Predicate<InetAddress> addressBlocked;

    private final Map<InetSocketAddress, Leg> legsByClient = new ConcurrentHashMap<>();
    private final Map<Integer, Leg> legsByLocalPort = new ConcurrentHashMap<>();
    private final Map<InetAddress, Integer> legsPerAddress = new ConcurrentHashMap<>();
    /** ufrag → 过期时刻（nanoTime）。 ufrag → expiry (nanoTime). */
    private final Map<String, Long> expectedUfrags = new ConcurrentHashMap<>();
    private final List<Channel> listeners = new CopyOnWriteArrayList<>();

    private final LongAdder legsOpened = new LongAdder();
    private final LongAdder packetsIn = new LongAdder();
    private final LongAdder bytesIn = new LongAdder();
    private final LongAdder packetsOut = new LongAdder();
    private final LongAdder bytesOut = new LongAdder();
    private final LongAdder rejectedUfrag = new LongAdder();
    private final LongAdder rejectedLimit = new LongAdder();
    private final LongAdder rejectedBlocked = new LongAdder();
    private final LongAdder droppedNoLeg = new LongAdder();

    private volatile ScheduledFuture<?> sweepTask;
    private volatile boolean closed;
    /** 首个 offer 前占住内部端口的 socket，见 {@link #reserveMediaPort}；未预留或已放开为 null。 Holds the internal port until the first offer, see {@link #reserveMediaPort}; null once released or never reserved. */
    private final AtomicReference<DatagramSocket> reservation = new AtomicReference<>();

    public NetherNetMediaRelay(int mediaPort, int maxLegs, Predicate<InetAddress> addressBlocked) {
        this(mediaPort, maxLegs, addressBlocked, DEFAULT_IDLE_TTL_NANOS);
    }

    /**
     * 以 {@link #reserveMediaPort} 预留好的端口为内部端口，中继接管预留：首个 offer 进入库之前或关闭时放开。
     * Takes a port reserved through {@link #reserveMediaPort} as the internal port and owns the
     * reservation, letting go right before the first offer enters the library or on shutdown.
     */
    public NetherNetMediaRelay(DatagramSocket reservedMediaPort, int maxLegs, Predicate<InetAddress> addressBlocked) {
        this(reservedMediaPort.getLocalPort(), maxLegs, addressBlocked, DEFAULT_IDLE_TTL_NANOS);
        this.reservation.set(reservedMediaPort);
    }

    NetherNetMediaRelay(int mediaPort, int maxLegs, Predicate<InetAddress> addressBlocked, long idleTtlNanos) {
        this.mediaEndpoint = new InetSocketAddress(LOOPBACK, mediaPort);
        this.maxLegs = maxLegs;
        this.addressBlocked = addressBlocked;
        this.idleTtlNanos = idleTtlNanos;
    }

    /**
     * 预留回环上的内部媒体端口。libjuice 只在首个 PeerConnection 收集候选时才绑定它，此前端口一直空着，
     * 别的进程（比如同机的第二个实例）可能抢走；用一个普通 UDP socket 占住，能立刻发现端口已被占用
     * （抛 {@link IOException}），也挡住启动到首个玩家之间的抢占。libjuice 不设 SO_REUSEADDR，
     * 放开前它绑不上；放开与它绑定同在一次新连接回调里，间隔可忽略。
     * Reserves the internal media port on loopback. libjuice binds it only when the first
     * PeerConnection gathers candidates, leaving it free for anyone else (a second instance on
     * the host, say) to grab until then; a plain UDP socket both surfaces a port already in use
     * (throws {@link IOException}) and keeps it from being taken between startup and the first
     * player. libjuice sets no SO_REUSEADDR so it cannot bind while the reservation stands; the
     * release and its bind happen in the same new-connection callback, a negligible gap.
     */
    public static DatagramSocket reserveMediaPort(int port) throws IOException {
        return new DatagramSocket(new InetSocketAddress(LOOPBACK, port));
    }

    /** 放开预留（幂等）：库即将绑定内部端口，或中继关闭。 Lets go of the reservation (idempotent): the library is about to bind, or the relay shuts down. */
    void releaseMediaPort() {
        DatagramSocket held = this.reservation.getAndSet(null);
        if (held != null) {
            held.close();
        }
    }

    boolean holdsMediaPort() {
        return this.reservation.get() != null;
    }

    /** libjuice 应当绑定的地址（回环 + 内部端口）。 Where libjuice should bind (loopback + internal port). */
    public InetSocketAddress mediaEndpoint() {
        return this.mediaEndpoint;
    }

    /**
     * 挂到 RakNet 底层 DatagramChannel 的管线上。Proxy Protocol 处理器在的话排在它后面，
     * 这样看到的才是真实客户端地址（回写也会被它映射回代理地址）。
     * Hooks into the RakNet datagram channel's pipeline, after the Proxy Protocol handler when
     * present so the sender is the real client (and replies get mapped back to the proxy).
     */
    public void attach(Channel listener) {
        // 从非事件循环线程 add 的 handler，其 handlerAdded 被推迟为事件循环上的后续任务，在它执行前
        // 流经的消息会静默跳过该 handler、径直落到 RakNet；必须在事件循环上挂载并等它生效
        // A handler added from off the event loop gets its handlerAdded deferred to a later
        // event-loop task, and messages dispatched before it runs skip the handler straight
        // into RakNet's arms; mount on the event loop and wait for it to take effect
        Runnable mount = () -> {
            ChannelPipeline pipeline = listener.pipeline();
            Demux demux = new Demux(listener);
            ChannelHandlerContext proxy = pipeline.context(ProxyProtocolHandler.class);
            if (proxy != null) {
                pipeline.addAfter(proxy.name(), HANDLER_NAME, demux);
            } else {
                pipeline.addFirst(HANDLER_NAME, demux);
            }
        };
        if (listener.eventLoop().inEventLoop()) {
            mount.run();
        } else {
            listener.eventLoop().submit(mount).syncUninterruptibly();
        }
        this.listeners.add(listener);
        if (this.sweepTask == null) {
            this.sweepTask = listener.eventLoop().scheduleAtFixedRate(this::sweep,
                    SWEEP_INTERVAL_MILLIS, SWEEP_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 包装信令：offer 剥掉客户端候选（回环 socket 发不到），应答经 {@code answerRewriter} 改写并登记本端 ufrag。
     * Wraps the signaling: offers lose their candidates (unreachable from loopback), answers pass
     * through {@code answerRewriter} and register their local ufrag.
     */
    public NetherNetServerSignaling decorate(NetherNetServerSignaling delegate, UnaryOperator<String> answerRewriter) {
        return new SharedPortSignaling(delegate, answerRewriter);
    }

    /** 登记一个待建连接的本端 ufrag，窗口内携带它的 STUN 请求可开 leg。 Registers a pending local ufrag that may open a leg within the window. */
    public void expectUfrag(String ufrag) {
        if (ufrag == null || ufrag.isEmpty()) {
            return;
        }
        this.expectedUfrags.put(ufrag, System.nanoTime() + UFRAG_TTL_NANOS);
    }

    boolean isExpected(String ufrag) {
        Long expiry = this.expectedUfrags.get(ufrag);
        return expiry != null && System.nanoTime() - expiry < 0;
    }

    /**
     * 回环 leg 地址对应的真实客户端地址；不是 leg 地址时返回 null。
     * The real client behind a loopback leg address, or null when the address is not a leg.
     */
    public InetSocketAddress clientFor(SocketAddress address) {
        if (!(address instanceof InetSocketAddress inet) || inet.getAddress() == null || !inet.getAddress().isLoopbackAddress()) {
            return null;
        }
        Leg leg = this.legsByLocalPort.get(inet.getPort());
        return leg == null ? null : leg.client;
    }

    public Snapshot snapshot() {
        return new Snapshot(this.legsByClient.size(), this.legsOpened.sum(), this.packetsIn.sum(), this.bytesIn.sum(),
                this.packetsOut.sum(), this.bytesOut.sum(), this.rejectedUfrag.sum(), this.rejectedLimit.sum(),
                this.rejectedBlocked.sum(), this.droppedNoLeg.sum());
    }

    int legCount() {
        return this.legsByClient.size();
    }

    public void shutdown() {
        this.closed = true;
        this.releaseMediaPort();
        ScheduledFuture<?> sweep = this.sweepTask;
        if (sweep != null) {
            sweep.cancel(false);
        }
        for (Channel listener : this.listeners) {
            ChannelPipeline pipeline = listener.pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                try {
                    pipeline.remove(HANDLER_NAME);
                } catch (Exception ignored) {
                    // 监听 channel 已在关闭途中 The listener is already closing
                }
            }
        }
        this.listeners.clear();
        for (Leg leg : new ArrayList<>(this.legsByClient.values())) {
            leg.close();
        }
        this.expectedUfrags.clear();
    }

    static Kind classify(ByteBuf buf) {
        int readable = buf.readableBytes();
        if (readable == 0) {
            return Kind.OTHER;
        }
        int first = buf.getUnsignedByte(buf.readerIndex());
        if (first <= 3) {
            // RakNet 的 0x01/0x02 ping 在同一位置放的是时间戳，magic cookie 把它们区分开
            // RakNet's 0x01/0x02 pings carry a timestamp here, the magic cookie tells them apart
            return readable >= STUN_HEADER_LENGTH && buf.getInt(buf.readerIndex() + 4) == STUN_MAGIC_COOKIE
                    ? Kind.STUN : Kind.OTHER;
        }
        if (first >= 20 && first <= 63) {
            return Kind.DTLS;
        }
        return Kind.OTHER;
    }

    /**
     * STUN Binding Request 的 USERNAME（"本端ufrag:对端ufrag"）中的本端部分；不是请求或没有 USERNAME 返回 null。
     * The local half of a STUN Binding Request USERNAME ("local:remote"); null for anything else.
     */
    static String stunLocalUfrag(ByteBuf buf) {
        int start = buf.readerIndex();
        int readable = buf.readableBytes();
        if (readable < STUN_HEADER_LENGTH || buf.getUnsignedShort(start) != STUN_BINDING_REQUEST) {
            return null;
        }
        int length = buf.getUnsignedShort(start + 2);
        if (STUN_HEADER_LENGTH + length > readable) {
            return null;
        }
        int offset = start + STUN_HEADER_LENGTH;
        int end = offset + length;
        while (offset + 4 <= end) {
            int type = buf.getUnsignedShort(offset);
            int valueLength = buf.getUnsignedShort(offset + 2);
            offset += 4;
            if (offset + valueLength > end) {
                return null;
            }
            if (type == STUN_ATTR_USERNAME) {
                if (valueLength > STUN_MAX_USERNAME_LENGTH) {
                    return null;
                }
                String username = buf.toString(offset, valueLength, StandardCharsets.UTF_8);
                int colon = username.indexOf(':');
                return colon <= 0 ? null : username.substring(0, colon);
            }
            offset += (valueLength + 3) & ~3;
        }
        return null;
    }

    private void forward(Channel listener, InetSocketAddress client, ByteBuf content, Kind kind, Set<Leg> touched) {
        Leg leg = this.legsByClient.get(client);
        if (leg == null) {
            if (kind != Kind.STUN) {
                this.droppedNoLeg.increment();
                return;
            }
            String ufrag = stunLocalUfrag(content);
            if (ufrag == null || !this.isExpected(ufrag)) {
                this.rejectedUfrag.increment();
                return;
            }
            if (this.addressBlocked.test(client.getAddress())) {
                this.rejectedBlocked.increment();
                return;
            }
            leg = this.openLeg(listener, client);
            if (leg == null) {
                this.rejectedLimit.increment();
                return;
            }
        }
        this.packetsIn.increment();
        this.bytesIn.add(content.readableBytes());
        leg.send(content.retain());
        touched.add(leg);
    }

    private Leg openLeg(Channel listener, InetSocketAddress client) {
        if (this.closed || this.legsByClient.size() >= this.maxLegs
                || this.legsPerAddress.getOrDefault(client.getAddress(), 0) >= MAX_LEGS_PER_ADDRESS) {
            return null;
        }

        Leg leg = new Leg(listener, client);
        ChannelFactory<? extends DatagramChannel> factory = listener instanceof EpollDatagramChannel
                ? () -> new EpollDatagramChannel(SocketProtocolFamily.INET)
                : () -> new NioDatagramChannel(SocketProtocolFamily.INET);
        ChannelFuture bound = new Bootstrap()
                .group(listener.eventLoop())
                .channelFactory(factory)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        channel.pipeline().addLast(new LegHandler(leg));
                    }
                })
                .bind(new InetSocketAddress(LOOPBACK, 0));
        leg.channel = bound.channel();
        // 在监听循环上注册是同步的，未注册即创建失败（如 fd 耗尽），此时不能挂 closeFuture 监听
        // Registration on the listener's loop is synchronous, so unregistered means creation failed
        // (fd exhaustion, say) and the close future must not be listened on
        if (!leg.channel.isRegistered()) {
            log.warn("Unable to create a NetherNet relay leg for {}", client, bound.cause());
            leg.channel.close();
            return null;
        }
        this.legsPerAddress.merge(client.getAddress(), 1, Integer::sum);
        this.legsByClient.put(client, leg);
        this.legsOpened.increment();
        leg.channel.closeFuture().addListener(future -> this.onLegClosed(leg));

        bound.addListener(bind -> {
            if (!bind.isSuccess()) {
                log.warn("Unable to open a NetherNet relay leg for {}", client, bind.cause());
                leg.close();
                return;
            }
            InetSocketAddress local = (InetSocketAddress) leg.channel.localAddress();
            this.legsByLocalPort.put(local.getPort(), leg);
            leg.channel.connect(this.mediaEndpoint).addListener(connect -> {
                if (!connect.isSuccess()) {
                    log.warn("Unable to connect a NetherNet relay leg to {}", this.mediaEndpoint, connect.cause());
                    leg.close();
                    return;
                }
                leg.ready();
            });
        });
        return leg;
    }

    private void onLegClosed(Leg leg) {
        this.legsByClient.remove(leg.client, leg);
        if (leg.channel.localAddress() instanceof InetSocketAddress local) {
            this.legsByLocalPort.remove(local.getPort(), leg);
        }
        this.legsPerAddress.computeIfPresent(leg.client.getAddress(), (address, held) -> held <= 1 ? null : held - 1);
        leg.releasePending();
    }

    void sweep() {
        long now = System.nanoTime();
        for (Leg leg : this.legsByClient.values()) {
            if (now - leg.lastActivityNanos > this.idleTtlNanos) {
                leg.close();
            }
        }
        this.expectedUfrags.values().removeIf(expiry -> now - expiry >= 0);
    }

    /**
     * 一条到 libjuice 的回环 leg。字段只在监听 channel 的事件循环上访问，leg channel 也注册在同一循环，
     * 所以无需加锁；sweep 只调用线程安全的 close。
     * One loopback leg to libjuice. Touched only on the listener's event loop, which the leg
     * channel also registers on, so no locking; the sweep only calls the thread-safe close.
     */
    private final class Leg {
        final Channel listener;
        final InetSocketAddress client;
        Channel channel;
        volatile long lastActivityNanos = System.nanoTime();
        private final List<ByteBuf> pending = new ArrayList<>(4);
        private boolean ready;

        Leg(Channel listener, InetSocketAddress client) {
            this.listener = listener;
            this.client = client;
        }

        void send(ByteBuf payload) {
            this.lastActivityNanos = System.nanoTime();
            if (!this.ready) {
                if (!this.channel.isOpen()) {
                    payload.release();
                    return;
                }
                this.pending.add(payload);
                return;
            }
            this.channel.write(payload);
        }

        void flush() {
            if (this.ready) {
                this.channel.flush();
            }
        }

        /** 连接完成前到达的数据报在此补发。 Datagrams that arrived before the connect completed are written out here. */
        void ready() {
            this.ready = true;
            for (ByteBuf payload : this.pending) {
                this.channel.write(payload);
            }
            this.pending.clear();
            this.channel.flush();
        }

        void releasePending() {
            for (ByteBuf payload : this.pending) {
                payload.release();
            }
            this.pending.clear();
        }

        void close() {
            this.channel.close();
        }
    }

    /** leg 上的回包：libjuice → leg → 监听 socket → 客户端。 Replies on a leg: libjuice → leg → listener socket → client. */
    private final class LegHandler extends ChannelInboundHandlerAdapter {
        private final Leg leg;

        LegHandler(Leg leg) {
            this.leg = leg;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof DatagramPacket packet)) {
                ReferenceCountUtil.release(msg);
                return;
            }
            try {
                ByteBuf content = packet.content();
                this.leg.lastActivityNanos = System.nanoTime();
                NetherNetMediaRelay.this.packetsOut.increment();
                NetherNetMediaRelay.this.bytesOut.add(content.readableBytes());
                this.leg.listener.write(new DatagramPacket(content.retain(), this.leg.client));
            } finally {
                packet.release();
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            this.leg.listener.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof PortUnreachableException) {
                // 媒体 socket 不在了（libjuice 关闭），这条 leg 没有意义
                // The media socket is gone (libjuice closed), the leg has nothing to talk to
                this.leg.close();
                return;
            }
            log.debug("NetherNet relay leg for {} failed", this.leg.client, cause);
        }
    }

    /**
     * 监听 socket 上的分流器：NetherNet 数据报转给 leg，其余原样交给 RakNet；同一读循环写过的 leg 在
     * readComplete 时一次 flush。
     * Demultiplexer on the listener: NetherNet datagrams go to legs, everything else continues to
     * RakNet untouched; legs written during one read loop flush once on readComplete.
     */
    private final class Demux extends ChannelInboundHandlerAdapter {
        private final Channel listener;
        private final Set<Leg> touched = new LinkedHashSet<>();

        Demux(Channel listener) {
            this.listener = listener;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof DatagramPacket packet)) {
                ctx.fireChannelRead(msg);
                return;
            }
            Kind kind = classify(packet.content());
            if (kind == Kind.OTHER) {
                ctx.fireChannelRead(msg);
                return;
            }
            try {
                NetherNetMediaRelay.this.forward(this.listener, packet.sender(), packet.content(), kind, this.touched);
            } finally {
                packet.release();
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            for (Leg leg : this.touched) {
                leg.flush();
            }
            this.touched.clear();
            ctx.fireChannelReadComplete();
        }
    }

    private final class SharedPortSignaling extends NetherNetDelegatingSignaling {
        private final UnaryOperator<String> answerRewriter;

        SharedPortSignaling(NetherNetServerSignaling delegate, UnaryOperator<String> answerRewriter) {
            super(delegate);
            this.answerRewriter = answerRewriter;
        }

        @Override
        public void setNewConnectionHandler(NewConnectionHandler handler) {
            this.delegate.setNewConnectionHandler((connectionId, remoteNetworkId, payload, clientAddress, player) -> {
                // 库在这次回调里创建 PeerConnection 并绑定内部端口，预留必须先让路
                // The library creates the PeerConnection and binds the internal port inside this
                // callback, so the reservation has to step aside first
                NetherNetMediaRelay.this.releaseMediaPort();
                handler.onConnect(connectionId, remoteNetworkId, NetherNetSharedPortSdp.stripCandidates(payload),
                        clientAddress, player);
            });
        }

        @Override
        public void sendFullSdp(String targetNetworkId, String sdp) {
            NetherNetMediaRelay.this.expectUfrag(NetherNetSharedPortSdp.iceUfrag(sdp));
            this.delegate.sendFullSdp(targetNetworkId, this.answerRewriter.apply(sdp));
        }
    }
}
