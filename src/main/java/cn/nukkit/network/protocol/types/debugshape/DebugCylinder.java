package cn.nukkit.network.protocol.types.debugshape;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Cylinder debug shape.
 *
 * @since v1_26_30
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class DebugCylinder extends DebugShape {

    @Nullable
    Vector2f radiusX;
    @Nullable
    Vector2f radiusZ;
    float height;
    int segments;

    public DebugCylinder(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Color color, @Nullable Vector2f radiusX, @Nullable Vector2f radiusZ, float height, int segments) {
        this(id, dimension, position, scale, rotation, totalTimeLeft, null, color, radiusX, radiusZ, height, segments);
    }

    public DebugCylinder(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Float maximumRenderDistance, @Nullable Color color, @Nullable Vector2f radiusX, @Nullable Vector2f radiusZ, float height, int segments) {
        super(id, dimension, position, scale, rotation, totalTimeLeft, maximumRenderDistance, color, null);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.height = height;
        this.segments = segments;
    }

    @Override
    public Type getType() {
        return Type.CYLINDER;
    }
}
