package cn.nukkit.network.protocol.types.clock;

import java.util.List;

public class SyncStateData implements SyncWorldClocksPayload {

    public List<SyncWorldClockStateData> clockData;

    public SyncStateData(List<SyncWorldClockStateData> clockData) {
        this.clockData = clockData;
    }
}
