package cn.nukkit.network.protocol.types.camera;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CameraSetInstruction {
    private NamedDefinition preset;
    private EaseData ease;
    private Vector3f pos;
    private Vector2f rot;
    private Vector3f facing;
    private OptionalBoolean defaultPreset = OptionalBoolean.empty();

    @Data
    @AllArgsConstructor
    public static class EaseData {
        private final CameraEase easeType;
        private final float time;
    }
}
