package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;

import java.util.LinkedHashSet;
import java.util.Set;

public final class InventoryObserverSync {

    private InventoryObserverSync() {
    }

    public static void syncOtherViewers(Player actor, Inventory inventory) {
        if (inventory == null) {
            return;
        }

        Set<Player> viewers = inventory.getViewers();
        if (viewers == null || viewers.isEmpty()) {
            return;
        }

        LinkedHashSet<Player> observers = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            if (viewer != null && viewer != actor) {
                observers.add(viewer);
            }
        }

        if (!observers.isEmpty()) {
            inventory.sendContents(observers);
        }
    }

    /**
     * Resend a single slot of an inventory to every player who can observe it:
     * the actor, the inventory holder (if a Player), and all current viewers.
     * Used to propagate bundle content changes up to the outer inventory slot so
     * the freshly serialised storage_item_component_content NBT reaches every
     * client that sees the bundle item.
     */
    public static void resendOuterSlot(Player actor, Inventory outer, int slot) {
        if (outer == null) {
            return;
        }
        if (slot < 0 || slot >= outer.getSize()) {
            return;
        }

        LinkedHashSet<Player> targets = new LinkedHashSet<>();
        if (actor != null) {
            targets.add(actor);
        }

        InventoryHolder holder = outer.getHolder();
        if (holder instanceof Player holderPlayer) {
            targets.add(holderPlayer);
        }

        Set<Player> viewers = outer.getViewers();
        if (viewers != null) {
            for (Player viewer : viewers) {
                if (viewer != null) {
                    targets.add(viewer);
                }
            }
        }

        if (!targets.isEmpty()) {
            outer.sendSlot(slot, targets);
        }
    }
}
