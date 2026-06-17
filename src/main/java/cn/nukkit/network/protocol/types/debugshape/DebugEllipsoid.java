package cn.nukkit.network.protocol.types.debugshape;

import cn.nukkit.math.Vector3f;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Ellipsoid debug shape.
 *
 * @since v1_26_30
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class DebugEllipsoid extends DebugShape {

    @Nullable
    Vector3f radii;
    int segments;

    public DebugEllipsoid(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Color color, @Nullable Vector3f radii, int segments) {
        this(id, dimension, position, scale, rotation, totalTimeLeft, null, color, radii, segments);
    }

    public DebugEllipsoid(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Float maximumRenderDistance, @Nullable Color color, @Nullable Vector3f radii, int segments) {
        super(id, dimension, position, scale, rotation, totalTimeLeft, maximumRenderDistance, color, null);
        this.radii = radii;
        this.segments = segments;
    }

    @Override
    public Type getType() {
        return Type.ELLIPSOID;
    }
}
