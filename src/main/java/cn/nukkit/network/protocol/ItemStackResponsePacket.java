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
                this.putBoolean(true);
                if (r.getContainers().isEmpty()) {
                    this.putBoolean(false);
                    return;
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
                        // v2168 wraps netId in two booleans (has + present); omit when zero
                        boolean hasStackId = item.getStackNetworkId() != 0;
                        this.putBoolean(hasStackId);
                        if (hasStackId) {
                            this.putBoolean(true);
                            this.putVarInt(item.getStackNetworkId());
                        }
                    } else {
                        this.putVarInt(item.getStackNetworkId());
                    }
                    if (this.protocol >= ProtocolInfo.v1_16_200) {
                        this.putString(item.getCustomName());
                    }
                    if (this.protocol >= ProtocolInfo.v1_21_50) {
                        this.putString(item.getFilteredCustomName());
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
