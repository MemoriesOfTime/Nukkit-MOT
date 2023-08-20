package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.template.ReadOnlyLegacyStructureTemplate;
import cn.nukkit.level.generator.template.ReadableStructureTemplate;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;

public class PopulatorFossil extends Populator {
	protected static final ReadableStructureTemplate[] FOSSILS = {
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_spine_01.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_spine_02.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_spine_03.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_spine_04.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_skull_01.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_skull_02.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_skull_03.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_skull_04.nbt"))
	};

	protected static final ReadableStructureTemplate[] FOSSILS_COAL = {
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_spine_01_coal.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_spine_02_coal.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_spine_03_coal.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_spine_04_coal.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_skull_01_coal.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_skull_02_coal.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_skull_03_coal.nbt")),
		new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/fossils/fossil_skull_04_coal.nbt"))
	};

	@Override
	public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		final int biome = chunk.getBiomeId(3, 3);
		if ((biome == EnumBiome.DESERT.id || biome == EnumBiome.BEACH.id || biome == EnumBiome.DESERT_HILLS.id || biome == EnumBiome.DESERT_M.id || biome == EnumBiome.SWAMP.id || biome == EnumBiome.SWAMPLAND_M.id) && random.nextBoundedInt(64) == (0x1211dfa1 & 63)) { //salted
			int y = Math.min(64, chunk.getHighestBlockAt(0, 0));

			int id = chunk.getBlockId(0, y, 0);
			while (id == BlockID.WATER || id == BlockID.STILL_WATER) {
				id = chunk.getBlockId(0, --y, 0);
			}

			final int index = random.nextBoundedInt(FOSSILS.length);
			final BlockVector3 vec = new BlockVector3(chunkX << 4, Math.max(10, y - 15 - random.nextBoundedInt(10)), chunkZ << 4);
			FOSSILS[index].placeInChunk(chunk, random, vec, 90, null);
			FOSSILS_COAL[index].placeInChunk(chunk, random, vec, 10, null);
		}
	}
}
