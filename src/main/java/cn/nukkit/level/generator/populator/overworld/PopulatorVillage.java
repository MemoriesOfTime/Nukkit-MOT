package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.structure.StructurePiece;
import cn.nukkit.level.generator.structure.StructureStart;
import cn.nukkit.level.generator.structure.VillagePieces;
import cn.nukkit.level.generator.task.CallbackableChunkGenerationTask;
import cn.nukkit.math.NukkitRandom;

import java.util.List;

public class PopulatorVillage extends Populator {
	protected static final int SIZE = 0;
	protected static final int SPACING = 32;
	protected static final int SEPARATION = 8;

	public PopulatorVillage() {
	}

	@Override
	public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		//\\ VillageFeature::isFeatureChunk(BiomeSource const &,Random &,ChunkPos const &,uint)
		final int biome = chunk.getBiomeId(7, 7);
		if (biome == EnumBiome.PLAINS.id || biome == EnumBiome.DESERT.id || biome == EnumBiome.SAVANNA.id || biome == EnumBiome.TAIGA.id || biome == EnumBiome.COLD_TAIGA.id || biome == EnumBiome.ICE_PLAINS.id) {
			final long seed = level.getSeed();
			final int cX = (chunkX < 0 ? chunkX - (SPACING - 1) : chunkX) / SPACING;
			final int cZ = (chunkZ < 0 ? chunkZ - (SPACING - 1) : chunkZ) / SPACING;
			random.setSeed(cX * 0x4f9939f508L + cZ * 0x1ef1565bd5L + seed + 0x9e7f70);

			if (chunkX == cX * SPACING + random.nextBoundedInt(SPACING - SEPARATION) && chunkZ == cZ * SPACING + random.nextBoundedInt(SPACING - SEPARATION)) {
				//\\ VillageFeature::createStructureStart(Dimension &,Random &,ChunkPos const &)
				final VillageStart start = new VillageStart(level, chunkX, chunkZ);
				start.generatePieces(level, chunkX, chunkZ);

				if (start.isValid()) { //TODO: serialize nbt
					random.setSeed(seed);
					final int r1 = random.nextInt();
					final int r2 = random.nextInt();

					final BoundingBox boundingBox = start.getBoundingBox();
					for (int cx = boundingBox.x0 >> 4; cx <= boundingBox.x1 >> 4; cx++) {
						for (int cz = boundingBox.z0 >> 4; cz <= boundingBox.z1 >> 4; cz++) {
							final NukkitRandom rand = new NukkitRandom((long) cx * r1 ^ (long) cz * r2 ^ seed);
							final int x = cx << 4;
							final int z = cz << 4;
							BaseFullChunk ck = level.getChunk(cx, cz);
							if (ck == null) {
								ck = chunk.getProvider().getChunk(cx, cz, true);
							}

							if (ck.isGenerated()) {
								start.postProcess(level, rand, new BoundingBox(x, z, x + 15, z + 15), cx, cz);
							} else {
								final int f_cx = cx;
								final int f_cz = cz;
								Server.getInstance().getScheduler().scheduleAsyncTask(new CallbackableChunkGenerationTask<>(chunk.getProvider().getLevel(), ck, start, structure -> structure.postProcess(level, rand, new BoundingBox(x, z, x + 15, z + 15), f_cx, f_cz)));
							}
						}
					}
				}
			}
		}
	}

	public enum Type {
		PLAINS, DESERT, SAVANNA, TAIGA, COLD; //BE

		public static Type byId(final int id) {
			final Type[] values = values();
			if (id < 0 || id >= values.length) {
				return Type.PLAINS;
			}
			return values[id];
		}
	}

	public static class VillageStart extends StructureStart {
		private boolean valid;

		public VillageStart(final ChunkManager level, final int chunkX, final int chunkZ) {
			super(level, chunkX, chunkZ);
		}

		@Override
		public void generatePieces(final ChunkManager level, final int chunkX, final int chunkZ) {
			final VillagePieces.StartPiece start = new VillagePieces.StartPiece(level, 0, random, (chunkX << 4) + 2, (chunkZ << 4) + 2, VillagePieces.getStructureVillageWeightedPieceList(random, SIZE), SIZE);
			pieces.add(start);
			start.addChildren(start, pieces, random);

			final List<StructurePiece> pendingRoads = start.pendingRoads;
			final List<StructurePiece> pendingHouses = start.pendingHouses;
			while (!pendingRoads.isEmpty() || !pendingHouses.isEmpty()) {
				if (pendingRoads.isEmpty()) {
					pendingHouses.remove(random.nextBoundedInt(pendingHouses.size())).addChildren(start, pieces, random);
				} else {
					pendingRoads.remove(random.nextBoundedInt(pendingRoads.size())).addChildren(start, pieces, random);
				}
			}

			calculateBoundingBox();

			int houseCount = 0;
			for (final StructurePiece piece : pieces) {
				if (!(piece instanceof VillagePieces.Road)) {
					++houseCount;
				}
			}
			valid = houseCount > 2;
		}

		@Override
		public boolean isValid() {
			return valid;
		}

		@Override
		public String getType() {
			return "Village";
		}
	}
}
