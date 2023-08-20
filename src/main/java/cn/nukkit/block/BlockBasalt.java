package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockBasalt extends BlockSolidMeta {
	public BlockBasalt() {
		super(0);
	}

	public BlockBasalt(final int meta) {
		super(meta);
	}

	@Override
	public double getHardness() {
		return 1.25;
	}

	@Override
	public double getResistance() {
		return 4.2;
	}

	@Override
	public int getToolType() {
		return ItemTool.TYPE_PICKAXE;
	}

	@Override
	public int getToolTier() {
		return ItemTool.TIER_WOODEN;
	}

	@Override
	public String getName() {
		return "Basalt";
	}

	@Override
	public int getId() {
		return BASALT;
	}
}
