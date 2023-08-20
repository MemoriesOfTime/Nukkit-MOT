package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.mob.EntitySkeleton;
import cn.nukkit.entity.mob.EntitySpider;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.loot.DungeonChest;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.task.BlockActorSpawnTask;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

public class PopulatorDungeon extends Populator {
	private static final int[] MOBS = {EntitySkeleton.NETWORK_ID, EntityZombie.NETWORK_ID, EntityZombie.NETWORK_ID, EntitySpider.NETWORK_ID};

	@Override
	public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		final int sourceX = chunkX << 4;
		final int sourceZ = chunkZ << 4;

		chance:
		for (int chance = 0; chance < 8; ++chance) {
			final int x = sourceX + random.nextBoundedInt(16) + 8;
			final int y = random.nextBoundedInt(256);
			final int z = sourceZ + random.nextBoundedInt(16) + 8;

			final int xv = random.nextBoundedInt(2) + 2;
			final int x1 = -xv - 1;
			final int x2 = xv + 1;

			final int zv = random.nextBoundedInt(2) + 2;
			final int z1 = -zv - 1;
			final int z2 = zv + 1;

			int t = 0;

			for (int dx = x1; dx <= x2; ++dx) {
				for (int dy = -1; dy <= 4; ++dy) {
					for (int dz = z1; dz <= z2; ++dz) {
						final int tx = x + dx;
						final int ty = y + dy;
						final int tz = z + dz;

						final int id = level.getBlockIdAt(tx, ty, tz);
						final boolean isSolid = Block.solid[id];

						if ((dy == -1 || dy == 4) && !isSolid) {
							continue chance;
						}
						if ((dx == x1 || dx == x2 || dz == z1 || dz == z2) && dy == 0 && level.getBlockIdAt(tx, ty + 1, tz) == BlockID.AIR) {
							++t;
						}
					}
				}
			}

			if (t >= 1 && t <= 5) {
				for (int dx = x1; dx <= x2; ++dx) {
					for (int dy = 3; dy >= -1; --dy) {
						for (int dz = z1; dz <= z2; ++dz) {
							final int tx = x + dx;
							final int ty = y + dy;
							final int tz = z + dz;

							final int id = level.getBlockIdAt(tx, ty, tz);

							if (dx != x1 && dy != -1 && dz != z1 && dx != x2 && dy != 4 && dz != z2) {
								if (id != BlockID.CHEST) {
									level.setBlockAt(tx, ty, tz, BlockID.AIR);
								}
							} else if (ty >= 0 && !Block.solid[level.getBlockIdAt(tx, ty - 1, tz)]) {
								level.setBlockAt(tx, ty, tz, BlockID.AIR);
							} else if (Block.solid[id] && id != BlockID.CHEST) {
								if (dy == -1 && random.nextBoundedInt(4) != 0) {
									level.setBlockAt(tx, ty, tz, BlockID.MOSS_STONE);
								} else {
									level.setBlockAt(tx, ty, tz, BlockID.COBBLESTONE);
								}
							}
						}
					}
				}

				for (int xx = 0; xx < 2; ++xx) {
					for (int zz = 0; zz < 3; ++zz) {
						final int tx = x + random.nextBoundedInt(xv * 2 + 1) - xv;
						final int tz = z + random.nextBoundedInt(zv * 2 + 1) - zv;

						if (level.getBlockIdAt(tx, y, tz) == BlockID.AIR) {
							int n = 0;

							if (Block.solid[level.getBlockIdAt(tx - 1, y, tz)]) {
								++n;
							}
							if (Block.solid[level.getBlockIdAt(tx + 1, y, tz)]) {
								++n;
							}
							if (Block.solid[level.getBlockIdAt(tx, y, tz - 1)]) {
								++n;
							}
							if (Block.solid[level.getBlockIdAt(tx, y, tz + 1)]) {
								++n;
							}

							if (n == 1) {
								level.setBlockAt(tx, y, tz, BlockID.CHEST, 2);
								final CompoundTag chest = BlockEntity.getDefaultCompound(new Vector3(tx, y, tz), BlockEntity.CHEST);
								final ListTag<CompoundTag> items = new ListTag<>("Items");
								DungeonChest.get().create(items, random);
								chest.putList(items);
								Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(), chest));
								break;
							}
						}
					}
				}

				level.setBlockAt(x, y, z, BlockID.MONSTER_SPAWNER);
				Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(),
					BlockEntity.getDefaultCompound(new Vector3(x, y, z), BlockEntity.MOB_SPAWNER)
						.putInt("EntityId", MOBS[random.nextBoundedInt(MOBS.length)])));
			}
		}
	}
}
