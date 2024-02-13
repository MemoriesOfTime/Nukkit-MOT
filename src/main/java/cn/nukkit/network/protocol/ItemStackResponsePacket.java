package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.itemstack.response.ItemStackResponse;
import cn.nukkit.network.protocol.types.itemstack.response.ItemStackResponseStatus;
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
        putArray(entries, (r) -> {
            putByte((byte) r.getResult().ordinal());
            putVarInt(r.getRequestId());
            if (r.getResult() != ItemStackResponseStatus.OK) return;
            putArray(r.getContainers(), (container) -> {
                putByte((byte) container.getContainer().getId());
                putArray(container.getItems(), (item) -> {
                    putByte((byte) item.getSlot());
                    putByte((byte) item.getHotbarSlot());
                    putByte((byte) item.getCount());
                    putVarInt(item.getStackNetworkId());
                    putString(item.getCustomName());
                    putVarInt(item.getDurabilityCorrection());
                });
            });
        });
    }

    @Override
    public void decode() {
        throw new UnsupportedOperationException();//client bound
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
