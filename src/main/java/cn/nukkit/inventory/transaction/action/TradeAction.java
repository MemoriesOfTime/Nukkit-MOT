package cn.nukkit.inventory.transaction.action;

import cn.nukkit.Player;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;

public class TradeAction extends InventoryAction {

	private final EntityVillager villager;
	private final int type;

	public TradeAction(Item sourceItem, Item targetItem, int windowId, EntityVillager villager) {
		super(sourceItem, targetItem);
		this.type = windowId;
		this.villager = villager;
	}

	@Override
	public boolean isValid(Player source) {
		if (type == NetworkInventoryAction.SOURCE_TYPE_TRADING_INPUT_1) {
			boolean result1 = false;
			boolean result2 = false;
			for (var tag : villager.getRecipes().getAll()) {
				CompoundTag cmp = (CompoundTag) tag;
				if (cmp.containsCompound("buyA")) {
					CompoundTag buyA = cmp.getCompound("buyA");
					result1 = buyA.getByte("Count") == targetItem.getCount()
							&& buyA.getByte("Damage") == targetItem.getDamage()
							&& buyA.getShort("id") == targetItem.getId()
							&& buyA.getString("Name").equals(targetItem.getNamespaceId());
					if (targetItem.hasCompoundTag()) {
						result1 = result1 && simpleVerifyCompoundTag(targetItem.getNamedTag(), buyA.getCompound("tag"));
					}
				}
				if (cmp.containsCompound("buyB")) {
					CompoundTag buyB = cmp.getCompound("buyB");
					result2 = buyB.getByte("Count") == targetItem.getCount()
							&& buyB.getByte("Damage") == targetItem.getDamage()
							&& buyB.getShort("id") == targetItem.getId()
							&& buyB.getString("Name").equals(targetItem.getNamespaceId());
					if (targetItem.hasCompoundTag()) {
						result2 = result2 && simpleVerifyCompoundTag(targetItem.getNamedTag(), buyB.getCompound("tag"));
					}
				}
				if (result1 || result2) {
					return true;
				}
			}
			return false;
		} else if (type == NetworkInventoryAction.SOURCE_TYPE_TRADING_OUTPUT) {
			var result = false;
			for (var tag : villager.getRecipes().getAll()) {
				CompoundTag cmp = (CompoundTag) tag;
				if (cmp.contains("sell")) {
					var sell = cmp.getCompound("sell");
					result = sell.getByte("Count") == sourceItem.getCount()
							&& sell.getByte("Damage") == sourceItem.getDamage()
							&& sell.getShort("id") == sourceItem.getId()
							&& sell.getString("Name").equals(sourceItem.getNamespaceId());
					if (sourceItem.hasCompoundTag()) {
						result = result && simpleVerifyCompoundTag(sourceItem.getNamedTag(), sell.getCompound("tag"));
					}
				}
				if (result) {
					return true;
				}
			}
			return false;
		}
		return false;
	}

	@Override
	public boolean execute(Player source) {
		return true;
	}

	@Override
	public void onExecuteSuccess(Player source) {

	}

	@Override
	public void onExecuteFail(Player source) {

	}

	private boolean simpleVerifyCompoundTag(CompoundTag nbt1, CompoundTag nbt2) {
		//如果有附魔 比较附魔
		if (nbt1.contains("ench") || nbt2.contains("ench")) {
			Tag ench1 = nbt1.get("ench");
			if (ench1 == null || !ench1.equals(nbt2.get("ench"))) {
				return false;
			}
		}
		//如果有改名 比较改名
		if (nbt1.contains("display") || nbt2.contains("display")) {
			Tag display1 = nbt1.get("display");
			if (display1 == null || !display1.equals(nbt2.get("display"))) {
				return false;
			}
		}
		return true;
	}

}
