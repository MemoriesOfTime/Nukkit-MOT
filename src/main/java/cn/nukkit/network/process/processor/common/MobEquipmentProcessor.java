package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MobEquipmentProcessor extends DataPacketProcessor<MobEquipmentPacket> {

    public static final MobEquipmentProcessor INSTANCE = new MobEquipmentProcessor();
    
    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull MobEquipmentPacket pk) {
        Player player = playerHandle.player;
        
        if (!player.spawned || !player.isAlive()) {
            return;
        }

        Inventory inv = player.getWindowById(pk.windowId);

        if (inv == null) {
            player.getServer().getLogger().debug(player.getName() + " has no open container with window ID " + pk.windowId);
            return;
        }

        Item item = inv.getItem(pk.hotbarSlot);

        if (!item.equals(pk.item)) {
            player.getServer().getLogger().debug(player.getName() + " tried to equip " + pk.item + " but have " + item + " in target slot");
            inv.sendContents(player);
            return;
        }

        if (inv instanceof PlayerInventory) {
            ((PlayerInventory) inv).equipItem(pk.hotbarSlot);
        }

        player.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.MOB_EQUIPMENT_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return MobEquipmentPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
