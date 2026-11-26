package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SetPlayerInventoryOptionsPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetPlayerInventoryOptionsProcessor extends DataPacketProcessor<SetPlayerInventoryOptionsPacket> {

    public static final SetPlayerInventoryOptionsProcessor INSTANCE = new SetPlayerInventoryOptionsProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SetPlayerInventoryOptionsPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }
        int packed = (pk.leftTab != null ? (pk.leftTab.ordinal() & 0xff) : 0)
                | ((pk.rightTab != null ? (pk.rightTab.ordinal() & 0xff) : 0) << 8)
                | ((pk.layout != null ? (pk.layout.ordinal() & 0xff) : 0) << 16)
                | ((pk.craftingLayout != null ? (pk.craftingLayout.ordinal() & 0x7f) : 0) << 24)
                | (pk.filtering ? (1 << 31) : 0);
        if (player.updateInventoryOptionsPacked(packed)) {
            player.recordClientInput();
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.SET_PLAYER_INVENTORY_OPTIONS_PACKET;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return SetPlayerInventoryOptionsPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_20_50;
    }
}
