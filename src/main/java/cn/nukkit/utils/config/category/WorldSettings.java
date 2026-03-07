package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
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
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class WorldSettings extends OkaeriConfig {

    @Comment("Enable the Nether dimension")
    private boolean nether = true;

    @Comment("Enable the End dimension")
    private boolean end = true;

    @Comment("Enable vanilla portal mechanics")
    private boolean vanillaPortals = true;

    @Comment("Ticks to wait in portal before teleporting")
    private int portalTicks = 80;

    @Comment("Worlds that should have their own nether dimensions, otherwise using the default nether world")
    private List<String> multiNetherWorlds = new ArrayList<>();

    @Comment("Worlds where the experimental built-in anti-xray is enabled")
    private List<String> antiXrayWorlds = new ArrayList<>();

    @Comment("Worlds where random block ticking is disabled")
    private List<String> doNotTickWorlds = new ArrayList<>();

    @Comment("Worlds where entity auto spawning is not allowed")
    private List<String> entitySpawningDisabledWorlds = new ArrayList<>();

    @Comment("Load all worlds on startup")
    private boolean loadAllWorlds = true;

    @Comment("Worlds where level auto-save is disabled")
    private List<String> autoSaveDisabledWorlds = new ArrayList<>();

    @Comment("Per-world custom settings (generator, seed, generator-settings)")
    private Map<String, WorldEntry> worlds = new LinkedHashMap<>();
}
