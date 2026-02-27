package cn.nukkit.network.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * @since v712
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

    /**
     * Memory category counters.
     * @since v924
     */
    public List<MemoryCategoryCounter> memoryCategoryValues = new ArrayList<>();

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

        if (this.protocol >= ProtocolInfo.v1_26_0) {
            int count = (int) this.getUnsignedVarInt();
            this.memoryCategoryValues = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int category = this.getByte();
                long currentBytes = this.getLLong();
                this.memoryCategoryValues.add(new MemoryCategoryCounter(category, currentBytes));
            }
        }
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

        if (this.protocol >= ProtocolInfo.v1_26_0) {
            this.putUnsignedVarInt(this.memoryCategoryValues.size());
            for (MemoryCategoryCounter counter : this.memoryCategoryValues) {
                this.putByte((byte) counter.category);
                this.putLLong(counter.currentBytes);
            }
        }
    }

    /**
     * Memory category counter data.
     * @since v924
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemoryCategoryCounter {
        public int category;
        public long currentBytes;
    }
}
