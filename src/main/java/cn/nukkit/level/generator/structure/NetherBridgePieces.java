package cn.nukkit.level.generator.structure;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.mob.EntityBlaze;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.block.LiquidUpdater;
import cn.nukkit.level.generator.block.state.BlockState;
import cn.nukkit.level.generator.block.state.WeirdoDirection;
import cn.nukkit.level.generator.loot.NetherBridgeChest;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.level.generator.task.BlockActorSpawnTask;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import com.google.common.collect.Lists;

import java.util.List;

public final class NetherBridgePieces {
	private static final BlockState NETHER_BRICKS = new BlockState(Block.NETHER_BRICKS);
	private static final BlockState NETHER_BRICK_FENCE = new BlockState(Block.NETHER_BRICK_FENCE);
	private static final BlockState SOUL_SAND = new BlockState(Block.SOUL_SAND);
	private static final BlockState NETHER_WART = new BlockState(Block.NETHER_WART_BLOCK);
	private static final BlockState LAVA = new BlockState(Block.LAVA);
	private static final BlockState SPAWNER = new BlockState(Block.MONSTER_SPAWNER);

	private static final PieceWeight[] BRIDGE_PIECE_WEIGHTS = new PieceWeight[]{
		new PieceWeight(BridgeStraight.class, 30, 0, true),
		new PieceWeight(BridgeCrossing.class, 10, 4),
		new PieceWeight(RoomCrossing.class, 10, 4),
		new PieceWeight(StairsRoom.class, 10, 3),
		new PieceWeight(MonsterThrone.class, 5, 2),
		new PieceWeight(CastleEntrance.class, 5, 1)
	};
	private static final PieceWeight[] CASTLE_PIECE_WEIGHTS = new PieceWeight[]{
		new PieceWeight(CastleSmallCorridorPiece.class, 25, 0, true),
		new PieceWeight(CastleSmallCorridorCrossingPiece.class, 15, 5),
		new PieceWeight(CastleSmallCorridorRightTurnPiece.class, 5, 10),
		new PieceWeight(CastleSmallCorridorLeftTurnPiece.class, 5, 10),
		new PieceWeight(CastleCorridorStairsPiece.class, 10, 3, true),
		new PieceWeight(CastleCorridorTBalconyPiece.class, 7, 2),
		new PieceWeight(CastleStalkRoom.class, 5, 2)
	};

	private static NetherBridgePiece findAndCreateBridgePieceFactory(final PieceWeight weight, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
		final Class<? extends NetherBridgePiece> pieceClass = weight.pieceClass;
		if (pieceClass == BridgeStraight.class) {
			return BridgeStraight.createPiece(pieces, random, x, y, z, orientation, genDepth);
		}
		if (pieceClass == BridgeCrossing.class) {
			return BridgeCrossing.createPiece(pieces, x, y, z, orientation, genDepth);
		}
		if (pieceClass == RoomCrossing.class) {
			return RoomCrossing.createPiece(pieces, x, y, z, orientation, genDepth);
		}
		if (pieceClass == StairsRoom.class) {
			return StairsRoom.createPiece(pieces, x, y, z, genDepth, orientation);
		}
		if (pieceClass == MonsterThrone.class) {
			return MonsterThrone.createPiece(pieces, x, y, z, genDepth, orientation);
		}
		if (pieceClass == CastleEntrance.class) {
			return CastleEntrance.createPiece(pieces, random, x, y, z, orientation, genDepth);
		}
		if (pieceClass == CastleSmallCorridorPiece.class) {
			return CastleSmallCorridorPiece.createPiece(pieces, x, y, z, orientation, genDepth);
		}
		if (pieceClass == CastleSmallCorridorRightTurnPiece.class) {
			return CastleSmallCorridorRightTurnPiece.createPiece(pieces, random, x, y, z, orientation, genDepth);
		}
		if (pieceClass == CastleSmallCorridorLeftTurnPiece.class) {
			return CastleSmallCorridorLeftTurnPiece.createPiece(pieces, random, x, y, z, orientation, genDepth);
		}
		if (pieceClass == CastleCorridorStairsPiece.class) {
			return CastleCorridorStairsPiece.createPiece(pieces, x, y, z, orientation, genDepth);
		}
		if (pieceClass == CastleCorridorTBalconyPiece.class) {
			return CastleCorridorTBalconyPiece.createPiece(pieces, x, y, z, orientation, genDepth);
		}
		if (pieceClass == CastleSmallCorridorCrossingPiece.class) {
			return CastleSmallCorridorCrossingPiece.createPiece(pieces, x, y, z, orientation, genDepth);
		}
		if (pieceClass == CastleStalkRoom.class) {
			return CastleStalkRoom.createPiece(pieces, x, y, z, orientation, genDepth);
		}
		return null;
	}

	static class PieceWeight {
		public final Class<? extends NetherBridgePiece> pieceClass;
		public final int weight;
		public final int maxPlaceCount;
		public final boolean allowInRow;
		public int placeCount;

		public PieceWeight(final Class<? extends NetherBridgePiece> pieceClass, final int weight, final int maxPlaceCount) {
			this(pieceClass, weight, maxPlaceCount, false);
		}

		public PieceWeight(final Class<? extends NetherBridgePiece> pieceClass, final int weight, final int maxPlaceCount, final boolean allowInRow) {
			this.pieceClass = pieceClass;
			this.weight = weight;
			this.maxPlaceCount = maxPlaceCount;
			this.allowInRow = allowInRow;
		}

		public final boolean doPlace() {
			return maxPlaceCount == 0 || placeCount < maxPlaceCount;
		}

		public final boolean isValid() {
			return maxPlaceCount == 0 || placeCount < maxPlaceCount;
		}
	}

	abstract static class NetherBridgePiece extends StructurePiece {
		protected NetherBridgePiece(final int genDepth) {
			super(genDepth);
		}

		protected static boolean isOkBox(final BoundingBox boundingBox) {
			return boundingBox != null && boundingBox.y0 > 10;
		}

		private int updatePieceWeight(final List<PieceWeight> weights) {
			boolean success = false;
			int total = 0;

			for (final PieceWeight weight : weights) {
				if (weight.maxPlaceCount > 0 && weight.placeCount < weight.maxPlaceCount) {
					success = true;
				}
				total += weight.weight;
			}

			return success ? total : -1;
		}

		private NetherBridgePiece generatePiece(final StartPiece start, final List<PieceWeight> weights, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final int total = updatePieceWeight(weights);
			if (total > 0 && genDepth <= 30) {
				for (int i = 0; i < 5; ++i) {
					int target = random.nextBoundedInt(total);

					for (final PieceWeight weight : weights) {
						target -= weight.weight;

						if (target < 0) {
							if (!weight.doPlace() || weight == start.previousPiece && !weight.allowInRow) {
								break;
							}

							final NetherBridgePiece piece = findAndCreateBridgePieceFactory(weight, pieces, random, x, y, z, orientation, genDepth);
							if (piece != null) {
								++weight.placeCount;
								start.previousPiece = weight;

								if (!weight.isValid()) {
									weights.remove(weight);
								}

								return piece;
							}
						}
					}
				}
			}

			return BridgeEndFiller.createPiece(pieces, random, x, y, z, orientation, genDepth);
		}

		private void generateAndAddPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth, final boolean isCastle) {
			if (Math.abs(x - start.getBoundingBox().x0) > 112 || Math.abs(z - start.getBoundingBox().z0) > 112) {
				BridgeEndFiller.createPiece(pieces, random, x, y, z, orientation, genDepth);
				return;
			}

			final List<PieceWeight> availablePieces;
			if (isCastle) {
				availablePieces = start.availableCastlePieces;
			} else {
				availablePieces = start.availableBridgePieces;
			}

			final StructurePiece piece = generatePiece(start, availablePieces, pieces, random, x, y, z, orientation, genDepth + 1);
			if (piece != null) {
				pieces.add(piece);
				start.pendingChildren.add(piece);
			}
		}

		protected final void generateChildForward(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int horizontalOffset, final int yOffset, final boolean isCastle) {
			final BlockFace orientation = getOrientation();
			if (orientation != null) {
				switch (orientation) {
					case SOUTH -> generateAndAddPiece(start, pieces, random, boundingBox.x0 + horizontalOffset, boundingBox.y0 + yOffset, boundingBox.z1 + 1, orientation, getGenDepth(), isCastle);
					case WEST -> generateAndAddPiece(start, pieces, random, boundingBox.x0 - 1, boundingBox.y0 + yOffset, boundingBox.z0 + horizontalOffset, orientation, getGenDepth(), isCastle);
					case EAST -> generateAndAddPiece(start, pieces, random, boundingBox.x1 + 1, boundingBox.y0 + yOffset, boundingBox.z0 + horizontalOffset, orientation, getGenDepth(), isCastle);
					default -> generateAndAddPiece(start, pieces, random, boundingBox.x0 + horizontalOffset, boundingBox.y0 + yOffset, boundingBox.z0 - 1, orientation, getGenDepth(), isCastle);
				}
			}
		}

		protected final void generateChildLeft(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int yOffset, final int horizontalOffset, final boolean isCastle) {
			final BlockFace orientation = getOrientation();
			if (orientation != null) {
				switch (orientation) {
					case WEST, EAST -> generateAndAddPiece(start, pieces, random, boundingBox.x0 + horizontalOffset, boundingBox.y0 + yOffset, boundingBox.z0 - 1, BlockFace.NORTH, getGenDepth(), isCastle);
					default -> generateAndAddPiece(start, pieces, random, boundingBox.x0 - 1, boundingBox.y0 + yOffset, boundingBox.z0 + horizontalOffset, BlockFace.WEST, getGenDepth(), isCastle);
				}
			}
		}

		protected final void generateChildRight(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int yOffset, final int horizontalOffset, final boolean isCastle) {
			final BlockFace orientation = getOrientation();
			if (orientation != null) {
				switch (orientation) {
					case WEST, EAST -> generateAndAddPiece(start, pieces, random, boundingBox.x0 + horizontalOffset, boundingBox.y0 + yOffset, boundingBox.z1 + 1, BlockFace.SOUTH, getGenDepth(), isCastle);
					default -> generateAndAddPiece(start, pieces, random, boundingBox.x1 + 1, boundingBox.y0 + yOffset, boundingBox.z0 + horizontalOffset, BlockFace.EAST, getGenDepth(), isCastle);
				}
			}
		}
	}

	public static class StartPiece extends BridgeCrossing {
		public final List<StructurePiece> pendingChildren;
		public PieceWeight previousPiece;
		public final List<PieceWeight> availableBridgePieces;
		public final List<PieceWeight> availableCastlePieces;

		public StartPiece(final NukkitRandom random, final int x, final int z) {
			super(random, x, z);
			pendingChildren = Lists.newArrayList();

			availableBridgePieces = Lists.newArrayList();
			for (final PieceWeight weight : NetherBridgePieces.BRIDGE_PIECE_WEIGHTS) {
				weight.placeCount = 0;
				availableBridgePieces.add(weight);
			}

			availableCastlePieces = Lists.newArrayList();
			for (final PieceWeight weight : NetherBridgePieces.CASTLE_PIECE_WEIGHTS) {
				weight.placeCount = 0;
				availableCastlePieces.add(weight);
			}
		}

		@Override
		public String getType() {
			return "NeStart";
		}
	}

	public static class BridgeStraight extends NetherBridgePiece {
		public BridgeStraight(final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static BridgeStraight createPiece(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, -3, 0, 5, 10, 19, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new BridgeStraight(genDepth, random, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildForward((StartPiece) piece, pieces, random, 1, 3, false);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 3, 0, 4, 4, 18, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 5, 0, 3, 7, 18, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 5, 0, 0, 5, 18, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 5, 0, 4, 5, 18, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 4, 2, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 13, 4, 2, 18, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 0, 0, 4, 1, 3, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 0, 15, 4, 1, 18, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 0; x <= 4; ++x) {
				for (int z = 0; z <= 2; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
					fillColumnDown(level, NETHER_BRICKS, x, -1, 18 - z, boundingBox);
				}
			}

			generateBox(level, boundingBox, 0, 1, 1, 0, 4, 1, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 3, 4, 0, 4, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 3, 14, 0, 4, 14, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 1, 17, 0, 4, 17, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 4, 1, 1, 4, 4, 1, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 4, 3, 4, 4, 4, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 4, 3, 14, 4, 4, 14, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 4, 1, 17, 4, 4, 17, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);

			return true;
		}

		@Override
		public String getType() {
			return "NeBS";
		}
	}

	public static class BridgeEndFiller extends NetherBridgePiece {
		private final int selfSeed;

		public BridgeEndFiller(final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
			selfSeed = random.nextInt();
		}

		public static BridgeEndFiller createPiece(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, -3, 0, 5, 10, 8, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new BridgeEndFiller(genDepth, random, boundingBox, orientation) : null;
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			final NukkitRandom rand = new NukkitRandom(selfSeed);

			for (int x = 0; x <= 4; ++x) {
				for (int y = 3; y <= 4; ++y) {
					generateBox(level, boundingBox, x, y, 0, x, y, rand.nextBoundedInt(8), NETHER_BRICKS, NETHER_BRICKS, false);
				}
			}

			generateBox(level, boundingBox, 0, 5, 0, 0, 5, rand.nextBoundedInt(8), NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 5, 0, 4, 5, rand.nextBoundedInt(8), NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 0; x <= 4; ++x) {
				generateBox(level, boundingBox, x, 2, 0, x, 2, rand.nextBoundedInt(5), NETHER_BRICKS, NETHER_BRICKS, false);
			}

			for (int x = 0; x <= 4; ++x) {
				for (int y = 0; y <= 1; ++y) {
					generateBox(level, boundingBox, x, y, 0, x, y, rand.nextBoundedInt(3), NETHER_BRICKS, NETHER_BRICKS, false);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeBEF";
		}
	}

	public static class BridgeCrossing extends NetherBridgePiece {
		protected BridgeCrossing(final NukkitRandom random, final int x, final int z) {
			super(0);
			setOrientation(BlockFace.Plane.HORIZONTAL.random(random));
			boundingBox = new BoundingBox(x, 64, z, x + 19 - 1, 73, z + 19 - 1);
		}

		public BridgeCrossing(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static BridgeCrossing createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -8, -3, 0, 19, 10, 19, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new BridgeCrossing(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public final void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildForward((StartPiece) piece, pieces, random, 8, 3, false);
			generateChildLeft((StartPiece) piece, pieces, random, 3, 8, false);
			generateChildRight((StartPiece) piece, pieces, random, 3, 8, false);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 7, 3, 0, 11, 4, 18, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 3, 7, 18, 4, 11, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 8, 5, 0, 10, 7, 18, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 5, 8, 18, 7, 10, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 7, 5, 0, 7, 5, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 7, 5, 11, 7, 5, 18, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 11, 5, 0, 11, 5, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 11, 5, 11, 11, 5, 18, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 5, 7, 7, 5, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 11, 5, 7, 18, 5, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 5, 11, 7, 5, 11, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 11, 5, 11, 18, 5, 11, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 7, 2, 0, 11, 2, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 7, 2, 13, 11, 2, 18, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 7, 0, 0, 11, 1, 3, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 7, 0, 15, 11, 1, 18, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 7; x <= 11; ++x) {
				for (int z = 0; z <= 2; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
					fillColumnDown(level, NETHER_BRICKS, x, -1, 18 - z, boundingBox);
				}
			}

			generateBox(level, boundingBox, 0, 2, 7, 5, 2, 11, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 13, 2, 7, 18, 2, 11, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 0, 7, 3, 1, 11, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 15, 0, 7, 18, 1, 11, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 0; x <= 2; ++x) {
				for (int z = 7; z <= 11; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
					fillColumnDown(level, NETHER_BRICKS, 18 - x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeBCr";
		}
	}

	public static class RoomCrossing extends NetherBridgePiece {
		public RoomCrossing(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static RoomCrossing createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 9, 7, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new RoomCrossing(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildForward((StartPiece) piece, pieces, random, 2, 0, false);
			generateChildLeft((StartPiece) piece, pieces, random, 0, 2, false);
			generateChildRight((StartPiece) piece, pieces, random, 0, 2, false);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 0, 0, 6, 1, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 6, 7, 6, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 2, 0, 1, 6, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 6, 1, 6, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 2, 0, 6, 6, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 2, 6, 6, 6, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 0, 6, 1, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 5, 0, 6, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 2, 0, 6, 6, 1, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 2, 5, 6, 6, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 6, 0, 4, 6, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 0, 4, 5, 0, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 2, 6, 6, 4, 6, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 6, 4, 5, 6, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 6, 2, 0, 6, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 5, 2, 0, 5, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 6, 6, 2, 6, 6, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 5, 2, 6, 5, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);

			for (int x = 0; x <= 6; ++x) {
				for (int z = 0; z <= 6; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeRC";
		}
	}

	public static class StairsRoom extends NetherBridgePiece {
		public StairsRoom(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static StairsRoom createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final int genDepth, final BlockFace orientation) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 11, 7, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new StairsRoom(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildRight((StartPiece) piece, pieces, random, 6, 2, false);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 0, 0, 6, 1, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 6, 10, 6, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 2, 0, 1, 8, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 2, 0, 6, 8, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 1, 0, 8, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 2, 1, 6, 8, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 2, 6, 5, 8, 6, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 3, 2, 0, 5, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 6, 3, 2, 6, 5, 2, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 6, 3, 4, 6, 5, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			placeBlock(level, NETHER_BRICKS, 5, 2, 5, boundingBox);
			generateBox(level, boundingBox, 4, 2, 5, 4, 3, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 3, 2, 5, 3, 4, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 2, 5, 2, 5, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 2, 5, 1, 6, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 7, 1, 5, 7, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 8, 2, 6, 8, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 2, 6, 0, 4, 8, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 0, 4, 5, 0, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);

			for (int x = 0; x <= 6; ++x) {
				for (int z = 0; z <= 6; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeSR";
		}
	}

	public static class MonsterThrone extends NetherBridgePiece {
		private boolean hasPlacedSpawner;

		public MonsterThrone(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static MonsterThrone createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final int genDepth, final BlockFace orientation) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 8, 9, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new MonsterThrone(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 2, 0, 6, 7, 7, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 1, 0, 0, 5, 1, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 2, 1, 5, 2, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 3, 2, 5, 3, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 4, 3, 5, 4, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 2, 0, 1, 4, 2, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 2, 0, 5, 4, 2, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 5, 2, 1, 5, 3, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 5, 2, 5, 5, 3, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 5, 3, 0, 5, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 5, 3, 6, 5, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 5, 8, 5, 5, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			placeBlock(level, NETHER_BRICK_FENCE, 1, 6, 3, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 5, 6, 3, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 0, 6, 3, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 6, 6, 3, boundingBox);
			generateBox(level, boundingBox, 0, 6, 4, 0, 6, 7, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 6, 6, 4, 6, 6, 7, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			placeBlock(level, NETHER_BRICK_FENCE, 0, 6, 8, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 6, 6, 8, boundingBox);
			generateBox(level, boundingBox, 1, 6, 8, 5, 6, 8, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			placeBlock(level, NETHER_BRICK_FENCE, 1, 7, 8, boundingBox);
			generateBox(level, boundingBox, 2, 7, 8, 4, 7, 8, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			placeBlock(level, NETHER_BRICK_FENCE, 5, 7, 8, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 2, 8, 8, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 3, 8, 8, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 4, 8, 8, boundingBox);

			if (!hasPlacedSpawner) {
				final BlockVector3 vec = new BlockVector3(getWorldX(3, 5), getWorldY(5), getWorldZ(3, 5));
				if (boundingBox.isInside(vec)) {
					hasPlacedSpawner = true;
					level.setBlockAt(vec.x, vec.y, vec.z, SPAWNER.getId(), SPAWNER.getMeta());

					final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
					if (chunk != null) {
						Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(),
							BlockEntity.getDefaultCompound(vec.asVector3(), BlockEntity.MOB_SPAWNER)
								.putInt("EntityId", EntityBlaze.NETWORK_ID)));
					}
				}
			}

			for (int x = 0; x <= 6; ++x) {
				for (int z = 0; z <= 6; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeMT";
		}
	}

	public static class CastleEntrance extends NetherBridgePiece {
		public CastleEntrance(final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static CastleEntrance createPiece(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -5, -3, 0, 13, 14, 13, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new CastleEntrance(genDepth, random, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildForward((StartPiece) piece, pieces, random, 5, 3, true);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 3, 0, 12, 4, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 5, 0, 12, 13, 12, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 5, 0, 1, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 11, 5, 0, 12, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 11, 4, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 8, 5, 11, 10, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 9, 11, 7, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 0, 4, 12, 1, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 8, 5, 0, 10, 12, 1, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 9, 0, 7, 12, 1, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 11, 2, 10, 12, 10, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 8, 0, 7, 8, 0, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);

			for (int i = 1; i <= 11; i += 2) {
				generateBox(level, boundingBox, i, 10, 0, i, 11, 0, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				generateBox(level, boundingBox, i, 10, 12, i, 11, 12, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				generateBox(level, boundingBox, 0, 10, i, 0, 11, i, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				generateBox(level, boundingBox, 12, 10, i, 12, 11, i, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				placeBlock(level, NETHER_BRICKS, i, 13, 0, boundingBox);
				placeBlock(level, NETHER_BRICKS, i, 13, 12, boundingBox);
				placeBlock(level, NETHER_BRICKS, 0, 13, i, boundingBox);
				placeBlock(level, NETHER_BRICKS, 12, 13, i, boundingBox);

				if (i != 11) {
					placeBlock(level, NETHER_BRICK_FENCE, i + 1, 13, 0, boundingBox);
					placeBlock(level, NETHER_BRICK_FENCE, i + 1, 13, 12, boundingBox);
					placeBlock(level, NETHER_BRICK_FENCE, 0, 13, i + 1, boundingBox);
					placeBlock(level, NETHER_BRICK_FENCE, 12, 13, i + 1, boundingBox);
				}
			}

			placeBlock(level, NETHER_BRICK_FENCE, 0, 13, 0, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 0, 13, 12, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 12, 13, 12, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 12, 13, 0, boundingBox);

			for (int z = 3; z <= 9; z += 2) {
				generateBox(level, boundingBox, 1, 7, z, 1, 8, z, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				generateBox(level, boundingBox, 11, 7, z, 11, 8, z, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			}

			generateBox(level, boundingBox, 4, 2, 0, 8, 2, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 4, 12, 2, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 0, 0, 8, 1, 3, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 0, 9, 8, 1, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 0, 4, 3, 1, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 9, 0, 4, 12, 1, 8, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 4; x <= 8; ++x) {
				for (int l = 0; l <= 2; ++l) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, l, boundingBox);
					fillColumnDown(level, NETHER_BRICKS, x, -1, 12 - l, boundingBox);
				}
			}

			for (int x = 0; x <= 2; ++x) {
				for (int n = 4; n <= 8; ++n) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, n, boundingBox);
					fillColumnDown(level, NETHER_BRICKS, 12 - x, -1, n, boundingBox);
				}
			}

			generateBox(level, boundingBox, 5, 5, 5, 7, 5, 7, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 1, 6, 6, 4, 6, BlockState.AIR, BlockState.AIR, false);
			placeBlock(level, NETHER_BRICKS, 6, 0, 6, boundingBox);
			placeBlock(level, LAVA, 6, 5, 6, boundingBox);

			final BlockVector3 vec = new BlockVector3(getWorldX(6, 6), getWorldY(5), getWorldZ(6, 6));
			if (boundingBox.isInside(vec)) {
				LiquidUpdater.lavaSpread(level, vec);
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeCE";
		}
	}

	public static class CastleStalkRoom extends NetherBridgePiece {
		public CastleStalkRoom(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static CastleStalkRoom createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -5, -3, 0, 13, 14, 13, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new CastleStalkRoom(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildForward((StartPiece) piece, pieces, random, 5, 3, true);
			generateChildForward((StartPiece) piece, pieces, random, 5, 11, true);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 3, 0, 12, 4, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 5, 0, 12, 13, 12, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 5, 0, 1, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 11, 5, 0, 12, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 11, 4, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 8, 5, 11, 10, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 9, 11, 7, 12, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 0, 4, 12, 1, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 8, 5, 0, 10, 12, 1, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 5, 9, 0, 7, 12, 1, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 11, 2, 10, 12, 10, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int i = 1; i <= 11; i += 2) {
				generateBox(level, boundingBox, i, 10, 0, i, 11, 0, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				generateBox(level, boundingBox, i, 10, 12, i, 11, 12, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				generateBox(level, boundingBox, 0, 10, i, 0, 11, i, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				generateBox(level, boundingBox, 12, 10, i, 12, 11, i, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				placeBlock(level, NETHER_BRICKS, i, 13, 0, boundingBox);
				placeBlock(level, NETHER_BRICKS, i, 13, 12, boundingBox);
				placeBlock(level, NETHER_BRICKS, 0, 13, i, boundingBox);
				placeBlock(level, NETHER_BRICKS, 12, 13, i, boundingBox);

				if (i != 11) {
					placeBlock(level, NETHER_BRICK_FENCE, i + 1, 13, 0, boundingBox);
					placeBlock(level, NETHER_BRICK_FENCE, i + 1, 13, 12, boundingBox);
					placeBlock(level, NETHER_BRICK_FENCE, 0, 13, i + 1, boundingBox);
					placeBlock(level, NETHER_BRICK_FENCE, 12, 13, i + 1, boundingBox);
				}
			}

			placeBlock(level, NETHER_BRICK_FENCE, 0, 13, 0, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 0, 13, 12, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 12, 13, 12, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 12, 13, 0, boundingBox);

			for (int z = 3; z <= 9; z += 2) {
				generateBox(level, boundingBox, 1, 7, z, 1, 8, z, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				generateBox(level, boundingBox, 11, 7, z, 11, 8, z, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			}

			final BlockState statrsN = new BlockState(Block.NETHER_BRICKS_STAIRS, WeirdoDirection.NORTH);
			for (int y = 0; y <= 6; ++y) {
				final int z = y + 4;

				for (int x = 5; x <= 7; ++x) {
					placeBlock(level, statrsN, x, 5 + y, z, boundingBox);
				}

				if (z >= 5 && z <= 8) {
					generateBox(level, boundingBox, 5, 5, z, 7, y + 4, z, NETHER_BRICKS, NETHER_BRICKS, false);
				} else if (z >= 9) {
					generateBox(level, boundingBox, 5, 8, z, 7, y + 4, z, NETHER_BRICKS, NETHER_BRICKS, false);
				}

				if (y >= 1) {
					generateBox(level, boundingBox, 5, 6 + y, z, 7, 9 + y, z, BlockState.AIR, BlockState.AIR, false);
				}
			}

			for (int x = 5; x <= 7; ++x) {
				placeBlock(level, statrsN, x, 12, 11, boundingBox);
			}

			generateBox(level, boundingBox, 5, 6, 7, 5, 7, 7, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 7, 6, 7, 7, 7, 7, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 5, 13, 12, 7, 13, 12, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 2, 5, 2, 3, 5, 3, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 9, 3, 5, 10, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 2, 5, 4, 2, 5, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 9, 5, 2, 10, 5, 3, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 9, 5, 9, 10, 5, 10, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 10, 5, 4, 10, 5, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			final BlockState statrsW = new BlockState(Block.NETHER_BRICKS_STAIRS, WeirdoDirection.WEST);
			placeBlock(level, statrsW, 4, 5, 2, boundingBox);
			placeBlock(level, statrsW, 4, 5, 3, boundingBox);
			placeBlock(level, statrsW, 4, 5, 9, boundingBox);
			placeBlock(level, statrsW, 4, 5, 10, boundingBox);
			final BlockState statrsE = new BlockState(Block.NETHER_BRICKS_STAIRS, WeirdoDirection.EAST);
			placeBlock(level, statrsE, 8, 5, 2, boundingBox);
			placeBlock(level, statrsE, 8, 5, 3, boundingBox);
			placeBlock(level, statrsE, 8, 5, 9, boundingBox);
			placeBlock(level, statrsE, 8, 5, 10, boundingBox);
			generateBox(level, boundingBox, 3, 4, 4, 4, 4, 8, SOUL_SAND, SOUL_SAND, false);
			generateBox(level, boundingBox, 8, 4, 4, 9, 4, 8, SOUL_SAND, SOUL_SAND, false);
			generateBox(level, boundingBox, 3, 5, 4, 4, 5, 8, NETHER_WART, NETHER_WART, false);
			generateBox(level, boundingBox, 8, 5, 4, 9, 5, 8, NETHER_WART, NETHER_WART, false);
			generateBox(level, boundingBox, 4, 2, 0, 8, 2, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 4, 12, 2, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 0, 0, 8, 1, 3, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 0, 9, 8, 1, 12, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 0, 4, 3, 1, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 9, 0, 4, 12, 1, 8, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 4; x <= 8; ++x) {
				for (int z = 0; z <= 2; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
					fillColumnDown(level, NETHER_BRICKS, x, -1, 12 - z, boundingBox);
				}
			}

			for (int x = 0; x <= 2; ++x) {
				for (int z = 4; z <= 8; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
					fillColumnDown(level, NETHER_BRICKS, 12 - x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeCSR";
		}
	}

	public static class CastleSmallCorridorPiece extends NetherBridgePiece {
		public CastleSmallCorridorPiece(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static CastleSmallCorridorPiece createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new CastleSmallCorridorPiece(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildForward((StartPiece) piece, pieces, random, 1, 0, true);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 0, 0, 4, 1, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 4, 5, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 2, 0, 0, 5, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 2, 0, 4, 5, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 3, 1, 0, 4, 1, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 3, 3, 0, 4, 3, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 4, 3, 1, 4, 4, 1, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 4, 3, 3, 4, 4, 3, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 6, 0, 4, 6, 4, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 0; x <= 4; ++x) {
				for (int z = 0; z <= 4; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeSC";
		}
	}

	public static class CastleSmallCorridorCrossingPiece extends NetherBridgePiece {

		public CastleSmallCorridorCrossingPiece(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static CastleSmallCorridorCrossingPiece createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new CastleSmallCorridorCrossingPiece(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildForward((StartPiece) piece, pieces, random, 1, 0, true);
			generateChildLeft((StartPiece) piece, pieces, random, 0, 1, true);
			generateChildRight((StartPiece) piece, pieces, random, 0, 1, true);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 0, 0, 4, 1, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 4, 5, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 2, 0, 0, 5, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 2, 0, 4, 5, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 4, 0, 5, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 2, 4, 4, 5, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 6, 0, 4, 6, 4, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 0; x <= 4; ++x) {
				for (int z = 0; z <= 4; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeSCSC";
		}
	}

	public static class CastleSmallCorridorRightTurnPiece extends NetherBridgePiece {
		private boolean isNeedingChest;

		public CastleSmallCorridorRightTurnPiece(final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
			isNeedingChest = random.nextBoundedInt(3) == 0;
		}

		public static CastleSmallCorridorRightTurnPiece createPiece(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new CastleSmallCorridorRightTurnPiece(genDepth, random, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildRight((StartPiece) piece, pieces, random, 0, 1, true);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 0, 0, 4, 1, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 4, 5, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 2, 0, 0, 5, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 3, 1, 0, 4, 1, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 3, 3, 0, 4, 3, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 4, 2, 0, 4, 5, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 2, 4, 4, 5, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 3, 4, 1, 4, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 3, 3, 4, 3, 4, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);

			if (isNeedingChest && boundingBox.isInside(new BlockVector3(getWorldX(1, 3), getWorldY(2), getWorldZ(1, 3)))) {
				isNeedingChest = false;

				final BlockFace orientation = getOrientation();
				placeBlock(level, new BlockState(Block.CHEST, (orientation == null ? BlockFace.NORTH : orientation).getOpposite().getIndex()), 1, 2, 3, boundingBox);

				final BlockVector3 vec = new BlockVector3(getWorldX(1, 3), getWorldY(2), getWorldZ(1, 3));
				if (boundingBox.isInside(vec)) {
					final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
					if (chunk != null) {
						final CompoundTag nbt = BlockEntity.getDefaultCompound(vec.asVector3(), BlockEntity.CHEST);
						final ListTag<CompoundTag> itemList = new ListTag<>("Items");
						NetherBridgeChest.get().create(itemList, random);
						nbt.putList(itemList);
						Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(), nbt));
					}
				}
			}

			generateBox(level, boundingBox, 0, 6, 0, 4, 6, 4, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 0; x <= 4; ++x) {
				for (int z = 0; z <= 4; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeSCRT";
		}
	}

	public static class CastleSmallCorridorLeftTurnPiece extends NetherBridgePiece {
		private boolean isNeedingChest;

		public CastleSmallCorridorLeftTurnPiece(final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
			isNeedingChest = random.nextBoundedInt(3) == 0;
		}

		public static CastleSmallCorridorLeftTurnPiece createPiece(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);
			if (!NetherBridgePiece.isOkBox(boundingBox) || StructurePiece.findCollisionPiece(pieces, boundingBox) != null) {
				return null;
			}
			return new CastleSmallCorridorLeftTurnPiece(genDepth, random, boundingBox, orientation);
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildLeft((StartPiece) piece, pieces, random, 0, 1, true);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 0, 0, 4, 1, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 4, 5, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 4, 2, 0, 4, 5, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 4, 3, 1, 4, 4, 1, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 4, 3, 3, 4, 4, 3, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 2, 0, 0, 5, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 4, 3, 5, 4, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 3, 4, 1, 4, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 3, 3, 4, 3, 4, 4, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);

			if (isNeedingChest && boundingBox.isInside(new BlockVector3(getWorldX(3, 3), getWorldY(2), getWorldZ(3, 3)))) {
				isNeedingChest = false;

				final BlockFace orientation = getOrientation();
				placeBlock(level, new BlockState(Block.CHEST, (orientation == null ? BlockFace.NORTH : orientation).getOpposite().getIndex()), 3, 2, 3, boundingBox);

				final BlockVector3 vec = new BlockVector3(getWorldX(3, 3), getWorldY(2), getWorldZ(3, 3));
				if (boundingBox.isInside(vec)) {
					final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
					if (chunk != null) {
						final CompoundTag nbt = BlockEntity.getDefaultCompound(vec.asVector3(), BlockEntity.CHEST);
						final ListTag<CompoundTag> itemList = new ListTag<>("Items");
						NetherBridgeChest.get().create(itemList, random);
						nbt.putList(itemList);
						Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(), nbt));
					}
				}
			}

			generateBox(level, boundingBox, 0, 6, 0, 4, 6, 4, NETHER_BRICKS, NETHER_BRICKS, false);

			for (int x = 0; x <= 4; ++x) {
				for (int z = 0; z <= 4; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeSCLT";
		}
	}

	public static class CastleCorridorStairsPiece extends NetherBridgePiece {
		public CastleCorridorStairsPiece(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static CastleCorridorStairsPiece createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, -7, 0, 5, 14, 10, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new CastleCorridorStairsPiece(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			generateChildForward((StartPiece) piece, pieces, random, 1, 0, true);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			final BlockState stairsS = new BlockState(Block.NETHER_BRICKS_STAIRS, WeirdoDirection.SOUTH);
			for (int i = 0; i <= 9; ++i) {
				final int maxY = Math.max(1, 7 - i);
				final int miny = Math.min(Math.max(maxY + 5, 14 - i), 13);

				generateBox(level, boundingBox, 0, 0, i, 4, maxY, i, NETHER_BRICKS, NETHER_BRICKS, false);
				generateBox(level, boundingBox, 1, maxY + 1, i, 3, miny - 1, i, BlockState.AIR, BlockState.AIR, false);

				if (i <= 6) {
					placeBlock(level, stairsS, 1, maxY + 1, i, boundingBox);
					placeBlock(level, stairsS, 2, maxY + 1, i, boundingBox);
					placeBlock(level, stairsS, 3, maxY + 1, i, boundingBox);
				}

				generateBox(level, boundingBox, 0, miny, i, 4, miny, i, NETHER_BRICKS, NETHER_BRICKS, false);
				generateBox(level, boundingBox, 0, maxY + 1, i, 0, miny - 1, i, NETHER_BRICKS, NETHER_BRICKS, false);
				generateBox(level, boundingBox, 4, maxY + 1, i, 4, miny - 1, i, NETHER_BRICKS, NETHER_BRICKS, false);

				if ((i & 0x1) == 0x0) {
					generateBox(level, boundingBox, 0, maxY + 2, i, 0, maxY + 3, i, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
					generateBox(level, boundingBox, 4, maxY + 2, i, 4, maxY + 3, i, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
				}

				for (int x = 0; x <= 4; ++x) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, i, boundingBox);
				}
			}
			return true;
		}

		@Override
		public String getType() {
			return "NeCCS";
		}
	}

	public static class CastleCorridorTBalconyPiece extends NetherBridgePiece {
		public CastleCorridorTBalconyPiece(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation) {
			super(genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static CastleCorridorTBalconyPiece createPiece(final List<StructurePiece> pieces, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -3, 0, 0, 9, 7, 9, orientation);
			return NetherBridgePiece.isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ?
				new CastleCorridorTBalconyPiece(genDepth, boundingBox, orientation) : null;
		}

		@Override
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			int horizontalOffset = 1;
			final BlockFace orientation = getOrientation();
			if (orientation == BlockFace.WEST || orientation == BlockFace.NORTH) {
				horizontalOffset = 5;
			}

			generateChildLeft((StartPiece) piece, pieces, random, 0, horizontalOffset, random.nextBoundedInt(8) > 0);
			generateChildRight((StartPiece) piece, pieces, random, 0, horizontalOffset, random.nextBoundedInt(8) > 0);
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			generateBox(level, boundingBox, 0, 0, 0, 8, 1, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 8, 5, 8, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 6, 0, 8, 6, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 0, 2, 0, 2, 5, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 2, 0, 8, 5, 0, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 3, 0, 1, 4, 0, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 7, 3, 0, 7, 4, 0, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 2, 4, 8, 2, 8, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 1, 4, 2, 2, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 6, 1, 4, 7, 2, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 1, 3, 8, 7, 3, 8, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			placeBlock(level, NETHER_BRICK_FENCE, 0, 3, 8, boundingBox);
			placeBlock(level, NETHER_BRICK_FENCE, 8, 3, 8, boundingBox);
			generateBox(level, boundingBox, 0, 3, 6, 0, 3, 7, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 8, 3, 6, 8, 3, 7, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 0, 3, 4, 0, 5, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 8, 3, 4, 8, 5, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 3, 5, 2, 5, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 6, 3, 5, 7, 5, 5, NETHER_BRICKS, NETHER_BRICKS, false);
			generateBox(level, boundingBox, 1, 4, 5, 1, 5, 5, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);
			generateBox(level, boundingBox, 7, 4, 5, 7, 5, 5, NETHER_BRICK_FENCE, NETHER_BRICK_FENCE, false);

			for (int x = 0; x <= 8; ++x) {
				for (int z = 0; z <= 5; ++z) {
					fillColumnDown(level, NETHER_BRICKS, x, -1, z, boundingBox);
				}
			}

			return true;
		}

		@Override
		public String getType() {
			return "NeCTB";
		}
	}
}
