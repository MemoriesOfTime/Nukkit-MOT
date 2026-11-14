package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * 客户端请求服务端更新自己的盔甲显示状态。
 * <p>
 * Client request asking the server to update the sender's armor visibility.
 *
 * @since v2250 1.26.70.23
 */
@ToString
public class PlayerToggleArmorVisibilityRequestPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PLAYER_TOGGLE_ARMOR_VISIBILITY_REQUEST_PACKET;

    public boolean hideArmor;

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
        this.hideArmor = this.getBoolean();
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
