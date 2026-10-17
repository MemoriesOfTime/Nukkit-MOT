package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.ToString;

/**
 * 网易商店购买成功通知（独立空包，ID 202，双向），与 PyRpc 子包
 * {@code StoreBuySuccServerEvent} 是两条不同的 wire 路径，互不替代。
 * <p>
 * NetEase store purchase-success notification: a standalone empty-body packet (ID 202, both
 * directions), a separate wire path from the PyRpc {@code StoreBuySuccServerEvent} sub-packet.
 */
@OnlyNetEase
@ToString
public class StoreBuySuccessPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.NETEASE_PACKET_STORE_BUY_SUCC;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
    }
}
