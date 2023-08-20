package cn.nukkit.level.generator.task;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;

import java.util.List;

public class ChunkPopulationTask extends Thread {
	private final ChunkManager level;
	private final FullChunk chunk;
	private final List<Populator> populators;

	public ChunkPopulationTask(final ChunkManager level, final FullChunk chunk, final List<Populator> populators) {
		this.level = level;
		this.chunk = chunk;
		this.populators = populators;
	}

	@Override
	public final void run() {
		final int chunkX = chunk.getX();
		final int chunkZ = chunk.getZ();
		final NukkitRandom random = new NukkitRandom(0xdeadbeef ^ (long) chunkX << 8 ^ chunkZ ^ level.getSeed());

		populators.forEach(populator -> populator.populate(level, chunkX, chunkZ, random, chunk));
	}
}
