package cn.nukkit.network;

import org.junit.jupiter.api.Test;
import tel.schich.libdatachannel.IceState;
import tel.schich.libdatachannel.PeerState;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NetherNetTransportStats 的窗口差分语义回归：分钟采样按结算槽聚合、缺口填零、
 * 上一轮环形残片过期、流量字段为累计值；告警判定依赖"窗口内零连通"精确为零。
 * <p>
 * Window semantics of NetherNetTransportStats: minute differentials aggregate per settled
 * slot, gaps fill with zeros, stale slots from the previous ring lap expire, and traffic
 * fields stay cumulative; the media alarm relies on an exact-zero ICE count in the window.
 */
class NetherNetTransportStatsTest {

    private static final long MINUTE = 60_000L;

    @Test
    void metricEventsAdvanceCounters() {
        NetherNetTransportStats stats = new NetherNetTransportStats();
        stats.sampleMinute(0);
        stats.peerStateChange(PeerState.RTC_CONNECTING);
        stats.peerStateChange(PeerState.RTC_CONNECTING);
        stats.peerStateChange(PeerState.RTC_CONNECTED);
        stats.peerStateChange(PeerState.RTC_DISCONNECTED);
        stats.peerStateChange(PeerState.RTC_CLOSED);
        stats.iceStateChange(IceState.RTC_ICE_CONNECTED);
        stats.iceStateChange(IceState.RTC_ICE_COMPLETED);
        stats.iceStateChange(IceState.RTC_ICE_FAILED);
        stats.onJoinAccepted();
        stats.onLoginTimeout();
        stats.bytesIn(100);
        stats.bytesIn(-1);
        stats.bytesOut(50);

        stats.sampleMinute(MINUTE + 1);
        var snapshot = stats.snapshot(1, MINUTE + 1);

        assertEquals(2, snapshot.peerConnecting(), "RTC_CONNECTING arrivals");
        assertEquals(1, snapshot.rtcConnected(), "RTC_CONNECTED arrivals");
        assertEquals(1, snapshot.postConnectDrops(), "only RTC_DISCONNECTED counts as a drop, RTC_CLOSED is a normal leave");
        assertEquals(2, snapshot.iceConnected(), "ICE CONNECTED and COMPLETED both count as arrivals");
        assertEquals(1, snapshot.iceFailed());
        assertEquals(1, snapshot.joinAccepted());
        assertEquals(1, snapshot.loginTimeouts());
        assertEquals(100, snapshot.bytesIn(), "negative byte counts are ignored");
        assertEquals(50, snapshot.bytesOut());
    }

    @Test
    void snapshotAggregatesSettledMinutesOnly() {
        NetherNetTransportStats stats = new NetherNetTransportStats();
        stats.sampleMinute(0);
        stats.onJoinAccepted();

        stats.sampleMinute(MINUTE + 1);
        assertEquals(1, stats.snapshot(1, MINUTE + 1).joinAccepted(), "the event lands in the settled window");

        stats.sampleMinute(2 * MINUTE + 1);
        assertEquals(0, stats.snapshot(1, 2 * MINUTE + 1).joinAccepted(), "a one-minute window slides past the event");
        assertEquals(1, stats.snapshot(2, 2 * MINUTE + 1).joinAccepted(), "a two-minute window still contains it");
    }

    @Test
    void gapMinutesFillWithZeroSamples() {
        NetherNetTransportStats stats = new NetherNetTransportStats();
        stats.sampleMinute(0);
        stats.onJoinAccepted();

        stats.sampleMinute(5 * MINUTE);
        assertEquals(1, stats.snapshot(5, 5 * MINUTE).joinAccepted(),
                "a sampling gap keeps the event in exactly one settled slot");
    }

    @Test
    void staleSlotsFromThePreviousRingLapAreIgnored() {
        NetherNetTransportStats stats = new NetherNetTransportStats();
        stats.sampleMinute(0);
        stats.onJoinAccepted();
        stats.sampleMinute(MINUTE);

        stats.sampleMinute(100 * MINUTE);
        assertEquals(0, stats.snapshot(60, 100 * MINUTE).joinAccepted(),
                "a sample older than one hour must not leak into the window");
    }

    @Test
    void trafficFieldsStayCumulativeAcrossWindows() {
        NetherNetTransportStats stats = new NetherNetTransportStats();
        stats.sampleMinute(0);
        stats.bytesIn(100);
        stats.bytesOut(30);

        stats.sampleMinute(10 * MINUTE);
        var snapshot = stats.snapshot(1, 10 * MINUTE);
        assertEquals(100, snapshot.bytesIn(), "traffic is a total since startup, not a window differential");
        assertEquals(30, snapshot.bytesOut());
    }
}
