package cn.nukkit.network.protocol.types.camera;

import cn.nukkit.math.Vector2f;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CameraAimAssistPreset {
    private String identifier;
    private Integer targetMode;
    private Vector2f angle;
    private Float distance;
}
