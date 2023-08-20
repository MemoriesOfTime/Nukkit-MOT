package cn.nukkit.level.generator.template;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import com.google.common.collect.Lists;

import java.util.Iterator;
import java.util.List;

public abstract class AbstractLegacyStructureTemplate extends AbstractStructureTemplate {
	protected final List<StructureBlockInfo> blockInfoList = Lists.newArrayList();
	protected final List<StructureEntityInfo> entityInfoList = Lists.newArrayList();

	@Override
	public boolean isInvalid() {
		return blockInfoList.isEmpty() && entityInfoList.isEmpty() || super.isInvalid();
	}

	@Override
	public void clean() {
		blockInfoList.clear();
		entityInfoList.clear();
	}

	protected static class SimplePalette implements Iterable<BlockEntry> {

		public static final BlockEntry DEFAULT_BLOCK_STATE = new BlockEntry(0);

		private final IdMapper<BlockEntry> ids;

		protected SimplePalette() {
			ids = new IdMapper<>();
		}

		@Override
		public Iterator<BlockEntry> iterator() {
			return ids.iterator();
		}

		public BlockEntry stateFor(final int id) {
			final BlockEntry block = ids.byId(id);
			return block == null ? DEFAULT_BLOCK_STATE : block;
		}

		public void addMapping(final BlockEntry block, final int id) {
			ids.addMapping(block, id);
		}
	}

	public static class StructureBlockInfo {
		public final BlockVector3 pos;
		public final BlockEntry state;
		public final CompoundTag nbt;

		public StructureBlockInfo(final BlockVector3 pos, final BlockEntry state, final CompoundTag nbt) {
			this.pos = pos;
			this.state = state;
			this.nbt = nbt;
		}
	}

	public static class StructureEntityInfo {
		public final Vector3 pos;
		public final BlockVector3 blockPos;
		public final CompoundTag nbt;

		public StructureEntityInfo(final Vector3 pos, final BlockVector3 blockPos, final CompoundTag nbt) {
			this.pos = pos;
			this.blockPos = blockPos;
			this.nbt = nbt;
		}
	}
}
