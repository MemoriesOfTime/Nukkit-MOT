package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class NetworkStackLatencyPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.NETWORK_STACK_LATENCY_PACKET;

    public long timestamp;
    public boolean fromServer;
    @Deprecated
    public boolean unknownBool;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        timestamp = this.getLLong();
        if (this.protocol >= 332) {
            this.fromServer = this.getBoolean();
            this.unknownBool = this.fromServer;
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putLLong(timestamp);
        if (protocol >= 332) {
            this.putBoolean(fromServer || unknownBool);
        }
    }
}
