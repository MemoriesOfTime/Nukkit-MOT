package cn.nukkit.network.protocol.types.clock;

import java.util.List;

public class InitializeRegistryData implements SyncWorldClocksPayload {

    public List<WorldClockData> clockData;

    public InitializeRegistryData(List<WorldClockData> clockData) {
        this.clockData = clockData;
    }
}
