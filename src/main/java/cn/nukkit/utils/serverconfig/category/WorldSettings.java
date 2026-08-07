package cn.nukkit.utils.serverconfig.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Accessors(fluent = true)
public class WorldSettings extends OkaeriConfig {

    @Comment("Enable the Nether dimension")
    private boolean nether = true;

    @Comment("Enable the End dimension")
    private boolean end = true;

    @Comment("Enable vanilla portal mechanics")
    @CustomKey("vanilla-portals")
    private boolean vanillaPortals = true;

    @Comment("Ticks to wait in portal before teleporting")
    @CustomKey("portal-ticks")
    private int portalTicks = 80;

    @Comment("Worlds that should have their own nether dimensions, otherwise using the default nether world")
    @CustomKey("multi-nether-worlds")
    private List<String> multiNetherWorlds = new ArrayList<>();

    @Comment("Worlds where the experimental built-in anti-xray is enabled")
    @CustomKey("anti-xray-worlds")
    private List<String> antiXrayWorlds = new ArrayList<>();

    @Comment("Worlds where random block ticking is disabled")
    @CustomKey("do-not-tick-worlds")
    private List<String> doNotTickWorlds = new ArrayList<>();

    @Comment("Worlds where entity auto spawning is not allowed")
    @CustomKey("entity-spawning-disabled-worlds")
    private List<String> entitySpawningDisabledWorlds = new ArrayList<>();

    @Comment("Load all worlds on startup")
    @CustomKey("load-all-worlds")
    private boolean loadAllWorlds = true;

    @Comment("Worlds where level auto-save is disabled")
    @CustomKey("auto-save-disabled-worlds")
    private List<String> autoSaveDisabledWorlds = new ArrayList<>();

    @Comment("Per-world custom settings (generator, seed, generator-settings)")
    private Map<String, WorldEntry> worlds = new LinkedHashMap<>();
}
