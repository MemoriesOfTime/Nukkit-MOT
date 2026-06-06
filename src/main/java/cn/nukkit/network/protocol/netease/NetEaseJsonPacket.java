package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.ToString;

/**
 * Carries NetEase-specific JSON payloads.
 */
@OnlyNetEase
@ToString
public class NetEaseJsonPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.NETEASE_JSON_PACKET;

    public String json = "";

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        this.json = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.json != null ? this.json : "");
    }

    public String getJson() {
        return this.json;
    }

    public void setJson(String json) {
        this.json = json != null ? json : "";
    }
}
