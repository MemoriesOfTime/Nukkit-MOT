package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ClientCacheStatusPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * 登录期客户端缓存能力协商：记录 supported 标志，供区块发送模式决策。
 * <p>
 * Login-phase client cache capability negotiation: records the supported flag
 * for chunk delivery mode selection.
 *
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientCacheStatusProcessor extends DataPacketProcessor<ClientCacheStatusPacket> {

    public static final ClientCacheStatusProcessor INSTANCE = new ClientCacheStatusProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ClientCacheStatusPacket pk) {
        Player player = playerHandle.player;
        player.setClientCacheSupported(pk.supported);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.CLIENT_CACHE_STATUS_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return ClientCacheStatusPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_12_0;
    }
}
