package cn.nukkit.network.protocol.types.clock;

import java.util.List;

public class AddTimeMarkerData implements SyncWorldClocksPayload {

    public long clockId;
    public List<TimeMarkerData> timeMarkers;

    public AddTimeMarkerData(long clockId, List<TimeMarkerData> timeMarkers) {
        this.clockId = clockId;
        this.timeMarkers = timeMarkers;
    }
}
