package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.inventory.InventoryLayout;
import cn.nukkit.network.protocol.types.inventory.InventoryTabLeft;
import cn.nukkit.network.protocol.types.inventory.InventoryTabRight;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class SetPlayerInventoryOptionsPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SET_PLAYER_INVENTORY_OPTIONS_PACKET;

    public InventoryTabLeft leftTab;
    public InventoryTabRight rightTab;
    public boolean filtering;
    public InventoryLayout layout;
    public InventoryLayout craftingLayout;

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
        this.leftTab = getEnumValue(InventoryTabLeft.VALUES, this.getVarInt());
        this.rightTab = getEnumValue(InventoryTabRight.VALUES, this.getVarInt());
        this.filtering = this.getBoolean();
        this.layout = getEnumValue(InventoryLayout.VALUES, this.getVarInt());
        this.craftingLayout = getEnumValue(InventoryLayout.VALUES, this.getVarInt());
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarInt(this.leftTab.ordinal());
        this.putVarInt(this.rightTab.ordinal());
        this.putBoolean(this.filtering);
        this.putVarInt(this.layout.ordinal());
        this.putVarInt(this.craftingLayout.ordinal());
    }

    private static <T extends Enum<T>> T getEnumValue(T[] values, int ordinal) {
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        log.warn("Received unknown enum ordinal {} (max {}), falling back to default", ordinal, values.length - 1);
        return values[0];
    }
}
