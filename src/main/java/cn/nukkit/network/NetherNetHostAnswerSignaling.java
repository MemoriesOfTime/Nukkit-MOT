package cn.nukkit.network;

import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling;

/**
 * 把玩家输入域名解析出的可达地址补进 SDP 应答。上游只把 Host 头里的 IP 字面量当候选，域名玩家在 NAT
 * 或容器部署下拿不到可达地址；地址在模式相关的端口改写之前追加，端口沿用应答里已有的 UDP host 候选，
 * 随后的改写把它平移到对外端口。等待解析有时间上限，超时按原应答发送。
 * <p>
 * Adds the addresses a player's typed domain resolves to into the SDP answer. Upstream takes
 * only an IP literal from the Host header, leaving a name-typed player without a reachable address
 * behind NAT or in a container. The addresses are appended before the mode-specific port rewrite,
 * on the port the answer's own UDP host candidate carries, which that rewrite then moves to the
 * public port. The wait for the lookup is bounded, past which the answer goes out unchanged.
 */
final class NetherNetHostAnswerSignaling extends NetherNetDelegatingSignaling {

    /** 解析在应答窗口内最多占用的时间。 The most of the answer window the lookup may take. */
    private static final long ANSWER_WAIT_MILLIS = 3000;

    private final NetherNetHostCandidates candidates;
    private final boolean includeIpv6;
    private final NetherNetSharedPortSdp.PortSource portSource;
    private final long answerWaitMillis;

    NetherNetHostAnswerSignaling(NetherNetServerSignaling delegate, NetherNetHostCandidates candidates,
                                 boolean includeIpv6, NetherNetSharedPortSdp.PortSource portSource) {
        this(delegate, candidates, includeIpv6, portSource, ANSWER_WAIT_MILLIS);
    }

    NetherNetHostAnswerSignaling(NetherNetServerSignaling delegate, NetherNetHostCandidates candidates,
                                 boolean includeIpv6, NetherNetSharedPortSdp.PortSource portSource,
                                 long answerWaitMillis) {
        super(delegate);
        this.candidates = candidates;
        this.includeIpv6 = includeIpv6;
        this.portSource = portSource;
        this.answerWaitMillis = answerWaitMillis;
    }

    @Override
    public void sendDescription(String targetNetworkId, String sdp) {
        this.candidates.consumeAddressesFor(targetNetworkId, this.answerWaitMillis).whenComplete((addresses, failure) ->
                this.delegate.sendDescription(targetNetworkId, failure != null || addresses.isEmpty()
                        ? sdp : NetherNetSharedPortSdp.appendCandidates(sdp, addresses, this.includeIpv6, this.portSource)));
    }
}
