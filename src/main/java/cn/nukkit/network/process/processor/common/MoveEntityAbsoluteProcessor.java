package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.entity.item.EntityBoat;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.MoveEntityAbsolutePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoveEntityAbsoluteProcessor extends DataPacketProcessor<MoveEntityAbsolutePacket> {

    public static final MoveEntityAbsoluteProcessor INSTANCE = new MoveEntityAbsoluteProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull MoveEntityAbsolutePacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || player.riding == null || player.riding.getId() != pk.eid || !player.riding.isControlling(player)) {
            return;
        }
        if (player.riding instanceof EntityBoat entityBoat) {
            if (player.temporalVector.setComponents(pk.x, pk.y, pk.z).distanceSquared(player.riding) < 1000) {
                entityBoat.onInput(pk.x, pk.y, pk.z, pk.headYaw);
            }
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.MOVE_ENTITY_ABSOLUTE_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        //1.20.60开始使用AuthInputAction.IN_CLIENT_PREDICTED_IN_VEHICLE
        return protocol >= ProtocolInfo.v1_2_0 && protocol < ProtocolInfo.v1_20_60;
    }
}
