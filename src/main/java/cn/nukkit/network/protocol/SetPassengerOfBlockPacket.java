package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.PassengerOfBlockArguments;
import lombok.ToString;

/**
 * @since v2225
 */
@ToString
public class SetPassengerOfBlockPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SET_PASSENGER_OF_BLOCK_PACKET;

    public long passengerUniqueId;
    public PassengerOfBlockArguments passengerOfBlockArguments;

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
        this.passengerUniqueId = this.getVarLong();
        if (this.getBoolean()) {
            this.passengerOfBlockArguments = PassengerOfBlockArguments.get(this);
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarLong(this.passengerUniqueId);
        this.putOptionalNull(this.passengerOfBlockArguments, PassengerOfBlockArguments::put);
    }
}
