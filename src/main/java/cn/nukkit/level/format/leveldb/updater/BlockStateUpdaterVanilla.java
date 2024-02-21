package cn.nukkit.level.format.leveldb.updater;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.cloudburstmc.blockstateupdater.BlockStateUpdater;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

/**
 * This is updater for vanilla worlds
 * Convert some blocks to blocks supported by nk
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockStateUpdaterVanilla implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdaterVanilla();

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext ctx) {
        ctx.addUpdater(1, 20, 10, true)
                .match("name", "minecraft:water")
                .visit("states")
                .tryAdd("liquid_depth", 0);
        ctx.addUpdater(1, 20, 10, true)
                .match("name", "minecraft:polished_blackstone_double_slab")
                .visit("states")
                .tryAdd("top_slot_bit", 0);
        ctx.addUpdater(1, 20, 10, true)
                .match("name", "minecraft:minecraft:wood")
                .visit("states")
                .edit("pillar_axis", helper -> {
                    helper.replaceWith("pillar_axis", "y");
                });
        ctx.addUpdater(1, 20, 10, true)
                .match("name", "minecraft:cobblestone_wall")
                .visit("states")
                .edit("wall_connection_type_east", helper -> {
                    helper.replaceWith("wall_connection_type_east", "none");
                })
                .edit("wall_connection_type_north", helper -> {
                    helper.replaceWith("wall_connection_type_north", "none");
                })
                .edit("wall_connection_type_south", helper -> {
                    helper.replaceWith("wall_connection_type_south", "none");
                })
                .edit("wall_connection_type_west", helper -> {
                    helper.replaceWith("wall_connection_type_west", "none");
                })
                .edit("wall_post_bit", helper -> {
                    helper.replaceWith("wall_post_bit", (byte) 0);
                });
    }

}
