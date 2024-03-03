package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class PlayerStartItemCoolDownPacket extends DataPacket {
    private String itemCategory;
    private int coolDownDuration;

    @Override
    public byte pid() {
        return ProtocolInfo.PLAYER_START_ITEM_COOLDOWN_PACKET;
    }

    @Override
    public void decode() {
        this.itemCategory = this.getString();
        this.coolDownDuration = this.getVarInt();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.itemCategory);
        this.putVarInt(this.coolDownDuration);
    }

    public String getItemCategory() {
        return this.itemCategory;
    }

    public void setItemCategory(String itemCategory) {
        this.itemCategory = itemCategory;
    }

    public int getCoolDownDuration() {
        return this.coolDownDuration;
    }

    public void setCoolDownDuration(int coolDownDuration) {
        this.coolDownDuration = coolDownDuration;
    }
}
