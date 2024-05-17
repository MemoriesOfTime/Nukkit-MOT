package cn.nukkit;

import cn.nukkit.network.protocol.AdventureSettingsPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.UpdateAbilitiesPacket;
import cn.nukkit.network.protocol.UpdateAdventureSettingsPacket;
import cn.nukkit.network.protocol.types.AbilityLayer;
import cn.nukkit.network.protocol.types.PlayerAbility;

import java.util.EnumMap;
import java.util.Map;

/**
 * Adventure settings
 *
 * @author MagicDroidX
 * Nukkit Project
 */
public class AdventureSettings implements Cloneable {

    public static final int PERMISSION_NORMAL = 0;
    public static final int PERMISSION_OPERATOR = 1;
    public static final int PERMISSION_HOST = 2;
    public static final int PERMISSION_AUTOMATION = 3;
    public static final int PERMISSION_ADMIN = 4;

    private final Map<Type, Boolean> values = new EnumMap<>(Type.class);

    private Player player;

    public AdventureSettings(Player player) {
        this.player = player;
    }

    public AdventureSettings clone(Player newPlayer) {
        try {
            AdventureSettings settings = (AdventureSettings) super.clone();
            settings.player = newPlayer;
            return settings;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    /**
     * Set an adventure setting value
     *
     * @param type  adventure setting
     * @param value new value
     * @return AdventureSettings
     */
    public AdventureSettings set(Type type, boolean value) {
        this.values.put(type, value);
        return this;
    }

    /**
     * Get an adventure setting value
     *
     * @param type adventure setting
     * @return value
     */
    public boolean get(Type type) {
        Boolean value = this.values.get(type);
        return value == null ? type.getDefaultValue() : value;
    }

    /**
     * Send adventure settings values to the player
     */
    public void update() {
        this.update(true);
    }

    /**
     * Send adventure settings values to the player
     *
     * @param reset reset in air ticks
     */
    void update(boolean reset) {
        if (this.player.protocol >= ProtocolInfo.v1_19_30_23) {
            UpdateAbilitiesPacket packet = new UpdateAbilitiesPacket();
            packet.setEntityId(player.getId());
            packet.setCommandPermission(player.isOp() ? UpdateAbilitiesPacket.CommandPermission.OPERATOR : UpdateAbilitiesPacket.CommandPermission.NORMAL);
            packet.setPlayerPermission(player.isOp() && !player.isSpectator() ? UpdateAbilitiesPacket.PlayerPermission.OPERATOR : UpdateAbilitiesPacket.PlayerPermission.MEMBER);

            AbilityLayer layer = new AbilityLayer();
            layer.setLayerType(AbilityLayer.Type.BASE);
            layer.getAbilitiesSet().addAll(PlayerAbility.VALUES);

            // TODO Multiversion 移除低版本不支持的内容
            if (player.protocol < ProtocolInfo.v1_19_70) {
                layer.getAbilitiesSet().remove(PlayerAbility.PRIVILEGED_BUILDER);
            }

            for (Type type : Type.values()) {
                if (type.isAbility() && this.get(type) && player.protocol >= type.protocol) {
                    layer.getAbilityValues().add(type.getAbility());
                }
            }

            // Because we send speed
            layer.getAbilityValues().add(PlayerAbility.WALK_SPEED);
            layer.getAbilityValues().add(PlayerAbility.FLY_SPEED);

            if (player.isCreative()) { // Make sure player can interact with creative menu
                layer.getAbilityValues().add(PlayerAbility.INSTABUILD);
            }

            if (player.isOp()) {
                layer.getAbilityValues().add(PlayerAbility.OPERATOR_COMMANDS);
            }

            layer.setWalkSpeed(Player.DEFAULT_SPEED);
            layer.setFlySpeed(Player.DEFAULT_FLY_SPEED);
            packet.getAbilityLayers().add(layer);

            if (this.get(Type.NO_CLIP)) {
                AbilityLayer layer2 = new AbilityLayer();
                layer2.setLayerType(AbilityLayer.Type.SPECTATOR);

                layer2.getAbilitiesSet().addAll(PlayerAbility.VALUES);
                layer2.getAbilitiesSet().remove(PlayerAbility.FLY_SPEED); //不要设置速度，这会导致视角出错
                layer2.getAbilitiesSet().remove(PlayerAbility.WALK_SPEED);

                layer2.getAbilityValues().add(PlayerAbility.FLYING);
                layer2.getAbilityValues().add(PlayerAbility.NO_CLIP);
                packet.getAbilityLayers().add(layer2);
            }

            UpdateAdventureSettingsPacket adventurePacket = new UpdateAdventureSettingsPacket();
            adventurePacket.setAutoJump(get(Type.AUTO_JUMP));
            adventurePacket.setImmutableWorld(get(Type.WORLD_IMMUTABLE));
            adventurePacket.setNoMvP(get(Type.NO_MVP));
            adventurePacket.setNoPvM(get(Type.NO_PVM));
            adventurePacket.setShowNameTags(get(Type.SHOW_NAME_TAGS));

            player.dataPacket(packet);
            player.dataPacket(adventurePacket);
        } else {
            AdventureSettingsPacket pk = new AdventureSettingsPacket();
            for (Type t : Type.values()) {
                if (t.getId() <= 0) {
                    continue;
                }
                pk.setFlag(t.getId(), get(t));
            }

            pk.commandPermission = (player.isOp() && player.showAdmin() ? AdventureSettingsPacket.PERMISSION_OPERATOR : AdventureSettingsPacket.PERMISSION_NORMAL);
            pk.playerPermission = (player.isOp() && player.showAdmin() && !player.isSpectator() ? Player.PERMISSION_OPERATOR : Player.PERMISSION_MEMBER);
            pk.entityUniqueId = player.getId();

            //Server.broadcastPacket(player.getViewers().values(), pk);
            player.dataPacket(pk);
        }

        if (reset) {
            player.resetInAirTicks();
        }
    }

    /**
     * List of adventure settings
     */
    public enum Type {
        WORLD_IMMUTABLE(AdventureSettingsPacket.WORLD_IMMUTABLE, null, false),
        NO_PVM(AdventureSettingsPacket.NO_PVM, null, false),
        NO_MVP(AdventureSettingsPacket.NO_MVP, PlayerAbility.INVULNERABLE, false),
        SHOW_NAME_TAGS(AdventureSettingsPacket.SHOW_NAME_TAGS, null, false),
        AUTO_JUMP(AdventureSettingsPacket.AUTO_JUMP, null, true),
        ALLOW_FLIGHT(AdventureSettingsPacket.ALLOW_FLIGHT, PlayerAbility.MAY_FLY, false),
        NO_CLIP(AdventureSettingsPacket.NO_CLIP, PlayerAbility.NO_CLIP, false),
        WORLD_BUILDER(AdventureSettingsPacket.WORLD_BUILDER, PlayerAbility.WORLD_BUILDER, false),
        FLYING(AdventureSettingsPacket.FLYING, PlayerAbility.FLYING, false),
        MUTED(AdventureSettingsPacket.MUTED, PlayerAbility.MUTED, false),
        MINE(AdventureSettingsPacket.MINE, PlayerAbility.MINE, true),
        DOORS_AND_SWITCHED(AdventureSettingsPacket.DOORS_AND_SWITCHES, PlayerAbility.DOORS_AND_SWITCHES, true),
        OPEN_CONTAINERS(AdventureSettingsPacket.OPEN_CONTAINERS, PlayerAbility.OPEN_CONTAINERS, true),
        ATTACK_PLAYERS(AdventureSettingsPacket.ATTACK_PLAYERS, PlayerAbility.ATTACK_PLAYERS, true),
        ATTACK_MOBS(AdventureSettingsPacket.ATTACK_MOBS, PlayerAbility.ATTACK_MOBS, true),
        OPERATOR(AdventureSettingsPacket.OPERATOR, PlayerAbility.OPERATOR_COMMANDS, false),
        TELEPORT(AdventureSettingsPacket.TELEPORT, PlayerAbility.TELEPORT, false),
        BUILD(AdventureSettingsPacket.BUILD, PlayerAbility.BUILD, true),
        PRIVILEGED_BUILDER(0, PlayerAbility.PRIVILEGED_BUILDER, false, ProtocolInfo.v1_19_70),

        @Deprecated //1.19.30弃用
        DEFAULT_LEVEL_PERMISSIONS(AdventureSettingsPacket.DEFAULT_LEVEL_PERMISSIONS, null, false);

        private final int id;
        private final PlayerAbility ability;
        private final boolean defaultValue;
        private final int protocol;

        Type(int id, PlayerAbility ability, boolean defaultValue) {
            this(id, ability, defaultValue, -1);
        }

        Type(int id, PlayerAbility ability, boolean defaultValue, int protocol) {
            this.id = id;
            this.ability = ability;
            this.defaultValue = defaultValue;
            this.protocol = protocol;
        }

        /**
         * Get ID
         *
         * @return ID
         */
        public int getId() {
            return id;
        }

        /**
         * Get default value
         *
         * @return default value
         */
        public boolean getDefaultValue() {
            return this.defaultValue;
        }

        public PlayerAbility getAbility() {
            return this.ability;
        }

        public boolean isAbility() {
            return this.ability != null;
        }
    }
}
