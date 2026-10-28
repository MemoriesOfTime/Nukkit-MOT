package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since v2225
 */
@ToString
public class ClientboundStonecutterSetRecipePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_STONECUTTER_SET_RECIPE_PACKET;

    public long playerUniqueId;
    public byte containerId;
    public int recipeIndex;

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
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarLong(this.playerUniqueId);
        this.putByte(this.containerId);
        this.putVarInt(this.recipeIndex);
    }
}
