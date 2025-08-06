package cn.nukkit.level.format.leveldb.updater;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.cloudburstmc.blockstateupdater.BlockStateUpdater;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.*;

/**
 * This is updater for vanilla worlds
 * Convert some blocks to blocks supported by nk
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockStateUpdaterVanilla implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdaterVanilla();

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext ctx) {
        ctx.addUpdater(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION, true)
                .match("name", "minecraft:water")
                .visit("states")
                .tryAdd("liquid_depth", (int) 0);

        ctx.addUpdater(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION, true)
                .match("name", "minecraft:polished_blackstone_double_slab")
                .visit("states")
                .tryAdd("top_slot_bit", (byte) 0);

        this.replaceState(ctx, "minecraft:wood", "pillar_axis", "y");
    }

    private void replaceState(CompoundTagUpdaterContext ctx, String identifier, String propertyName, Object value) {
        ctx.addUpdater(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION, true)
                .match("name", identifier)
                .visit("states")
                .edit(propertyName, helper -> helper.replaceWith(propertyName, value));
    }

}
