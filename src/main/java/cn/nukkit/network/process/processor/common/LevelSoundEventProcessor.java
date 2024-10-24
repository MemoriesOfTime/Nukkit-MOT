package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelSoundEventProcessor<T extends LevelSoundEventPacket> extends DataPacketProcessor<LevelSoundEventPacket> {

    public static final LevelSoundEventProcessor INSTANCE = new LevelSoundEventProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull LevelSoundEventPacket pk) {
        Player player = playerHandle.player;
        if (!player.isSpectator()) {
            /*if (player.isSpectator()
                    && (pk.sound == LevelSoundEventPacket.SOUND_HIT
                    || pk.sound == LevelSoundEventPacket.SOUND_ATTACK_NODAMAGE
                    || pk.sound == LevelSoundEventPacket.SOUND_ATTACK
                    || pk.sound == LevelSoundEventPacket.SOUND_ATTACK_STRONG)) {
                return;
            }*/
            player.level.addChunkPacket(player.getChunkX(), player.getChunkZ(), pk);
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return LevelSoundEventPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
