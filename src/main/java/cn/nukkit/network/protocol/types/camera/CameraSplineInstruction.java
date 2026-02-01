package cn.nukkit.network.protocol.types.camera;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CameraSplineInstruction {

    private float totalTime;
    private CameraSplineType type;
    private List<Vector3f> curve;
    /**
     * Progress key frames for the spline animation.
     * @since v924 includes easing function via {@link SplineProgressOption}
     */
    private List<SplineProgressOption> progressKeyFrames;
    private List<SplineRotationOption> rotationOption;

    /**
     * Progress option with value, time and optional easing function.
     * @since v924
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SplineProgressOption {
        private float value;
        private float time;
        /**
         * Easing function for this progress keyframe.
         * @since v924
         */
        private CameraEase easingFunc;

        /**
         * Constructor for backwards compatibility (without easing).
         */
        public SplineProgressOption(float value, float time) {
            this.value = value;
            this.time = time;
            this.easingFunc = CameraEase.LINEAR;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SplineRotationOption {

        private Vector3f keyFrameValues;
        private float keyFrameTimes;
    }
}
