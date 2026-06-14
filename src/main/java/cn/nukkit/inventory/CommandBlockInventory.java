package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityCommandBlock;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.network.protocol.ContainerClosePacket;
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
    public void onClose(Player who) {
        ContainerClosePacket pk = new ContainerClosePacket();
        pk.windowId = who.getWindowId(this);
        pk.type = cn.nukkit.network.protocol.types.inventory.ContainerType.from(this.type.getNetworkType());
        who.dataPacket(pk);
        super.onClose(who);
    }

    /**
     * The Bedrock client does not send a {@code ContainerClosePacket} when the
     * command block editor is closed (unlike regular containers). Because of
     * this, {@link Player#addWindow} keeps the inventory tracked after the first
     * open, so subsequent clicks short-circuit in {@code addWindow} and never
     * resend the {@code ContainerOpenPacket} — meaning the UI can only be opened
     * once per command block.
     * <p>
     * Callers must remove a stale tracked window before invoking
     * {@link Player#addWindow}; by the time this method runs, {@code addWindow}
     * has already assigned the fresh window id used by {@link #onOpen(Player)}.
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
