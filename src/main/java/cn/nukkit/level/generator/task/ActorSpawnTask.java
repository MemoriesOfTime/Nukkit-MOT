package cn.nukkit.level.generator.task;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.scheduler.Task;

public class ActorSpawnTask extends Task {
    private final Level level;
    private final CompoundTag nbt;

    public ActorSpawnTask(final Level level, final CompoundTag nbt) {
        this.level = level;
        this.nbt = nbt;
    }

    @Override
    public void onRun(final int currentTick) {
        final ListTag<DoubleTag> pos = nbt.getList("Pos", DoubleTag.class);
        final Entity entity = Entity.createEntity(nbt.getString("id"), level.getChunk((int) pos.get(0).data >> 4, (int) pos.get(2).data >> 4), nbt);
        if (entity != null) {
            entity.spawnToAll();
        }
    }
}
