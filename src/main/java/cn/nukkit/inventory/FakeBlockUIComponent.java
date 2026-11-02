package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.types.inventory.ContainerType;

public class FakeBlockUIComponent extends PlayerUIComponent {

    private final InventoryType type;
    private final Object deferredInputCloseIdentity = new Object();
    private boolean inputCloseInProgress;
    private boolean returningNativeInputs;
    private boolean closeEventAwaitingInputs;

    /** Internal native admission preserves virtual onClose ownership for custom window implementations. */
    public final boolean usesNativeInputReturn() {
        Class<?> type = this.getClass();
        return type == AnvilInventory.class || type == GrindstoneInventory.class || type == StonecutterInventory.class
                || type == EnchantInventory.class || type == SmithingInventory.class || type == BeaconInventory.class
                || type == LoomInventory.class || type == CartographyTableInventory.class;
    }

    protected final boolean isReturningNativeInputs() { return this.returningNativeInputs; }

    FakeBlockUIComponent(PlayerUIInventory playerUI, InventoryType type, int offset, Position position) {
        super(playerUI, offset, type.getDefaultSize());
        this.type = type;
        this.holder = new FakeBlockMenu(this, position);
    }


    @Override
    public FakeBlockMenu getHolder() {
        return (FakeBlockMenu) this.holder;
    }

    public InventoryType getFakeBlockType() {
        return this.type;
    }

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
    public void onOpen(Player who) {
        super.onOpen(who);
        ContainerOpenPacket pk = new ContainerOpenPacket();
        pk.windowId = who.getWindowId(this);
        pk.type = type.getNetworkType();
        InventoryHolder holder = this.getHolder();
        if (holder != null) {
            pk.x = (int) ((Vector3) holder).getX();
            pk.y = (int) ((Vector3) holder).getY();
            pk.z = (int) ((Vector3) holder).getZ();
        } else {
            pk.x = pk.y = pk.z = 0;
        }

        if (!who.dataPacket(pk)) return;

        this.sendContents(who);
    }

    @Override
    public void onClose(Player who) {
        if (who.getClosingWindowId() != Integer.MAX_VALUE) {
            ContainerClosePacket pk = new ContainerClosePacket();
            pk.windowId = who.getWindowId(this);
            pk.wasServerInitiated = who.getClosingWindowId() != pk.windowId;
            pk.type = ContainerType.from(this.type.getNetworkType());
            who.dataPacket(pk);
        }

        super.onClose(who);
    }

    @Override
    public void close(Player who) {
        if (!this.usesNativeInputReturn() || !NativeUIItemReturn.isSupported(who)) {
            InventoryCloseEvent ev = new InventoryCloseEvent(this, who);
            who.getServer().getPluginManager().callEvent(ev);
            this.onClose(who);
            return;
        }
        if (this.inputCloseInProgress) return;
        if (InventoryMutationGuard.deferProfileUntilReleased(java.util.Arrays.asList(this, who.getInventory(), who.getUIInventory()),
                this.deferredInputCloseIdentity, () -> this.close(who))) return;
        this.inputCloseInProgress = true;
        try {
            if (!this.closeEventAwaitingInputs) {
                who.getServer().getPluginManager().callEvent(new InventoryCloseEvent(this, who));
                this.closeEventAwaitingInputs = true;
            }
            this.finishNativeInputClose(who);
        } finally {
            this.inputCloseInProgress = false;
        }
    }

    private void finishNativeInputClose(Player who) {
        if (InventoryMutationGuard.deferProfileUntilReleased(java.util.Arrays.asList(this, who.getInventory(), who.getUIInventory()),
                this.deferredInputCloseIdentity, () -> this.finishNativeInputClose(who))) return;
        boolean previousClosing = this.inputCloseInProgress;
        this.inputCloseInProgress = true;
        try {
            NativeUIItemReturn.returnInputs(who, () -> {
                this.returningNativeInputs = true;
                try { this.onClose(who); } finally { this.returningNativeInputs = false; }
            }, this.getClass() == StonecutterInventory.class || this.getClass() == GrindstoneInventory.class
                    || this.getClass() == EnchantInventory.class || this.getClass() == SmithingInventory.class
                    || this.getClass() == CartographyTableInventory.class ? this : null);
        } finally {
            this.closeEventAwaitingInputs = false;
            this.inputCloseInProgress = previousClosing;
        }
    }

    @Override
    public void sendContents(Player... players) {
        for (int slot = 0; slot < getSize(); slot++) {
            sendSlot(slot, players);
        }
    }
}
