package cn.nukkit.network.process.processor.netease;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.event.player.PlayerNetEaseModEventC2SEvent;
import cn.nukkit.event.player.PlayerNetEasePyRpcReceivedEvent;
import cn.nukkit.event.player.PlayerNetEaseStoreBuySuccessEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.netease.PyRpcPacket;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacket;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.StoreBuySuccessPyRpcSubPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Dispatches NetEase PyRpc sub-packets after the raw PyRpcPacket has been decoded.
 * <p>
 * Adapted from SynapseAPI (<a href="https://github.com/EaseCation/SynapseAPI">SynapseAPI</a>).
 */
@OnlyNetEase
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PyRpcProcessor extends DataPacketProcessor<PyRpcPacket> {

    public static final PyRpcProcessor INSTANCE = new PyRpcProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull PyRpcPacket pk) {
        GameVersion gameVersion = playerHandle.getGameVersion();
        if (gameVersion == null || !gameVersion.isNetEase()) {
            return;
        }

        Player player = playerHandle.player;
        PyRpcMessage message = pk.getMessage();
        PyRpcSubPacket subPacket = pk.getSubPacket();
        if (subPacket == null) {
            return;
        }

        PlayerNetEasePyRpcReceivedEvent pyRpcEvent = new PlayerNetEasePyRpcReceivedEvent(
                player,
                pk.getMsgId(),
                message,
                subPacket
        );
        pyRpcEvent.call();
        if (pyRpcEvent.isCancelled()) {
            return;
        }

        if (subPacket instanceof ModEventPyRpcSubPacket modEvent) {
            new PlayerNetEaseModEventC2SEvent(
                    player,
                    pk.getMsgId(),
                    message,
                    modEvent
            ).call();
        } else if (subPacket instanceof StoreBuySuccessPyRpcSubPacket storeBuySuccess) {
            new PlayerNetEaseStoreBuySuccessEvent(
                    player,
                    pk.getMsgId(),
                    message,
                    storeBuySuccess
            ).call();
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.PY_RPC_PACKET;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return PyRpcPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= GameVersion.V1_20_50_NETEASE.getProtocol();
    }
}
