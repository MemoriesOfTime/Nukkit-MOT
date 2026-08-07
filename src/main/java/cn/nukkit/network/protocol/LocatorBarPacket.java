package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.LocatorBarWaypoint;
import cn.nukkit.utils.BinaryStream;
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
        this.waypoints = new ArrayList<>();
        this.getArray(this.waypoints, bs -> {
            UUID groupHandle = bs.getUUID();
            LocatorBarWaypoint waypoint = this.readWaypoint();
            int actionFlag = bs.getByte();
            return new Payload(Action.values()[actionFlag], groupHandle, waypoint);
        });
    }

    @Override
    public void encode() {
        this.reset();
        this.putArray(this.waypoints, payload -> {
            this.putUUID(payload.groupHandle);
            this.writeWaypoint(payload.waypoint);
            this.putByte((byte) payload.actionFlag.ordinal());
        });
    }

    private void writeWaypoint(LocatorBarWaypoint waypoint) {
        this.putLInt(waypoint.updateFlag);
        this.putOptionalNull(waypoint.visible, this::putBoolean);
        this.putOptionalNull(waypoint.worldPosition, (wp) -> {
            this.putVector3f(wp.position);
            this.putVarInt(wp.dimension);
        });
        if (this.protocol >= ProtocolInfo.v1_26_20_26) {
            this.putOptionalNull(waypoint.texturePath, this::putString);
            this.putOptionalNull(waypoint.iconSize, (icon) -> {
                this.putLFloat(icon.x);
                this.putLFloat(icon.y);
            });
        } else {
            this.putOptionalNull(waypoint.textureId, this::putLInt);
        }
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
        if (this.protocol >= ProtocolInfo.v1_26_20_26) {
            waypoint.texturePath = this.getOptional(null, BinaryStream::getString);
            waypoint.iconSize = this.getOptional(null, (s) -> new LocatorBarWaypoint.Vector2f(s.getLFloat(), s.getLFloat()));
        } else {
            waypoint.textureId = this.getOptional(null, BinaryStream::getLInt);
        }
        waypoint.color = this.getOptional(null, (s) -> new Color(s.getLInt(), true));
        waypoint.clientPositionAuthority = this.getOptional(null, BinaryStream::getBoolean);
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
