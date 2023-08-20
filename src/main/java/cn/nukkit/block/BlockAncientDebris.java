package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockAncientDebris extends BlockSolid {
	@Override
	public String getName() {
		return "Ancient Debris";
	}

	@Override
	public int getId() {
		return ANCIENT_DEBRIS;
	}

	@Override
	public double getHardness() {
		return 30;
	}

	@Override
	public double getResistance() {
		return 1200;
	}

	@Override
	public int getToolType() {
		return ItemTool.TYPE_PICKAXE;
	}

	@Override
	public Item[] getDrops(final Item item) {
		if (item.isPickaxe()) {
			return new Item[]{new ItemBlock(this, 0, 1)};
		}
		return Item.EMPTY_ARRAY;
	}

	@Override
	public boolean canHarvestWithHand() {
		return false;
	}

	@Override
	public int getToolTier() {
		return ItemTool.TIER_DIAMOND;
	}
}
