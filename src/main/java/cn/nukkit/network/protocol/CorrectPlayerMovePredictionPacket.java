package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
@ToString(doNotUseGetters = true)
public class CorrectPlayerMovePredictionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CORRECT_PLAYER_MOVE_PREDICTION_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    /**
     * Client's reported position by the server
     *
     * @param position reported position
     * @return reported position
     */
    private Vector3f position;

    /**
     * Difference in client and server prediction
     *
     * @param delta position difference
     * @return position difference
     */
    private Vector3f delta;

    /**
     * If the client is on the ground. (Not falling or jumping)
     *
     * @param onGround is client on the ground
     * @return is client on the ground
     */
    private boolean onGround;

    /**
     * The tick which is being corrected by the server.
     *
     * @param tick to be corrected
     * @return to be corrected
     */
    private long tick;

    /**
     * @since 649
     *
     * The type of prediction player sends.
     */
    private PredictionType predictionType = PredictionType.PLAYER;

    /**
     * @since 671
     *
     * The rotation of the vehicle.
     */
    private Vector2f vehicleRotation;

    /**
     * @since v712
     */
    private Float vehicleAngularVelocity;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_20_80) {
            this.putByte((byte) this.predictionType.ordinal());
        }
        this.putVector3f(this.position);
        this.putVector3f(this.delta);
        if (this.protocol >= ProtocolInfo.v1_20_80) {
            if (this.predictionType == PredictionType.VEHICLE || this.protocol >= ProtocolInfo.v1_21_100) {
                this.putVector2f(this.vehicleRotation);
                if (this.protocol >= ProtocolInfo.v1_21_20) {
                    this.putOptionalNull(this.vehicleAngularVelocity, this::putFloat);
                }
            }
        }
        this.putBoolean(this.onGround);
        this.putUnsignedVarLong(this.tick);
        if (this.protocol >= ProtocolInfo.v1_20_60 && this.protocol < ProtocolInfo.v1_20_80) {
            this.putByte((byte) this.predictionType.ordinal());
        }
    }

    public enum PredictionType {
        PLAYER,
        VEHICLE
    }

}
