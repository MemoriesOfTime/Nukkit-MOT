package cn.nukkit.network.protocol.types.camera;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CameraSplineInstruction {

    private float totalTime;
    private CameraSplineType type;
    private List<Vector3f> curve;
    private List<Vector2f> progressKeyFrames;
    private List<SplineRotationOption> rotationOption;

    @Data
    @AllArgsConstructor
    public static class SplineRotationOption {

        private Vector3f keyFrameValues;
        private float keyFrameTimes;
    }
}
