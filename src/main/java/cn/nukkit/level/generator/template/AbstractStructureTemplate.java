package cn.nukkit.level.generator.template;

import cn.nukkit.math.BlockVector3;

public abstract class AbstractStructureTemplate implements StructureTemplate {
	protected BlockVector3 size = new BlockVector3();

	@Override
	public BlockVector3 getSize() {
		return size;
	}

	@Override
	public boolean isInvalid() {
		return size.x < 1 || size.y < 1 || size.z < 1;
	}
}
