package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.debugshape.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;

/**
 * Used by Scripting to send new, removed or modified debug shapes information to the client to be used for rendering.
 * Sends debug geometry to the client. Meant for script debugging purposes.
 *
 * @since v818
 */
@ToString
public class DebugDrawerPacket extends DataPacket {

    public List<DebugShape> shapes = new ObjectArrayList<>();

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return ProtocolInfo.DEBUG_DRAWER_PACKET;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_21_120) {
            this.putArray(this.shapes, this::writeShape859);
        } else {
            this.putArray(this.shapes, this::writeShape818);
        }
    }

    protected void writeShape859(DebugShape shape) {
        this.putUnsignedVarLong(shape.getId());
        this.writeCommonShapeData(shape);
        if (this.protocol >= ProtocolInfo.v1_26_0) {
            // v924: dimension is optional
            this.putBoolean(true);
            this.putVarInt(shape.getDimension());
        } else {
            this.putVarInt(shape.getDimension());
        }
        if (this.protocol >= ProtocolInfo.v1_26_0) {
            this.putOptionalNull(shape.getAttachedToEntityId(), val -> this.putUnsignedVarLong(val));
        }
        this.putUnsignedVarInt(this.toPayloadType(shape.getType()));

        if (shape.getType() != null) {
            switch (shape.getType()) {
                case ARROW:
                    DebugArrow arrow = (DebugArrow) shape;
                    this.putOptionalNull(arrow.getArrowEndPosition(), this::putVector3f);
                    this.putOptionalNull(arrow.getArrowHeadLength(), this::putLFloat);
                    this.putOptionalNull(arrow.getArrowHeadRadius(), this::putLFloat);
                    this.putOptionalNull(arrow.getArrowHeadSegments(), this::putByte);
                    break;
                case BOX:
                    DebugBox box = (DebugBox) shape;
                    this.putVector3f(box.getBoxBounds());
                    break;
                case CIRCLE:
                    DebugCircle circle = (DebugCircle) shape;
                    this.putByte(circle.getSegments());
                    break;
                case LINE:
                    DebugLine line = (DebugLine) shape;
                    this.putVector3f(line.getLineEndPosition());
                    break;
                case SPHERE:
                    DebugSphere sphere = (DebugSphere) shape;
                    this.putByte(sphere.getSegments());
                    break;
                case TEXT:
                    DebugText text = (DebugText) shape;
                    this.putString(text.getText());
                    break;
            }
        }
    }

    protected void writeShape818(DebugShape shape) {
        this.putUnsignedVarLong(shape.getId());
        this.writeCommonShapeData(shape);

        if (shape.getType() != null) {
            switch (shape.getType()) {
                case ARROW:
                    DebugArrow arrow = (DebugArrow) shape;
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putOptionalNull(arrow.getArrowEndPosition(), this::putVector3f);
                    this.putOptionalNull(arrow.getArrowHeadLength(), this::putLFloat);
                    this.putOptionalNull(arrow.getArrowHeadRadius(), this::putLFloat);
                    this.putOptionalNull(arrow.getArrowHeadSegments(), this::putByte);
                    break;
                case BOX:
                    DebugBox box = (DebugBox) shape;
                    this.putBoolean(false);
                    this.putOptionalNull(box.getBoxBounds(), this::putVector3f);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    break;
                case CIRCLE:
                    DebugCircle circle = (DebugCircle) shape;
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putOptionalNull(circle.getSegments(), this::putByte);
                    break;
                case LINE:
                    DebugLine line = (DebugLine) shape;
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putOptionalNull(line.getLineEndPosition(), this::putVector3f);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    break;
                case SPHERE:
                    DebugSphere sphere = (DebugSphere) shape;
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putOptionalNull(sphere.getSegments(), this::putByte);
                    break;
                case TEXT:
                    DebugText text = (DebugText) shape;
                    this.putOptionalNull(text.getText(), this::putString);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    this.putBoolean(false);
                    break;
            }
        }
    }

    protected int toPayloadType(DebugShape.Type type) {
        if (type == null) {
            return 0;
        }

        return switch (type) {
            case ARROW -> 1;
            case TEXT -> 2;
            case BOX -> 3;
            case LINE -> 4;
            case SPHERE, CIRCLE -> 5;
            default -> throw new IllegalStateException("Unknown debug shape type");
        };
    }

    protected void writeCommonShapeData(DebugShape shape) {
        this.putOptionalNull(shape.getType(), type -> this.putByte(type.ordinal()));
        this.putOptionalNull(shape.getPosition(), this::putVector3f);
        this.putOptionalNull(shape.getScale(), this::putLFloat);
        this.putOptionalNull(shape.getRotation(), this::putVector3f);
        this.putOptionalNull(shape.getTotalTimeLeft(), this::putLFloat);
        this.putOptionalNull(shape.getColor(), color -> this.putLInt(color.getRGB()));
    }
}
