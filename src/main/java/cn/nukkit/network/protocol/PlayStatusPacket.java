package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class PlayStatusPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAY_STATUS_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    public static final int LOGIN_SUCCESS = 0;
    public static final int LOGIN_FAILED_CLIENT = 1;
    public static final int LOGIN_FAILED_SERVER = 2;
    public static final int PLAYER_SPAWN = 3;
    public static final int LOGIN_FAILED_INVALID_TENANT = 4;
    public static final int LOGIN_FAILED_VANILLA_EDU = 5;
    public static final int LOGIN_FAILED_EDU_VANILLA = 6;
    public static final int LOGIN_FAILED_SERVER_FULL = 7;
    public static final int LOGIN_FAILED_EDITOR_TO_VANILLA_MISMATCH = 8;
    public static final int LOGIN_FAILED_VANILLA_TO_EDITOR_MISMATCH = 9;

    public int status;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_1_0_0){
            this.tryReset();
            this.putInt(this.status);
            return;
        }
        this.reset();
        this.putInt(this.status);
    }
}
