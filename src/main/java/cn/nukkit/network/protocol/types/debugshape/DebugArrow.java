package cn.nukkit.network.protocol.types.debugshape;

import cn.nukkit.math.Vector3f;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class DebugArrow extends DebugShape {

    @Nullable
    Vector3f arrowEndPosition;
    @Nullable
    Float arrowHeadLength;
    @Nullable
    Float arrowHeadRadius;
    @Nullable
    Integer arrowHeadSegments;

    public DebugArrow(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Color color, @Nullable Vector3f arrowEndPosition, @Nullable Float arrowHeadLength, @Nullable Float arrowHeadRadius, @Nullable Integer arrowHeadSegments) {
        super(id, dimension, position, scale, rotation, totalTimeLeft, color);
        this.arrowEndPosition = arrowEndPosition;
        this.arrowHeadLength = arrowHeadLength;
        this.arrowHeadRadius = arrowHeadRadius;
        this.arrowHeadSegments = arrowHeadSegments;
    }

    @Override
    public Type getType() {
        return Type.ARROW;
    }
}
