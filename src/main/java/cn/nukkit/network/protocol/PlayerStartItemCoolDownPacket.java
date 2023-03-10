package cn.nukkit.network.protocol;

/**
 * @author PowerNukkitX Project Team
 * <a href="https://github.com/PowerNukkitX/PowerNukkitX/blob/master/src/main/java/cn/nukkit/network/protocol/PlayerStartItemCoolDownPacket.java">powernukkitx original file</a>
 */
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
