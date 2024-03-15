package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ChunkRadiusUpdatedPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.RequestChunkRadiusPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestChunkRadiusProcessor extends DataPacketProcessor<RequestChunkRadiusPacket> {

    public static final RequestChunkRadiusProcessor INSTANCE = new RequestChunkRadiusProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull RequestChunkRadiusPacket pk) {
        Player player = playerHandle.player;
        ChunkRadiusUpdatedPacket chunkRadiusUpdatePacket = new ChunkRadiusUpdatedPacket();
        playerHandle.setChunkRadius(Math.max(3, Math.min(pk.radius, player.getViewDistance())));
        chunkRadiusUpdatePacket.radius = playerHandle.getChunkRadius();
        player.dataPacket(chunkRadiusUpdatePacket);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET);
    }
}
