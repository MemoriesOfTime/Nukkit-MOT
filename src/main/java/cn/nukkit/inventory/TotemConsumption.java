package cn.nukkit.inventory;

import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.Arrays;
import java.util.Objects;

/** One explicit native decrement, never shared with reentrant public inventory writes. */
final class TotemConsumption {
    private final BaseInventory inventory;
    private final Item selected;
    private final Item before;
    private final byte[] bytes;
    private final CompoundTag tag;
    private boolean observed;
    private boolean confirmed;

    TotemConsumption(BaseInventory inventory, Item selected, Item before) {
        this.inventory = inventory;
        this.selected = selected;
        this.before = before.clone();
        this.bytes = this.before.getCompoundTag();
        this.tag = this.before.getNamedTagSnapshot();
    }

    void committed(Item previous, Item next, Item overflow) {
        if (observed) return;
        observed = true;
        // A changed live identity or metadata cannot be attributed to the selected totem.
        if (!InventoryMutationGuard.canConfirmSlotMutation(inventory)
                || previous != selected || !same(previous, before.getCount()) || overflow != null) return;
        confirmed = before.getCount() == 1 ? next == null : same(next, before.getCount() - 1);
    }

    private boolean same(Item item, int count) {
        return item != null && item.getId() == Item.TOTEM && item.getDamage() == before.getDamage()
                && item.getCount() == count && item.getStackNetId() == before.getStackNetId()
                && Arrays.equals(item.getCompoundTag(), bytes)
                && Objects.equals(item.getNamedTagSnapshot(), tag);
    }

    Item consumed() {
        if (!confirmed) return null;
        Item result = before.clone();
        result.setCount(1);
        return result;
    }
}
