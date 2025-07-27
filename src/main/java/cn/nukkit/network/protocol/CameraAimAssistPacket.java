package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector2f;
import lombok.Getter;
import lombok.Setter;

/**
 * @since v729
 */
@Getter
@Setter
public class CameraAimAssistPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CAMERA_AIM_ASSIST_PACKET;

    private Vector2f viewAngle;
    private float distance;
    private TargetMode targetMode;
    private Action action;
    /**
     * @since v766
     */
    private String presetId;
    /**
     * @since v827
     */
    private boolean showDebugRender;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        if (protocol >= ProtocolInfo.v1_21_50) {
            this.setPresetId(this.getString());
        }
        this.setViewAngle(this.getVector2f());
        this.setDistance(this.getFloat());
        this.setTargetMode(CameraAimAssistPacket.TargetMode.values()[this.getByte()]);
        this.setAction(CameraAimAssistPacket.Action.values()[this.getByte()]);
        if (protocol >= ProtocolInfo.v1_21_100) {
            this.setShowDebugRender(this.getBoolean());
        }
    }

    @Override
    public void encode() {
        if (protocol >= ProtocolInfo.v1_21_50) {
            this.putString(this.presetId);
        }
        this.putVector2f(this.getViewAngle());
        this.putFloat(this.getDistance());
        this.putByte((byte) this.getTargetMode().ordinal());
        this.putByte((byte) this.getAction().ordinal());
        if (protocol >= ProtocolInfo.v1_21_100) {
            this.putBoolean(this.isShowDebugRender());
        }
    }

    public enum TargetMode {
        ANGLE,
        DISTANCE,
        COUNT
    }

    public enum Action {
        SET,
        CLEAR,
        COUNT
    }
}