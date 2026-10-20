package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class AnvilInventory extends FakeBlockUIComponent {

    public static final int ANVIL_INPUT_UI_SLOT = 1;
    public static final int ANVIL_MATERIAL_UI_SLOT = 2;
    public static final int ANVIL_OUTPUT_UI_SLOT = CREATED_ITEM_OUTPUT_UI_SLOT;

    public static final int TARGET = 0;
    public static final int SACRIFICE = 1;
    public static final int RESULT = ANVIL_OUTPUT_UI_SLOT - 1; //1: offset

    private int cost;
    private final Object deferredCloseIdentity = new Object();
    private final Object deferredCleanupIdentity = new Object();
    private boolean cleanupCompleted;

    public AnvilInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.ANVIL, 1, position);
    }

    @Override
    public void close(Player who) {
        if (InventoryMutationGuard.deferUntilReleased(
                java.util.Arrays.asList(this, who.getInventory(), who.getUIInventory()),
                this.deferredCloseIdentity, () -> this.close(who))) return;
        super.close(who);
    }

    @Override
    public void onClose(Player who) {
        if (this.cleanupCompleted) return;
        if (this.isReturningNativeInputs()) {
            this.completeClose(who);
            super.onClose(who);
            return;
        }
        if (InventoryMutationGuard.deferProfileUntilReleased(
                java.util.Arrays.asList(this, who.getInventory(), who.getUIInventory()),
                this.deferredCleanupIdentity, () -> this.onClose(who))) return;
        // Keep ownership through cleanup and the close packet's mapped window identity.
        try (InventoryMutationGuard.Lease close = InventoryMutationGuard.tryClaimProfile(
                java.util.Arrays.asList(this, who.getInventory(), who.getOffhandInventory(), who.getEnderChestInventory(), who.getUIInventory()))) {
            if (close == null) return;
            if (NativeUIItemReturn.isSupported(who) && !NativeUIItemReturn.returnInputs(who, close)) return;
            this.completeClose(who);
            super.onClose(who);
        }
    }

    private void completeClose(Player who) {
        if (this.cleanupCompleted) return;
        this.cleanupCompleted = true;
        who.craftingType = Player.CRAFTING_SMALL;
        who.resetCraftingGridType();

        // Like every other station: back into the backpack, only the overflow on the ground,
        // and nothing at all in creative, where the inputs are free items.
        who.returnUiItems(this.getItem(TARGET), this.getItem(SACRIFICE));
        this.clear(TARGET);
        this.clear(SACRIFICE);
    }

    @Override
    public void onOpen(Player who) {
        this.cleanupCompleted = false;
        super.onOpen(who);
        who.craftingType = Player.ANVIL_WINDOW_ID;
    }

    public Item getInputSlot() {
        return this.getItem(TARGET);
    }

    public Item getMaterialSlot() {
        return this.getItem(SACRIFICE);
    }

    public Item getOutputSlot() {
        return this.getItem(RESULT);
    }

    public int getCost() {
        return this.cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }
}
