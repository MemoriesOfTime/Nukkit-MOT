package cn.nukkit.level.generator.structure;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.block.BlockTypes;
import cn.nukkit.level.generator.block.state.BlockState;
import cn.nukkit.level.generator.block.state.Direction;
import cn.nukkit.level.generator.block.state.UpperBlockBit;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.level.generator.math.Rotation;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;

import java.util.Iterator;
import java.util.List;

public abstract class StructurePiece {
	protected final int genDepth;
	protected ChunkManager level;
	protected BoundingBox boundingBox;
	private BlockFace orientation;
	private Rotation rotation = Rotation.NONE;

	protected StructurePiece(final int genDepth) {
		this.genDepth = genDepth;
	}

	public static StructurePiece findCollisionPiece(final List<StructurePiece> pieces, final BoundingBox boundingBox) {
		final Iterator<StructurePiece> iterator = pieces.iterator();

		StructurePiece piece;
		do {
			if (!iterator.hasNext()) {
				return null;
			}

			piece = iterator.next();
		} while (piece.getBoundingBox() == null || !piece.getBoundingBox().intersects(boundingBox));

		return piece;
	}

	public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
		// NOOP
	}

	public abstract boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ);

	public BoundingBox getBoundingBox() {
		return boundingBox;
	}

	public int getGenDepth() {
		return genDepth;
	}

	protected int getWorldX(final int x, final int z) {
		final BlockFace orientation = getOrientation();
		if (orientation == null) {
			return x;
		}
		return switch (orientation) {
			case NORTH, SOUTH -> boundingBox.x0 + x;
			case WEST -> boundingBox.x1 - z;
			case EAST -> boundingBox.x0 + z;
			default -> x;
		};
	}

	protected int getWorldY(final int y) {
		return getOrientation() == null ? y : y + boundingBox.y0;
	}

	protected int getWorldZ(final int x, final int z) {
		final BlockFace orientation = getOrientation();
		if (orientation == null) {
			return z;
		}
		return switch (orientation) {
			case NORTH -> boundingBox.z1 - z;
			case SOUTH -> boundingBox.z0 + z;
			case WEST, EAST -> boundingBox.z0 + x;
			default -> z;
		};
	}

	protected void placeBlock(final ChunkManager level, BlockState block, final int x, final int y, final int z, final BoundingBox boundingBox) {
		final BlockVector3 vec = new BlockVector3(getWorldX(x, z), getWorldY(y), getWorldZ(x, z));
		if (boundingBox.isInside(vec)) {
			if (rotation != Rotation.NONE) {
				block = block.rotate(rotation);
			}

			level.setBlockAt(vec.x, vec.y, vec.z, block.getId(), block.getMeta());
		}
	}

	protected void generateDoor(final ChunkManager level, final BoundingBox boundingBox, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final BlockState door) {
		switch (orientation) {
			case SOUTH:
				placeBlock(level, new BlockState(door.getId(), Direction.SOUTH), x, y, z, boundingBox);
			case EAST:
				placeBlock(level, new BlockState(door.getId(), Direction.EAST), x, y, z, boundingBox);
			case WEST:
				placeBlock(level, new BlockState(door.getId(), Direction.WEST), x, y, z, boundingBox);
			default:
				placeBlock(level, new BlockState(door.getId(), Direction.NORTH), x, y, z, boundingBox);
		}
		placeBlock(level, new BlockState(door.getId(), UpperBlockBit.UPPER), x, y + 1, z, boundingBox);
	}

	protected BlockState getBlock(final ChunkManager level, final int x, final int y, final int z, final BoundingBox boundingBox) {
		final BlockVector3 vec = new BlockVector3(getWorldX(x, z), getWorldY(y), getWorldZ(x, z));
		return !boundingBox.isInside(vec) ? BlockState.AIR : new BlockState(level.getBlockIdAt(vec.x, vec.y, vec.z), level.getBlockDataAt(vec.x, vec.y, vec.z));
	}

	protected boolean isInterior(final ChunkManager level, final int x, final int y, final int z, final BoundingBox boundingBox) {
		final int worldX = getWorldX(x, z);
		final int worldY = getWorldY(y + 1);
		final int worldZ = getWorldZ(x, z);
		if (!boundingBox.isInside(new BlockVector3(worldX, worldY, worldZ))) {
			return false;
		}
		final BaseFullChunk chunk = level.getChunk(worldX >> 4, worldZ >> 4);
		if (chunk == null) {
			return false;
		}
		return worldY < chunk.getHighestBlockAt(worldX & 0xf, worldZ & 0xf);
	}

	protected boolean edgesLiquid(final ChunkManager level, final BoundingBox boundingBox) {
		final int x0 = Math.max(this.boundingBox.x0 - 1, boundingBox.x0);
		final int y0 = Math.max(this.boundingBox.y0 - 1, boundingBox.y0);
		final int z0 = Math.max(this.boundingBox.z0 - 1, boundingBox.z0);
		final int x1 = Math.min(this.boundingBox.x1 + 1, boundingBox.x1);
		final int y1 = Math.min(this.boundingBox.y1 + 1, boundingBox.y1);
		final int z1 = Math.min(this.boundingBox.z1 + 1, boundingBox.z1);

		for (int x = x0; x <= x1; ++x) {
			for (int z = z0; z <= z1; ++z) {
				if (BlockTypes.isLiquid(level.getBlockIdAt(x, y0, z)) || BlockTypes.isLiquid(level.getBlockIdAt(x, y1, z))) {
					return true;
				}
			}
		}
		for (int x = x0; x <= x1; ++x) {
			for (int y = y0; y <= y1; ++y) {
				if (BlockTypes.isLiquid(level.getBlockIdAt(x, y, z0)) || BlockTypes.isLiquid(level.getBlockIdAt(x, y, z1))) {
					return true;
				}
			}
		}
		for (int z = z0; z <= z1; ++z) {
			for (int y = y0; y <= y1; ++y) {
				if (BlockTypes.isLiquid(level.getBlockIdAt(x0, y, z)) || BlockTypes.isLiquid(level.getBlockIdAt(x1, y, z))) {
					return true;
				}
			}
		}

		return false;
	}

	protected void generateBox(final ChunkManager level, final BoundingBox boundingBox, final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final BlockState outsideBlock, final BlockState insideBlock, final boolean skipAir) {
		for (int y = y1; y <= y2; ++y) {
			for (int x = x1; x <= x2; ++x) {
				for (int z = z1; z <= z2; ++z) {
					if (!skipAir || !getBlock(level, x, y, z, boundingBox).equals(BlockState.AIR)) {
						if (y != y1 && y != y2 && x != x1 && x != x2 && z != z1 && z != z2) {
							placeBlock(level, insideBlock, x, y, z, boundingBox);
						} else {
							placeBlock(level, outsideBlock, x, y, z, boundingBox);
						}
					}
				}
			}
		}
	}

	protected void generateBox(final ChunkManager level, final BoundingBox boundingBox, final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final boolean skipAir, final NukkitRandom random, final BlockSelector selector) {
		for (int y = y1; y <= y2; ++y) {
			for (int x = x1; x <= x2; ++x) {
				for (int z = z1; z <= z2; ++z) {
					if (!skipAir || !getBlock(level, x, y, z, boundingBox).equals(BlockState.AIR)) {
						selector.next(random, x, y, z, y == y1 || y == y2 || x == x1 || x == x2 || z == z1 || z == z2);
						placeBlock(level, selector.getNext(), x, y, z, boundingBox);
					}
				}
			}
		}
	}

	protected void generateMaybeBox(final ChunkManager level, final BoundingBox boundingBox, final NukkitRandom random, final int prob, final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final BlockState outsideBlock, final BlockState insideBlock, final boolean skipAir, final boolean checkInterior) {
		for (int y = y1; y <= y2; ++y) {
			for (int x = x1; x <= x2; ++x) {
				for (int z = z1; z <= z2; ++z) {
					if (random.nextBoundedInt(100) <= prob && (!skipAir || !getBlock(level, x, y, z, boundingBox).equals(BlockState.AIR)) && (!checkInterior || isInterior(level, x, y, z, boundingBox))) {
						if (y != y1 && y != y2 && x != x1 && x != x2 && z != z1 && z != z2) {
							placeBlock(level, insideBlock, x, y, z, boundingBox);
						} else {
							placeBlock(level, outsideBlock, x, y, z, boundingBox);
						}
					}
				}
			}
		}
	}

	protected void maybeGenerateBlock(final ChunkManager level, final BoundingBox boundingBox, final NukkitRandom random, final int prob, final int x, final int y, final int z, final BlockState block) {
		if (random.nextBoundedInt(100) < prob) {
			placeBlock(level, block, x, y, z, boundingBox);
		}
	}

	protected void generateUpperHalfSphere(final ChunkManager level, final BoundingBox boundingBox, final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final BlockState block, final boolean skipAir) {
		final float xLen = x2 - x1 + 1;
		final float yLen = y2 - y1 + 1;
		final float zLen = z2 - z1 + 1;
		final float xHalf = x1 + xLen / 2f;
		final float zHalf = z1 + zLen / 2f;

		for (int y = y1; y <= y2; ++y) {
			final float dy = (float) (y - y1) / yLen;
			for (int x = x1; x <= x2; ++x) {
				final float dx = ((float) x - xHalf) / (xLen * .5f);
				for (int z = z1; z <= z2; ++z) {
					final float dz = ((float) z - zHalf) / (zLen * .5f);
					if (!skipAir || !getBlock(level, x, y, z, boundingBox).equals(BlockState.AIR)) {
						final float d = dx * dx + dy * dy + dz * dz;
						if (d <= 1.05f) {
							placeBlock(level, block, x, y, z, boundingBox);
						}
					}
				}
			}
		}
	}

	protected void fillAirColumnUp(final ChunkManager level, final int x, final int y, final int z, final BoundingBox boundingBox) {
		BlockVector3 vec = new BlockVector3(getWorldX(x, z), getWorldY(y), getWorldZ(x, z));
		if (boundingBox.isInside(vec)) {
			while (level.getBlockIdAt(vec.x, vec.y, vec.z) != BlockID.AIR && vec.getY() < 255) {
				level.setBlockAt(vec.x, vec.y, vec.z, BlockID.AIR);
				vec = vec.up();
			}
		}
	}

	protected void fillColumnDown(final ChunkManager level, final BlockState block, final int x, final int y, final int z, final BoundingBox boundingBox) {
		final int worldX = getWorldX(x, z);
		int worldY = getWorldY(y);
		final int worldZ = getWorldZ(x, z);
		if (boundingBox.isInside(new BlockVector3(worldX, worldY, worldZ))) {
			final BaseFullChunk chunk = level.getChunk(worldX >> 4, worldZ >> 4);
			final int cx = worldX & 0xf;
			final int cz = worldZ & 0xf;
			int blockId = chunk.getBlockId(cx, worldY, cz);
			while ((blockId == BlockID.AIR || blockId == BlockID.WATER || blockId == BlockID.STILL_WATER || blockId == BlockID.LAVA || blockId == BlockID.STILL_LAVA) && worldY > 1) {
				chunk.setBlock(cx, worldY, cz, block.getId(), block.getMeta());
				blockId = chunk.getBlockId(cx, --worldY, cz);
			}
		}
	}

	public void move(final int x, final int y, final int z) {
		boundingBox.move(x, y, z);
	}

	public BlockFace getOrientation() {
		return orientation;
	}

	public void setOrientation(final BlockFace orientation) {
		this.orientation = orientation;
		if (orientation == null) {
			rotation = Rotation.NONE;
		} else {
			switch (orientation) {
				case SOUTH -> rotation = Rotation.CLOCKWISE_180;
				case WEST -> rotation = Rotation.COUNTERCLOCKWISE_90;
				case EAST -> rotation = Rotation.CLOCKWISE_90;
				default -> rotation = Rotation.NONE;
			}
		}
	}

	public Rotation getRotation() {
		return rotation;
	}

	public abstract String getType();

	public abstract static class BlockSelector {

		protected BlockState next;

		protected BlockSelector() {
			next = BlockState.AIR;
		}

		public abstract void next(NukkitRandom random, int x, int y, int z, boolean hasNext);

		public BlockState getNext() {
			return next;
		}
	}
}
