package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.types.camera.CameraEase;
import cn.nukkit.network.protocol.types.camera.CameraSplineDefinition;
import cn.nukkit.network.protocol.types.camera.CameraSplineInstruction;
import cn.nukkit.network.protocol.types.camera.CameraSplineType;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera custom spline data sent from server to client.
 * @since v924
 */
@ToString
public class CameraSplinePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CAMERA_SPLINE_PACKET;

    public List<CameraSplineDefinition> splines = new ArrayList<>();

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        int splineCount = (int) this.getUnsignedVarInt();
        this.splines = new ArrayList<>(splineCount);

        for (int i = 0; i < splineCount; i++) {
            String name = this.getString();
            float totalTime = this.getLFloat();
            CameraSplineType type = CameraSplineType.values()[this.getByte()];

            // Read curve points
            int curveCount = (int) this.getUnsignedVarInt();
            List<Vector3f> curve = new ArrayList<>(curveCount);
            for (int j = 0; j < curveCount; j++) {
                curve.add(this.getVector3f());
            }

            // Read progress key frames
            int progressCount = (int) this.getUnsignedVarInt();
            List<CameraSplineInstruction.SplineProgressOption> progressKeyFrames = new ArrayList<>(progressCount);
            for (int j = 0; j < progressCount; j++) {
                float value = this.getLFloat();
                float time = this.getLFloat();
                CameraEase easingFunc = CameraEase.values()[this.getLInt()];
                progressKeyFrames.add(new CameraSplineInstruction.SplineProgressOption(value, time, easingFunc));
            }

            // Read rotation options
            int rotationCount = (int) this.getUnsignedVarInt();
            List<CameraSplineInstruction.SplineRotationOption> rotationOptions = new ArrayList<>(rotationCount);
            for (int j = 0; j < rotationCount; j++) {
                Vector3f keyFrameValues = this.getVector3f();
                float keyFrameTimes = this.getLFloat();
                rotationOptions.add(new CameraSplineInstruction.SplineRotationOption(keyFrameValues, keyFrameTimes));
            }

            CameraSplineInstruction instruction = new CameraSplineInstruction(totalTime, type, curve, progressKeyFrames, rotationOptions);
            this.splines.add(new CameraSplineDefinition(name, instruction));
        }
    }

    @Override
    public void encode() {
        this.reset();

        this.putUnsignedVarInt(this.splines.size());
        for (CameraSplineDefinition spline : this.splines) {
            this.putString(spline.getName());
            CameraSplineInstruction instruction = spline.getInstruction();
            this.putLFloat(instruction.getTotalTime());
            this.putByte((byte) instruction.getType().ordinal());

            // Write curve points
            this.putUnsignedVarInt(instruction.getCurve().size());
            for (Vector3f point : instruction.getCurve()) {
                this.putVector3f(point);
            }

            // Write progress key frames
            this.putUnsignedVarInt(instruction.getProgressKeyFrames().size());
            for (CameraSplineInstruction.SplineProgressOption progress : instruction.getProgressKeyFrames()) {
                this.putLFloat(progress.getValue());
                this.putLFloat(progress.getTime());
                this.putLInt(progress.getEasingFunc().ordinal());
            }

            // Write rotation options
            this.putUnsignedVarInt(instruction.getRotationOption().size());
            for (CameraSplineInstruction.SplineRotationOption rotation : instruction.getRotationOption()) {
                this.putVector3f(rotation.getKeyFrameValues());
                this.putLFloat(rotation.getKeyFrameTimes());
            }
        }
    }
}
