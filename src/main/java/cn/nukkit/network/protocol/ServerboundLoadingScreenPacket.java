package cn.nukkit.network.protocol;

import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * @since v712
 */
@ToString
public class ServerboundLoadingScreenPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_LOADING_SCREEN_PACKET;

    public static final int TYPE_UNKNOWN = 1;

    public static final int TYPE_START_LOADING_SCREEN = 2;

    public static final int TYPE_STOP_LOADING_SCREEN = 3;

    public int loadingScreenType;

    public Integer loadingScreenId;

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
        this.loadingScreenType = this.getVarInt();
        this.loadingScreenId = this.getOptional(null, BinaryStream::getLInt);
    }

    @Override
    public void encode() {
        this.putVarInt(this.loadingScreenType);
        this.putOptionalNull(this.loadingScreenId, this::putLInt);
    }
}
