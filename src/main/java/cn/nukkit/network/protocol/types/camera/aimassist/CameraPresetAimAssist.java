package cn.nukkit.network.protocol.types.camera.aimassist;

import cn.nukkit.math.Vector2f;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@AllArgsConstructor
public class CameraPresetAimAssist {
    @Nullable
    public String presetId;
    @Nullable
    public CameraAimAssist targetMode;
    @Nullable
    private Vector2f angle;
    @Nullable
    private Float distance;
}