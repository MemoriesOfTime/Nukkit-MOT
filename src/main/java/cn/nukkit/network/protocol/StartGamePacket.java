package cn.nukkit.network.protocol;

import cn.nukkit.Server;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.types.ExperimentData;
import cn.nukkit.network.protocol.types.NetworkPermissions;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@ToString
public class StartGamePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.START_GAME_PACKET;

    public static final int GAME_PUBLISH_SETTING_NO_MULTI_PLAY = 0;
    public static final int GAME_PUBLISH_SETTING_INVITE_ONLY = 1;
    public static final int GAME_PUBLISH_SETTING_FRIENDS_ONLY = 2;
    public static final int GAME_PUBLISH_SETTING_FRIENDS_OF_FRIENDS = 3;
    public static final int GAME_PUBLISH_SETTING_PUBLIC = 4;
    public final List<ExperimentData> experiments = new ObjectArrayList<>();
    public String version;

    public long entityUniqueId;
    public long entityRuntimeId;
    public int playerGamemode;
    public float x;
    public float y;
    public float z;
    public float yaw;
    public float pitch;
    public int seed;
    public byte dimension;
    public int generator = 1;
    public int worldGamemode;
    public int difficulty;
    public int spawnX;
    public int spawnY;
    public int spawnZ;
    public boolean hasAchievementsDisabled = true;
    public boolean worldEditor;
    public int dayCycleStopTime = -1;
    public boolean eduMode = false;
    public int eduEditionOffer = 0;
    public boolean hasEduFeaturesEnabled = false;
    public float rainLevel;
    public float lightningLevel;
    public boolean hasConfirmedPlatformLockedContent = false;
    public boolean multiplayerGame = true;
    public boolean broadcastToLAN = true;
    public boolean broadcastToXboxLive = true;
    public int xblBroadcastIntent = GAME_PUBLISH_SETTING_PUBLIC;
    public int platformBroadcastIntent = GAME_PUBLISH_SETTING_PUBLIC;
    public boolean commandsEnabled;
    public boolean isTexturePacksRequired = false;
    public GameRules gameRules;
    public boolean bonusChest = false;
    public boolean hasStartWithMapEnabled = false;
    public boolean trustPlayers = false;
    public int permissionLevel = 1;
    public int gamePublish = 4;
    public int serverChunkTickRange = 4;
    public boolean broadcastToPlatform;
    public int platformBroadcastMode = 4;
    public boolean xblBroadcastIntentOld = true;
    public boolean hasLockedBehaviorPack = false;
    public boolean hasLockedResourcePack = false;
    public boolean isFromLockedWorldTemplate = false;
    public boolean isUsingMsaGamertagsOnly = false;
    public boolean isFromWorldTemplate = false;
    public boolean isWorldTemplateOptionLocked = false;
    public boolean isOnlySpawningV1Villagers = false;
    public String vanillaVersion = Utils.getVersionByProtocol(ProtocolInfo.CURRENT_PROTOCOL);
    public String levelId = "";
    public String worldName;
    public String premiumWorldTemplateId = "";
    public boolean isTrial = false;
    public boolean isMovementServerAuthoritative;
    public boolean isServerAuthoritativeBlockBreaking;
    public long currentTick;
    public int enchantmentSeed;
    public String multiplayerCorrelationId = "";
    public boolean isDisablingPersonas;
    public boolean isDisablingCustomSkins;
    /**
     * @since v527
     */
    public CompoundTag playerPropertyData = new CompoundTag("");
    public boolean clientSideGenerationEnabled;
    public byte chatRestrictionLevel;
    public boolean disablePlayerInteractions;
    /**
     * @since v567
     */
    public boolean emoteChatMuted;
    /**
     * Whether block runtime IDs should be replaced by 32-bit integer hashes of NBT block state.
     * Unlike runtime IDs, this hashes should be persistent across versions and should make support for data-driven/custom blocks easier.
     *
     * @since v582
     */
    public boolean blockNetworkIdsHashed;
    /**
     * @since v582
     */
    public boolean createdInEditor;
    /**
     * @since v582
     */
    public boolean exportedFromEditor;
    /**
     * @since v588
     */
    public NetworkPermissions networkPermissions = NetworkPermissions.DEFAULT;
    /**
     * @since v671
     */
    public boolean hardcore;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityUniqueId(this.entityUniqueId);
        this.putEntityRuntimeId(this.entityRuntimeId);
        this.putVarInt(this.playerGamemode);
        this.putVector3f(this.x, this.y, this.z);
        this.putLFloat(this.yaw);
        this.putLFloat(this.pitch);

        /* Level settings start */
        if (protocol >= ProtocolInfo.v1_18_30) {
            this.putLLong(this.seed);
        } else {
            this.putVarInt(this.seed);
        }
        if (protocol >= 407) {
            this.putLShort(0x00); // SpawnBiomeType - Default
            this.putString(protocol >= ProtocolInfo.v1_16_100 ? "plains" : ""); // UserDefinedBiomeName
        }
        this.putVarInt(this.dimension);
        this.putVarInt(this.generator);
        this.putVarInt(this.worldGamemode);
        if (this.protocol >= ProtocolInfo.v1_20_80) {
            this.putBoolean(this.hardcore);
        }
        this.putVarInt(this.difficulty);
        this.putBlockVector3(this.spawnX, this.spawnY, this.spawnZ);
        this.putBoolean(this.hasAchievementsDisabled);
        if (protocol >= ProtocolInfo.v1_19_10) {
            this.putBoolean(this.worldEditor);
            if (protocol >= ProtocolInfo.v1_19_80) {
                this.putBoolean(this.createdInEditor);
                this.putBoolean(this.exportedFromEditor);
            }
        }
        this.putVarInt(this.dayCycleStopTime);
        if (protocol >= 388) {
            this.putVarInt(this.eduEditionOffer);
        } else {
            this.putBoolean(this.eduMode);
        }
        if (protocol > 224) {
            this.putBoolean(this.hasEduFeaturesEnabled);
            if (protocol >= 407) {
                this.putString(""); // Education Edition Product ID
            }
        }
        this.putLFloat(this.rainLevel);
        this.putLFloat(this.lightningLevel);
        if (protocol >= 332) {
            this.putBoolean(this.hasConfirmedPlatformLockedContent);
        }
        if (protocol >= ProtocolInfo.v1_2_0) {
            this.putBoolean(this.multiplayerGame);
            this.putBoolean(this.broadcastToLAN);
        }
        if (protocol >= 332) {
            this.putVarInt(this.xblBroadcastIntent);
            this.putVarInt(this.platformBroadcastIntent);
        } else if (protocol >= ProtocolInfo.v1_2_0) {
            this.putBoolean(this.broadcastToXboxLive);
        }
        this.putBoolean(this.commandsEnabled);
        this.putBoolean(this.isTexturePacksRequired);
        this.putGameRules(protocol, gameRules);
        if (protocol >= ProtocolInfo.v1_16_100) {
            if (Server.getInstance().enableExperimentMode && !this.experiments.isEmpty()) {
                this.putLInt(this.experiments.size()); // Experiment count
                for (ExperimentData experiment : this.experiments) {
                    this.putString(experiment.getName());
                    this.putBoolean(experiment.isEnabled());
                }
                this.putBoolean(true); // Were experiments previously toggled
            } else {
                this.putLInt(0); // Experiment count
                this.putBoolean(false); // Were experiments previously toggled
            }
        }
        if (protocol >= ProtocolInfo.v1_2_0) {
            this.putBoolean(this.bonusChest);
            if (protocol > 201) {
                this.putBoolean(this.hasStartWithMapEnabled);
            }
            if (protocol < 332) {
                this.putBoolean(this.trustPlayers);
            }
            this.putVarInt(this.permissionLevel);
            if (protocol < 332) {
                this.putVarInt(this.gamePublish);
            }
        }
        if (protocol >= 201) {
            this.putLInt(this.serverChunkTickRange);
        }
        if (protocol >= 223 && protocol < 332) {
            this.putBoolean(this.broadcastToPlatform);
            this.putVarInt(this.platformBroadcastMode);
            this.putBoolean(this.xblBroadcastIntentOld);
        }
        if (protocol > 224) {
            this.putBoolean(this.hasLockedBehaviorPack);
            this.putBoolean(this.hasLockedResourcePack);
            this.putBoolean(this.isFromLockedWorldTemplate);
        }
        if (protocol >= 291) {
            this.putBoolean(this.isUsingMsaGamertagsOnly);
            if (protocol >= 313) {
                this.putBoolean(this.isFromWorldTemplate);
                this.putBoolean(this.isWorldTemplateOptionLocked);
                if (protocol >= 361) {
                    this.putBoolean(this.isOnlySpawningV1Villagers);
                    if (protocol >= ProtocolInfo.v1_13_0) {
                        if (protocol >= ProtocolInfo.v1_19_20) {
                            this.putBoolean(this.isDisablingPersonas);
                            this.putBoolean(this.isDisablingCustomSkins);
                            if (protocol >= ProtocolInfo.v1_19_60) {
                                this.putBoolean(this.emoteChatMuted);
                            }
                        }
                        this.putString(this.vanillaVersion);
                    }
                }
            }
            if (protocol >= ProtocolInfo.v1_16_0) {
                this.putLInt(protocol >= ProtocolInfo.v1_16_100 ? 16 : 0); // Limited world width
                this.putLInt(protocol >= ProtocolInfo.v1_16_100 ? 16 : 0); // Limited world height
                this.putBoolean(false); // Nether type
                if (protocol >= ProtocolInfo.v1_17_30) { // EduSharedUriResource
                    this.putString(""); // buttonName
                    this.putString(""); // linkUri
                }
                this.putBoolean(/*Server.getInstance().enableExperimentMode*/ false); //Force Experimental Gameplay (exclusive to debug clients)
                if (protocol >= ProtocolInfo.v1_19_20) {
                    this.putByte(this.chatRestrictionLevel);
                    this.putBoolean(this.disablePlayerInteractions);
                }
            }
        }
        /* Level settings end */

        this.putString(this.levelId);
        this.putString(this.worldName);
        this.putString(this.premiumWorldTemplateId);
        this.putBoolean(this.isTrial);
        if (protocol >= ProtocolInfo.v1_13_0) {
            if (protocol >= ProtocolInfo.v1_16_100) {
                if (protocol >= ProtocolInfo.v1_16_210) {
                    this.putVarInt(this.isMovementServerAuthoritative ? 1 : 0); // 2 - rewind
                    this.putVarInt(0); // RewindHistorySize
                    this.putBoolean(this.isServerAuthoritativeBlockBreaking); // isServerAuthoritativeBlockBreaking
                } else {
                    this.putVarInt(this.isMovementServerAuthoritative ? 1 : 0); // 2 - rewind
                }
            } else {
                this.putBoolean(this.isMovementServerAuthoritative);
            }
        }
        this.putLLong(this.currentTick);
        if (protocol >= ProtocolInfo.v1_2_0) {
            this.putVarInt(this.enchantmentSeed);
        }
        if (protocol > ProtocolInfo.v1_5_0) {
            if (protocol >= ProtocolInfo.v1_16_100) {
                this.putUnsignedVarInt(0); // Custom blocks
            } else {
                this.put(GlobalBlockPalette.getCompiledTable(this.protocol));
            }
            if (protocol >= ProtocolInfo.v1_12_0) {
                this.put(RuntimeItems.getMapping(protocol).getItemPalette());
            }
            this.putString(this.multiplayerCorrelationId);
            if (protocol == 354 && version != null && version.startsWith("1.11.4")) {
                this.putBoolean(this.isOnlySpawningV1Villagers);
            } else if (protocol >= ProtocolInfo.v1_16_0) {
                this.putBoolean(false); // isInventoryServerAuthoritative
                if (protocol >= ProtocolInfo.v1_16_230_50) {
                    this.putString(""); // serverEngine
                    if (protocol >= ProtocolInfo.v1_18_0) {
                        if (protocol >= ProtocolInfo.v1_19_0_29) {
                            try {
                                this.put(NBTIO.writeNetwork(this.playerPropertyData));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        this.putLLong(0L); // BlockRegistryChecksum
                        if (protocol >= ProtocolInfo.v1_19_0_29) {
                            this.putUUID(new UUID(0, 0)); // worldTemplateId
                            if (protocol >= ProtocolInfo.v1_19_20) {
                                this.putBoolean(this.clientSideGenerationEnabled);
                                if (protocol >= ProtocolInfo.v1_19_80) {
                                    this.putBoolean(this.blockNetworkIdsHashed);
                                    if (protocol >= ProtocolInfo.v1_20_0_23) {
                                        this.putBoolean(this.networkPermissions.isServerAuthSounds());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private static class ItemData {
        private String name;
        private int id;
    }
}
