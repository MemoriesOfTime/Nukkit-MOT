package cn.nukkit.network;

import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.util.nethernet.EndpointAddress;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/**
 * 把玩家输入的域名解析成媒体候选。上游只把信令 Host 头里的 IP 字面量当作候选，域名玩家在 NAT 或容器
 * 部署下拿不到任何可达地址；这里按 networkId 记住玩家输入的域名，解析一次并缓存，供应答改写补上对端可达的
 * 地址（须能直达 UDP，只代理 TCP 的 CDN/负载均衡域名会得到死候选）。
 * <p>
 * Resolves the domain a player typed into a media candidate. Upstream takes only an IP literal from
 * the signaling Host header, leaving a name-typed player without a reachable address behind NAT or
 * in a container. The typed host is remembered per network ID, resolved once and cached so the
 * answer rewrite can publish the resolved reachable address, which has to carry UDP directly: a CDN
 * or load balancer proxying only TCP yields a dead candidate.
 */
@Log4j2
final class NetherNetHostCandidates {

    /** 解析器，测试可替换。 The resolver, replaceable in tests. */
    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private record Resolution(List<InetAddress> addresses, long expiresAt) {
    }

    private record Mapping(String host, long observedAt, long expiresAt) {
    }

    private static final long POSITIVE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long NEGATIVE_TTL_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long MAPPING_TTL_MILLIS = TimeUnit.MINUTES.toMillis(2);
    /**
     * 首个加入可能仍在等待应答的窗口，取客户端自身的加入窗口（约 10 秒），窗口内另一个域名不算新的
     * 加入。上游在过滤器之后才拒绝重复的 networkId，此刻的重复 offer 已经过过滤器，它的域名不能顶掉
     * 首个加入的候选。
     * <p>
     * The window in which the first join may still be waiting for its answer, the client's own join
     * window (about ten seconds); during it another host is not a new join. Upstream refuses a
     * duplicate network ID only after the filter, so the duplicate has already passed through it and
     * must not displace the first join's candidates.
     */
    static final long MAPPING_REPLACEMENT_GRACE_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final int MAX_HOSTS = 128;
    private static final int MAX_MAPPINGS = 1024;
    /** 解析预算：Host 头可伪造，不设上限就能拿它刷 DNS 与缓存。 The lookup budget: a forged Host header would otherwise drive DNS and cache churn without limit. */
    private static final int MAX_LOOKUPS_PER_MINUTE = 20;
    private static final int MAX_LOOKUPS_PER_ADDRESS_PER_MINUTE = 3;
    private static final int MAX_ADDRESS_BUDGETS = 1024;
    private static final long LOOKUP_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);
    /** 单次解析的期限，超时按无结果处理。 The budget for one lookup; past it the lookup yields nothing. */
    private static final long LOOKUP_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

    private final Map<String, Resolution> resolutions = new ConcurrentHashMap<>();
    private final Map<String, Mapping> mappings = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<InetAddress>>> pending = new ConcurrentHashMap<>();
    private final Deque<Long> lookupTimes = new ArrayDeque<>();
    private final Map<InetAddress, Deque<Long>> addressLookupTimes = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final HostResolver resolver;
    private final ExecutorService executor;
    private final long lookupTimeoutMillis;

    NetherNetHostCandidates() {
        this(System::currentTimeMillis, InetAddress::getAllByName);
    }

    NetherNetHostCandidates(LongSupplier clock, HostResolver resolver) {
        this(clock, resolver, LOOKUP_TIMEOUT_MILLIS);
    }

    NetherNetHostCandidates(LongSupplier clock, HostResolver resolver, long lookupTimeoutMillis) {
        this.clock = clock;
        this.resolver = resolver;
        this.lookupTimeoutMillis = lookupTimeoutMillis;
        this.executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8),
                runnable -> {
                    Thread thread = new Thread(runnable, "NetherNet host resolver");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * 记下这次加入的域名并启动解析；同一 networkId 的重复 offer 沿用首个映射，首个加入确实结束
     * （映射已超过应答窗口或已过期）后换了域名才视为新的加入并替换。
     * <p>
     * Remembers the host this join typed and starts the lookup. A repeated offer for one network ID
     * keeps the first mapping; only once that join is certainly over (the mapping is past the
     * answer window or expired) does another host mean another attempt and replace it.
     */
    void observe(String networkId, InetAddress remote, String host) {
        if (networkId == null || networkId.isEmpty()) {
            return;
        }
        String name = name(host);
        if (name == null) {
            return;
        }
        long now = this.clock.getAsLong();
        prune(now);
        Mapping observed = new Mapping(name, now, now + MAPPING_TTL_MILLIS);
        Mapping effective = this.mappings.compute(networkId, (id, current) ->
                current == null || current.expiresAt() < now || replaceable(current, observed, now)
                        ? observed : current);
        lookup(effective.host(), remote, now);
    }

    /**
     * 另一个域名只有在首个加入确定已经结束之后才算新的加入：上游在过滤器之后才拒绝重复的
     * networkId，所以此刻的第二个域名既可能是重试，也可能是马上被拒的重复请求，窗口内不能替换。
     * <p>
     * Another host is a new join only once the first one is certainly over: upstream refuses a
     * duplicate network ID after the filter, so the second host is either a retry or a duplicate
     * about to be rejected, and within the window it must not replace the first join's candidates.
     */
    private static boolean replaceable(Mapping current, Mapping observed, long now) {
        return now - current.observedAt() >= MAPPING_REPLACEMENT_GRACE_MILLIS
                && !current.host().equals(observed.host());
    }

    /** 状态请求里提前解析，让随后的加入命中缓存。 Resolves from the status request so a later join finds it cached. */
    void prefetch(InetAddress remote, String host) {
        String name = name(host);
        if (name != null) {
            long now = this.clock.getAsLong();
            // 状态请求也会写入解析缓存，同样按上限清理 This path fills the resolution cache too, so prune here as well
            prune(now);
            lookup(name, remote, now);
        }
    }

    /** 该次加入应发布的地址；映射过期或尚未解析时为空。 The addresses this join publishes, empty when unmapped or unresolved. */
    List<InetAddress> addressesFor(String networkId) {
        if (networkId == null) {
            return List.of();
        }
        Mapping mapping = this.mappings.get(networkId);
        long now = this.clock.getAsLong();
        if (mapping != null && mapping.expiresAt() < now) {
            this.mappings.remove(networkId, mapping);
        }
        return addresses(mapping, now);
    }

    /**
     * 在期限内等待该次加入仍在进行的解析，然后取出地址并消费映射；超时按没有地址应答。
     * <p>
     * Waits up to the given budget for this join's running lookup, then takes the addresses and
     * consumes the mapping; past the budget the join gets none.
     */
    CompletableFuture<List<InetAddress>> consumeAddressesFor(String networkId, long waitMillis) {
        if (networkId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        Mapping mapping = this.mappings.get(networkId);
        if (mapping == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        long now = this.clock.getAsLong();
        if (mapping.expiresAt() < now) {
            this.mappings.remove(networkId, mapping);
            return CompletableFuture.completedFuture(List.of());
        }
        Resolution resolution = this.resolutions.get(mapping.host());
        if (resolution != null && resolution.expiresAt() > now) {
            this.mappings.remove(networkId, mapping);
            return CompletableFuture.completedFuture(resolution.addresses());
        }
        CompletableFuture<List<InetAddress>> running = this.pending.get(mapping.host());
        if (running == null) {
            this.mappings.remove(networkId, mapping);
            return CompletableFuture.completedFuture(addresses(mapping, now));
        }
        return running
                .handle((ignored, failure) -> addresses(mapping, this.clock.getAsLong()))
                .completeOnTimeout(List.of(), waitMillis, TimeUnit.MILLISECONDS)
                .whenComplete((addresses, failure) -> this.mappings.remove(networkId, mapping));
    }

    private List<InetAddress> addresses(Mapping mapping, long now) {
        if (mapping == null || mapping.expiresAt() < now) {
            return List.of();
        }
        Resolution resolution = this.resolutions.get(mapping.host());
        return resolution == null ? List.of() : resolution.addresses();
    }

    private CompletableFuture<List<InetAddress>> lookup(String name, InetAddress remote, long now) {
        Resolution cached = this.resolutions.get(name);
        if (cached != null && cached.expiresAt() > now) {
            return CompletableFuture.completedFuture(cached.addresses());
        }
        CompletableFuture<List<InetAddress>> running = this.pending.get(name);
        if (running != null) {
            return running;
        }
        CompletableFuture<List<InetAddress>> future = new CompletableFuture<>();
        CompletableFuture<List<InetAddress>> raced = this.pending.putIfAbsent(name, future);
        if (raced != null) {
            return raced;
        }
        if (!acquireLookup(remote, now)) {
            this.pending.remove(name, future);
            return null;
        }
        try {
            this.executor.execute(() -> resolve(name, future));
        } catch (RejectedExecutionException e) {
            this.pending.remove(name, future);
            return null;
        }
        // 挂死的系统解析器不能一直占着挂起表；晚到的真实结果仍会写入缓存
        // A hung system resolver must not pin the pending entry; a late real answer still fills the cache
        future.orTimeout(this.lookupTimeoutMillis, TimeUnit.MILLISECONDS).whenComplete((ignored, failure) -> {
            if (failure != null) {
                this.pending.remove(name, future);
            }
        });
        return future;
    }

    private void resolve(String name, CompletableFuture<List<InetAddress>> future) {
        List<InetAddress> addresses = List.of();
        try {
            addresses = select(this.resolver.resolve(name));
        } catch (UnknownHostException | RuntimeException e) {
            log.debug("Unable to resolve the host a player typed in: {} ({})", name, e.toString());
        } finally {
            long ttl = addresses.isEmpty() ? NEGATIVE_TTL_MILLIS : POSITIVE_TTL_MILLIS;
            this.resolutions.put(name, new Resolution(addresses, this.clock.getAsLong() + ttl));
            this.pending.remove(name, future);
            future.complete(addresses);
        }
    }

    private static List<InetAddress> select(InetAddress[] resolved) {
        int cap = NetherNetSharedPortSdp.MAX_ADDRESSES_PER_FAMILY;
        List<InetAddress> ipv4 = new ArrayList<>(cap);
        List<InetAddress> ipv6 = new ArrayList<>(cap);
        for (InetAddress address : resolved) {
            // 与上游 typed 字面量同一口径：公网与私网（含 CGNAT、ULA）都发布，回环与保留段不发布
            // The same rule the typed literal follows upstream: public and private (CGNAT, ULA) are
            // published, loopback and special-purpose ranges are not
            if (address == null || !EndpointAddress.advertisable(address, false)) {
                continue;
            }
            List<InetAddress> family = address instanceof Inet4Address ? ipv4 : ipv6;
            if (family.size() < cap && !family.contains(address)) {
                family.add(address);
            }
        }
        if (ipv4.isEmpty() || ipv6.isEmpty()) {
            return List.copyOf(ipv4.isEmpty() ? ipv6 : ipv4);
        }
        List<InetAddress> selected = new ArrayList<>(ipv4.size() + ipv6.size());
        selected.addAll(ipv4);
        selected.addAll(ipv6);
        return List.copyOf(selected);
    }

    /**
     * 只接受域名：IP 字面量交给上游自己处理，端口忽略（媒体端口由配置决定）。
     * Accepts host names only: an IP literal is upstream's own case and a port is ignored, since
     * the media port comes from the configuration.
     */
    static String name(String host) {
        if (host == null) {
            return null;
        }
        String text = host.trim();
        if (text.isEmpty() || text.length() > 255 || text.startsWith("[")) {
            return null;
        }
        int colon = text.indexOf(':');
        if (colon >= 0) {
            if (colon != text.lastIndexOf(':')) {
                return null; // 未加括号的 IPv6 字面量 An unbracketed IPv6 literal
            }
            if (!text.substring(colon + 1).matches("[0-9]{1,5}")) {
                return null;
            }
            text = text.substring(0, colon);
        }
        if (!text.contains(".") || !text.matches(".*[A-Za-z].*")) {
            return null; // 不含点的名字或写错的 IP 字面量 A dotless name or a mistyped IP literal
        }
        try {
            EndpointAddress.parse(text);
            return null; // IP 字面量 An IP literal
        } catch (UnknownHostException notALiteral) {
            // 继续按域名校验 Continue as a host name
        }
        if (!NetherNetUdpPorts.isHostName(text)) {
            return null;
        }
        // 根点只是全限定写法，缓存键与解析入参都不带 It is only the fully qualified spelling, so the cache key and the lookup drop it
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * 解析预算：全局与单来源各自每分钟计数，任一超限即不解析。单来源额度防止一个 IP 耗尽全局额度，
     * 全局额度封顶伪造 Host 头能驱动的 DNS 查询与缓存写入。
     * Lookup budget: a global and a per-source count per minute, either exhausted refusing the
     * lookup. The per-source share keeps one address from draining the global one, and the global
     * one caps what forged Host headers can make the server resolve and cache.
     */
    private boolean acquireLookup(InetAddress remote, long now) {
        synchronized (this.lookupTimes) {
            while (!this.lookupTimes.isEmpty() && now - this.lookupTimes.peekFirst() >= LOOKUP_WINDOW_MILLIS) {
                this.lookupTimes.removeFirst();
            }
            if (this.lookupTimes.size() >= MAX_LOOKUPS_PER_MINUTE) {
                return false;
            }
            if (remote != null) {
                Deque<Long> times = this.addressLookupTimes.computeIfAbsent(remote, address -> new ArrayDeque<>());
                while (!times.isEmpty() && now - times.peekFirst() >= LOOKUP_WINDOW_MILLIS) {
                    times.removeFirst();
                }
                if (times.size() >= MAX_LOOKUPS_PER_ADDRESS_PER_MINUTE) {
                    return false;
                }
                times.addLast(now);
                if (this.addressLookupTimes.size() > MAX_ADDRESS_BUDGETS) {
                    this.addressLookupTimes.entrySet().removeIf(entry ->
                            entry.getValue().isEmpty() || now - entry.getValue().peekLast() >= LOOKUP_WINDOW_MILLIS);
                    if (this.addressLookupTimes.size() > MAX_ADDRESS_BUDGETS) {
                        this.addressLookupTimes.keySet().stream()
                                .filter(address -> !address.equals(remote))
                                .findFirst()
                                .ifPresent(this.addressLookupTimes::remove);
                    }
                }
            }
            this.lookupTimes.addLast(now);
            return true;
        }
    }

    private void prune(long now) {
        if (this.resolutions.size() >= MAX_HOSTS) {
            this.resolutions.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
            if (this.resolutions.size() >= MAX_HOSTS) {
                this.resolutions.keySet().stream().findFirst().ifPresent(this.resolutions::remove);
            }
        }
        if (this.mappings.size() >= MAX_MAPPINGS) {
            this.mappings.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
            if (this.mappings.size() >= MAX_MAPPINGS) {
                this.mappings.keySet().stream().findFirst().ifPresent(this.mappings::remove);
            }
        }
    }

    void close() {
        this.executor.shutdownNow();
        this.pending.clear();
        this.resolutions.clear();
        this.mappings.clear();
        synchronized (this.lookupTimes) {
            this.lookupTimes.clear();
            this.addressLookupTimes.clear();
        }
    }
}
