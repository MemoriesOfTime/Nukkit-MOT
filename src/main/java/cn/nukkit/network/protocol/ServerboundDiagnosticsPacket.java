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
    /**
     * Entity diagnostic timing info.
     * @since v975
     */
    public List<EntityDiagnosticTimingInfo> entityDiagnostics = new ArrayList<>();
    /**
     * System diagnostic timing info.
     * @since v975
     */
    public List<SystemDiagnosticTimingInfo> systemDiagnostics = new ArrayList<>();

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
            this.memoryCategoryValues = new ArrayList<>();
            this.getArray(this.memoryCategoryValues, bs -> new MemoryCategoryCounter(bs.getByte(), bs.getLLong()));
        }

        if (this.protocol >= ProtocolInfo.v1_26_20_26) {
            this.entityDiagnostics = new ArrayList<>();
            this.getArray(this.entityDiagnostics, bs -> new EntityDiagnosticTimingInfo(bs.getString(), bs.getString(), bs.getLLong(), (byte) bs.getByte()));

            this.systemDiagnostics = new ArrayList<>();
            this.getArray(this.systemDiagnostics, bs -> new SystemDiagnosticTimingInfo(bs.getString(), bs.getLLong(), bs.getLLong(), (byte) bs.getByte()));
        }
    }

    @Override
    public void encode() {
        this.reset();
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
            this.putArray(this.memoryCategoryValues, counter -> {
                this.putByte((byte) counter.category);
                this.putLLong(counter.currentBytes);
            });
        }

        if (this.protocol >= ProtocolInfo.v1_26_20_26) {
            this.putArray(this.entityDiagnostics, info -> {
                this.putString(info.displayName);
                this.putString(info.entity);
                this.putLLong(info.timeInNs);
                this.putByte(info.percentOfTotal);
            });

            this.putArray(this.systemDiagnostics, info -> {
                this.putString(info.displayName);
                this.putLLong(info.systemIndex);
                this.putLLong(info.timeInNs);
                this.putByte(info.percentOfTotal);
            });
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

    /**
     * Entity diagnostic timing info.
     * @since v975
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EntityDiagnosticTimingInfo {
        public String displayName;
        public String entity;
        public long timeInNs;
        public byte percentOfTotal;
    }

    /**
     * System diagnostic timing info.
     * @since v975
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SystemDiagnosticTimingInfo {
        public String displayName;
        public long systemIndex;
        public long timeInNs;
        public byte percentOfTotal;
    }
}
