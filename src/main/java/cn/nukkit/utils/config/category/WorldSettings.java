package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class WorldSettings extends OkaeriConfig {

    @Comment("Enable the Nether dimension")
    private boolean nether = true;

    @Comment("Enable the End dimension")
    private boolean end = true;

    @Comment("Enable vanilla portal mechanics")
    private boolean vanillaPortals = true;

    @Comment("Ticks to wait in portal before teleporting")
    private int portalTicks = 80;

    @Comment("Multiple nether worlds (comma-separated)")
    private String multiNetherWorlds = "";

    @Comment("Worlds where anti-xray is enabled (comma-separated)")
    private String antiXrayWorlds = "";

    @Comment("Worlds that should not be ticked (comma-separated)")
    private String doNotTickWorlds = "";

    @Comment("Worlds where entity spawning is disabled (comma-separated)")
    private String entitySpawningDisabledWorlds = "";

    @Comment("Load all worlds on startup")
    private boolean loadAllWorlds = true;

    @Comment("Worlds where auto-save is disabled (comma-separated)")
    private String autoSaveDisabledWorlds = "";
}
