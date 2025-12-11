package cn.nukkit.network;

import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.camera.ClientCameraAimAssistPacketAction;
import lombok.*;

@ToString
public class ClientCameraAimAssistPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENT_CAMERA_AIM_ASSIST_PACKET;

    public String cameraPresetId;
    public ClientCameraAimAssistPacketAction action;
    public boolean allowAimAssist;

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.cameraPresetId = this.getString();
        this.action = ClientCameraAimAssistPacketAction.values()[this.getByte()];
        this.allowAimAssist = this.getBoolean();
    }

    @Override
    public void encode() {
        this.putString(cameraPresetId);
        this.putByte((byte) action.ordinal());
        this.putBoolean(allowAimAssist);
    }
}