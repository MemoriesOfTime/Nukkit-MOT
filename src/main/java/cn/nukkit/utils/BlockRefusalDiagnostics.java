package cn.nukkit.utils;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Optional diagnostics for native refusals that do not identify their cause in an event. */
public final class BlockRefusalDiagnostics {
    private static final long INTERVAL_NANOS = 2_000_000_000L;
    private static final int MAX_ENTRIES = 512;
    private static final BlockRefusalDiagnostics SHARED = new BlockRefusalDiagnostics(System::nanoTime);

    private final LongSupplier clock;
    private final Map<Key, Long> recent = new LinkedHashMap<>();

    BlockRefusalDiagnostics(LongSupplier clock) {
        this.clock = clock;
    }

    public enum Reason {
        ADVENTURE_CAN_DESTROY, ADVENTURE_CAN_PLACE_ON,
        CACTUS_SUPPORT, CACTUS_ADJACENT, TORCH_SUPPORT, TORCH_LIQUID,
        LADDER_SUPPORT, RAIL_SUPPORT, CARPET_SUPPORT, SNOW_LAYER_SUPPORT,
        PRESSURE_PLATE_SUPPORT, FLOWER_SUPPORT, DEAD_BUSH_SUPPORT,
        SAPLING_SUPPORT, SAPLING_LIQUID, FLOWABLE_LIQUID
    }

    public static void refused(Player player, Block block, Reason reason) {
        SHARED.record(null, player, block, reason);
    }

    public static void refused(Level level, Player player, Block block, Reason reason) {
        SHARED.record(level, player, block, reason);
    }

    void record(Level level, Player player, Block block, Reason reason) {
        if (Nukkit.DEBUG <= 1 || player == null || block == null || reason == null) {
            return;
        }
        try {
            UUID identity = player.getUniqueId();
            if (identity == null || !sample(new Key(identity, reason))) {
                return;
            }
            Level world = level == null ? block.getLevel() : level;
            world.getServer().getLogger().debug("Block action refused: reason="
                    + reason.name().toLowerCase(java.util.Locale.ROOT)
                    + " world_id=" + world.getId()
                    + " x=" + block.getFloorX() + " y=" + block.getFloorY() + " z=" + block.getFloorZ()
                    + " block=" + block.getId() + " protocol=" + player.protocol);
        } catch (RuntimeException ignored) {
            // A diagnostic failure must not interrupt the native refusal/client correction.
        }
    }

    private synchronized boolean sample(Key key) {
        long now = clock.getAsLong();
        Long previous = recent.get(key);
        if (previous != null && now - previous >= 0 && now - previous < INTERVAL_NANOS) {
            return false;
        }
        recent.remove(key);
        if (recent.size() >= MAX_ENTRIES) {
            Key oldest = recent.keySet().iterator().next();
            long age = now - recent.get(oldest);
            if (age >= 0 && age < INTERVAL_NANOS) {
                return false;
            }
            recent.remove(oldest);
        }
        recent.put(key, now);
        return true;
    }

    private record Key(UUID identity, Reason reason) { }
}
