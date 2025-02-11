package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;

import java.util.List;

@Getter
public class ContainerRegistryCleanupPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CONTAINER_REGISTRY_CLEANUP_PACKET;

    private final List<FullContainerName> removedContainers = new ObjectArrayList<>();

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
    }

    @Override
    public void encode() {
        this.putArray(this.getRemovedContainers(), this::writeFullContainerName);
    }
}