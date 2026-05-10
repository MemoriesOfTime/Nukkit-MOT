package cn.nukkit.network.protocol.types.debugshape;

import cn.nukkit.math.Vector3f;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@Getter
@AllArgsConstructor
public class DebugShape {

    private final long id;
    /**
     * @since v859
     */
    private final int dimension;

    @Nullable
    private final Vector3f position;
    @Nullable
    private final Float scale;
    @Nullable
    private final Vector3f rotation;
    @Nullable
    private final Float totalTimeLeft;
    /**
     * @since v975
     */
    @Nullable
    private final Float maximumRenderDistance;
    @Nullable
    private final Color color;
    /**
     * @since v924
     */
    @Nullable
    private final Long attachedToEntityId;

    public DebugShape(long id) {
        this(id, 0, null, null, null, null, null, null, null);
    }

    public DebugShape(long id, int dimension) {
        this(id, dimension, null, null, null, null, null, null, null);
    }

    public DebugShape(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Color color, @Nullable Long attachedToEntityId) {
        this(id, dimension, position, scale, rotation, totalTimeLeft, null, color, attachedToEntityId);
    }

    public Type getType() {
        return null;
    }

    public enum Type {
        LINE,
        BOX,
        SPHERE,
        CIRCLE,
        TEXT,
        ARROW
    }
}
