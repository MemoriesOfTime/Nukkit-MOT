package cn.nukkit.event.inventory;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.inventory.request.ActionResponse;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;

/**
 * Called before an ItemStackRequest action is processed.
 * Allows plugins to cancel or provide a custom response.
 */
public class ItemStackRequestActionEvent extends InventoryEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private final Player player;
    private final ItemStackRequestAction action;
    private final int actionIndex;
    private ActionResponse response;

    public ItemStackRequestActionEvent(Player player, ItemStackRequestAction action, int actionIndex) {
        super(null);
        this.player = player;
        this.action = action;
        this.actionIndex = actionIndex;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStackRequestAction getAction() {
        return action;
    }

    public int getActionIndex() {
        return actionIndex;
    }

    public void setResponse(ActionResponse response) {
        this.response = response;
    }

    public ActionResponse getResponse() {
        return response;
    }
}
