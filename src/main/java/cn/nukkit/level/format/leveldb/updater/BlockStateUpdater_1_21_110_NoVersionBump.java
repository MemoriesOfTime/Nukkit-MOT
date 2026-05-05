package cn.nukkit.level.format.leveldb.updater;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.cloudburstmc.blockstateupdater.BlockStateUpdater;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.*;

/**
 * Applies the same block state transformations as the upstream
 * {@code BlockStateUpdater_1_21_110} (block-state-updater 1.21.110-SNAPSHOT),
 * but with {@code ignore=true} so the NBT schema version is not advanced.
 * <p>
 * This is necessary because {@code STATE_VERSION} is derived from
 * {@code (1, 21, 60) + updates} and must stay in sync with the palette data
 * files ({@code leveldb_palette.nbt}). Bumping the version to 1.21.110
 * would break that contract, while the data changes (chain → iron_chain,
 * lightning_rod powered_bit) still need to be applied.
 * <p>
 * Adapted from CloudburstMC block-state-updater
 * ({@code org.cloudburstmc.blockstateupdater.BlockStateUpdater_1_21_110})
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockStateUpdater_1_21_110_NoVersionBump implements BlockStateUpdater {

    public static final BlockStateUpdater INSTANCE = new BlockStateUpdater_1_21_110_NoVersionBump();

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext ctx) {
        ctx.addUpdater(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION, true)
                .match("name", "minecraft:chain")
                .edit("name", helper -> helper.replaceWith("name", "minecraft:iron_chain"));

        ctx.addUpdater(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION, true)
                .match("name", "minecraft:lightning_rod")
                .visit("states")
                .tryAdd("powered_bit", (byte) 0);
    }
}
