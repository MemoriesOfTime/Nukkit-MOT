package cn.nukkit.block;

public class BlockSoulLantern extends BlockLantern {
	@Override
	public int getId() {
		return SOUL_LANTERN;
	}

	@Override
	public String getName() {
		return "Soul Lantern";
	}

	@Override
	public int getLightLevel() {
		return 10;
	}
}
