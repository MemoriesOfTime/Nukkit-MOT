package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @author glorydark
 */
@ToString
public class ServerboundDiagnosticsPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_DIAGNOSTICS_PACKET;

    public float avgFps;
    public float avgServerSimTickTimeMS;
    public float avgClientSimTickTimeMS;
    public float avgBeginFrameTimeMS;
    public float avgInputTimeMS;
    public float avgRenderTimeMS;
    public float avgEndFrameTimeMS;
    public float avgRemainderTimePercent;
    public float avgUnaccountedTimePercent;

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
        this.avgFps = this.getLFloat();
        this.avgServerSimTickTimeMS = this.getLFloat();
        this.avgClientSimTickTimeMS = this.getLFloat();
        this.avgBeginFrameTimeMS = this.getLFloat();
        this.avgInputTimeMS = this.getLFloat();
        this.avgRenderTimeMS = this.getLFloat();
        this.avgEndFrameTimeMS = this.getLFloat();
        this.avgRemainderTimePercent = this.getLFloat();
        this.avgUnaccountedTimePercent = this.getLFloat();
    }

    @Override
    public void encode() {
        this.putLFloat(this.avgFps);
        this.putLFloat(this.avgServerSimTickTimeMS);
        this.putLFloat(this.avgClientSimTickTimeMS);
        this.putLFloat(this.avgBeginFrameTimeMS);
        this.putLFloat(this.avgInputTimeMS);
        this.putLFloat(this.avgRenderTimeMS);
        this.putLFloat(this.avgEndFrameTimeMS);
        this.putLFloat(this.avgRemainderTimePercent);
        this.putLFloat(this.avgUnaccountedTimePercent);
    }
}
