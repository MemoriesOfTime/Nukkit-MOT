package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class PlayerSettings extends OkaeriConfig {

    @Comment("Save player data to disk")
    private boolean savePlayerData = true;

    @Comment("Save player data by UUID instead of name")
    private boolean savePlayerDataByUuid = true;

    @Comment("Allow persona skins (custom player models)")
    private boolean personaSkins = true;

    @Comment("Cooldown for skin changes (seconds)")
    private int skinChangeCooldown = 15;

    @Comment("Don't limit skin geometry size")
    private boolean doNotLimitSkinGeometry = true;

    @Comment("Don't limit interaction distance")
    private boolean doNotLimitInteractions = false;

    @Comment("How to handle spaces in usernames (ignore, replace, deny)")
    private String spaceNameMode = "ignore";

    @Comment("Allow XP bottles in creative mode")
    private boolean xpBottlesOnCreative = true;

    @Comment("Allow stopping server from in-game")
    private boolean stopInGame = false;

    @Comment("Allow opping players from in-game")
    private boolean opInGame = true;
}
