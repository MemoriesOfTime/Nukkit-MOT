package cn.nukkit.network.protocol.types.clock;

import java.util.ArrayList;
import java.util.List;

public class RemoveTimeMarkerData implements SyncWorldClocksPayload {

    public long clockId;
    public List<Long> timeMarkerIds;

    public RemoveTimeMarkerData(long clockId) {
        this.clockId = clockId;
        this.timeMarkerIds = new ArrayList<>();
    }

    public RemoveTimeMarkerData(long clockId, List<Long> timeMarkerIds) {
        this.clockId = clockId;
        this.timeMarkerIds = timeMarkerIds;
    }
}
