package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.clock.*;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializes and syncs world clocks from server to client.
 *
 * @since v944
 */
@ToString
public class SyncWorldClocksPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SYNC_WORLD_CLOCKS_PACKET;

    public SyncWorldClocksPayload data;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        int type = (int) this.getUnsignedVarInt();
        switch (type) {
            case 0:
                this.data = readSyncState();
                return;
            case 1:
                this.data = readInitializeRegistry();
                return;
            case 2:
                this.data = readAddTimeMarker();
                return;
            case 3:
                this.data = readRemoveTimeMarker();
                return;
        }
        throw new IllegalArgumentException(type + " is not oneOf<SyncStateData, InitializeRegistryData, AddTimeMarkerData, RemoveTimeMarkerData>");
    }

    @Override
    public void encode() {
        this.reset();
        if (this.data instanceof SyncStateData) {
            this.putUnsignedVarInt(0);
            writeSyncState((SyncStateData) this.data);
        } else if (this.data instanceof InitializeRegistryData) {
            this.putUnsignedVarInt(1);
            writeInitializeRegistry((InitializeRegistryData) this.data);
        } else if (this.data instanceof AddTimeMarkerData) {
            this.putUnsignedVarInt(2);
            writeAddTimeMarker((AddTimeMarkerData) this.data);
        } else if (this.data instanceof RemoveTimeMarkerData) {
            this.putUnsignedVarInt(3);
            writeRemoveTimeMarker((RemoveTimeMarkerData) this.data);
        } else {
            throw new IllegalArgumentException("Not oneOf<SyncStateData, InitializeRegistryData, AddTimeMarkerData, RemoveTimeMarkerData>");
        }
    }

    private void writeSyncState(SyncStateData data) {
        this.putArray(data.clockData, (entry) -> {
            this.putUnsignedVarLong(entry.clockId);
            this.putVarInt(entry.time);
            this.putBoolean(entry.paused);
        });
    }

    private SyncStateData readSyncState() {
        List<SyncWorldClockStateData> clockData = new ArrayList<>();
        this.getArray(clockData, (s) -> {
            long id = s.getUnsignedVarLong();
            int time = s.getVarInt();
            boolean paused = s.getBoolean();
            return new SyncWorldClockStateData(id, time, paused);
        });
        return new SyncStateData(clockData);
    }

    private void writeInitializeRegistry(InitializeRegistryData data) {
        this.putArray(data.clockData, (entry) -> {
            this.putUnsignedVarLong(entry.id);
            this.putString(entry.name);
            this.putVarInt(entry.time);
            this.putBoolean(entry.paused);
            this.putArray(entry.timeMarkers, this::writeTimeMarker);
        });
    }

    private void writeTimeMarker(TimeMarkerData marker) {
        this.putUnsignedVarLong(marker.id);
        this.putString(marker.name);
        this.putVarInt(marker.time);
        this.putOptionalNull(marker.period, this::putLInt);
    }

    private TimeMarkerData readTimeMarker() {
        long markerId = this.getUnsignedVarLong();
        String markerName = this.getString();
        int markerTime = this.getVarInt();
        Integer period = this.getOptional(null, (s) -> s.getLInt());
        return new TimeMarkerData(markerId, markerName, markerTime, period);
    }

    private InitializeRegistryData readInitializeRegistry() {
        List<WorldClockData> clockData = new ArrayList<>();
        this.getArray(clockData, (s) -> {
            long id = s.getUnsignedVarLong();
            String name = s.getString();
            int time = s.getVarInt();
            boolean paused = s.getBoolean();
            List<TimeMarkerData> timeMarkers = new ArrayList<>();
            s.getArray(timeMarkers, (s2) -> readTimeMarker());
            return new WorldClockData(id, name, time, paused, timeMarkers);
        });
        return new InitializeRegistryData(clockData);
    }

    private void writeAddTimeMarker(AddTimeMarkerData data) {
        this.putUnsignedVarLong(data.clockId);
        this.putArray(data.timeMarkers, this::writeTimeMarker);
    }

    private AddTimeMarkerData readAddTimeMarker() {
        long clockId = this.getUnsignedVarLong();
        List<TimeMarkerData> timeMarkers = new ArrayList<>();
        this.getArray(timeMarkers, (s) -> readTimeMarker());
        return new AddTimeMarkerData(clockId, timeMarkers);
    }

    private void writeRemoveTimeMarker(RemoveTimeMarkerData data) {
        this.putUnsignedVarLong(data.clockId);
        this.putArray(data.timeMarkerIds, this::putUnsignedVarLong);
    }

    private RemoveTimeMarkerData readRemoveTimeMarker() {
        long clockId = this.getUnsignedVarLong();
        List<Long> timeMarkerIds = new ArrayList<>();
        this.getArray(timeMarkerIds, (s) -> s.getUnsignedVarLong());
        return new RemoveTimeMarkerData(clockId, timeMarkerIds);
    }
}
