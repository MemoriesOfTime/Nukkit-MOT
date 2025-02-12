package cn.nukkit.network.protocol.types.camera;

import cn.nukkit.math.Vector2f;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
@AllArgsConstructor
public class CameraAimAssistPreset {
    @Nullable
    public String identifier;
    @Nullable
    public AimAssistAction targetMode;
    @Nullable
    private Vector2f angle;
    @Nullable
    private Float distance;
}