package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.DropItemPacketV113;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DropItemProcessor_v113 extends DataPacketProcessor<DropItemPacketV113> {

    public static final DropItemProcessor_v113 INSTANCE = new DropItemProcessor_v113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull DropItemPacketV113 pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive() || pk.item.getId() <= 0) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        Item item = (player.isCreative() || inventory.contains(pk.item)) ? pk.item : inventory.getItemInHand();

        PlayerDropItemEvent dropItemEvent = new PlayerDropItemEvent(player, item);
        Server.getInstance().getPluginManager().callEvent(dropItemEvent);
        if (dropItemEvent.isCancelled()) {
            player.getUIInventory().clearAll();
            inventory.sendContents(player);
            return;
        }

        if (!player.isCreative()) {
            inventory.removeItem(item);
        }
        Vector3 motion = player.getDirectionVector().multiply(0.4);

        player.level.dropItem(player.add(0, 1.3, 0), item, motion, 40);

        player.stopAction();
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(DropItemPacketV113.NETWORK_ID);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol < ProtocolInfo.v1_2_0;
    }
}
