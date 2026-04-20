package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.UpdateTradePacket;
import cn.nukkit.utils.TradeRecipeBuildUtils;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

public class TradeInventory extends BaseInventory {

    public static final int TRADE_INPUT1_UI_SLOT = 4;
    public static final int TRADE_INPUT2_UI_SLOT = 5;

    /**
     * Trade recipe network ids allocated for this villager session. Cleared on
     * close to avoid leaking entries in {@link TradeRecipeBuildUtils#RECIPE_MAP}.
     */
    private final Set<Integer> assignedRecipeIds = new HashSet<>();

    public TradeInventory(InventoryHolder holder) {
        super(holder, InventoryType.TRADING);
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        EntityVillager villager = this.getHolder();

        assignRecipeNetIds(villager.getRecipes());

        UpdateTradePacket pk = new UpdateTradePacket();
        pk.windowId = (byte) who.getWindowId(this);
        pk.windowType = (byte) InventoryType.TRADING.getNetworkType();
        pk.size = 0;
        pk.tradeTier = this.getHolder().getTradeTier();
        pk.traderUniqueEntityId = this.getHolder().getId();
        pk.playerUniqueEntityId = who.getId();
        pk.displayName = this.getHolder().getNameTag();

        ListTag<CompoundTag> tierExpRequirements = new ListTag<>("TierExpRequirements");
        for (int i = 0, len = villager.tierExpRequirement.length; i < len; ++i) {
            tierExpRequirements.add(i, new CompoundTag().putInt(String.valueOf(i), villager.tierExpRequirement[i]));
        }

        try {
            pk.offers = NBTIO.write(new CompoundTag()
                    .putList(villager.getRecipes())
                    .putList(tierExpRequirements), ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException ignored) {

        }

        pk.newTradingUi = true;
        pk.usingEconomyTrade = true;

        who.dataPacket(pk);

        this.sendContents(who);
    }

    @Override
    public void onClose(Player who) {
        for (int i = 0; i <= 1; i++) {
            Item item = getItem(i);
            if (who.getInventory().canAddItem(item)) {
                who.getInventory().addItem(item);
            } else {
                who.dropItem(item);
            }
            this.clear(i);
        }

        super.onClose(who);

        this.getHolder().setTradingPlayer(0L);
        releaseAssignedRecipeIds();
    }

    @Override
    public EntityVillager getHolder() {
        return (EntityVillager) this.holder;
    }

    private void assignRecipeNetIds(ListTag<Tag> recipes) {
        if (recipes == null) {
            return;
        }
        releaseAssignedRecipeIds();
        for (Tag tag : recipes.getAll()) {
            if (tag instanceof CompoundTag recipe) {
                int id = TradeRecipeBuildUtils.assignRecipeId(recipe);
                recipe.putInt("netId", id);
                assignedRecipeIds.add(id);
            }
        }
    }

    private void releaseAssignedRecipeIds() {
        if (assignedRecipeIds.isEmpty()) {
            return;
        }
        for (Integer id : assignedRecipeIds) {
            TradeRecipeBuildUtils.RECIPE_MAP.remove(id.intValue());
        }
        assignedRecipeIds.clear();
    }
}
