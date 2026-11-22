package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityStructureBlock;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.types.inventory.ContainerType;

/**
 * 结构方块编辑器使用的零槽位容器，只负责打开客户端的结构方块界面。
 * <p>
 * Zero-slot container used by the structure block editor; it only opens the
 * client-side structure block screen.
 */
public class StructureBlockInventory extends BaseInventory {

    public StructureBlockInventory(BlockEntityStructureBlock blockEntity) {
        super(blockEntity, InventoryType.STRUCTURE_BLOCK, new java.util.HashMap<>(), 0);
    }

    @Override
    public BlockEntityStructureBlock getHolder() {
        return (BlockEntityStructureBlock) this.holder;
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
    public void onClose(Player who) {
        ContainerClosePacket pk = new ContainerClosePacket();
        pk.windowId = who.getWindowId(this);
        pk.type = ContainerType.from(this.type.getNetworkType());
        who.dataPacket(pk);
        super.onClose(who);
    }

    /**
     * 结构方块界面关闭时客户端不回传 ContainerClosePacket，必须先清掉旧窗口再重新打开。
     * <p>
     * The client never sends ContainerClosePacket for the structure block screen, so a
     * previously tracked window must be dropped before reopening.
     */
    @Override
    public boolean open(Player who) {
        InventoryOpenEvent ev = new InventoryOpenEvent(this, who);
        who.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return false;
        }
        this.onOpen(who);
        return true;
    }

    @Override
    public int getSize() {
        return 0;
    }
}
