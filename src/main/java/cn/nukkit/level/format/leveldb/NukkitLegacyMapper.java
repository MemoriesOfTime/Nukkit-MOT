package cn.nukkit.level.format.leveldb;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.PALETTE_VERSION;

public class NukkitLegacyMapper implements LegacyStateMapper {

    public static void registerStates(BlockStateMapping blockStateMapping) {
        List<NbtMap> list = NukkitLegacyMapper.loadBlockPalette();
        for (int i = 0; i < list.size(); ++i) {
            NbtMap nbtMap = list.get(i);
            //删除不属于原版的内容
            if (nbtMap.containsKey("network_id") || nbtMap.containsKey("name_hash")) {
                NbtMapBuilder builder = NbtMapBuilder.from(nbtMap);
                builder.remove("network_id");
                builder.remove("name_hash");
                nbtMap = builder.build();
            }
            //生成并缓存hashCode
            //noinspection ResultOfMethodCallIgnored
            nbtMap.hashCode();
            blockStateMapping.registerState(i, nbtMap);
        }
    }

    public static List<NbtMap> loadBlockPalette() {
        List<NbtMap> nbtMaps;
        try (InputStream stream = Server.class.getClassLoader().getResourceAsStream("leveldb_palette.nbt")) {
            if (stream == null) {
                throw new AssertionError("Unable to load leveldb_palette.nbt");
            }
            nbtMaps = ((NbtMap) NbtUtils.createGZIPReader(stream).readTag()).getList("blocks", NbtType.COMPOUND);
        } catch (IOException e) {
            throw new AssertionError("Unable to load leveldb_palette.nbt", e);
        }
        return nbtMaps;
    }

    @Override
    public int legacyToRuntime(int legacyId, int meta) {
        return GlobalBlockPalette.getOrCreateRuntimeId(PALETTE_VERSION, legacyId, meta);
    }

    @Override
    public int runtimeToFullId(int runtimeId) {
        return GlobalBlockPalette.getLegacyFullId(PALETTE_VERSION, runtimeId);
    }

    @Override
    public int runtimeToLegacyId(int runtimeId) {
        int fullId = this.runtimeToFullId(runtimeId);
        return fullId == -1 ? -1 : fullId >> Block.DATA_BITS;
    }

    @Override
    public int runtimeToLegacyData(int runtimeId) {
        int fullId = this.runtimeToFullId(runtimeId);
        return fullId == -1 ? -1 : fullId & Block.DATA_MASK;
    }

}
