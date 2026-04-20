package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.EnchantmentHelper;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantInventory extends FakeBlockUIComponent {

    public static final int ENCHANT_INPUT_ITEM_UI_SLOT = 14;
    public static final int ENCHANT_REAGENT_UI_SLOT = 15;

    /**
     * Enchant option net ids published for the current open session. Cleared on
     * close to keep {@link PlayerEnchantOptionsPacket#RECIPE_MAP} bounded.
     */
    private final Set<Integer> publishedOptionIds = new HashSet<>();

    public EnchantInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.ENCHANT_TABLE, 14, position);
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        who.craftingType = Player.ENCHANT_WINDOW_ID;
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);
        if (this.getViewers().isEmpty()) {
            for (int i = 0; i < 2; ++i) {
                who.getInventory().addItem(this.getItem(i));
                this.clear(i);
            }
        }
        releasePublishedOptions();
        who.craftingType = Player.CRAFTING_SMALL;
        who.resetCraftingGridType();
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        super.onSlotChange(index, before, send);
        if (index != 0) {
            return;
        }
        Item current = this.getItem(0);
        if (current.isNull() || current.hasEnchantments()) {
            sendEmptyOptions();
            releasePublishedOptions();
            return;
        }
        if (before != null && !before.isNull() && before.equals(current, true, true)) {
            return;
        }
        publishOptions(current);
    }

    private void publishOptions(Item input) {
        releasePublishedOptions();
        long seed = System.nanoTime();
        List<PlayerEnchantOptionsPacket.EnchantOptionData> generated = EnchantmentHelper.generateOptions(input, seed);
        if (generated.isEmpty()) {
            sendEmptyOptions();
            return;
        }
        List<PlayerEnchantOptionsPacket.EnchantOptionData> published = new ArrayList<>(generated.size());
        for (PlayerEnchantOptionsPacket.EnchantOptionData option : generated) {
            int netId = PlayerEnchantOptionsPacket.assignRecipeId(option);
            published.add(new PlayerEnchantOptionsPacket.EnchantOptionData(
                    option.getMinLevel(), option.getPrimarySlot(),
                    option.getEnchants0(), option.getEnchants1(), option.getEnchants2(),
                    option.getEnchantName(), netId));
            publishedOptionIds.add(netId);
        }
        PlayerEnchantOptionsPacket pk = new PlayerEnchantOptionsPacket();
        pk.options.addAll(published);
        for (Player viewer : this.getViewers()) {
            viewer.dataPacket(pk);
        }
    }

    private void sendEmptyOptions() {
        if (this.getViewers().isEmpty()) {
            return;
        }
        PlayerEnchantOptionsPacket pk = new PlayerEnchantOptionsPacket();
        for (Player viewer : this.getViewers()) {
            viewer.dataPacket(pk);
        }
    }

    private void releasePublishedOptions() {
        if (publishedOptionIds.isEmpty()) {
            return;
        }
        for (Integer id : publishedOptionIds) {
            PlayerEnchantOptionsPacket.RECIPE_MAP.remove(id.intValue());
        }
        publishedOptionIds.clear();
    }

    public Item getInputSlot() {
        return this.getItem(0);
    }

    public Item getOutputSlot() {
        return this.getItem(0);
    }

    public Item getReagentSlot() {
        return this.getItem(1);
    }
}
