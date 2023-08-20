package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;

public class BlockCampfireSoul extends BlockCampfire {
	public BlockCampfireSoul() {
		super(0);
	}

	public BlockCampfireSoul(final int meta) {
		super(meta);
	}

	@Override
	public int getId() {
		return SOUL_CAMPFIRE_BLOCK;
	}

	@Override
	public int getLightLevel() {
		return isExtinguished() ? 0 : 10;
	}

	@Override
	public Item[] getDrops(final Item item) {
		return new Item[]{Item.get(SOUL_SOIL)};
	}

	@Override
	public String getName() {
		return "Soul Campfire";
	}

	@Override
	public Item toItem() {
		return Item.get(ItemID.SOUL_CAMPFIRE);
	}
}
