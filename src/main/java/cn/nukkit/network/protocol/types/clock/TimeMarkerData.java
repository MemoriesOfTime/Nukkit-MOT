package cn.nukkit.network.protocol.types.clock;

public class TimeMarkerData {

    public long id;
    public String name;
    public int time;
    public Integer period;

    public TimeMarkerData(long id, String name, int time, Integer period) {
        this.id = id;
        this.name = name;
        this.time = time;
        this.period = period;
    }
}
