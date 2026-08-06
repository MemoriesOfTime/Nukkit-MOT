package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class UpdateTradePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_TRADE_PACKET;

    public byte windowId;
    public byte windowType = 15;  //trading id
    public int size;
    public int unknownVarInt2;
    public int unknownVarInt3;
    public int tradeTier;
    public long traderUniqueEntityId;
    public long playerUniqueEntityId;
    public String displayName;
    public boolean newTradingUi;
    public boolean usingEconomyTrade;
    public byte[] offers;

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.UPDATE_TRADE_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.reset();
        }
        this.putByte(windowId);
        this.putByte(windowType);
        this.putVarInt(size);
        if (protocol < 354) {
            this.putVarInt(unknownVarInt2);
            if (this.protocol >= 313) {
                this.putVarInt(unknownVarInt3);
            }
            this.putBoolean(usingEconomyTrade);
        } else {
            this.putVarInt(tradeTier);
        }
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.putEntityUniqueId(traderUniqueEntityId);
            this.putEntityUniqueId(playerUniqueEntityId);
        }else{
            this.putEntityUniqueId(playerUniqueEntityId);
            this.putEntityUniqueId(traderUniqueEntityId);
        }
        this.putString(displayName);
        if (protocol >= 354) {
            this.putBoolean(newTradingUi);
            this.putBoolean(usingEconomyTrade);
        }
        this.put(this.offers);
    }
}
