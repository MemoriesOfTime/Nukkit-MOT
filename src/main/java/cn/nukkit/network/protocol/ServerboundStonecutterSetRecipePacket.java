package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since v2225
 */
@ToString
public class ServerboundStonecutterSetRecipePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_STONECUTTER_SET_RECIPE_PACKET;

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
        this.containerId = (byte) this.getByte();
        this.recipeIndex = this.getVarInt();
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte(this.containerId);
        this.putVarInt(this.recipeIndex);
    }
}
