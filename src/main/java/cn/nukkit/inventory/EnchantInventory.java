package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.event.player.PlayerEnchantOptionsRequestEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.EnchantmentHelper;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantInventory extends FakeBlockUIComponent {
    public static final int ENCHANT_INPUT_ITEM_UI_SLOT = 14;
    public static final int ENCHANT_REAGENT_UI_SLOT = 15;

    @Getter
    private List<PlayerEnchantOptionsPacket.EnchantOptionData> options = Collections.emptyList();

    public EnchantInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.ENCHANT_TABLE, 14, position);
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        who.craftingType = Player.CRAFTING_ENCHANT;
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
        who.craftingType = Player.CRAFTING_SMALL;
        who.resetCraftingGridType();
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        if (index == 0) {
            for (final Player viewer : this.getViewers()) {
                List<PlayerEnchantOptionsPacket.EnchantOptionData> options = EnchantmentHelper.getEnchantOptions(this.getHolder(), this.getInputSlot(), viewer.getEnchantmentSeed());
                PlayerEnchantOptionsRequestEvent event = new PlayerEnchantOptionsRequestEvent(viewer, this, options);

                if (!event.isCancelled() && !event.getOptions().isEmpty()) {
                    this.options = event.getOptions();

                    PlayerEnchantOptionsPacket pk = new PlayerEnchantOptionsPacket();
                    pk.options = event.getOptions();
                    viewer.dataPacket(pk);
                }
            }
        }

        super.onSlotChange(index, before, false);
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
