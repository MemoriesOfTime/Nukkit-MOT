package cn.nukkit.network.protocol.types.debugshape;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Cone debug shape.
 *
 * @since v1_26_30
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class DebugCone extends DebugShape {

    @Nullable
    Vector2f radii;
    float height;
    int segments;

    public DebugCone(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Color color, @Nullable Vector2f radii, float height, int segments) {
        this(id, dimension, position, scale, rotation, totalTimeLeft, null, color, radii, height, segments);
    }

    public DebugCone(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Float maximumRenderDistance, @Nullable Color color, @Nullable Vector2f radii, float height, int segments) {
        super(id, dimension, position, scale, rotation, totalTimeLeft, maximumRenderDistance, color, null);
        this.radii = radii;
        this.height = height;
        this.segments = segments;
    }

    @Override
    public Type getType() {
        return Type.CONE;
    }
}
