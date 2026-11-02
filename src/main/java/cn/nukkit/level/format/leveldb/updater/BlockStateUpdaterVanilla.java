package cn.nukkit.level.format.leveldb.updater;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.cloudburstmc.blockstateupdater.BlockStateUpdater;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;
import org.cloudburstmc.nbt.NbtMap;

import java.util.HashSet;
import java.util.Set;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.*;

/**
 * This is updater for vanilla worlds
 * Convert some blocks to blocks supported by nk
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockStateUpdaterVanilla implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdaterVanilla();

    /**
     * 1.26.50 起由 corner/connection 方块状态驱动渲染的方块名单（唯一事实源，
     * 供 updater 补默认值与区块加载时的"需重算连接"判定共用）。
     * <p>
     * Blocks whose rendering is corner/connection-state-driven since 1.26.50 (single source of
     * truth, shared by the updater's default backfill and the chunk-load recompute detection).
     */
    public static final String[] STAIRS_WITH_CORNER_STATE = {
            "acacia_stairs",
            "andesite_stairs",
            "bamboo_mosaic_stairs",
            "bamboo_stairs",
            "birch_stairs",
            "black_wool_stairs",
            "blackstone_stairs",
            "blue_wool_stairs",
            "brick_stairs",
            "brown_wool_stairs",
            "cherry_stairs",
            "cinnabar_brick_stairs",
            "cinnabar_stairs",
            "cobbled_deepslate_stairs",
            "crimson_stairs",
            "cut_copper_stairs",
            "cyan_wool_stairs",
            "dark_oak_stairs",
            "dark_prismarine_stairs",
            "deepslate_brick_stairs",
            "deepslate_tile_stairs",
            "diorite_stairs",
            "end_brick_stairs",
            "exposed_cut_copper_stairs",
            "granite_stairs",
            "gray_wool_stairs",
            "green_wool_stairs",
            "jungle_stairs",
            "light_blue_wool_stairs",
            "light_gray_wool_stairs",
            "lime_wool_stairs",
            "magenta_wool_stairs",
            "mangrove_stairs",
            "mossy_cobblestone_stairs",
            "mossy_stone_brick_stairs",
            "mud_brick_stairs",
            "nether_brick_stairs",
            "normal_stone_stairs",
            "oak_stairs",
            "orange_wool_stairs",
            "oxidized_cut_copper_stairs",
            "pale_oak_stairs",
            "pink_wool_stairs",
            "polished_andesite_stairs",
            "polished_blackstone_brick_stairs",
            "polished_blackstone_stairs",
            "polished_cinnabar_stairs",
            "polished_deepslate_stairs",
            "polished_diorite_stairs",
            "polished_granite_stairs",
            "polished_sulfur_stairs",
            "polished_tuff_stairs",
            "poplar_stairs",
            "prismarine_bricks_stairs",
            "prismarine_stairs",
            "purple_wool_stairs",
            "purpur_stairs",
            "quartz_stairs",
            "red_nether_brick_stairs",
            "red_sandstone_stairs",
            "red_wool_stairs",
            "resin_brick_stairs",
            "sandstone_stairs",
            "smooth_quartz_stairs",
            "smooth_red_sandstone_stairs",
            "smooth_sandstone_stairs",
            "spruce_stairs",
            "stone_brick_stairs",
            "stone_stairs",
            "sulfur_brick_stairs",
            "sulfur_stairs",
            "tuff_brick_stairs",
            "tuff_stairs",
            "warped_stairs",
            "waxed_cut_copper_stairs",
            "waxed_exposed_cut_copper_stairs",
            "waxed_oxidized_cut_copper_stairs",
            "waxed_weathered_cut_copper_stairs",
            "weathered_cut_copper_stairs",
            "white_wool_stairs",
            "yellow_wool_stairs"
    };

    public static final String[] BLOCKS_WITH_CONNECTION_STATE = {
            "acacia_fence",
            "bamboo_fence",
            "birch_fence",
            "black_stained_glass_pane",
            "blue_stained_glass_pane",
            "brown_stained_glass_pane",
            "cherry_fence",
            "copper_bars",
            "crimson_fence",
            "cyan_stained_glass_pane",
            "dark_oak_fence",
            "exposed_copper_bars",
            "glass_pane",
            "gray_stained_glass_pane",
            "green_stained_glass_pane",
            "hard_black_stained_glass_pane",
            "hard_blue_stained_glass_pane",
            "hard_brown_stained_glass_pane",
            "hard_cyan_stained_glass_pane",
            "hard_glass_pane",
            "hard_gray_stained_glass_pane",
            "hard_green_stained_glass_pane",
            "hard_light_blue_stained_glass_pane",
            "hard_light_gray_stained_glass_pane",
            "hard_lime_stained_glass_pane",
            "hard_magenta_stained_glass_pane",
            "hard_orange_stained_glass_pane",
            "hard_pink_stained_glass_pane",
            "hard_purple_stained_glass_pane",
            "hard_red_stained_glass_pane",
            "hard_white_stained_glass_pane",
            "hard_yellow_stained_glass_pane",
            "iron_bars",
            "jungle_fence",
            "light_blue_stained_glass_pane",
            "light_gray_stained_glass_pane",
            "lime_stained_glass_pane",
            "magenta_stained_glass_pane",
            "mangrove_fence",
            "nether_brick_fence",
            "oak_fence",
            "orange_stained_glass_pane",
            "oxidized_copper_bars",
            "pale_oak_fence",
            "pink_stained_glass_pane",
            "poplar_fence",
            "purple_stained_glass_pane",
            "red_stained_glass_pane",
            "spruce_fence",
            "trip_wire",
            "warped_fence",
            "waxed_copper_bars",
            "waxed_exposed_copper_bars",
            "waxed_oxidized_copper_bars",
            "waxed_weathered_copper_bars",
            "weathered_copper_bars",
            "white_stained_glass_pane",
            "yellow_stained_glass_pane"
    };

    private static final Set<String> CONNECTION_DRIVEN_NAMES;

    static {
        Set<String> names = new HashSet<>();
        for (String stairs : STAIRS_WITH_CORNER_STATE) {
            names.add("minecraft:" + stairs);
        }
        for (String connectable : BLOCKS_WITH_CONNECTION_STATE) {
            names.add("minecraft:" + connectable);
        }
        CONNECTION_DRIVEN_NAMES = names;
    }

    /**
     * 判断磁盘上的方块状态是否缺少 1.26.50 的 corner/connection 键（即由旧版本升级而来），
     * 为 true 时区块需要按邻居重算连接/角落位。
     * <p>
     * Whether a serialized block state predates 1.26.50's corner/connection keys (i.e. was
     * upgraded from an older version); true means the chunk must recompute them from neighbours.
     */
    public static boolean needsConnectionRecompute(NbtMap state) {
        String name = state.getString("name");
        if (name == null || !CONNECTION_DRIVEN_NAMES.contains(name)) {
            return false;
        }
        NbtMap states = state.getCompound("states");
        return !states.containsKey("minecraft:corner") && !states.containsKey("minecraft:connection_north");
    }

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext ctx) {
        this.addProperty(ctx, "minecraft:water", "liquid_depth", (int) 0);
        this.addProperty(ctx, "minecraft:polished_blackstone_double_slab", "top_slot_bit", (byte) 0);
        this.addProperty(ctx, "minecraft:bedrock", "infiniburn_bit", (byte) 0);
        this.addProperty(ctx,"minecraft:snow_layer", "covered_bit", (byte) 0);

        this.replaceState(ctx, "minecraft:wood", "pillar_axis", "y");
    }

    private void addProperty(CompoundTagUpdaterContext ctx, String identifier, String propertyName, Object value) {
        ctx.addUpdater(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION, true)
                .match("name", identifier)
                .visit("states")
                .tryAdd(propertyName, value);
    }

    private void replaceState(CompoundTagUpdaterContext ctx, String identifier, String propertyName, Object value) {
        ctx.addUpdater(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION, true)
                .match("name", identifier)
                .visit("states")
                .edit(propertyName, helper -> helper.replaceWith(propertyName, value));
    }

}
