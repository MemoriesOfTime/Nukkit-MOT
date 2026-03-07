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
public class PlayerSettings extends OkaeriConfig {

    @Comment("Save player data to disk")
    @CustomKey("save-player-data")
    private boolean savePlayerData = true;

    @Comment("Save player data by UUID instead of name")
    @CustomKey("save-player-data-by-uuid")
    private boolean savePlayerDataByUuid = true;

    @Comment("Allow persona skins (custom player models)")
    @CustomKey("persona-skins")
    private boolean personaSkins = true;

    @Comment("Cooldown for skin changes (seconds)")
    @CustomKey("skin-change-cooldown")
    private int skinChangeCooldown = 15;

    @Comment("Don't limit skin geometry size")
    @CustomKey("do-not-limit-skin-geometry")
    private boolean doNotLimitSkinGeometry = true;

    @Comment("Don't limit interaction distance")
    @CustomKey("do-not-limit-interactions")
    private boolean doNotLimitInteractions = false;

    @Comment("How to handle spaces in usernames (ignore, replace, deny)")
    @CustomKey("space-name-mode")
    private String spaceNameMode = "ignore";

    @Comment("Allow XP bottles in creative mode")
    @CustomKey("xp-bottles-on-creative")
    private boolean xpBottlesOnCreative = true;

    @Comment("Allow stopping server from in-game")
    @CustomKey("stop-in-game")
    private boolean stopInGame = false;

    @Comment("Allow opping players from in-game")
    @CustomKey("op-in-game")
    private boolean opInGame = true;
}
