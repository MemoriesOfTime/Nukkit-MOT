package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacketV1;
import cn.nukkit.network.protocol.LevelSoundEventPacketV2;
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
        if (!player.isSpectator() && hasFinitePosition(pk)) {
            /*if (player.isSpectator()
                    && (pk.sound == LevelSoundEventPacket.SOUND_HIT
                    || pk.sound == LevelSoundEventPacket.SOUND_ATTACK_NODAMAGE
                    || pk.sound == LevelSoundEventPacket.SOUND_ATTACK
                    || pk.sound == LevelSoundEventPacket.SOUND_ATTACK_STRONG)) {
                return;
            }*/
            LevelSoundEventPacket sound = (LevelSoundEventPacket) pk.clone();
            sound.isEncoded = false;
            sound.reuse();
            sound.isGlobal = false;
            sound.entityUniqueId = player.getId();
            // These legacy codecs encode their own shadowed authority fields.
            if (sound instanceof LevelSoundEventPacketV1 legacy) legacy.isGlobal = false;
            if (sound instanceof LevelSoundEventPacketV2 legacy) legacy.isGlobal = false;
            player.level.addChunkPacket(player.getChunkX(), player.getChunkZ(), sound);
        }
    }

    private static boolean hasFinitePosition(LevelSoundEventPacket packet) {
        // Legacy packets hide the parent's coordinates with their own wire fields.
        if (packet instanceof LevelSoundEventPacketV1 legacy) {
            return hasFinitePosition(legacy.x, legacy.y, legacy.z);
        }
        if (packet instanceof LevelSoundEventPacketV2 legacy) {
            return hasFinitePosition(legacy.x, legacy.y, legacy.z);
        }
        if (!hasFinitePosition(packet.x, packet.y, packet.z)) {
            return false;
        }
        if (packet.protocol < ProtocolInfo.v1_26_20_26 || packet.fireAtPosition == null) {
            return true;
        }
        Vector3f fire = packet.fireAtPosition;
        return hasFinitePosition(fire.x, fire.y, fire.z);
    }

    private static boolean hasFinitePosition(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
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
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_1_0;
    }
}
