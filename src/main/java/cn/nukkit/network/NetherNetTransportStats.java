package cn.nukkit.network;

import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelMetrics;
import tel.schich.libdatachannel.IceState;
import tel.schich.libdatachannel.PeerState;

import java.util.concurrent.atomic.LongAdder;

/**
 * NetherNet 接入漏斗的被动聚合计数。
 * <p>
 * 三类线程并发写入：metrics 回调来自 libdatachannel native 线程、信令准入来自 HTTP eventLoop、
 * 超时与采样来自主线程，计数一律 LongAdder 无锁累加；指标事件不带连接标识，仅做全局聚合，
 * 到达次数可能高估连接数（状态抖动会计多次），零值判别不受影响。
 * 近况窗口由主线程每分钟差分采样进环形数组（一小时），读侧弱一致。
 * <p>
 * Passive aggregate funnel counters for NetherNet joins. Lock-free LongAdders shared by
 * three writer domains (native metrics callbacks, the signaling event loop, the main thread);
 * events carry no connection id so the aggregate is global and counts state arrivals, which
 * may overcount connections — zero stays exact. The recent-window view is a one-minute
 * differential ring (one hour) sampled on the main thread, weakly consistent for readers.
 * <p>
 * 诊断口径：joinAccepted 涨而 iceConnected 恒为零即媒体 UDP 不可达；
 * iceConnected 涨而 rtcConnected 少为 DTLS 握手问题；rtcConnected 后 postConnectDrops 高为 NAT/抖动。
 * Diagnosis grid: joinAccepted rising while iceConnected stays zero means the media UDP window
 * is unreachable; iceConnected rising with few rtcConnected points at DTLS trouble; a high
 * postConnectDrops after rtcConnected points at NAT timeouts or jitter.
 */
public final class NetherNetTransportStats implements NetherChannelMetrics {

    /** 历史环形数组长度：每分钟一个差分样本，共一小时。 One differential sample per minute, one hour deep. */
    static final int HISTORY_MINUTES = 60;

    private final LongAdder joinAccepted = new LongAdder();
    private final LongAdder peerConnecting = new LongAdder();
    private final LongAdder iceConnected = new LongAdder();
    private final LongAdder iceFailed = new LongAdder();
    private final LongAdder rtcConnected = new LongAdder();
    /** 仅计 RTC_DISCONNECTED：必经 RTC_CONNECTED，是"连上后掉线"的纯指纹。 RTC_DISCONNECTED only, which presupposes RTC_CONNECTED. */
    private final LongAdder postConnectDrops = new LongAdder();
    private final LongAdder loginTimeouts = new LongAdder();
    private final LongAdder bytesIn = new LongAdder();
    private final LongAdder bytesOut = new LongAdder();

    /** 主线程单写者，槽为不可变记录；命令线程读引用，弱一致即可。 Main thread is the sole writer. */
    private final MinuteSample[] history = new MinuteSample[HISTORY_MINUTES];
    private long lastSampleMinute = -1;
    private Counts lastCounts;

    @Override
    public void peerStateChange(PeerState state) {
        switch (state) {
            case RTC_CONNECTING -> this.peerConnecting.increment();
            case RTC_CONNECTED -> this.rtcConnected.increment();
            case RTC_DISCONNECTED -> this.postConnectDrops.increment();
            default -> { }
        }
    }

    @Override
    public void iceStateChange(IceState state) {
        switch (state) {
            case RTC_ICE_CONNECTED, RTC_ICE_COMPLETED -> this.iceConnected.increment();
            case RTC_ICE_FAILED -> this.iceFailed.increment();
            default -> { }
        }
    }

    @Override
    public void bytesIn(int bytes) {
        if (bytes > 0) {
            this.bytesIn.add(bytes);
        }
    }

    @Override
    public void bytesOut(int bytes) {
        if (bytes > 0) {
            this.bytesOut.add(bytes);
        }
    }

    /** 信令准入放行一次（HTTP eventLoop 线程）。 One join accepted by the signaling player filter. */
    void onJoinAccepted() {
        this.joinAccepted.increment();
    }

    /** nukkit 层登录超时回收一次（主线程）。 One login-phase timeout reaped by the interface. */
    void onLoginTimeout() {
        this.loginTimeouts.increment();
    }

    /**
     * 每分钟差分采样，仅主线程调用；跨多分钟的缺口以零样本补齐，
     * 期间发生的计数计入缺口后的首个样本（卡顿后瞬时突增属预期）。
     * Differential sampling, main thread only; gaps of several minutes fill with zero
     * samples and the counts accumulated during them land in the first sample after.
     */
    void sampleMinute(long nowMillis) {
        long minute = Math.floorDiv(nowMillis, 60_000L);
        if (this.lastSampleMinute < 0) {
            this.lastSampleMinute = minute;
            this.lastCounts = Counts.of(this);
            return;
        }
        for (long m = this.lastSampleMinute + 1; m <= minute; m++) {
            Counts current = Counts.of(this);
            this.history[(int) Math.floorMod(m, HISTORY_MINUTES)] = new MinuteSample(m, current.minus(this.lastCounts));
            this.lastCounts = current;
        }
        this.lastSampleMinute = minute;
    }

    /**
     * 近 {@code minutes} 分钟的差分聚合；流量两字段为启动以来的累计值。
     * Differential totals over the last {@code minutes} minutes;
     * the two traffic fields are totals since startup.
     */
    public TransportSnapshot snapshot(int minutes, long nowMillis) {
        long nowMinute = Math.floorDiv(nowMillis, 60_000L);
        Counts sum = Counts.ZERO;
        for (int i = 0; i < Math.min(minutes, HISTORY_MINUTES); i++) {
            MinuteSample sample = this.history[(int) Math.floorMod(nowMinute - i, HISTORY_MINUTES)];
            // 槽属于上一轮（超过一小时）时已过期。 Stale slot from the previous ring lap.
            if (sample == null || sample.minute() > nowMinute || nowMinute - sample.minute() >= HISTORY_MINUTES) {
                continue;
            }
            sum = sum.plus(sample.delta());
        }
        return new TransportSnapshot(sum, this.bytesIn.sum(), this.bytesOut.sum());
    }

    private record Counts(long joinAccepted, long peerConnecting, long iceConnected, long iceFailed,
                          long rtcConnected, long postConnectDrops, long loginTimeouts) {

        static final Counts ZERO = new Counts(0, 0, 0, 0, 0, 0, 0);

        static Counts of(NetherNetTransportStats stats) {
            return new Counts(stats.joinAccepted.sum(), stats.peerConnecting.sum(), stats.iceConnected.sum(),
                    stats.iceFailed.sum(), stats.rtcConnected.sum(), stats.postConnectDrops.sum(),
                    stats.loginTimeouts.sum());
        }

        Counts minus(Counts other) {
            return new Counts(this.joinAccepted - other.joinAccepted, this.peerConnecting - other.peerConnecting,
                    this.iceConnected - other.iceConnected, this.iceFailed - other.iceFailed,
                    this.rtcConnected - other.rtcConnected, this.postConnectDrops - other.postConnectDrops,
                    this.loginTimeouts - other.loginTimeouts);
        }

        Counts plus(Counts other) {
            return new Counts(this.joinAccepted + other.joinAccepted, this.peerConnecting + other.peerConnecting,
                    this.iceConnected + other.iceConnected, this.iceFailed + other.iceFailed,
                    this.rtcConnected + other.rtcConnected, this.postConnectDrops + other.postConnectDrops,
                    this.loginTimeouts + other.loginTimeouts);
        }
    }

    private record MinuteSample(long minute, Counts delta) {
    }

    /**
     * 漏斗窗口视图：前七项为窗口内差分，后两项为累计流量。
     * Funnel window view: the first seven fields are window differentials, traffic is cumulative.
     */
    public record TransportSnapshot(long joinAccepted, long peerConnecting, long iceConnected, long iceFailed,
                                    long rtcConnected, long postConnectDrops, long loginTimeouts,
                                    long bytesIn, long bytesOut) {

        TransportSnapshot(Counts counts, long bytesIn, long bytesOut) {
            this(counts.joinAccepted(), counts.peerConnecting(), counts.iceConnected(), counts.iceFailed(),
                    counts.rtcConnected(), counts.postConnectDrops(), counts.loginTimeouts(), bytesIn, bytesOut);
        }
    }
}
