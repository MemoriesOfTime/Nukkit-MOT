package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.entity.item.EntityMinecartCommandBlock;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.types.inventory.ContainerType;

/**
 * Command block UI inventory bound to a command block minecart entity.
 * <p>
 * Mirrors Geyser's {@code CommandBlockMinecartEntity.interact}: a
 * {@link ContainerOpenPacket} with {@code type=COMMAND_BLOCK} referenced by the
 * minecart's entity unique id (block position left at 0,0,0). The Bedrock client
 * opens the command block editor from the entity id for this container type.
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
            pk.entityId = this.getHolder().getId();
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
     * The Bedrock client does not send a {@code ContainerClosePacket} when the
     * command block editor is closed (unlike regular containers). Because of
     * this, {@link Player#addWindow} keeps the inventory tracked after the first
     * open, so subsequent clicks short-circuit in {@code addWindow} and never
     * resend the {@link ContainerOpenPacket} — meaning the UI can only be opened
     * once per minecart.
     * <p>
     * Callers must remove a stale tracked window before invoking
     * {@link Player#addWindow}; by the time this method runs, {@code addWindow}
     * has already assigned the fresh window id used by {@link #onOpen(Player)}.
     * Mirrors {@link CommandBlockInventory#open}.
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
