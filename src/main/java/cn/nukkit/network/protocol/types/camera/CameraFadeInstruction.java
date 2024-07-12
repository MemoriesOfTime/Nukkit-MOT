package cn.nukkit.network.protocol.types.camera;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CameraFadeInstruction {
    private TimeData timeData;
    private Color color;

    @Data
    @AllArgsConstructor
    public static class TimeData {
        private float fadeInTime;
        private float waitTime;
        private float fadeOutTime;
    }
}
