package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.UpdateTradePacket;

import java.io.IOException;
import java.nio.ByteOrder;

public class TradeInventory extends BaseInventory {
    
    public static final int TRADE_INPUT1_UI_SLOT = 4;
    public static final int TRADE_INPUT2_UI_SLOT = 5;
    
    public TradeInventory(InventoryHolder holder) {
        super(holder, InventoryType.TRADING);
    }
    
    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        EntityVillager villager = this.getHolder();
        
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
    }
    
    @Override
    public EntityVillager getHolder() {
        return (EntityVillager) this.holder;
    }
}
