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
public class GameFeatureSettings extends OkaeriConfig {

    @Comment("Enable achievements")
    private boolean achievements = true;

    @Comment("Announce player achievements in chat")
    @CustomKey("announce-player-achievements")
    private boolean announcePlayerAchievements = true;

    @Comment("Allow beds to set spawn points")
    @CustomKey("bed-spawnpoints")
    private boolean bedSpawnpoints = true;

    @Comment("Allow explosions to break blocks")
    @CustomKey("explosion-break-blocks")
    private boolean explosionBreakBlocks = true;

    @Comment("Allow spawners to drop when broken")
    @CustomKey("drop-spawners")
    private boolean dropSpawners = true;

    @Comment("Enable anvil usage")
    @CustomKey("anvils-enabled")
    private boolean anvilsEnabled = true;

    @Comment("Enable vanilla boss bars")
    @CustomKey("vanilla-bossbars")
    private boolean vanillaBossbars = false;

    @Comment("Use client-side spectator mode")
    @CustomKey("use-client-spectator")
    private boolean useClientSpectator = true;

    @Comment("Enable experimental mode features")
    @CustomKey("enable-experiment-mode")
    private boolean enableExperimentMode = true;

    @Comment("Minimum protocol version to allow (0 = no limit)")
    @CustomKey("multiversion-min-protocol")
    private int multiversionMinProtocol = 0;

    @Comment("Maximum protocol version to allow (-1 = no limit)")
    @CustomKey("multiversion-max-protocol")
    private int multiversionMaxProtocol = -1;

    @Comment("Enable raw ore items")
    @CustomKey("enable-raw-ores")
    private boolean enableRawOres = true;

    @Comment("Enable new painting variants")
    @CustomKey("enable-new-paintings")
    private boolean enableNewPaintings = true;

    @Comment("Enable new chicken egg laying mechanics")
    @CustomKey("enable-new-chicken-eggs-laying")
    private boolean enableNewChickenEggsLaying = true;

    @Comment("Force safety enchantment checks")
    @CustomKey("forced-safety-enchant")
    private boolean forcedSafetyEnchant = true;

    @Comment("Enable vibrant visuals feature")
    @CustomKey("enable-vibrant-visuals")
    private boolean enableVibrantVisuals = true;

    @Comment("Enable raytracing support")
    @CustomKey("enable-raytracing")
    private boolean enableRaytracing = true;

    @Comment("Temporarily ban players who fail Xbox authentication")
    @CustomKey("temp-ip-ban-failed-xbox-auth")
    private boolean tempIpBanFailedXboxAuth = false;

    @Comment("Enable stricter IP bans")
    @CustomKey("strong-ip-bans")
    private boolean strongIpBans = false;

    @Comment("Check operator movement for cheating")
    @CustomKey("check-op-movement")
    private boolean checkOpMovement = false;
}
