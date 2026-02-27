package cn.nukkit.network.protocol.types.debugshape;

import cn.nukkit.math.Vector3f;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class DebugBox extends DebugShape {

    Vector3f boxBounds;

    public DebugBox(long id, int dimension, @Nullable Vector3f position, @Nullable Float scale, @Nullable Vector3f rotation, @Nullable Float totalTimeLeft, @Nullable Color color, Vector3f boxBounds) {
        super(id, dimension, position, scale, rotation, totalTimeLeft, color, null);
        this.boxBounds = boxBounds;
    }

    @Override
    public Type getType() {
        return Type.BOX;
    }
}
