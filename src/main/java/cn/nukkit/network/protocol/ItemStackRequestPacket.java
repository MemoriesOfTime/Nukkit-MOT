package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;


@ToString
public class ItemStackRequestPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ITEM_STACK_REQUEST_PACKET;
    private static final int MAX_REQUESTS = 128;

    private final List<ItemStackRequest> requests = new ArrayList<>();

    public List<ItemStackRequest> getRequests() {
        return requests;
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        requests.addAll(List.of(getArray(ItemStackRequest.class,
                stream -> stream.readItemStackRequest(this.gameVersion), MAX_REQUESTS, "item stack request count")));
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
