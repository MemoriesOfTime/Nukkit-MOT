package cn.nukkit.network.protocol.types.debugshape;

import cn.nukkit.math.Vector3f;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class DebugText extends DebugShape {

    String text;
    boolean useRotation;
    @Nullable Color backgroundColor;
    boolean depthTest;
    boolean showBackface;
    boolean showTextBackface;

    public DebugText(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Color color, String text) {
        this(id, dimension, position, scale, rotation, totalTimeLeft, null, color, text, false, null, false, false, false);
    }

    public DebugText(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Float maximumRenderDistance, @Nullable Color color, String text) {
        this(id, dimension, position, scale, rotation, totalTimeLeft, maximumRenderDistance, color, text, false, null, false, false, false);
    }

    public DebugText(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Float maximumRenderDistance, @Nullable Color color, String text, boolean useRotation, @Nullable Color backgroundColor, boolean depthTest, boolean showBackface, boolean showTextBackface) {
        super(id, dimension, position, scale, rotation, totalTimeLeft, maximumRenderDistance, color, null);
        this.text = text;
        this.useRotation = useRotation;
        this.backgroundColor = backgroundColor;
        this.depthTest = depthTest;
        this.showBackface = showBackface;
        this.showTextBackface = showTextBackface;
    }

    @Override
    public Type getType() {
        return Type.TEXT;
    }
}
