package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ServerboundStonecutterSetRecipePacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServerboundStonecutterSetRecipeProcessor extends DataPacketProcessor<ServerboundStonecutterSetRecipePacket> {

    public static final ServerboundStonecutterSetRecipeProcessor INSTANCE = new ServerboundStonecutterSetRecipeProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ServerboundStonecutterSetRecipePacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive() || pk.recipeIndex < 0) {
            return;
        }
        player.recordClientInput();
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.SERVERBOUND_STONECUTTER_SET_RECIPE_PACKET;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return ServerboundStonecutterSetRecipePacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_26_60;
    }
}
