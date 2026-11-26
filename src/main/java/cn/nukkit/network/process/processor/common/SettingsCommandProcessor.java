package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SettingsCommandPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingsCommandProcessor extends DataPacketProcessor<SettingsCommandPacket> {

    public static final SettingsCommandProcessor INSTANCE = new SettingsCommandProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SettingsCommandPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }
        player.recordClientInput();
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.SETTINGS_COMMAND_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return SettingsCommandPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_21_40;
    }
}
