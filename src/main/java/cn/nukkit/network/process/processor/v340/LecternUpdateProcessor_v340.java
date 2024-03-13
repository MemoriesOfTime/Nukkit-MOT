package cn.nukkit.network.process.processor.v340;

import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLectern;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityLectern;
import cn.nukkit.event.block.LecternPageChangeEvent;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.LecternUpdatePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LecternUpdateProcessor_v340 extends DataPacketProcessor<LecternUpdatePacket> {

    public static final LecternUpdateProcessor_v340 INSTANCE = new LecternUpdateProcessor_v340();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull LecternUpdatePacket pk) {
        BlockVector3 blockPosition = pk.blockPosition;
        playerHandle.player.temporalVector.setComponents(blockPosition.x, blockPosition.y, blockPosition.z);
        if (pk.dropBook) {
            Block blockLectern = playerHandle.player.getLevel().getBlock(playerHandle.player.temporalVector);
            if (blockLectern instanceof BlockLectern && blockLectern.distance(playerHandle.player) <= 6) {
                ((BlockLectern) blockLectern).dropBook(playerHandle.player);
            }
        } else {
            BlockEntity blockEntityLectern = playerHandle.player.level.getBlockEntity(playerHandle.player.temporalVector);
            if (blockEntityLectern instanceof BlockEntityLectern lectern) {
                LecternPageChangeEvent lecternPageChangeEvent = new LecternPageChangeEvent(playerHandle.player, lectern, pk.page);
                playerHandle.player.getServer().getPluginManager().callEvent(lecternPageChangeEvent);
                if (!lecternPageChangeEvent.isCancelled()) {
                    lectern.setRawPage(lecternPageChangeEvent.getNewRawPage());
                    lectern.spawnToAll();
                    Block blockLectern = lectern.getBlock();
                    if (blockLectern instanceof BlockLectern) {
                        ((BlockLectern) blockLectern).executeRedstonePulse();
                    }
                }
            }
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.LECTERN_UPDATE_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_10_0;
    }
}
