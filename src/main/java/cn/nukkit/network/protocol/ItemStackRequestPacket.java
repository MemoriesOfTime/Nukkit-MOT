package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;


@ToString
public class ItemStackRequestPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ITEM_STACK_REQUEST_PACKET;

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
        requests.addAll(List.of(getArray(ItemStackRequest.class, BinaryStream::readItemStackRequest)));
    }

    @Override
    public void encode() {
        //non server bound
    }
}
