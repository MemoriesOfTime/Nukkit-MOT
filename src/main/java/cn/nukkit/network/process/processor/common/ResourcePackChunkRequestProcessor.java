package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ResourcePackChunkDataPacket;
import cn.nukkit.network.protocol.ResourcePackChunkRequestPacket;
import cn.nukkit.resourcepacks.ResourcePack;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import static cn.nukkit.Player.RESOURCE_PACK_CHUNK_SIZE;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourcePackChunkRequestProcessor extends DataPacketProcessor<ResourcePackChunkRequestPacket> {

    public static final ResourcePackChunkRequestProcessor INSTANCE = new ResourcePackChunkRequestProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ResourcePackChunkRequestPacket pk) {
        Player player = playerHandle.player;
        ResourcePack resourcePack = player.getServer().getResourcePackManager().getPackById(pk.packId);
        if (resourcePack == null) {
            player.close("", "disconnectionScreen.resourcePack");
            return;
        }

        ResourcePackChunkDataPacket dataPacket = new ResourcePackChunkDataPacket();
        dataPacket.packId = resourcePack.getPackId();
        dataPacket.chunkIndex = pk.chunkIndex;
        dataPacket.data = resourcePack.getPackChunk(RESOURCE_PACK_CHUNK_SIZE * pk.chunkIndex, RESOURCE_PACK_CHUNK_SIZE);
        dataPacket.progress = (long) RESOURCE_PACK_CHUNK_SIZE * pk.chunkIndex;
        player.dataPacket(dataPacket);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return ResourcePackChunkRequestPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
