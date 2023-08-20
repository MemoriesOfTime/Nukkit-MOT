package cn.nukkit.level.generator.task;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.scheduler.Task;

public class LootSpawnTask extends Task {
	private final Level level;
	private final BlockVector3 pos;
	private final ListTag<CompoundTag> list;

	public LootSpawnTask(final Level level, final BlockVector3 pos, final ListTag<CompoundTag> list) {
		this.level = level;
		this.pos = pos;
		this.list = list;
	}

	@Override
	public void onRun(final int currentTick) {
		final BlockEntity tile = level.getBlockEntity(pos.asVector3());
		if (tile instanceof InventoryHolder) {
			tile.namedTag.putList(list);
			final Inventory inventory = ((InventoryHolder) tile).getInventory();
			for (int i = 0; i < list.size(); i++) {
				inventory.setItem(i, NBTIO.getItemHelper(list.get(i)), false);
			}
		}
	}
}
