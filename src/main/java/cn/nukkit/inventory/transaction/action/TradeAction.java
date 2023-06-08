package cn.nukkit.inventory.transaction.action;

import cn.nukkit.Player;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.item.Item;
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
			//TODO 验证配方
		} else if (type == NetworkInventoryAction.SOURCE_TYPE_TRADING_OUTPUT) {
			//TODO 验证配方
		}
		return source.getTradeInventory() != null;
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

}
