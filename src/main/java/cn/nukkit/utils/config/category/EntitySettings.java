package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class EntitySettings extends OkaeriConfig {

    @Comment("Allow spawn eggs to work")
    private boolean spawnEggs = true;

    @Comment("Enable mob AI (pathfinding, targeting, etc.)")
    private boolean mobAi = true;

    @Comment("Enable automatic entity spawning task")
    private boolean autoSpawnTask = true;

    @Comment("Enable automatic entity despawn task")
    private boolean despawnTask = true;

    @Comment("Ticks between entity spawn attempts")
    private int ticksPerSpawns = 200;

    @Comment("Ticks between entity despawn checks")
    private int ticksPerDespawns = 12000;
}
