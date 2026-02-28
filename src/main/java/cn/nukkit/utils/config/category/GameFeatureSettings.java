package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class GameFeatureSettings extends OkaeriConfig {

    @Comment("Enable achievements")
    private boolean achievements = true;

    @Comment("Announce player achievements in chat")
    private boolean announcePlayerAchievements = true;

    @Comment("Allow beds to set spawn points")
    private boolean bedSpawnpoints = true;

    @Comment("Allow explosions to break blocks")
    private boolean explosionBreakBlocks = true;

    @Comment("Allow spawners to drop when broken")
    private boolean dropSpawners = true;

    @Comment("Enable anvil usage")
    private boolean anvilsEnabled = true;

    @Comment("Enable vanilla boss bars")
    private boolean vanillaBossbars = false;

    @Comment("Use client-side spectator mode")
    private boolean useClientSpectator = true;

    @Comment("Enable experimental mode features")
    private boolean enableExperimentMode = true;

    @Comment("Minimum protocol version to allow (0 = no limit)")
    private int multiversionMinProtocol = 0;

    @Comment("Maximum protocol version to allow (-1 = no limit)")
    private int multiversionMaxProtocol = -1;

    @Comment("Enable raw ore items")
    private boolean enableRawOres = true;

    @Comment("Enable new painting variants")
    private boolean enableNewPaintings = true;

    @Comment("Enable new chicken egg laying mechanics")
    private boolean enableNewChickenEggsLaying = true;

    @Comment("Force safety enchantment checks")
    private boolean forcedSafetyEnchant = true;

    @Comment("Enable vibrant visuals feature")
    private boolean enableVibrantVisuals = true;

    @Comment("Enable raytracing support")
    private boolean enableRaytracing = true;

    @Comment("Temporarily ban players who fail Xbox authentication")
    private boolean tempIpBanFailedXboxAuth = false;

    @Comment("Enable stricter IP bans")
    private boolean strongIpBans = false;

    @Comment("Check operator movement for cheating")
    private boolean checkOpMovement = false;
}
