package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockCrafter;
import cn.nukkit.blockentity.BlockEntityCrafter;
import cn.nukkit.inventory.CrafterInventory;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ToggleCrafterSlotRequestPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ToggleCrafterSlotRequestProcessor extends DataPacketProcessor<ToggleCrafterSlotRequestPacket> {

    public static final ToggleCrafterSlotRequestProcessor INSTANCE = new ToggleCrafterSlotRequestProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ToggleCrafterSlotRequestPacket pk) {
        Player player = playerHandle.player;
        int slot = pk.slot;
        if (slot < 0 || slot >= 9 || pk.blockPosition == null) {
            return;
        }

        Vector3 position = new Vector3(pk.blockPosition.x, pk.blockPosition.y, pk.blockPosition.z);
        if (position.distanceSquared(player) > 100) {
            return;
        }

        Block block = player.getLevel().getBlock(position);
        if (!(block instanceof BlockCrafter crafter)) {
            return;
        }

        BlockEntityCrafter blockEntity = crafter.getOrCreateBlockEntity();
        CrafterInventory inventory = blockEntity.getInventory();
        if (player.getWindowId(inventory) == -1 || !inventory.getViewers().contains(player)) {
            return;
        }
        if (!inventory.setSlotDisabled(slot, pk.disabled)) {
            inventory.sendContents(player);
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.TOGGLE_CRAFTER_SLOT_REQUEST_PACKET;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return ToggleCrafterSlotRequestPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_20_50;
    }
}
