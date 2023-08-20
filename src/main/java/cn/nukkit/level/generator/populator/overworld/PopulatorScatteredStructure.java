package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.structure.ScatteredStructurePiece;
import cn.nukkit.level.generator.structure.StructureBoundingBox;
import cn.nukkit.level.generator.task.CallbackableScatteredGenerationTask;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

public abstract class PopulatorScatteredStructure extends Populator {
	protected static final int MIN_DISTANCE = 8;
	protected static final int MAX_DISTANCE = 32;

	protected final Map<Long, Set<Long>> waitingChunks = Maps.newConcurrentMap();

	@Override
	public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		if (canGenerate(chunkX, chunkZ, random, chunk)) {
			final ScatteredStructurePiece piece = getPiece(chunkX, chunkZ);
			final StructureBoundingBox boundingBox = piece.getBoundingBox();

			if (boundingBox.getMinChunkX() != boundingBox.getMaxChunkX() || boundingBox.getMinChunkZ() != boundingBox.getMaxChunkZ()) { // cross-chunk
				final Level world = chunk.getProvider().getLevel();
				final Set<BaseFullChunk> chunks = Sets.newHashSet();
				final Set<Long> indexes = Sets.newConcurrentHashSet();

				for (int cX = boundingBox.getMinChunkX(); cX <= boundingBox.getMaxChunkX(); cX++) {
					for (int cZ = boundingBox.getMinChunkZ(); cZ <= boundingBox.getMaxChunkZ(); cZ++) {
						final BaseFullChunk ck = world.getChunk(cX, cZ, true);
						if (!ck.isGenerated()) {
							chunks.add(ck);
							indexes.add(Level.chunkHash(cX, cZ));
						}
					}
				}

				if (!chunks.isEmpty()) {
					waitingChunks.put(Level.chunkHash(chunkX, chunkZ), indexes);
					chunks.forEach(ck -> Server.getInstance().getScheduler().scheduleAsyncTask(new CallbackableScatteredGenerationTask(world, ck, this, piece, level, chunkX, chunkZ)));
					return;
				}
			}

			generate(level, chunkX, chunkZ, piece);
		}
	}

	protected void generate(final ChunkManager level, final int chunkX, final int chunkZ, final ScatteredStructurePiece piece) {
		piece.generate(level, new NukkitRandom(0xdeadbeef ^ (long) chunkX << 8 ^ chunkZ ^ level.getSeed()));
		waitingChunks.remove(Level.chunkHash(chunkX, chunkZ));
	}

	public void generateChunkCallback(final ChunkManager level, final int startChunkX, final int startChunkZ, final ScatteredStructurePiece piece, final int chunkX, final int chunkZ) {
		final Set<Long> indexes = waitingChunks.get(Level.chunkHash(startChunkX, startChunkZ));
		indexes.remove(Level.chunkHash(chunkX, chunkZ));
		if (indexes.isEmpty()) {
			generate(level, startChunkX, startChunkZ, piece);
		}
	}

	protected boolean canGenerate(final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		return (chunkX < 0 ? (chunkX - MAX_DISTANCE - 1) / MAX_DISTANCE : chunkX / MAX_DISTANCE) * MAX_DISTANCE + random.nextBoundedInt(MAX_DISTANCE - MIN_DISTANCE) == chunkX && (chunkZ < 0 ? (chunkZ - MAX_DISTANCE - 1) / MAX_DISTANCE : chunkZ / MAX_DISTANCE) * MAX_DISTANCE + random.nextBoundedInt(MAX_DISTANCE - MIN_DISTANCE) == chunkZ;
	}

	protected BlockVector3 getStart(final int chunkX, final int chunkZ) {
		return new BlockVector3(chunkX << 4, 64, chunkZ << 4);
	}

	protected abstract ScatteredStructurePiece getPiece(int chunkX, int chunkZ);
}
