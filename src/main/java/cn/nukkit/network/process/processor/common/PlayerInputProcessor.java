package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.entity.EntityControllable;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerInputPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerInputProcessor extends DataPacketProcessor<PlayerInputPacket> {

    public static final PlayerInputProcessor INSTANCE = new PlayerInputProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull PlayerInputPacket pk) {
        Player player = playerHandle.player;
        if (!player.isAlive() || !player.spawned || player.isMovementServerAuthoritative() || player.isLockMovementInput()) {
            return;
        }

        float moveVecX = pk.motionX;
        float moveVecY = pk.motionY;
        if (!Player.validateVehicleInput(moveVecX) || !Player.validateVehicleInput(moveVecY)) {
            player.getServer().getLogger().warning("Invalid vehicle input received: " + playerHandle.getUsername());
            return;
        }

        if (player.riding != null && (moveVecX != 0 || moveVecY != 0)) {
            moveVecX = NukkitMath.clamp(pk.motionX, -1, 1);
            moveVecY = NukkitMath.clamp(pk.motionY, -1, 1);
            if (player.riding instanceof EntityControllable controllable) {
                controllable.onPlayerInput(player, moveVecX, moveVecY);
            }
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.PLAYER_INPUT_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return PlayerInputPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
