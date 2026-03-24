package cn.nukkit.network.protocol.types.clock;

import java.util.List;

public class WorldClockData {

    public long id;
    public String name;
    public int time;
    public boolean paused;
    public List<TimeMarkerData> timeMarkers;

    public WorldClockData(long id, String name, int time, boolean paused, List<TimeMarkerData> timeMarkers) {
        this.id = id;
        this.name = name;
        this.time = time;
        this.paused = paused;
        this.timeMarkers = timeMarkers;
    }
}
