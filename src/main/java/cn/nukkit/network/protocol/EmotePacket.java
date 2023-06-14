package cn.nukkit.network.protocol;

public class EmotePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.EMOTE_PACKET;

    public long runtimeId;
    /**
     * @since v588
     */
    public String xuid = "";
    /**
     * @since 588
     */
    public String platformId = "";
    public String emoteID;
    public byte flags;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.runtimeId = this.getEntityRuntimeId();
        this.emoteID = this.getString();
        if (this.protocol >= ProtocolInfo.v1_20_0_23) {
            this.xuid = this.getString();
            this.platformId = this.getString();
        }
        this.flags = (byte) this.getByte();
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityRuntimeId(this.runtimeId);
        this.putString(this.emoteID);
        if (this.protocol >= ProtocolInfo.v1_20_0_23) {
            this.putString(this.xuid);
            this.putString(this.platformId);
        }
        this.putByte(flags);
    }
}
