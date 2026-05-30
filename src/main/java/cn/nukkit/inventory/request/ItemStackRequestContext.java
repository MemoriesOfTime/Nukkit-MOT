package cn.nukkit.inventory.request;

import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Per-request processing context shared across all action processors of a single
 * ItemStackRequest. Carries the original request, the current action index and an
 * opaque scratchpad for cross-action state (e.g. the recipe resolved by
 * CraftRecipeAction that CreateAction / CraftResultsDeprecated will consume).
 */
public class ItemStackRequestContext {

    @Getter
    private final ItemStackRequest itemStackRequest;
    @Getter
    @Setter
    private int currentActionIndex;
    private final Map<String, Object> extraData = new HashMap<>();
    private final List<Runnable> commitActions = new ArrayList<>();
    private final Map<Inventory, Map<Integer, Item>> pluginModifiedSlots = new LinkedHashMap<>();

    public ItemStackRequestContext(ItemStackRequest itemStackRequest) {
        this.itemStackRequest = itemStackRequest;
    }

    public void put(String key, Object value) {
        extraData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) extraData.get(key);
    }

    public boolean has(String key) {
        return extraData.containsKey(key);
    }

    public void onCommit(Runnable action) {
        if (action != null) {
            commitActions.add(action);
        }
    }

    public boolean commit() {
        try {
            for (Runnable action : commitActions) {
                action.run();
            }
            commitActions.clear();
            return true;
        } catch (Throwable t) {
            commitActions.clear();
            return false;
        }
    }

    public void addPluginModifiedSlots(Inventory inventory, Map<Integer, Item> slots) {
        Inventory canonical = ItemStackRequestHandler.canonicalizeInventory(inventory);
        if (canonical != null && slots != null && !slots.isEmpty()) {
            Map<Integer, Item> modifiedSlots = this.pluginModifiedSlots
                    .computeIfAbsent(canonical, ignored -> new LinkedHashMap<>());
            for (var entry : slots.entrySet()) {
                Item item = entry.getValue();
                modifiedSlots.put(entry.getKey(), item == null ? Item.get(Item.AIR) : item.clone());
            }
        }
    }

    public Map<Inventory, Map<Integer, Item>> getPluginModifiedSlots() {
        return this.pluginModifiedSlots;
    }

    public ActionResponse error() {
        return ActionResponse.error();
    }

    public ActionResponse success(List<ItemStackResponseContainer> containers) {
        return ActionResponse.ok(containers);
    }

    public ActionResponse success() {
        return ActionResponse.ok(Collections.emptyList());
    }
}
