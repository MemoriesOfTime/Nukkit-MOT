package cn.nukkit.network.protocol.types.clock;

public class SyncWorldClockStateData {

    public long clockId;
    public int time;
    public boolean paused;

    public SyncWorldClockStateData(long clockId, int time, boolean paused) {
        this.clockId = clockId;
        this.time = time;
        this.paused = paused;
    }
}
