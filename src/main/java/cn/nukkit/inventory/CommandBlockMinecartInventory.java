package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.entity.item.EntityMinecartCommandBlock;
import cn.nukkit.network.protocol.ContainerOpenPacket;

/**
 * Command block UI inventory bound to a command block minecart entity.
 * Opens via an entity-referenced {@link ContainerOpenPacket}.
 */
public class CommandBlockMinecartInventory extends BaseInventory {

    public CommandBlockMinecartInventory(EntityMinecartCommandBlock commandBlock) {
        super(commandBlock, InventoryType.COMMAND_BLOCK, new java.util.HashMap<>(), 0);
    }

    @Override
    public EntityMinecartCommandBlock getHolder() {
        return (EntityMinecartCommandBlock) this.holder;
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        if (who.isOp() && who.isCreative()) {
            ContainerOpenPacket pk = new ContainerOpenPacket();
            pk.windowId = who.getWindowId(this);
            pk.type = this.type.getNetworkType();
            // Reference the minecart entity rather than block coordinates.
            pk.entityId = this.getHolder().getId();
            who.dataPacket(pk);
        }
    }

    @Override
    public int getSize() {
        return 0;
    }
}
