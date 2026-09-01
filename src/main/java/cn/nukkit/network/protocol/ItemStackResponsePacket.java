package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponse;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
@NoArgsConstructor
public class ItemStackResponsePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ITEM_STACK_RESPONSE_PACKET;

    public final List<ItemStackResponse> entries = new ArrayList<>();

    @Override
    public void encode() {
        this.reset();
        this.putArray(entries, (r) -> {
            this.putByte((byte) r.getResult().ordinal());
            this.putVarInt(r.getRequestId());
            if (this.protocol >= ProtocolInfo.v1_26_40) {
                if (r.getContainers().isEmpty()) {
                    this.putBoolean(false);
                    return;
                }
                // v2192 起容器段仅一个存在性 bool / single presence bool for containers since v2192
                if (this.protocol < ProtocolInfo.v1_26_50) {
                    this.putBoolean(true);
                }
                this.putBoolean(true);
            } else {
                if (r.getResult() != ItemStackResponseStatus.OK) return;
            }
            this.putArray(r.getContainers(), (container) -> {
                if (this.protocol >= ProtocolInfo.v1_21_20) {
                    this.writeFullContainerName(container.getContainerName() != null
                            ? container.getContainerName()
                            : new FullContainerName(container.getContainer(), null));
                } else {
                    this.putByte((byte) container.getContainer().getId(this.gameVersion));
                }
                this.putArray(container.getItems(), (item) -> {
                    this.putByte((byte) item.getSlot());
                    this.putByte((byte) item.getHotbarSlot());
                    this.putByte((byte) item.getCount());
                    if (this.protocol >= ProtocolInfo.v1_26_40) {
                        // v2168~v2169 netId 外包双 bool（has-entry + present）；v2192 起仅 present
                        // v2168~v2169 wrap netId in two booleans; only the presence bool remains since v2192
                        if (this.protocol < ProtocolInfo.v1_26_50) {
                            this.putBoolean(true);
                        }
                        boolean present = item.getStackNetworkId() != 0;
                        this.putBoolean(present);
                        if (present) {
                            this.putVarInt(item.getStackNetworkId());
                        }
                    } else {
                        this.putVarInt(item.getStackNetworkId());
                    }
                    if (this.protocol >= ProtocolInfo.v1_16_200) {
                        this.putString(item.getCustomName() != null ? item.getCustomName() : "");
                    }
                    if (this.protocol >= ProtocolInfo.v1_21_50) {
                        if (this.protocol >= ProtocolInfo.v1_26_40) {
                            // v2168 起 filteredCustomName 为可选 string（CB 修复 6d903898）
                            // filteredCustomName became optional since v2168 (CB fix 6d903898)
                            this.putOptionalNull(item.getFilteredCustomName(), name -> this.putString(name));
                        } else {
                            this.putString(item.getFilteredCustomName() != null ? item.getFilteredCustomName() : "");
                        }
                    }
                    if (this.protocol >= ProtocolInfo.v1_16_210) {
                        this.putVarInt(item.getDurabilityCorrection());
                    }
                });
            });
        });
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
