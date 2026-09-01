package cn.nukkit.network.protocol.types.debugshape;

import cn.nukkit.math.Vector3f;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Pyramid debug shape.
 *
 * @since v1_26_30
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class DebugPyramid extends DebugShape {

    float width;
    @Nullable
    Float depth;
    float height;

    public DebugPyramid(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Color color, float width, @Nullable Float depth, float height) {
        this(id, dimension, position, scale, rotation, totalTimeLeft, null, color, width, depth, height);
    }

    public DebugPyramid(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Float maximumRenderDistance, @Nullable Color color, float width, @Nullable Float depth, float height) {
        super(id, dimension, position, scale, rotation, totalTimeLeft, maximumRenderDistance, color, null);
        this.width = width;
        this.depth = depth;
        this.height = height;
    }

    @Override
    public Type getType() {
        return Type.PYRAMID;
    }
}
