package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.jetbrains.annotations.Nullable;

public interface ItemStackRequestActionProcessor<T extends ItemStackRequestAction> {

    ItemStackRequestActionType getType();

    @Nullable
    ActionResponse handle(T action, Player player, ItemStackRequestContext context);

    /**
     * Validate that the client-reported stack network id matches the server's
     * current id for that slot. Returns {@code true} when a mismatch is detected
     * (i.e. the caller should reject the action).
     * <p>
     * Either side being non-positive means "no id to compare against" and skips
     * validation: {@code serverNetId <= 0} when the server has not allocated a
     * stackNetId for that slot yet (e.g. items freshly loaded from NBT or
     * produced by the legacy InventoryTransaction path), and {@code clientNetId
     * <= 0} when the client defers to server state (typically for follow-up
     * actions in a chain that share the same slot).
     */
    default boolean validateStackNetworkId(int serverNetId, int clientNetId) {
        return serverNetId > 0 && clientNetId > 0 && serverNetId != clientNetId;
    }
}
