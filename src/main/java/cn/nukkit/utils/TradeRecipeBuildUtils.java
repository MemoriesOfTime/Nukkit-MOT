package cn.nukkit.utils;

import cn.nukkit.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry for villager trade recipes addressable by a synthetic network
 * ID. The Server Authoritative Inventory ItemStackRequest CraftRecipe flow uses
 * this to resolve a selected trade option back to its recipe payload.
 */
public final class TradeRecipeBuildUtils {

    /**
     * Base recipe ID for trade recipes. Values in [TRADE_RECIPEID, ENCH_RECIPEID)
     * are treated as trade recipes by the ItemStackRequest CraftRecipe flow.
     */
    public static final int TRADE_RECIPEID = 0x20000000;

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static final Int2ObjectMap<CompoundTag> RECIPE_MAP = new Int2ObjectOpenHashMap<>();

    private TradeRecipeBuildUtils() {
    }

    /**
     * Allocate a new trade recipe ID, register the given recipe payload under it,
     * and return the new ID. The caller should attach the returned ID to the
     * UpdateTradePacket payload so the client echoes it in subsequent
     * CraftRecipeActions.
     */
    public static int assignRecipeId(CompoundTag recipe) {
        int id = TRADE_RECIPEID + COUNTER.incrementAndGet();
        RECIPE_MAP.put(id, recipe);
        return id;
    }
}
