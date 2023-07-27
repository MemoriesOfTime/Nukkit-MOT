package cn.nukkit.inventory.transaction.action;

import cn.nukkit.Player;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
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
			var result1 = false;
			var result2 = false;
			for (var tag : villager.getRecipes().getAll()) {
				var cmp = (CompoundTag) tag;
				if (cmp.containsCompound("buyA")) {
					var buyA = cmp.getCompound("buyA");
					result1 = buyA.getByte("Count") == targetItem.getCount() && buyA.getByte("Damage") == targetItem.getDamage()
							&& buyA.getShort("id") == targetItem.getId();
					if (targetItem.hasCompoundTag()) {
						result1 = simpleVerifyCompoundTag(targetItem.getNamedTag(), buyA.getCompound("tag"));
					}
				}
				if (cmp.containsCompound("buyB")) {
					var buyB = cmp.getCompound("buyB");
					result2 = buyB.getByte("Count") == targetItem.getCount() && buyB.getByte("Damage") == targetItem.getDamage()
							&& buyB.getShort("id") == targetItem.getId();
					if (targetItem.hasCompoundTag()) {
						result2 = simpleVerifyCompoundTag(targetItem.getNamedTag(), buyB.getCompound("tag"));
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
				var cmp = (CompoundTag) tag;
				if (cmp.contains("sell")) {
					var sell = cmp.getCompound("sell");
					result = sell.getByte("Count") == sourceItem.getCount() && sell.getByte("Damage") == sourceItem.getDamage()
							&& sell.getShort("id") == sourceItem.getId();
					if (sourceItem.hasCompoundTag()) {
						result = simpleVerifyCompoundTag(sourceItem.getNamedTag(), sell.getCompound("tag"));
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
		var result = false;
		//如果有附魔 比较附魔
		if (nbt1.contains("ench") && nbt2.contains("ench")) {
			result = nbt1.get("ench").equals(nbt2.get("ench"));
		}
		//如果有改名 比较改名
		if (nbt1.contains("display") && nbt2.contains("display")) {
			result = nbt1.get("display").equals(nbt2.get("display"));
		}
		return result;
	}

}
