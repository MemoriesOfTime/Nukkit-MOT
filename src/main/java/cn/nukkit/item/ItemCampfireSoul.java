package cn.nukkit.item;

import cn.nukkit.block.BlockCampfireSoul;

public class ItemCampfireSoul extends Item {
	public ItemCampfireSoul() {
		this(0, 1);
	}

	public ItemCampfireSoul(final int meta) {
		this(meta, 1);
	}

	public ItemCampfireSoul(final int meta, final int count) {
		super(SOUL_CAMPFIRE, meta, count, "Soul Campfire");
		block = new BlockCampfireSoul();
	}
}
