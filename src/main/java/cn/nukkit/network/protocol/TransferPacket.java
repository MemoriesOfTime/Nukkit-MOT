package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class TransferPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.TRANSFER_PACKET;

    public String address;
    public int port = 19132;
    /**
     * @since v729
     */
    public boolean reloadWorld;

    @Override
    public void decode() {
        this.address = this.getString();
        this.port = (short) this.getLShort();
        if (this.protocol >= ProtocolInfo.v1_21_30) {
            this.reloadWorld = this.getBoolean();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(address);
        this.putLShort(port);
        if (this.protocol >= ProtocolInfo.v1_21_30) {
            this.putBoolean(this.reloadWorld);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol <= ProtocolInfo.v_0_12_1){
            return ProtocolInfo.oldProtocolInfo.get((byte) this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }
}
