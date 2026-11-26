package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ServerboundCursorItemDragPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServerboundCursorItemDragProcessor extends DataPacketProcessor<ServerboundCursorItemDragPacket> {

    public static final ServerboundCursorItemDragProcessor INSTANCE = new ServerboundCursorItemDragProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ServerboundCursorItemDragPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }
        player.recordClientInput();
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.SERVERBOUND_CURSOR_ITEM_DRAG_PACKET;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return ServerboundCursorItemDragPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_26_60;
    }
}
