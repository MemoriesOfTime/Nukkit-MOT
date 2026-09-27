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
        this.leftTab = InventoryTabLeft.VALUES[this.getVarInt()];
        this.rightTab = InventoryTabRight.VALUES[this.getVarInt()];
        this.filtering = this.getBoolean();
        this.layout = InventoryLayout.VALUES[this.getVarInt()];
        this.craftingLayout = InventoryLayout.VALUES[this.getVarInt()];
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
        if (this.gameVersion.isNetEase() && this.protocol >= ProtocolInfo.v1_21_124) {
            // v860 起（3.9 提取）网易必带匿名尾部 varint；按 8 字节批量读 varint，缺失触发
            // read-overflow 断言断连——永不省略，无捕获值时写中性 0
            //
            // since v860 (per the 3.9 extraction) NetEase always carries the anonymous trailing
            // varint; bulk varint reads trip a read-overflow assert when missing — never omit,
            // neutral 0 when no captured value exists
            this.putUnsignedVarInt(this.trailingVarint != null ? this.trailingVarint : 0);
        }
    }
}
