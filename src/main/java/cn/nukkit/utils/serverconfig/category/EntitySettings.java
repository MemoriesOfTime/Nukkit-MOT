package cn.nukkit.utils.serverconfig.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class EntitySettings extends OkaeriConfig {

    @Comment("Allow spawn eggs to work")
    @CustomKey("spawn-eggs")
    private boolean spawnEggs = true;

    @Comment("Enable mob AI (pathfinding, targeting, etc.)")
    @CustomKey("mob-ai")
    private boolean mobAi = true;

    @Comment("Enable automatic entity spawning task")
    @CustomKey("auto-spawn-task")
    private boolean autoSpawnTask = true;

    @Comment("Enable automatic entity despawn task")
    @CustomKey("despawn-task")
    private boolean despawnTask = true;

    @Comment("Ticks between entity spawn attempts")
    @CustomKey("ticks-per-spawns")
    private int ticksPerSpawns = 200;

    @Comment("Mobs farther than this many blocks (horizontally) from every player in their world run their")
    @Comment("update once per second instead of every tick, catching up the skipped time. Mobs already stop")
    @Comment("walking past this range. 0 turns the throttle off and keeps the walking range at 80 blocks")
    @CustomKey("activation-blocks")
    private int activationBlocks = 80;

    @Comment("Ticks between entity despawn checks")
    @CustomKey("ticks-per-despawns")
    private int ticksPerDespawns = 12000;
}
