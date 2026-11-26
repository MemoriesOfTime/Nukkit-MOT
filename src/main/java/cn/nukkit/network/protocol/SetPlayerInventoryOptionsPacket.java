package cn.nukkit.network.protocol;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.types.inventory.InventoryLayout;
import cn.nukkit.network.protocol.types.inventory.InventoryTabLeft;
import cn.nukkit.network.protocol.types.inventory.InventoryTabRight;
import lombok.ToString;

@ToString
public class SetPlayerInventoryOptionsPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SET_PLAYER_INVENTORY_OPTIONS_PACKET;

    public InventoryTabLeft leftTab;
    public InventoryTabRight rightTab;
    public boolean filtering;
    public InventoryLayout layout;
    public InventoryLayout craftingLayout;
    /**
     * 网易客户端在标准 5 字段后的匿名尾部 varint；decode 捕获原值，encode 回放。
     * <p>
     * NetEase-only anonymous trailing varint; captured on decode, replayed on encode.
     */
    @OnlyNetEase
    public Integer trailingVarint;

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
        int leftOrdinal = this.getVarInt();
        this.leftTab = leftOrdinal >= 0 && leftOrdinal < InventoryTabLeft.VALUES.length
                ? InventoryTabLeft.VALUES[leftOrdinal] : InventoryTabLeft.NONE;
        int rightOrdinal = this.getVarInt();
        this.rightTab = rightOrdinal >= 0 && rightOrdinal < InventoryTabRight.VALUES.length
                ? InventoryTabRight.VALUES[rightOrdinal] : InventoryTabRight.NONE;
        this.filtering = this.getBoolean();
        int layoutOrdinal = this.getVarInt();
        this.layout = layoutOrdinal >= 0 && layoutOrdinal < InventoryLayout.VALUES.length
                ? InventoryLayout.VALUES[layoutOrdinal] : InventoryLayout.NONE;
        int craftingLayoutOrdinal = this.getVarInt();
        this.craftingLayout = craftingLayoutOrdinal >= 0 && craftingLayoutOrdinal < InventoryLayout.VALUES.length
                ? InventoryLayout.VALUES[craftingLayoutOrdinal] : InventoryLayout.NONE;
        if (this.gameVersion.isNetEase() && !this.feof()) {
            try {
                this.trailingVarint = (int) this.getUnsignedVarInt();
            } catch (Exception ignored) {
                // trailing bytes not a valid varint; left in the frame, discarded with the batch
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarInt(this.leftTab.ordinal());
        this.putVarInt(this.rightTab.ordinal());
        this.putBoolean(this.filtering);
        this.putVarInt(this.layout.ordinal());
        this.putVarInt(this.craftingLayout.ordinal());
        if (this.gameVersion.isNetEase() && this.protocol >= ProtocolInfo.v1_21_124 && this.protocol < ProtocolInfo.v1_21_130) {
            this.putUnsignedVarInt(this.trailingVarint != null ? this.trailingVarint : 0);
        }
    }
}
