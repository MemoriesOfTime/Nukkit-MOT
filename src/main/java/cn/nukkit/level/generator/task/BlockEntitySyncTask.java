package cn.nukkit.level.generator.task;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.scheduler.Task;

public class BlockEntitySyncTask extends Task {
    public final String type;
    public final FullChunk chunk;
    public final CompoundTag nbt;

    public BlockEntitySyncTask(final String type, final FullChunk chunk, final CompoundTag nbt) {
        this.type = type;
        this.chunk = chunk;
        this.nbt = nbt;
    }

    @Override
    public void onRun(final int currentTick) {
        BlockEntity.createBlockEntity(type, chunk, nbt);
    }
}
