package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SetPlayerFurnaceOptionsPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetPlayerFurnaceOptionsProcessor extends DataPacketProcessor<SetPlayerFurnaceOptionsPacket> {

    public static final SetPlayerFurnaceOptionsProcessor INSTANCE = new SetPlayerFurnaceOptionsProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SetPlayerFurnaceOptionsPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }
        int packed = (pk.type != null ? (pk.type.ordinal() & 0xff) : 0)
                | ((pk.leftTabIndex != null ? (pk.leftTabIndex.ordinal() & 0xff) : 0) << 8)
                | ((pk.layout != null ? (pk.layout.ordinal() & 0xff) : 0) << 16)
                | (pk.filtering ? (1 << 24) : 0);
        if (player.updateFurnaceOptionsPacked(packed)) {
            player.recordClientInput();
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.SET_PLAYER_FURNACE_OPTIONS_PACKET;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return SetPlayerFurnaceOptionsPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_26_50_27;
    }
}
