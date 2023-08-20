package cn.nukkit.level.generator.structure;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.math.NukkitRandom;
import com.google.common.collect.Lists;

import java.util.List;

public abstract class StructureStart {
	protected final ChunkManager level;
	protected final List<StructurePiece> pieces = Lists.newArrayList();
	protected final NukkitRandom random;
	private final int chunkX;
	private final int chunkZ;
	protected BoundingBox boundingBox;

	public StructureStart(final ChunkManager level, final int chunkX, final int chunkZ) {
		this.level = level;
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
		random = new NukkitRandom(level.getSeed());
		random.setSeed((long) chunkX * random.nextInt() ^ (long) chunkZ * random.nextInt() ^ level.getSeed());
		boundingBox = BoundingBox.getUnknownBox();
	}

	public abstract void generatePieces(ChunkManager level, int chunkX, int chunkZ);

	public BoundingBox getBoundingBox() {
		return boundingBox;
	}

	public List<StructurePiece> getPieces() {
		return pieces;
	}

	public void postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
		synchronized (pieces) {
			pieces.removeIf(piece -> piece.getBoundingBox().intersects(boundingBox) && !piece.postProcess(level, random, boundingBox, chunkX, chunkZ));
			calculateBoundingBox();
		}
	}

	protected void calculateBoundingBox() {
		boundingBox = BoundingBox.getUnknownBox();
		for (final StructurePiece piece : pieces) {
			boundingBox.expand(piece.getBoundingBox());
		}
	}

	protected void moveBelowSeaLevel(final int max, final NukkitRandom random, final int min) {
		final int range = max - min;
		int y = boundingBox.getYSpan() + 1;
		if (y < range) {
			y += random.nextBoundedInt(range - y);
		}

		final int offset = y - boundingBox.y1;
		boundingBox.move(0, offset, 0);

		for (final StructurePiece piece : pieces) {
			piece.move(0, offset, 0);
		}
	}

	protected void moveInsideHeights(final NukkitRandom random, final int min, final int max) {
		final int range = max - min + 1 - boundingBox.getYSpan();
		final int y;
		if (range > 1) {
			y = min + random.nextBoundedInt(range);
		} else {
			y = min;
		}

		final int offset = y - boundingBox.y0;
		boundingBox.move(0, offset, 0);

		for (final StructurePiece piece : pieces) {
			piece.move(0, offset, 0);
		}
	}

	public boolean isValid() {
		return !pieces.isEmpty();
	}

	public int getChunkX() {
		return chunkX;
	}

	public int getChunkZ() {
		return chunkZ;
	}

	public abstract String getType();
}
