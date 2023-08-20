package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;

public class PopulatorGlowStone extends Populator {
	@Override
	public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		if (random.nextBoundedInt(10) == 0) {
			final int x = NukkitMath.randomRange(random, chunkX << 4, (chunkX << 4) + 15);
			final int z = NukkitMath.randomRange(random, chunkZ << 4, (chunkZ << 4) + 15);
			final int y = getHighestWorkableBlock(chunk, x & 0xF, z & 0xF);
			if (y != -1 && level.getBlockIdAt(x, y, z) != BlockID.NETHERRACK) {
				int count = NukkitMath.randomRange(random, 40, 60);
				level.setBlockAt(x, y, z, BlockID.GLOWSTONE);
				int cyclesNum = 0;
				while (count != 0) {
					if (cyclesNum == 1500) break;
					final int spawnX = x + random.nextBoundedInt(8) - random.nextBoundedInt(8);
					final int spawnY = y - random.nextBoundedInt(5);
					final int spawnZ = z + random.nextBoundedInt(8) - random.nextBoundedInt(8);
					if (cyclesNum % 128 == 0 && cyclesNum != 0) {
						level.setBlockAt(x + random.nextRange(-3, 3), y - random.nextBoundedInt(4), z + random.nextRange(-3, 3), BlockID.GLOWSTONE);
						count--;
					}
					if (checkAroundBlock(spawnX, spawnY, spawnZ, level)) {
						level.setBlockAt(spawnX, spawnY, spawnZ, BlockID.GLOWSTONE);
						count--;
					}
					cyclesNum++;
				}
			}
		}
	}

	private int getHighestWorkableBlock(final FullChunk chunk, final int x, final int z) {
		int y;
		for (y = 125; y >= 0; y--) {
			final int b = chunk.getBlockId(x, y, z);
			if (b == Block.AIR) {
				break;
			}
		}
		return y == 0 ? -1 : y;
	}

	private boolean checkAroundBlock(final int x, final int y, final int z, final ChunkManager level) {
		for (final BlockFace i : BlockFace.values()) {
			if (level.getBlockIdAt(x + i.getXOffset(), y + i.getYOffset(), z + i.getZOffset()) == BlockID.GLOWSTONE) {
				return true;
			}
		}
		return false;
	}
}
