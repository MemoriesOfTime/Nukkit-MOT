package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ItemFrameDropItemPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author glorydark
 * @date {2024/1/10} {12:47}
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemFrameDropItemProcessor extends DataPacketProcessor<ItemFrameDropItemPacket> {

    public static final ItemFrameDropItemProcessor INSTANCE = new ItemFrameDropItemProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ItemFrameDropItemPacket pk) {
        Player player = playerHandle.player;
        Vector3 vector3 = player.temporalVector.setComponents(pk.x, pk.y, pk.z);
        if (vector3.distanceSquared(player) < 1000) {
            BlockEntity itemFrame = player.level.getBlockEntityIfLoaded(vector3);
            if (itemFrame instanceof BlockEntityItemFrame) {
                ((BlockEntityItemFrame) itemFrame).dropItem(player);
            }
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET);
    }

    @Override
    public Class<ItemFrameDropItemPacket> getPacketClass() {
        return ItemFrameDropItemPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
