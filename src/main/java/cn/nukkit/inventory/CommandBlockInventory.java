package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityCommandBlock;
import cn.nukkit.network.protocol.ContainerOpenPacket;

public class CommandBlockInventory extends BaseInventory {

    public CommandBlockInventory(BlockEntityCommandBlock commandBlock) {
        super(commandBlock, InventoryType.COMMAND_BLOCK, new java.util.HashMap<>(), 0);
    }

    @Override
    public BlockEntityCommandBlock getHolder() {
        return (BlockEntityCommandBlock) this.holder;
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        if (who.isOp() && who.isCreative()) {
            ContainerOpenPacket pk = new ContainerOpenPacket();
            pk.windowId = who.getWindowId(this);
            pk.type = this.type.getNetworkType();
            pk.x = (int) this.getHolder().x;
            pk.y = (int) this.getHolder().y;
            pk.z = (int) this.getHolder().z;
            who.dataPacket(pk);
        }
    }

    @Override
    public int getSize() {
        return 0;
    }
}
