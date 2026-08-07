package cn.nukkit.level.format.leveldb;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

public class NukkitLegacyMapper implements LegacyStateMapper {

    public static void registerStates(BlockStateMapping blockStateMapping) {
        List<NbtMap> list = NukkitLegacyMapper.loadBlockPalette();
        for (int i = 0; i < list.size(); ++i) {
            NbtMap nbtMap = list.get(i);
            //删除不属于原版的内容
            if (nbtMap.containsKey("network_id") || nbtMap.containsKey("name_hash") || nbtMap.containsKey("block_id")) {
                NbtMapBuilder builder = NbtMapBuilder.from(nbtMap);
                builder.remove("network_id");
                builder.remove("name_hash");
                builder.remove("block_id");
                nbtMap = builder.build();
            }
            //noinspection ResultOfMethodCallIgnored
            nbtMap.hashCode(); // cache hashCode
            blockStateMapping.registerState(i, nbtMap, getOverrideLegacyId(nbtMap), -1);
        }
    }

    /**
     * 返回需要覆盖的 legacy id / Returns the override legacy id, or -1 for no override.
     * <p>
     * vanilla 1.20+ 把 {@code lava_cauldron} 合并进 {@code cauldron}（用 {@code cauldron_liquid=lava} 区分），
     * 但 Nukkit 内部仍用独立的 {@code LAVA_CAULDRON=465}。调色板反查时 id 118 会遮蔽 id 465，
     * 导致世界加载时岩浆炼药锅被误识别为水炼药锅，这里对 cauldron+lava 预设 465 以修正往返。
     * <p>
     * Vanilla 1.20+ merged {@code lava_cauldron} into {@code cauldron} (via {@code cauldron_liquid=lava}),
     * but Nukkit keeps a separate {@code LAVA_CAULDRON=465}. Reverse palette lookup lets id 118 shadow 465,
     * misidentifying lava cauldrons as water on world load; this preset restores the correct round-trip.
     */
    private static int getOverrideLegacyId(NbtMap nbtMap) {
        if ("minecraft:cauldron".equals(nbtMap.getString("name"))) {
            NbtMap states = nbtMap.getCompound("states");
            if ("lava".equals(states.getString("cauldron_liquid"))) {
                return BlockID.LAVA_CAULDRON;
            }
        }
        return -1;
    }

    public static List<NbtMap> loadBlockPalette() {
        List<NbtMap> nbtMaps;
        try (InputStream stream = Server.class.getClassLoader().getResourceAsStream("leveldb_palette.nbt")) {
            nbtMaps = ((NbtMap) NbtUtils.createGZIPReader(Objects.requireNonNull(stream)).readTag()).getList("blocks", NbtType.COMPOUND);
        } catch (Exception e) {
            throw new AssertionError("Error loading block palette leveldb_palette.nbt", e);
        }
        return nbtMaps;
    }

    private BlockPalette blockPalette;

    @Override
    public int legacyToRuntime(int legacyId, int meta) {
        return this.getBlockPalette().getRuntimeId(legacyId, meta);
    }

    @Override
    public int runtimeToFullId(int runtimeId) {
        return this.getBlockPalette().getLegacyFullId(runtimeId);
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

    private BlockPalette getBlockPalette() {
        if (this.blockPalette == null) {
            this.blockPalette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.getFeatureVersion());
        }
        return this.blockPalette;
    }

}
