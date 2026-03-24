package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.LocatorBarWaypoint;
import lombok.ToString;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Syncs LocatorBar waypoints from server to client.
 *
 * @since v944
 */
@ToString
public class LocatorBarPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.LOCATOR_BAR_PACKET;

    public List<Payload> waypoints = new ArrayList<>();

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
        this.waypoints.clear();
        int count = (int) this.getUnsignedVarInt();
        for (int i = 0; i < count; i++) {
            UUID groupHandle = this.getUUID();
            LocatorBarWaypoint waypoint = readWaypoint();
            int actionFlag = this.getByte();
            this.waypoints.add(new Payload(Action.values()[actionFlag], groupHandle, waypoint));
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.waypoints.size());
        for (Payload payload : this.waypoints) {
            this.putUUID(payload.groupHandle);
            writeWaypoint(payload.waypoint);
            this.putByte((byte) payload.actionFlag.ordinal());
        }
    }

    private void writeWaypoint(LocatorBarWaypoint waypoint) {
        this.putLInt(waypoint.updateFlag);
        this.putOptionalNull(waypoint.visible, this::putBoolean);
        this.putOptionalNull(waypoint.worldPosition, (wp) -> {
            this.putVector3f(wp.position);
            this.putVarInt(wp.dimension);
        });
        this.putOptionalNull(waypoint.textureId, this::putLInt);
        this.putOptionalNull(waypoint.color, (c) -> this.putLInt(c.getRGB()));
        this.putOptionalNull(waypoint.clientPositionAuthority, this::putBoolean);
        this.putOptionalNull(waypoint.entityUniqueId, this::putVarLong);
    }

    private LocatorBarWaypoint readWaypoint() {
        LocatorBarWaypoint waypoint = new LocatorBarWaypoint();
        waypoint.updateFlag = this.getLInt();
        waypoint.visible = this.getOptional(null, (s) -> s.getBoolean());
        waypoint.worldPosition = this.getOptional(null, (s) -> {
            var position = s.getVector3f();
            int dimension = s.getVarInt();
            return new LocatorBarWaypoint.WorldPosition(position, dimension);
        });
        Integer textureId = this.getOptional(null, (s) -> s.getLInt());
        waypoint.textureId = textureId;
        waypoint.color = this.getOptional(null, (s) -> new Color(s.getLInt(), true));
        waypoint.clientPositionAuthority = this.getOptional(null, (s) -> s.getBoolean());
        waypoint.entityUniqueId = this.getOptional(null, (s) -> (long) s.getVarLong());
        return waypoint;
    }

    public enum Action {
        NONE,
        ADD,
        REMOVE,
        UPDATE
    }

    public static class Payload {
        public Action actionFlag;
        public UUID groupHandle;
        public LocatorBarWaypoint waypoint;

        public Payload(Action actionFlag, UUID groupHandle, LocatorBarWaypoint waypoint) {
            this.actionFlag = actionFlag;
            this.groupHandle = groupHandle;
            this.waypoint = waypoint;
        }
    }
}
