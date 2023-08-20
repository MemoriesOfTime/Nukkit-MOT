package cn.nukkit.level.generator.task;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.scheduler.Task;

public class BlockActorSpawnTask extends Task {
	private final Level level;
	private final CompoundTag nbt;

	public BlockActorSpawnTask(final Level level, final CompoundTag nbt) {
		this.level = level;
		this.nbt = nbt;
	}

	@Override
	public final void onRun(final int currentTick) {
		BlockEntity.createBlockEntity(nbt.getString("id"), level.getChunk(nbt.getInt("x") >> 4, nbt.getInt("z") >> 4), nbt);
	}
}
