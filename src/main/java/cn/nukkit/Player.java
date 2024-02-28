package cn.nukkit;

import cn.nukkit.AdventureSettings.Type;
import cn.nukkit.block.*;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCampfire;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandDataVersions;
import cn.nukkit.command.defaults.HelpCommand;
import cn.nukkit.entity.*;
import cn.nukkit.entity.data.*;
import cn.nukkit.entity.data.property.EntityProperty;
import cn.nukkit.entity.item.*;
import cn.nukkit.entity.mob.EntityWalkingMob;
import cn.nukkit.entity.mob.EntityWolf;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.EntityDamageEvent.DamageModifier;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryPickupArrowEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.inventory.InventoryPickupTridentEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.event.player.PlayerInteractEvent.Action;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindow;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowDialog;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.*;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.data.ReleaseItemData;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.inventory.transaction.data.UseItemOnEntityData;
import cn.nukkit.item.*;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.food.Food;
import cn.nukkit.item.trim.TrimFactory;
import cn.nukkit.lang.LangCode;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.*;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.level.particle.PunchBlockParticle;
import cn.nukkit.level.sound.ExperienceOrbSound;
import cn.nukkit.math.*;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.encryption.PrepareEncryptionTask;
import cn.nukkit.network.process.DataPacketManager;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.*;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.permission.PermissibleBase;
import cn.nukkit.permission.Permission;
import cn.nukkit.permission.PermissionAttachment;
import cn.nukkit.permission.PermissionAttachmentInfo;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.scoreboard.displayer.IScoreboardViewer;
import cn.nukkit.scoreboard.scoreboard.IScoreboard;
import cn.nukkit.scoreboard.scoreboard.IScoreboardLine;
import cn.nukkit.scoreboard.scorer.PlayerScorer;
import cn.nukkit.utils.*;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonParser;
import io.netty.util.internal.PlatformDependent;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteOrder;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Player class
 *
 * @author MagicDroidX &amp; Box
 * Nukkit Project
 */
@Log4j2
public class Player extends EntityHuman implements CommandSender, InventoryHolder, ChunkLoader, IPlayer, IScoreboardViewer {

    public static final Player[] EMPTY_ARRAY = new Player[0];

    public static final int SURVIVAL = 0;
    public static final int CREATIVE = 1;
    public static final int ADVENTURE = 2;
    public static final int SPECTATOR = 3;
    public static final int VIEW = SPECTATOR;

    public static final int CRAFTING_SMALL = 0;
    public static final int CRAFTING_BIG = 1;
    public static final int CRAFTING_ANVIL = 2;
    public static final int CRAFTING_ENCHANT = 3;
    public static final int CRAFTING_BEACON = 4;

    public static final int CRAFTING_SMITHING = 1003;

    public static final int TRADE_WINDOW_ID = 500;

    public static final float DEFAULT_SPEED = 0.1f;
    public static final float MAXIMUM_SPEED = 0.5f;
    public static final float DEFAULT_FLY_SPEED = 0.05f;

    public static final int PERMISSION_CUSTOM = 3;
    public static final int PERMISSION_OPERATOR = 2;
    public static final int PERMISSION_MEMBER = 1;
    public static final int PERMISSION_VISITOR = 0;

    public static final int ANVIL_WINDOW_ID = 2;
    public static final int ENCHANT_WINDOW_ID = 3;
    public static final int BEACON_WINDOW_ID = 4;
    public static final int GRINDSTONE_WINDOW_ID = 5;
    public static final int SMITHING_WINDOW_ID = 6;
    /**
     * @since 649 1.20.60
     * 自1.20.60开始，需要发送ContainerOpenPacket给玩家才能正常打开讲台上的书
     * 在原版中id按顺序增加，但测试中采用固定id也可正常实现功能
     */
    public static final int LECTERN_WINDOW_ID = 7;

    // 后续创建的窗口应该从此数值开始
    public static final int MINIMUM_OTHER_WINDOW_ID = Utils.dynamic(8);

    protected static final int RESOURCE_PACK_CHUNK_SIZE = 8 * 1024; // 8KB

    protected final SourceInterface interfaz;
    protected final NetworkPlayerSession networkSession;

    public boolean playedBefore;
    public boolean spawned = false;
    public boolean loggedIn = false;
    protected boolean loginVerified = false;
    private int unverifiedPackets;
    private boolean loginPacketReceived;
    protected boolean awaitingEncryptionHandshake;
    public int gamemode;
    public long lastBreak = -1;
    private BlockVector3 lastBreakPosition = new BlockVector3();

    protected int windowCnt = MINIMUM_OTHER_WINDOW_ID;

    protected final BiMap<Inventory, Integer> windows = HashBiMap.create();
    protected final BiMap<Integer, Inventory> windowIndex = windows.inverse();
    protected final Set<Integer> permanentWindows = new IntOpenHashSet();
    private boolean inventoryOpen;
    protected int closingWindowId = Integer.MIN_VALUE;

    public Vector3 speed = null;

    private final Queue<Vector3> clientMovements = PlatformDependent.newMpscQueue(4);
    public final HashSet<String> achievements = new HashSet<>();

    public int craftingType = CRAFTING_SMALL;

    protected PlayerUIInventory playerUIInventory;
    protected CraftingGrid craftingGrid;
    protected CraftingTransaction craftingTransaction;
    protected EnchantTransaction enchantTransaction;
    protected RepairItemTransaction repairItemTransaction;
    protected SmithingTransaction smithingTransaction;
    protected TradingTransaction tradingTransaction;

    protected long randomClientId;

    protected Vector3 forceMovement = null;

    protected Vector3 teleportPosition = null;

    protected boolean connected = true;
    protected final InetSocketAddress rawSocketAddress;
    protected InetSocketAddress socketAddress;
    protected boolean removeFormat = true;

    protected String username;
    protected String iusername;
    protected String displayName;

    /**
     * Client protocol version
     */
    public int protocol = 999;
    /**
     * Client RakNet protocol version
     */
    public int raknetProtocol;
    /**
     * Client version string
     */
    protected String version;

    protected int startAction = -1;

    protected Vector3 sleeping = null;

    private final int loaderId;

    public final Map<Long, Boolean> usedChunks = new Long2ObjectOpenHashMap<>();

    private int chunksSent = 0;
    private boolean hasSpawnChunks;
    protected final Long2ObjectLinkedOpenHashMap<Boolean> loadQueue = new Long2ObjectLinkedOpenHashMap<>();
    protected int nextChunkOrderRun = 1;

    protected final Map<UUID, Player> hiddenPlayers = new HashMap<>();

    protected Vector3 newPosition = null;

    protected int chunkRadius;
    protected int viewDistance;

    protected Position spawnPosition;
    protected Position spawnBlockPosition;

    protected int inAirTicks = 0;
    protected int startAirTicks = 10;

    protected AdventureSettings adventureSettings;

    protected boolean checkMovement = true;

    private PermissibleBase perm;
    /**
     * Option to hide admin permissions from player list tab in client.
     * Admin player shown in server list will look same as normal player.
     */
    private boolean showAdmin = true;
    /**
     * Option not to spawn the player for others.
     */
    public boolean showToOthers = true;

    private int exp = 0;
    private int expLevel = 0;

    protected PlayerFood foodData = null;

    private Entity killer = null;

    private final AtomicReference<Locale> locale = new AtomicReference<>(null);

    private int hash;

    private String buttonText = "";

    protected boolean enableClientCommand = true;

    private BlockEnderChest viewingEnderChest = null;

    protected LoginChainData loginChainData;

    public Block breakingBlock = null;
    private PlayerBlockActionData lastBlockAction;

    private static final int NO_SHIELD_DELAY = 10;
    private int noShieldTicks;

    public int pickedXPOrb = 0;
    private boolean canPickupXP = true;

    protected int formWindowCount = 0;
    public Map<Integer, FormWindow> formWindows = new Int2ObjectOpenHashMap<>();
    protected Map<Integer, FormWindow> serverSettings = new Int2ObjectOpenHashMap<>();

    protected Map<Long, DummyBossBar> dummyBossBars = new Long2ObjectLinkedOpenHashMap<>();

    protected Cache<String, FormWindowDialog> dialogWindows = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    protected AsyncTask preLoginEventTask = null;
    protected boolean shouldLogin = false;

    private static Stream<Field> pkIDs;

    private int lastEmote;
    private int lastEnderPearl = 20;
    private int lastChorusFruitTeleport = 20;
    public long lastSkinChange = -1;
    private double lastRightClickTime = 0.0;
    private Vector3 lastRightClickPos = null;
    public EntityFishingHook fishing = null;
    public boolean formOpen;
    public boolean locallyInitialized;
    private boolean foodEnabled = true;
    private int failedTransactions;
    private int timeSinceRest;
    private boolean inSoulSand;
    private boolean dimensionChangeInProgress;
    private boolean needDimensionChangeACK;
    /**
     * 用于修复1.20.0连续执行despawnFromAll和spawnToAll导致玩家移动不显示问题
     */
    private int lastDespawnFromAllTick;
    /**
     * 用于修复1.20.0连续执行despawnFromAll和spawnToAll导致玩家移动不显示问题
     */
    private boolean needSpawnToAll;

    /**
     * Packets that can be received before the player has logged in
     */
    private static final List<Byte> PRE_LOGIN_PACKETS = Arrays.asList(
            ProtocolInfo.BATCH_PACKET, ProtocolInfo.LOGIN_PACKET,
            ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET,
            ProtocolInfo.SET_LOCAL_PLAYER_AS_INITIALIZED_PACKET,
            ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET,
            ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET,
            ProtocolInfo.CLIENT_CACHE_STATUS_PACKET,
            ProtocolInfo.PACKET_VIOLATION_WARNING_PACKET,
            ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET,
            ProtocolInfo.CLIENT_TO_SERVER_HANDSHAKE_PACKET);

    @Getter
    @Setter
    protected List<PlayerFogPacket.Fog> fogStack = new ArrayList<>();

    private final @NotNull PlayerHandle playerHandle = new PlayerHandle(this);

    public int getStartActionTick() {
        return startAction;
    }

    public void startAction() {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, true);
        this.startAction = this.server.getTick();
    }

    public void stopAction() {
        this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);
        this.startAction = -1;
    }

    public int getLastEnderPearlThrowingTick() {
        return lastEnderPearl;
    }

    public void onThrowEnderPearl() {
        this.lastEnderPearl = this.server.getTick();
    }

    public int getLastChorusFruitTeleport() {
        return lastChorusFruitTeleport;
    }

    public void onChorusFruitTeleport() {
        this.lastChorusFruitTeleport = this.server.getTick();
    }

    public BlockEnderChest getViewingEnderChest() {
        return viewingEnderChest;
    }

    public void setViewingEnderChest(BlockEnderChest chest) {
        if (chest == null && this.viewingEnderChest != null) {
            this.viewingEnderChest.getViewers().remove(this);
        } else if (chest != null) {
            chest.getViewers().add(this);
        }
        this.viewingEnderChest = chest;
    }

    public TranslationContainer getLeaveMessage() {
        return new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.left", this.displayName);
    }

    /**
     * This might disappear in the future.
     * Please use getUniqueId() instead (IP + clientId + name combo, in the future it'll change to real UUID for online auth)
     *
     * @return random client id
     */
    public Long getClientId() {
        return randomClientId;
    }

    @Override
    public boolean isBanned() {
        return this.server.getNameBans().isBanned(this.username);
    }

    @Override
    public void setBanned(boolean value) {
        if (value) {
            this.server.getNameBans().addBan(this.username, null, null, null);
            this.kick(PlayerKickEvent.Reason.NAME_BANNED, "You are banned!");
        } else {
            this.server.getNameBans().remove(this.username);
        }
    }

    @Override
    public boolean isWhitelisted() {
        return this.server.isWhitelisted(this.username.toLowerCase());
    }

    @Override
    public void setWhitelisted(boolean value) {
        if (value) {
            this.server.addWhitelist(this.username.toLowerCase());
        } else {
            this.server.removeWhitelist(this.username.toLowerCase());
        }
    }

    @Override
    public Player getPlayer() {
        return this;
    }

    @Override
    public Long getFirstPlayed() {
        return this.namedTag != null ? this.namedTag.getLong("firstPlayed") : null;
    }

    @Override
    public Long getLastPlayed() {
        return this.namedTag != null ? this.namedTag.getLong("lastPlayed") : null;
    }

    @Override
    public boolean hasPlayedBefore() {
        return this.playedBefore;
    }

    public AdventureSettings getAdventureSettings() {
        return adventureSettings;
    }

    public void setAdventureSettings(AdventureSettings adventureSettings) {
        this.adventureSettings = adventureSettings.clone(this);
        this.adventureSettings.update();
    }

    public void resetInAirTicks() {
        this.inAirTicks = 0;
    }

    public void setAllowFlight(boolean value) {
        this.adventureSettings.set(Type.ALLOW_FLIGHT, value);
        this.adventureSettings.update();
    }

    public boolean getAllowFlight() {
        return this.adventureSettings.get(Type.ALLOW_FLIGHT);
    }

    public void setAllowModifyWorld(boolean value) {
        this.adventureSettings.set(Type.WORLD_IMMUTABLE, !value);
        this.adventureSettings.set(Type.MINE, value);
        this.adventureSettings.set(Type.BUILD, value);
        this.adventureSettings.update();
    }

    public void setAllowInteract(boolean value) {
        setAllowInteract(value, value);
    }

    public void setAllowInteract(boolean value, boolean containers) {
        this.adventureSettings.set(Type.WORLD_IMMUTABLE, !value);
        this.adventureSettings.set(Type.DOORS_AND_SWITCHED, value);
        this.adventureSettings.set(Type.OPEN_CONTAINERS, containers);
        this.adventureSettings.update();
    }

    public void setAutoJump(boolean value) {
        this.adventureSettings.set(Type.AUTO_JUMP, value);
        this.adventureSettings.update();
    }

    public boolean hasAutoJump() {
        return this.adventureSettings.get(Type.AUTO_JUMP);
    }

    @Override
    public void spawnTo(Player player) {
        if (this.spawned && player.spawned &&
                this.isAlive() && player.isAlive()
                && player.getLevel() == this.level && player.canSee(this) &&
                (!this.isSpectator() || (this.server.useClientSpectator && player.protocol >= ProtocolInfo.v1_19_30)) &&
                this.showToOthers) {
            super.spawnTo(player);
            if (this.isSpectator()) {
                UpdatePlayerGameTypePacket pk = new UpdatePlayerGameTypePacket();
                pk.gameType = GameType.from(getClientFriendlyGamemode(gamemode));
                pk.entityId = this.getId();
                player.dataPacket(pk);
            }
        }
    }

    public boolean getRemoveFormat() {
        return removeFormat;
    }

    public void setRemoveFormat() {
        this.setRemoveFormat(true);
    }

    public void setRemoveFormat(boolean remove) {
        this.removeFormat = remove;
    }

    public boolean canSee(Player player) {
        return !this.hiddenPlayers.containsKey(player.getUniqueId());
    }

    public void hidePlayer(Player player) {
        if (this == player) {
            return;
        }
        this.hiddenPlayers.put(player.getUniqueId(), player);
        player.despawnFrom(this);
    }

    public void showPlayer(Player player) {
        if (this == player) {
            return;
        }
        this.hiddenPlayers.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.spawnTo(this);
        }
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    public boolean canPickupXP() {
        return this.canPickupXP;
    }

    public void setCanPickupXP(boolean canPickupXP) {
        this.canPickupXP = canPickupXP;
    }

    @Override
    public void resetFallDistance() {
        super.resetFallDistance();
        if (this.inAirTicks != 0) {
            this.startAirTicks = 10;
        }
        this.inAirTicks = 0;
    }

    @Override
    public boolean isOnline() {
        return this.connected && this.loggedIn;
    }

    @Override
    public boolean isOp() {
        return this.server.isOp(this.username);
    }

    @Override
    public void setOp(boolean value) {
        if (value == this.isOp()) {
            return;
        }

        if (value) {
            this.server.addOp(this.username);
        } else {
            this.server.removeOp(this.username);
        }

        this.recalculatePermissions();
        this.adventureSettings.update();
        this.sendCommandData();
    }

    /**
     * Set visibility of player's admin status on the player list
     */
    public void setShowAdmin(boolean showAdmin) {
        this.showAdmin = showAdmin;
    }

    /**
     * Get visibility of player's admin status on the player list
     */
    public boolean showAdmin() {
        return this.showAdmin;
    }

    @Override
    public boolean isPermissionSet(String name) {
        return this.perm.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission permission) {
        return this.perm.isPermissionSet(permission);
    }

    @Override
    public boolean hasPermission(String name) {
        return this.perm != null && this.perm.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return this.perm.hasPermission(permission);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return this.addAttachment(plugin, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name) {
        return this.addAttachment(plugin, name, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, Boolean value) {
        return this.perm.addAttachment(plugin, name, value);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        this.perm.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
        this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);

        if (this.perm == null) {
            return;
        }

        this.perm.recalculatePermissions();

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }

        if (this.enableClientCommand && spawned) this.sendCommandData();
    }

    /**
     * Are commands enabled for this player on the client side
     *
     * @return commands enabled
     */
    public boolean isEnableClientCommand() {
        return this.enableClientCommand;
    }

    public void setEnableClientCommand(boolean enable) {
        this.enableClientCommand = enable;
        SetCommandsEnabledPacket pk = new SetCommandsEnabledPacket();
        pk.enabled = enable;
        this.dataPacket(pk);
        if (enable) this.sendCommandData();
    }

    public void sendCommandData() {
        AvailableCommandsPacket pk = new AvailableCommandsPacket();
        Map<String, CommandDataVersions> data = new HashMap<>();

        for (Command command : this.server.getCommandMap().getCommands().values()) {
            //1.20.0+客户端自带help命令
            if (this.protocol >= ProtocolInfo.v1_20_0_23) {
                if (command instanceof HelpCommand || "help".equalsIgnoreCase(command.getName())) {
                    continue;
                }
            }
            if (!command.testPermissionSilent(this) || !command.isRegistered()) {
                continue;
            }

            data.put(command.getName(), command.generateCustomCommandData(this));
        }

        if (!data.isEmpty()) {
            pk.commands = data;
            this.dataPacket(pk);
        }
    }

    @Override
    public Map<String, PermissionAttachmentInfo> getEffectivePermissions() {
        return this.perm.getEffectivePermissions();
    }

    public Player(SourceInterface interfaz, Long clientID, InetSocketAddress socketAddress) {
        super(null, new CompoundTag());
        this.interfaz = interfaz;
        this.networkSession = interfaz.getSession(socketAddress);
        this.perm = new PermissibleBase(this);
        this.server = Server.getInstance();
        this.rawSocketAddress = socketAddress;
        this.socketAddress = socketAddress;
        this.loaderId = Level.generateChunkLoaderId(this);
        this.gamemode = this.server.getGamemode();
        this.setLevel(this.server.getDefaultLevel());
        this.viewDistance = this.server.getViewDistance();
        this.chunkRadius = viewDistance;
        this.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0, 0, 0);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        this.addDefaultWindows();
    }

    @Override
    public boolean isEntity() {
        return true;
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    public boolean isPlayer() {
        return true;
    }

    @Override
    public Player asPlayer() {
        return this;
    }

    public void removeAchievement(String achievementId) {
        achievements.remove(achievementId);
    }

    public boolean hasAchievement(String achievementId) {
        return achievements.contains(achievementId);
    }

    public boolean isConnected() {
        return connected;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (this.spawned) {
            this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.displayName, this.getSkin(), this.loginChainData.getXUID());
        }
    }

    @Override
    public void setSkin(Skin skin) {
        super.setSkin(skin);
        if (this.spawned) {
            this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.displayName, skin, this.loginChainData.getXUID());
        }
    }

    public String getRawAddress() {
        return this.rawSocketAddress.getAddress().getHostAddress();
    }

    public int getRawPort() {
        return this.rawSocketAddress.getPort();
    }

    public InetSocketAddress getRawSocketAddress() {
        return this.rawSocketAddress;
    }

    public String getAddress() {
        return this.socketAddress.getAddress().getHostAddress();
    }

    public int getPort() {
        return this.socketAddress.getPort();
    }

    public InetSocketAddress getSocketAddress() {
        return this.socketAddress;
    }

    public Position getNextPosition() {
        return this.newPosition != null ? new Position(this.newPosition.x, this.newPosition.y, this.newPosition.z, this.level) : this.getPosition();
    }

    public boolean isSleeping() {
        return this.sleeping != null;
    }

    public int getInAirTicks() {
        return this.inAirTicks;
    }

    /**
     * Returns whether the player is currently using an item (right-click and hold).
     *
     * @return whether the player is currently using an item
     */
    public boolean isUsingItem() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_ACTION) && this.startAction > -1;
    }

    public void setUsingItem(boolean value) {
        this.startAction = value ? this.server.getTick() : -1;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, value);
    }

    public String getButtonText() {
        return this.buttonText;
    }

    public void setButtonText(String text) {
        if (!text.equals(buttonText)) {
            this.buttonText = text;
            this.setDataPropertyAndSendOnlyToSelf(new StringEntityData(Entity.DATA_INTERACTIVE_TAG, this.buttonText));
        }
    }

    public void unloadChunk(int x, int z) {
        this.unloadChunk(x, z, null);
    }

    public void unloadChunk(int x, int z, Level level) {
        level = level == null ? this.level : level;
        long index = Level.chunkHash(x, z);
        if (this.usedChunks.containsKey(index)) {
            for (Entity entity : level.getChunkEntities(x, z).values()) {
                if (entity != this) {
                    entity.despawnFrom(this);
                }
            }

            this.usedChunks.remove(index);
        }
        level.unregisterChunkLoader(this, x, z);
        this.loadQueue.remove(index);
    }

    private void unloadChunks(boolean online) {
        for (long index : this.usedChunks.keySet()) {
            int chunkX = Level.getHashX(index);
            int chunkZ = Level.getHashZ(index);
            this.level.unregisterChunkLoader(this, chunkX, chunkZ);

            for (Entity entity : level.getChunkEntities(chunkX, chunkZ).values()) {
                if (entity != this) {
                    if (online) {
                        entity.despawnFrom(this);
                    } else {
                        entity.hasSpawned.remove(loaderId);
                    }
                }
            }
        }

        this.usedChunks.clear();
        this.loadQueue.clear();
    }

    public Position getSpawn() {
        if (this.spawnBlockPosition != null && this.spawnBlockPosition.isValid()) {
            return this.spawnBlockPosition;
        } else if (this.spawnPosition != null && this.spawnPosition.isValid()) {
            return this.spawnPosition;
        } else {
            return this.server.getDefaultLevel().getSafeSpawn();
        }
    }

    public void checkSpawnBlockPosition() {
        if (this.spawnBlockPosition != null && this.spawnBlockPosition.isValid()) {
            Block spawnBlock = spawnBlockPosition.getLevelBlock();
            if (spawnBlock == null || !isValidRespawnBlock(spawnBlock)) {
                this.spawnBlockPosition = null;
                this.sendMessage(new TranslationContainer(TextFormat.GRAY + "%tile." + (this.getLevel().getDimension() == Level.DIMENSION_OVERWORLD ? "bed" : "respawn_anchor") + ".notValid"));
            }
        }
    }

    protected boolean isValidRespawnBlock(Block block) {
        if (block.getId() == BlockID.RESPAWN_ANCHOR && block.getLevel().getDimension() == Level.DIMENSION_NETHER) {
            BlockRespawnAnchor anchor = (BlockRespawnAnchor) block;
            return anchor.getCharge() > 0;
        }
        if (block.getId() == BlockID.BED_BLOCK && block.getLevel().getDimension() == Level.DIMENSION_OVERWORLD) {
            BlockBed bed = (BlockBed) block;
            return bed.isBedValid();
        }

        return false;
    }

    public void sendChunk(int x, int z, DataPacket packet) {
        if (!this.connected) {
            return;
        }

        this.usedChunks.put(Level.chunkHash(x, z), Boolean.TRUE);

        this.dataPacket(packet);

        this.chunksSent++;

        if (this.spawned) {
            for (Entity entity : this.level.getChunkEntities(x, z).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }

        if (this.protocol >= ProtocolInfo.v1_19_10) {
            for (BlockEntity blockEntity : this.level.getChunkBlockEntities(x, z).values()) {
                if (!(blockEntity instanceof BlockEntityItemFrame) && !(blockEntity instanceof BlockEntityCampfire))
                    continue;
                ((BlockEntitySpawnable) blockEntity).spawnTo(this);
            }
        }

        if (this.needDimensionChangeACK) {
            this.needDimensionChangeACK = false;

            PlayerActionPacket playerActionPacket = new PlayerActionPacket();
            playerActionPacket.action = PlayerActionPacket.ACTION_DIMENSION_CHANGE_SUCCESS;
            playerActionPacket.entityId = this.getId();
            this.dataPacket(playerActionPacket);
        }
    }

    @Deprecated
    public void sendChunk(int x, int z, int subChunkCount, byte[] payload) {
        log.warn("Player#sendChunk(int x, int z, int subChunkCount, byte[] payload) is deprecated");
        this.sendChunk(x, z, subChunkCount, payload, 0);
    }

    public void sendChunk(int x, int z, int subChunkCount, byte[] payload, int dimension) {
        if (!this.connected) {
            return;
        }

        LevelChunkPacket pk = new LevelChunkPacket();
        pk.chunkX = x;
        pk.chunkZ = z;
        pk.dimension = dimension;
        pk.subChunkCount = subChunkCount;
        pk.data = payload;

        this.sendChunk(x, z, pk);
    }

    protected void sendNextChunk() {
        if (!this.connected) {
            return;
        }

        if (!loadQueue.isEmpty()) {
            int count = 0;
            ObjectIterator<Long2ObjectMap.Entry<Boolean>> iter = loadQueue.long2ObjectEntrySet().fastIterator();
            while (iter.hasNext()) {
                if (count >= server.chunksPerTick) {
                    break;
                }

                Long2ObjectMap.Entry<Boolean> entry = iter.next();
                long index = entry.getLongKey();
                int chunkX = Level.getHashX(index);
                int chunkZ = Level.getHashZ(index);

                ++count;

                try {
                    this.usedChunks.put(index, false);
                    this.level.registerChunkLoader(this, chunkX, chunkZ, false);

                    if (!this.level.populateChunk(chunkX, chunkZ)) {
                        if (this.spawned && this.teleportPosition == null) {
                            continue;
                        } else {
                            break;
                        }
                    }

                    iter.remove();
                } catch (Exception ex) {
                    server.getLogger().logException(ex);
                    return;
                }

                PlayerChunkRequestEvent ev = new PlayerChunkRequestEvent(this, chunkX, chunkZ);
                this.server.getPluginManager().callEvent(ev);
                if (!ev.isCancelled()) {
                    this.level.requestChunk(chunkX, chunkZ, this);
                }
            }
        }

        if (!this.hasSpawnChunks && this.chunksSent >= server.spawnThreshold) {
            this.hasSpawnChunks = true;

            if (this.protocol <= ProtocolInfo.v1_5_0) {
                this.doFirstSpawn();
            }

            this.sendPlayStatus(PlayStatusPacket.PLAYER_SPAWN);

            if (protocol <= ProtocolInfo.v1_5_0) {
                this.server.getPluginManager().callEvent(new PlayerLocallyInitializedEvent(this));
            }
        }
    }

    protected void doFirstSpawn() {
        this.locallyInitialized = true;

        if (this.spawned) {
            return;
        }

        this.noDamageTicks = 60;
        this.setAirTicks(400);

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }

        boolean dead = this.getHealth() < 1;
        this.checkSpawnBlockPosition();
        PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(this, this.level.getSafeSpawn(dead ? this.getSpawn() : this), true);
        this.server.getPluginManager().callEvent(respawnEvent);

        if (dead) {
            if (this.server.isHardcore()) {
                this.setBanned(true);
                return;
            }

            this.teleport(respawnEvent.getRespawnPosition(), null);

            if (this.protocol < ProtocolInfo.v1_13_0) {
                RespawnPacket respawnPacket = new RespawnPacket();
                respawnPacket.x = (float) respawnEvent.getRespawnPosition().x;
                respawnPacket.y = (float) respawnEvent.getRespawnPosition().y;
                respawnPacket.z = (float) respawnEvent.getRespawnPosition().z;
                this.dataPacket(respawnPacket);
            }

            this.setHealth(this.getMaxHealth());
            this.foodData.setLevel(20, 20);
            this.sendData(this);
        } else {
            this.setPosition(respawnEvent.getRespawnPosition());
            this.sendPosition(respawnEvent.getRespawnPosition(), yaw, pitch, MovePlayerPacket.MODE_RESET);

            if (this.protocol < ProtocolInfo.v1_5_0) {
                RespawnPacket respawnPacket = new RespawnPacket();
                respawnPacket.x = (float) respawnEvent.getRespawnPosition().x;
                respawnPacket.y = (float) respawnEvent.getRespawnPosition().y;
                respawnPacket.z = (float) respawnEvent.getRespawnPosition().z;
                this.dataPacket(respawnPacket);
            }

            this.getLevel().sendTime(this);
            this.getLevel().sendWeather(this);
        }

        this.spawned = true;

        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(this,
                new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.joined", new String[]{this.displayName})
        );

        this.server.getPluginManager().callEvent(playerJoinEvent);

        if (!playerJoinEvent.getJoinMessage().toString().isBlank()) {
            this.server.broadcastMessage(playerJoinEvent.getJoinMessage());
        }

        for (long index : this.usedChunks.keySet()) {
            int chunkX = Level.getHashX(index);
            int chunkZ = Level.getHashZ(index);
            for (Entity entity : this.level.getChunkEntities(chunkX, chunkZ).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }

        // Prevent PlayerTeleportEvent during player spawn
        //this.teleport(pos, null);

        if (!this.isSpectator() || this.server.useClientSpectator) {
            this.spawnToAll();
        }

        this.sendFogStack();
        this.sendCameraPresets();

        if (server.updateChecks && this.isOp()) {
            CompletableFuture.runAsync(() -> {
                try {
                    URLConnection request = new URL(Nukkit.BRANCH).openConnection();
                    request.connect();
                    InputStreamReader content = new InputStreamReader((InputStream) request.getContent());
                    String latest = "git-" + JsonParser.parseReader(content).getAsJsonObject().get("sha").getAsString().substring(0, 7);
                    content.close();

                    if (Nukkit.getBranch().equals("master")) {
                        if (!server.getNukkitVersion().equals(latest) && !server.getNukkitVersion().equals("git-null")) {
                            this.sendMessage("§c[Nukkit-MOT][Update] §eThere is a new build of §cNukkit§3-§dMOT §eavailable! Current: " + server.getNukkitVersion() + " Latest: " + latest);
                        }
                    }
                } catch (Exception ignore) {
                }
            });
        }
    }

    protected boolean orderChunks() {
        if (!this.connected) {
            return false;
        }

        this.nextChunkOrderRun = 200;

        loadQueue.clear();
        Long2ObjectOpenHashMap<Boolean> lastChunk = new Long2ObjectOpenHashMap<>(this.usedChunks);

        int centerX = (int) this.x >> 4;
        int centerZ = (int) this.z >> 4;

        int radius = spawned ? this.chunkRadius : server.c_s_spawnThreshold;
        int radiusSqr = radius * radius;

        long index;
        for (int x = 0; x <= radius; x++) {
            int xx = x * x;
            for (int z = 0; z <= x; z++) {
                int distanceSqr = xx + z * z;
                if (distanceSqr > radiusSqr) continue;

                /* Top right quadrant */
                if (this.usedChunks.get(index = Level.chunkHash(centerX + x, centerZ + z)) != Boolean.TRUE) {
                    this.loadQueue.put(index, Boolean.TRUE);
                }
                lastChunk.remove(index);
                /* Top left quadrant */
                if (this.usedChunks.get(index = Level.chunkHash(centerX - x - 1, centerZ + z)) != Boolean.TRUE) {
                    this.loadQueue.put(index, Boolean.TRUE);
                }
                lastChunk.remove(index);
                /* Bottom right quadrant */
                if (this.usedChunks.get(index = Level.chunkHash(centerX + x, centerZ - z - 1)) != Boolean.TRUE) {
                    this.loadQueue.put(index, Boolean.TRUE);
                }
                lastChunk.remove(index);
                /* Bottom left quadrant */
                if (this.usedChunks.get(index = Level.chunkHash(centerX - x - 1, centerZ - z - 1)) != Boolean.TRUE) {
                    this.loadQueue.put(index, Boolean.TRUE);
                }
                lastChunk.remove(index);
                if (x != z) {
                    /* Top right quadrant mirror */
                    if (this.usedChunks.get(index = Level.chunkHash(centerX + z, centerZ + x)) != Boolean.TRUE) {
                        this.loadQueue.put(index, Boolean.TRUE);
                    }
                    lastChunk.remove(index);
                    /* Top left quadrant mirror */
                    if (this.usedChunks.get(index = Level.chunkHash(centerX - z - 1, centerZ + x)) != Boolean.TRUE) {
                        this.loadQueue.put(index, Boolean.TRUE);
                    }
                    lastChunk.remove(index);
                    /* Bottom right quadrant mirror */
                    if (this.usedChunks.get(index = Level.chunkHash(centerX + z, centerZ - x - 1)) != Boolean.TRUE) {
                        this.loadQueue.put(index, Boolean.TRUE);
                    }
                    lastChunk.remove(index);
                    /* Bottom left quadrant mirror */
                    if (this.usedChunks.get(index = Level.chunkHash(centerX - z - 1, centerZ - x - 1)) != Boolean.TRUE) {
                        this.loadQueue.put(index, Boolean.TRUE);
                    }
                    lastChunk.remove(index);
                }
            }
        }

        LongIterator keys = lastChunk.keySet().iterator();
        while (keys.hasNext()) {
            index = keys.nextLong();
            this.unloadChunk(Level.getHashX(index), Level.getHashZ(index));
        }

        if (this.protocol >= 313) {
            if (!loadQueue.isEmpty()) {
                NetworkChunkPublisherUpdatePacket packet = new NetworkChunkPublisherUpdatePacket();
                packet.position = this.asBlockVector3();
                packet.radius = this.chunkRadius << 4;
                this.dataPacket(packet);
            }
        }

        return true;
    }

    @Deprecated
    public boolean batchDataPacket(DataPacket packet) {
        return this.dataPacket(packet);
    }

    /**
     * other is identifer
     *
     * @param packet packet to send
     * @return packet successfully sent
     */
    public boolean dataPacket(DataPacket packet) {
        if (!this.connected) {
            return false;
        }

        packet = packet.clone();
        packet.protocol = this.protocol;

        if (server.callDataPkSendEv) {
            DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return false;
            }
        }

        if (Nukkit.DEBUG > 2 /*&& !server.isIgnoredPacket(packet.getClass())*/) {
            log.trace("Outbound {}: {}", this.getName(), packet);
        }

        if (packet instanceof BatchPacket) {
            this.networkSession.sendPacket(packet);
        } else {
            this.server.batchPackets(new Player[]{this}, new DataPacket[]{packet}, true);
        }
        return true;
    }

    public int dataPacket(DataPacket packet, boolean needACK) {
        return this.dataPacket(packet) ? 0 : -1;
    }

    /**
     * 0 is true
     * -1 is false
     * other is identifer
     *
     * @param packet packet to send
     * @return packet successfully sent
     */
    public boolean directDataPacket(DataPacket packet) {
        return this.dataPacket(packet);
    }

    public int directDataPacket(DataPacket packet, boolean needACK) {
        return this.directDataPacket(packet) ? 0 : -1;
    }

    public void forceDataPacket(DataPacket packet, Runnable callback) {
        packet.protocol = this.protocol;
        this.networkSession.sendImmediatePacket(packet, (callback == null ? () -> {
        } : callback));
    }

    /**
     * Get network latency
     *
     * @return network latency in milliseconds
     */
    public int getPing() {
        return this.interfaz.getNetworkLatency(this);
    }

    public boolean sleepOn(Vector3 pos) {
        if (!this.isOnline()) {
            return false;
        }

        Entity[] e = this.level.getNearbyEntities(this.boundingBox.grow(2, 1, 2), this);
        for (Entity p : e) {
            if (p instanceof Player) {
                if (((Player) p).sleeping != null && pos.distance(((Player) p).sleeping) <= 0.1) {
                    return false;
                }
            }
        }

        PlayerBedEnterEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerBedEnterEvent(this, this.level.getBlock(pos)));
        if (ev.isCancelled()) {
            return false;
        }

        this.sleeping = pos.clone();
        this.teleport(new Location(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, this.yaw, this.pitch, this.level), null);

        this.setDataProperty(new IntPositionEntityData(DATA_PLAYER_BED_POSITION, (int) pos.x, (int) pos.y, (int) pos.z));
        this.setDataFlag(DATA_PLAYER_FLAGS, DATA_PLAYER_FLAG_SLEEP, true);

        if (this.getServer().bedSpawnpoints) {
            //if (!this.getSpawn().equals(pos)) {
            //    this.setSpawn(pos);
                this.setSpawnBlock(pos);
                this.sendTranslation("§7%tile.bed.respawnSet");
            //}
        }

        this.level.sleepTicks = 60;
        this.timeSinceRest = 0;

        return true;
    }

    public void setSpawn(Vector3 pos) {
        Level level;
        if (!(pos instanceof Position)) {
            level = this.level;
        } else {
            level = ((Position) pos).getLevel();
        }
        this.spawnPosition = new Position(pos.x, pos.y, pos.z, level);
        this.sendSpawnPos((int) pos.x, (int) pos.y, (int) pos.z, level.getDimension());
    }

    /**
     * 设置保存玩家重生位置的方块的位置。当未知时可能为空。
     * <p>
     * Sets the position of the block that holds the player respawn position. May be null when unknown.
     * <p>
     * 设置保存着玩家重生位置的方块的位置。可以设置为空。
     *
     * @param spawnBlock 床位或重生锚的位置<br>The position of a bed or respawn anchor
     */
    public void setSpawnBlock(@Nullable Vector3 spawnBlock) {
        if (spawnBlock == null) {
            this.spawnBlockPosition = null;
        } else {
            Level level;
            if (spawnBlock instanceof Position position && position.isValid()) {
                level = position.level;
            } else {
                level = this.level;
            }
            this.spawnBlockPosition = new Position(spawnBlock.x, spawnBlock.y, spawnBlock.z, level);
            SetSpawnPositionPacket pk = new SetSpawnPositionPacket();
            pk.spawnType = SetSpawnPositionPacket.TYPE_PLAYER_SPAWN;
            pk.x = this.spawnBlockPosition.getFloorX();
            pk.y = this.spawnBlockPosition.getFloorY();
            pk.z = this.spawnBlockPosition.getFloorZ();
            pk.dimension = this.spawnBlockPosition.level.getDimension();
            this.dataPacket(pk);
        }
    }

    /**
     * Internal: Send player spawn position
     */
    private void sendSpawnPos(int x, int y, int z, int dimension) {
        SetSpawnPositionPacket pk = new SetSpawnPositionPacket();
        pk.spawnType = SetSpawnPositionPacket.TYPE_PLAYER_SPAWN;
        pk.x = x;
        pk.y = y;
        pk.z = z;
        pk.dimension = dimension;
        this.dataPacket(pk);
    }

    public void stopSleep() {
        if (this.sleeping != null) {
            this.server.getPluginManager().callEvent(new PlayerBedLeaveEvent(this, this.level.getBlock(this.sleeping)));

            this.sleeping = null;
            this.setDataProperty(new IntPositionEntityData(DATA_PLAYER_BED_POSITION, 0, 0, 0));
            this.setDataFlag(DATA_PLAYER_FLAGS, DATA_PLAYER_FLAG_SLEEP, false);

            this.level.sleepTicks = 0;

            AnimatePacket pk = new AnimatePacket();
            pk.eid = this.id;
            pk.action = AnimatePacket.Action.WAKE_UP;
            this.dataPacket(pk);
        }
    }

    public Vector3 getSleepingPos() {
        return this.sleeping;
    }

    public boolean awardAchievement(String achievementId) {
        Achievement achievement = Achievement.achievements.get(achievementId);

        if (achievement == null || hasAchievement(achievementId)) {
            return false;
        }

        for (String id : achievement.requires) {
            if (!this.hasAchievement(id)) {
                return false;
            }
        }
        PlayerAchievementAwardedEvent event = new PlayerAchievementAwardedEvent(this, achievementId);
        this.server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        this.achievements.add(achievementId);
        achievement.broadcast(this);
        return true;
    }

    /**
     * Get player's gamemode
     * <p>
     * 0 = survival
     * 1 = creative
     * 2 = adventure
     * 3 = spectator
     *
     * @return gamemode (number)
     */
    public int getGamemode() {
        return gamemode;
    }

    /**
     * Returns a client-friendly gamemode of the specified real gamemode
     * This function takes care of handling gamemodes known to MCPE (as of 1.1.0.3, that includes Survival, Creative and Adventure)
     */
    private int getClientFriendlyGamemode(int gamemode) {
        gamemode &= 0x03;
        if (gamemode == Player.SPECTATOR) {
            //1.19.30+使用真正的旁观模式
            if (this.server.useClientSpectator && this.protocol >= ProtocolInfo.v1_19_30) {
                return GameType.SPECTATOR.ordinal();
            }
            return Player.CREATIVE;
        }
        return gamemode;
    }

    /**
     * Set player's gamemode
     *
     * @param gamemode new gamemode
     * @return gamemode changed
     */
    public boolean setGamemode(int gamemode) {
        return this.setGamemode(gamemode, false, null);
    }

    public boolean setGamemode(int gamemode, boolean clientSide) {
        return this.setGamemode(gamemode, clientSide, null);
    }

    public boolean setGamemode(int gamemode, boolean clientSide, AdventureSettings newSettings) {
        if (gamemode < 0 || gamemode > 3 || this.gamemode == gamemode) {
            return false;
        }

        if (newSettings == null) {
            newSettings = this.adventureSettings.clone(this);
            newSettings.set(Type.WORLD_IMMUTABLE, (gamemode & 0x02) > 0);
            newSettings.set(Type.MINE, (gamemode & 0x02) <= 0);
            newSettings.set(Type.BUILD, (gamemode & 0x02) <= 0);
            newSettings.set(Type.NO_PVM, gamemode == SPECTATOR);
            newSettings.set(Type.ALLOW_FLIGHT, (gamemode & 0x01) > 0);
            newSettings.set(Type.NO_CLIP, gamemode == SPECTATOR);
            newSettings.set(Type.FLYING, switch (gamemode) {
                case CREATIVE -> newSettings.get(Type.FLYING);
                case SPECTATOR -> true;
                default -> false;
            });
        }

        PlayerGameModeChangeEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerGameModeChangeEvent(this, gamemode, newSettings));

        if (ev.isCancelled()) {
            return false;
        }

        this.gamemode = gamemode;

        if (this.server.useClientSpectator) {
            List<Player> updatePlayers = this.hasSpawned.values().stream().filter(p -> p.protocol >= ProtocolInfo.v1_19_30).filter(p -> p != this).toList();
            ArrayList<Player> spawnPlayers = new ArrayList<>(this.hasSpawned.values());
            spawnPlayers.removeAll(updatePlayers);

            if (this.isSpectator()) {
                this.keepMovement = true;
                this.onGround = false;
                spawnPlayers.forEach(this::despawnFrom);
            } else {
                this.keepMovement = false;
                spawnPlayers.forEach(this::spawnTo);
            }

            if (!clientSide) {
                UpdatePlayerGameTypePacket pk = new UpdatePlayerGameTypePacket();
                pk.gameType = GameType.from(getClientFriendlyGamemode(gamemode));
                pk.entityId = this.getId();
                Server.broadcastPacket(updatePlayers, pk);
            }
        } else {
            if (this.isSpectator()) {
                this.keepMovement = true;
                this.onGround = false;
                this.despawnFromAll();
            } else {
                this.keepMovement = false;
                this.spawnToAll();
            }
        }

        this.namedTag.putInt("playerGameType", this.gamemode);

        if (!clientSide) {
            SetPlayerGameTypePacket pk = new SetPlayerGameTypePacket();
            pk.gamemode = getClientFriendlyGamemode(gamemode);
            this.dataPacket(pk);
        }

        this.setAdventureSettings(ev.getNewAdventureSettings());

        if (this.isSpectator()) {
            this.teleport(this, null);

            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SILENT, true);
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_HAS_COLLISION, false);

            if (this.protocol < ProtocolInfo.v1_16_0) {
                InventoryContentPacket inventoryContentPacket = new InventoryContentPacket();
                inventoryContentPacket.inventoryId = InventoryContentPacket.SPECIAL_CREATIVE;
                this.dataPacket(inventoryContentPacket);
            }
        } else {
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SILENT, false);
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_HAS_COLLISION, true);
            if (this.protocol < ProtocolInfo.v1_16_0) {
                InventoryContentPacket inventoryContentPacket = new InventoryContentPacket();
                inventoryContentPacket.inventoryId = InventoryContentPacket.SPECIAL_CREATIVE;
                inventoryContentPacket.slots = Item.getCreativeItems(this.protocol).toArray(Item.EMPTY_ARRAY);
                this.dataPacket(inventoryContentPacket);
            }
        }

        this.resetFallDistance();

        this.inventory.sendContents(this);
        this.inventory.sendHeldItem(this.hasSpawned.values());
        this.offhandInventory.sendContents(this);
        this.offhandInventory.sendContents(this.getViewers().values());

        this.inventory.sendCreativeContents();
        return true;
    }

    /**
     * Send adventure settings
     */
    public void sendSettings() {
        this.adventureSettings.update();
    }

    /**
     * Check player game mode
     *
     * @return whether player is in survival mode
     */
    public boolean isSurvival() {
        return this.gamemode == SURVIVAL;
    }

    /**
     * Check player game mode
     *
     * @return whether player is in creative mode
     */
    public boolean isCreative() {
        return this.gamemode == CREATIVE;
    }

    /**
     * Check player game mode
     *
     * @return whether player is in spectator mode
     */
    public boolean isSpectator() {
        return this.gamemode == SPECTATOR;
    }

    /**
     * Check player game mode
     *
     * @return whether player is in adventure mode
     */
    public boolean isAdventure() {
        return this.gamemode == ADVENTURE;
    }

    @Override
    public Item[] getDrops() {
        if (!this.isCreative() && !this.isSpectator()) {
            if (this.inventory != null) {
                List<Item> drops = new ArrayList<>(this.inventory.getContents().values());
                drops.addAll(this.offhandInventory.getContents().values());
                drops.addAll(this.playerUIInventory.getContents().values());
                return drops.toArray(Item.EMPTY_ARRAY);
            }
            return Item.EMPTY_ARRAY;
        }

        return Item.EMPTY_ARRAY;
    }

    @Override
    public boolean fastMove(double dx, double dy, double dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        this.recalculateBoundingBox();

        this.checkChunks();

        if (!this.isSpectator()) {
            if (!this.onGround || dy != 0) {
                AxisAlignedBB bb = this.boundingBox.clone();
                bb.setMinY(bb.getMinY() - 0.75);

                this.onGround = this.level.getCollisionBlocks(bb).length > 0;
            }
            this.isCollided = this.onGround;
            this.updateFallState(this.onGround);
        }

        return true;
    }

    @Override
    protected void checkGroundState(double movX, double movY, double movZ, double dx, double dy, double dz) {
        if (!this.onGround || movX != 0 || movY != 0 || movZ != 0) {
            boolean onGround = false;

            AxisAlignedBB bb = this.boundingBox.clone();
            bb.setMaxY(bb.getMinY() + 0.5);
            bb.setMinY(bb.getMinY() - 1);

            AxisAlignedBB realBB = this.boundingBox.clone();
            realBB.setMaxY(realBB.getMinY() + 0.1);
            realBB.setMinY(realBB.getMinY() - 0.2);

            int minX = NukkitMath.floorDouble(bb.getMinX());
            int minY = NukkitMath.floorDouble(bb.getMinY());
            int minZ = NukkitMath.floorDouble(bb.getMinZ());
            int maxX = NukkitMath.ceilDouble(bb.getMaxX());
            int maxY = NukkitMath.ceilDouble(bb.getMaxY());
            int maxZ = NukkitMath.ceilDouble(bb.getMaxZ());

            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = minY; y <= maxY; ++y) {
                        Block block = this.level.getBlock(x, y, z, false);

                        if (!block.canPassThrough() && block.collidesWithBB(realBB)) {
                            onGround = true;
                            break;
                        }
                    }
                }
            }

            this.onGround = onGround;
        }

        this.isCollided = this.onGround;
    }

    @Override
    protected void checkBlockCollision() {
        if (this.isSpectator()) {
            if (this.blocksAround == null) {
                this.blocksAround = new ArrayList<>();
            }
            if (this.collisionBlocks == null) {
                this.collisionBlocks = new ArrayList<>();
            }
            return;
        }

        boolean portal = false;
        boolean endPortal = false;
        boolean scaffolding = false;

        for (Block block : this.getCollisionBlocks()) {
            switch (block.getId()) {
                case Block.NETHER_PORTAL:
                    portal = true;
                    continue;
                case Block.END_PORTAL:
                    endPortal = true;
                    continue;
                case Block.SCAFFOLDING:
                    scaffolding = true;
                    break;
            }

            block.onEntityCollide(this);
            block.getLevelBlockAtLayer(1).onEntityCollide(this);
        }

        this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_IN_SCAFFOLDING, scaffolding);

        AxisAlignedBB scanBoundingBox = this.boundingBox.getOffsetBoundingBox(0, -0.125, 0);
        scanBoundingBox.setMaxY(this.boundingBox.getMinY());
        Block[] scaffoldingUnder = this.level.getCollisionBlocks(scanBoundingBox, true, true, b -> b.getId() == BlockID.SCAFFOLDING);
        this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_OVER_SCAFFOLDING, scaffoldingUnder.length > 0);

        if (endPortal) {
            inEndPortalTicks++;
        } else {
            this.inEndPortalTicks = 0;
        }

        if (server.endEnabled && inEndPortalTicks == 1) {
            EntityPortalEnterEvent ev = new EntityPortalEnterEvent(this, EntityPortalEnterEvent.PortalType.END);
            this.getServer().getPluginManager().callEvent(ev);

            if (!ev.isCancelled()) {
                if (this.getLevel().isEnd) {
                    if (server.vanillaPortals && this.getSpawn().getLevel().getDimension() == Level.DIMENSION_OVERWORLD) {
                        this.teleport(this.getSpawn(), TeleportCause.END_PORTAL);
                    } else {
                        this.teleport(this.getServer().getDefaultLevel().getSafeSpawn(), TeleportCause.END_PORTAL);
                    }
                } else {
                    Level end = this.getServer().getLevelByName("the_end");
                    if (end != null) {
                        this.teleport(end.getSafeSpawn(), TeleportCause.END_PORTAL);
                    }
                }
            }
        }

        if (portal) {
            this.inPortalTicks++;
        } else {
            this.inPortalTicks = 0;
            this.portalPos = null;
        }

        if (this.server.isNetherAllowed()) {
            if (this.server.vanillaPortals && (this.inPortalTicks == 40 || this.inPortalTicks == 10 && this.gamemode == CREATIVE) && this.portalPos == null) {
                Position portalPos = this.level.calculatePortalMirror(this);
                if (portalPos == null) {
                    return;
                }

                for (int x = -1; x < 2; x++) {
                    for (int z = -1; z < 2; z++) {
                        int chunkX = (portalPos.getFloorX() >> 4) + x, chunkZ = (portalPos.getFloorZ() >> 4) + z;
                        FullChunk chunk = portalPos.level.getChunk(chunkX, chunkZ, false);
                        if (chunk == null || !(chunk.isGenerated() || chunk.isPopulated())) {
                            portalPos.level.generateChunk(chunkX, chunkZ, true);
                        }
                    }
                }
                this.portalPos = portalPos;
            }

            if (this.inPortalTicks == 80 || (this.server.vanillaPortals && this.inPortalTicks == 25 && this.gamemode == CREATIVE)) {
                EntityPortalEnterEvent ev = new EntityPortalEnterEvent(this, EntityPortalEnterEvent.PortalType.NETHER);
                this.getServer().getPluginManager().callEvent(ev);

                if (ev.isCancelled()) {
                    this.portalPos = null;
                    return;
                }

                if (server.vanillaPortals) {
                    this.inPortalTicks = 81;
                    this.getServer().getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, new AsyncTask() {
                        @Override
                        public void onRun() {
                            Position foundPortal = BlockNetherPortal.findNearestPortal(portalPos);
                            getServer().getScheduler().scheduleTask(InternalPlugin.INSTANCE, () -> {
                                if (foundPortal == null) {
                                    BlockNetherPortal.spawnPortal(portalPos);
                                    teleport(portalPos.add(1.5, 1, 0.5));
                                } else {
                                    teleport(BlockNetherPortal.getSafePortal(foundPortal));
                                }
                                portalPos = null;
                            });
                        }
                    });
                } else {
                    if (this.getLevel().getDimension() == Level.DIMENSION_NETHER) {
                        this.teleport(this.getServer().getDefaultLevel().getSafeSpawn(), TeleportCause.NETHER_PORTAL);
                    } else {
                        Level nether = this.getServer().getNetherWorld(this.level.getName());
                        if (nether != null) {
                            this.teleport(nether.getSafeSpawn(), TeleportCause.NETHER_PORTAL);
                        }
                    }
                }
            }
        }
    }

    /**
     * Internal: Check nearby entities and try to pick them up
     */
    protected void checkNearEntities() {
        Entity[] e = this.level.getNearbyEntities(this.boundingBox.grow(1, 0.5, 1), this);
        for (Entity entity : e) {
            //entity.scheduleUpdate();

            if (!entity.isAlive()) {
                continue;
            }

            this.pickupEntity(entity, true);
        }
    }

    protected void handleMovement(Vector3 clientPos) {
        if (!this.isAlive() || !this.spawned || this.teleportPosition != null || this.isSleeping()) {
            return;
        }

        boolean invalidMotion = false;
        Location revertPos = this.getLocation().clone();
        double distance = clientPos.distanceSquared(this);

        if (!this.level.isChunkGenerated(clientPos.getChunkX(), clientPos.getChunkZ())) {
            invalidMotion = true;
            this.nextChunkOrderRun = 0;
        } else if (distance > 128) {
            invalidMotion = true;
            getServer().getLogger().warning(String.format("%s moved too far (%.2f)", this.getName(), distance));
        }

        if (invalidMotion) {
            this.revertClientMotion(revertPos);
            return;
        }

        double diffX = clientPos.getX() - this.x;
        double diffY = clientPos.getY() - this.y;
        double diffZ = clientPos.getZ() - this.z;

        // Client likes to clip into few blocks like stairs or slabs
        // This should help reduce the server mis-prediction at least a bit
        diffY += this.ySize * (1 - 0.4D);

        this.fastMove(diffX, diffY, diffZ);

        double corrX = this.x - clientPos.getX();
        double corrY = this.y - clientPos.getY();
        double corrZ = this.z - clientPos.getZ();

        double yS = this.getStepHeight() + this.ySize;
        if (corrY >= -yS || corrY <= yS) {
            corrY = 0;
        }

        if (this.checkMovement && (Math.abs(corrX) > 0.5 || Math.abs(corrY) > 0.5 || Math.abs(corrZ) > 0.5) &&
                this.riding == null && !this.hasEffect(Effect.LEVITATION) && !this.hasEffect(Effect.SLOW_FALLING)) {
            double diff = corrX * corrX + corrZ * corrZ;
            if (diff > 0.5) {
                PlayerInvalidMoveEvent invalidMoveEvent = new PlayerInvalidMoveEvent(this, true);
                this.getServer().getPluginManager().callEvent(invalidMoveEvent);
                if (!invalidMoveEvent.isCancelled() && (invalidMotion = invalidMoveEvent.isRevert())) {
                    this.server.getLogger().warning(this.getServer().getLanguage().translateString("nukkit.player.invalidMove", this.getName()));
                }
            }

            if (invalidMotion) {
                this.setPositionAndRotation(revertPos.asVector3f().asVector3(), revertPos.getYaw(), revertPos.getPitch(), revertPos.getHeadYaw());
                this.revertClientMotion(revertPos);
                this.resetClientMovement();
                return;
            }
        }

        // 瞬移检测
        Location source = new Location(this.lastX, this.lastY, this.lastZ, this.lastYaw, this.lastPitch, this.level);
        Location target = this.getLocation();
        double delta = Math.pow(this.lastX - target.getX(), 2) + Math.pow(this.lastY - target.getY(), 2) + Math.pow(this.lastZ - target.getZ(), 2);
        double deltaAngle = Math.abs(this.lastYaw - target.getYaw()) + Math.abs(this.lastPitch - target.getPitch());

        if (delta > 0.0005 || deltaAngle > 1) {
            boolean isFirst = this.firstMove;
            this.firstMove = false;

            this.setLastLocation(target);

            if (!isFirst) {
                List<Block> blocksAround = null;
                if (this.blocksAround != null) {
                    blocksAround = new ObjectArrayList<>(this.blocksAround);
                }
                List<Block> collidingBlocks = null;
                if (this.collisionBlocks != null) {
                    collidingBlocks = new ObjectArrayList<>(this.collisionBlocks);
                }

                PlayerMoveEvent event = new PlayerMoveEvent(this, source, target);
                this.blocksAround = null;
                this.collisionBlocks = null;
                this.server.getPluginManager().callEvent(event);

                if (!(invalidMotion = event.isCancelled())) {
                    if (!target.equals(event.getTo())) {
                        this.teleport(event.getTo(), null);
                    } else {
                        //1.19.0-
                        this.addMovement(this.x, this.y, this.z, this.yaw, this.pitch, this.yaw,
                                this.getViewers().values()
                                        .stream()
                                        .filter(p -> p.protocol < ProtocolInfo.v1_19_0)
                                        .collect(Collectors.toList()));
                        //1.19.0+
                        this.broadcastMovement();
                    }
                } else {
                    this.blocksAround = blocksAround;
                    this.collisionBlocks = collidingBlocks;
                }
            }

            if (this.speed == null) {
                this.speed = new Vector3(source.x - target.x, source.y - target.y, source.z - target.z);
            } else {
                this.speed.setComponents(source.x - target.x, source.y - target.y, source.z - target.z);
            }
        } else {
            if (this.speed == null) {
                speed = new Vector3(0, 0, 0);
            } else {
                this.speed.setComponents(0, 0, 0);
            }
        }

        if (!invalidMotion && this.isFoodEnabled() && this.getServer().getDifficulty() > 0 && distance >= 0.05) {
            double jump = 0;
            double swimming = this.isInsideOfWater() ? 0.015 * distance : 0;
            double distance2 = distance;
            if (swimming != 0) {
                distance2 = 0;
            }
            if (this.isSprinting()) {
                if (this.inAirTicks == 3 && swimming == 0) {
                    jump = 0.2;
                }
                this.getFoodData().updateFoodExpLevel(0.1 * distance2 + jump + swimming);
            } else if (this.isSneaking() && this.inAirTicks == 3) {
                jump = 0.05;
                this.getFoodData().updateFoodExpLevel(jump);
            } else {
                if (this.inAirTicks == 3 && swimming == 0) {
                    jump = 0.05;
                }
                this.getFoodData().updateFoodExpLevel(jump + swimming);
            }
        }

        // if plugin cancel move
        if (invalidMotion) {
            this.positionChanged = false;
            this.setPositionAndRotation(revertPos.asVector3f().asVector3(), revertPos.getYaw(), revertPos.getPitch(), revertPos.getHeadYaw());
            this.revertClientMotion(revertPos);
            this.resetClientMovement();
        } else {
            this.forceMovement = null;
            if (distance != 0) {
                if (this.nextChunkOrderRun > 20) {
                    this.nextChunkOrderRun = 20;
                }
                this.level.antiXrayOnBlockChange(this, this, 2);
            }
        }
    }

    protected void resetClientMovement() {
        this.newPosition = null;
        this.positionChanged = false;
        this.clientMovements.clear();
    }

    protected void revertClientMotion(Location originalPos) {
        this.setLastLocation(originalPos);

        Vector3 syncPos = originalPos.add(0, 0.00001, 0);
        this.sendPosition(syncPos, originalPos.getYaw(), originalPos.getPitch(), MovePlayerPacket.MODE_RESET);
        this.forceMovement = syncPos;

        if (this.speed == null) {
            this.speed = new Vector3(0, 0, 0);
        } else {
            this.speed.setComponents(0, 0, 0);
        }
    }

    protected void setLastLocation(Location location) {
        this.lastX = location.getX();
        this.lastY = location.getY();
        this.lastZ = location.getZ();
        this.lastYaw = location.getYaw();
        this.lastHeadYaw = location.getHeadYaw();
        this.lastPitch = location.getPitch();
    }

    @Override
    public void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw) {
        //this.sendPosition(x, y, z, yaw, pitch, MovePlayerPacket.MODE_NORMAL, this.getViewers().values());
        this.addMovement(x, y, z, yaw, pitch, headYaw, this.getViewers().values());
    }

    public void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw, Collection<Player> viewers) {
        this.sendPosition(x, y, z, yaw, pitch, MovePlayerPacket.MODE_NORMAL, viewers);
    }

    @Override
    public boolean setMotion(Vector3 motion) {
        if (super.setMotion(motion)) {
            if (this.chunk != null && this.spawned) {
                this.addMotion(this.motionX, this.motionY, this.motionZ); // Send to others
                SetEntityMotionPacket pk = new SetEntityMotionPacket();
                pk.eid = this.id;
                pk.motionX = (float) motion.x;
                pk.motionY = (float) motion.y;
                pk.motionZ = (float) motion.z;
                this.dataPacket(pk);
            }

            if (this.motionY > 0) {
                this.startAirTicks = (int) ((-(Math.log(this.getGravity() / (this.getGravity() + this.getDrag() * this.motionY))) / this.getDrag()) * 2 + 5);
            }

            return true;
        }

        return false;
    }

    /**
     * Send all default attributes
     */
    public void sendAttributes() {
        UpdateAttributesPacket pk = new UpdateAttributesPacket();
        pk.entityId = this.getId();
        pk.entries = new Attribute[]{
                Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0),
                Attribute.getAttribute(Attribute.MAX_HUNGER).setValue(this.foodData.getLevel()).setDefaultValue(this.foodData.getMaxLevel()),
                Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(this.getMovementSpeed()).setDefaultValue(this.getMovementSpeed()),
                Attribute.getAttribute(Attribute.EXPERIENCE_LEVEL).setValue(this.expLevel),
                Attribute.getAttribute(Attribute.EXPERIENCE).setValue(((float) this.exp) / calculateRequireExperience(this.expLevel))
        };
        this.dataPacket(pk);
    }

    public void sendFogStack() {
        PlayerFogPacket pk = new PlayerFogPacket();
        pk.setFogStack(this.fogStack);
        this.dataPacket(pk);
    }

    public void sendCameraPresets() {
        if (this.protocol < ProtocolInfo.v1_20_0_23) {
            return;
        }
        CameraPresetsPacket pk = new CameraPresetsPacket();
        pk.getPresets().addAll(CameraPresetManager.getPresets().values());
        this.dataPacket(pk);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.loggedIn) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0) {
            return true;
        }

        this.lastUpdate = currentTick;

        this.failedTransactions = 0;

        if (this.fishing != null && this.age % 20 == 0) {
            if (this.distanceSquared(fishing) > 1089) { // 33 blocks
                this.stopFishing(false);
            }
        }

        if (!this.isAlive() && this.spawned) {
            //++this.deadTicks;
            //if (this.deadTicks >= 10) {
            this.despawnFromAll(); // HACK: fix "dead" players
            //}
            return true;
        }

        if (this.spawned) {
            if (this.needSpawnToAll) {
                this.needSpawnToAll = false;
                this.spawnToAll();
            }

            while (!this.clientMovements.isEmpty()) {
                this.handleMovement(this.clientMovements.poll());
            }
            this.motionX = this.motionY = this.motionZ = 0; // HACK: fix player knockback being messed up

            if (!this.isSpectator() && this.isAlive()) {
                this.checkNearEntities();
            }

            this.entityBaseTick(tickDiff);

            if (this.getServer().getDifficulty() == 0 && this.level.getGameRules().getBoolean(GameRule.NATURAL_REGENERATION)) {
                if (this.getHealth() < this.getMaxHealth() && this.age % 20 == 0) {
                    this.heal(1);
                }

                if (this.foodData.getLevel() < 20 && this.age % 10 == 0) {
                    this.foodData.addFoodLevel(1, 0);
                }
            }

            if (this.isOnFire() && this.lastUpdate % 10 == 0) {
                if (this.isCreative() && !this.isInsideOfFire()) {
                    this.extinguish();
                } else if (this.getLevel().isRaining() && this.canSeeSky()) {
                    this.extinguish();
                }
            }

            if (!this.isSpectator() && this.speed != null) {
                if (this.onGround) {
                    if (this.inAirTicks != 0) {
                        this.startAirTicks = 10;
                    }
                    this.inAirTicks = 0;
                    this.highestPosition = this.y;
                    if (this.isGliding()) {
                        this.setGliding(false);
                    }
                } else {
                    if (this.checkMovement && !this.isGliding() && !server.getAllowFlight() && this.inAirTicks > 20 && !this.getAllowFlight() && !this.isSleeping() && !this.isImmobile() && !this.isSwimming() && this.riding == null && !this.hasEffect(Effect.LEVITATION) && !this.hasEffect(Effect.SLOW_FALLING)) {
                        double expectedVelocity = (-this.getGravity()) / ((double) this.getDrag()) - ((-this.getGravity()) / ((double) this.getDrag())) * FastMath.exp(-((double) this.getDrag()) * ((double) (this.inAirTicks - this.startAirTicks)));
                        double diff = (this.speed.y - expectedVelocity) * (this.speed.y - expectedVelocity);

                        if (this.isOnLadder()) {
                            this.resetFallDistance();
                        } else {
                            if (diff > 2 && expectedVelocity < this.speed.y && this.speed.y != 0) {
                                if (this.inAirTicks < 150) {
                                    PlayerInvalidMoveEvent ev = new PlayerInvalidMoveEvent(this, true);
                                    this.getServer().getPluginManager().callEvent(ev);
                                    if (!ev.isCancelled()) {
                                        this.setMotion(new Vector3(0, expectedVelocity, 0));
                                    }
                                } else if (this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server", true, "type=MOVE, expectedVelocity=" + expectedVelocity + ", diff=" + diff + ", speed.y=" + speed.y)) {
                                    return false;
                                }
                            }
                        }
                    }

                    if (this.y > highestPosition) {
                        this.highestPosition = this.y;
                    }

                    // Wiki: 使用鞘翅滑翔时在垂直高度下降率低于每刻 0.5 格的情况下，摔落高度被重置为 1 格。
                    // Wiki: 玩家在较小的角度和足够低的速度上着陆不会受到坠落伤害。着陆时临界伤害角度为50°，伤害值等同于玩家从滑行的最高点直接摔落到着陆点受到的伤害。
                    if (this.isSwimming() || this.isGliding() && Math.abs(this.speed.y) < 0.5 && this.getPitch() <= 40) {
                        this.resetFallDistance();
                    } else if (this.isGliding()) {
                        this.resetInAirTicks();
                    } else {
                        ++this.inAirTicks;
                    }
                }

                if (this.foodData != null) {
                    this.foodData.update(tickDiff);
                }

                //鞘翅检查和耐久计算
                if (this.isGliding()) {
                    PlayerInventory playerInventory = this.getInventory();
                    if (playerInventory != null) {
                        Item chestplate = playerInventory.getChestplateFast();
                        if ((chestplate == null || chestplate.getId() != ItemID.ELYTRA)) {
                            this.setGliding(false);
                        } else if (this.age % (20 * (chestplate.getEnchantmentLevel(Enchantment.ID_DURABILITY) + 1)) == 0) {
                            int newDamage = chestplate.getDamage() + 1;
                            if (newDamage < chestplate.getMaxDurability()) {
                                chestplate.setDamage(newDamage);
                                playerInventory.setChestplate(chestplate);
                            } else {
                                this.setGliding(false);
                            }
                        }
                    }
                }
            }
        }

        this.checkTeleportPosition();

        /*if (currentTick % 20 == 0) {
            this.checkInteractNearby();
        }*/

        if (this.spawned && !this.dummyBossBars.isEmpty() && currentTick % 100 == 0) {
            this.dummyBossBars.values().forEach(DummyBossBar::updateBossEntityPosition);
        }

        // Shields were added in 1.10
        // Change this if you map shields to some other item for old versions
        if (this.protocol >= ProtocolInfo.v1_10_0) {
            updateBlockingFlag();
        }

        if (!this.isSleeping()) {
            this.timeSinceRest++;
        }

        if (protocol >= ProtocolInfo.v1_20_10_21) {
            if (this.age%200 == 0) {
                this.dataPacket(new NetworkStackLatencyPacket());
            }
        }

        return true;
    }

    private void updateBlockingFlag() {
        boolean shouldBlock = getNoShieldTicks() == 0
                && (this.isSneaking() || getRiding() != null)
                && (this.getInventory().getItemInHand() instanceof ItemShield || this.getOffhandInventory().getItem(0) instanceof ItemShield);

        if (isBlocking() != shouldBlock) {
            this.setBlocking(shouldBlock);
        }

        /*boolean shieldInHand = this.getInventory().getItemInHandFast() instanceof ItemShield;
        boolean shieldInOffhand = this.getOffhandInventory().getItemFast(0) instanceof ItemShield;
        if (isBlocking()) {
            if (!isSneaking() || (!shieldInHand && !shieldInOffhand)) {
                this.setBlocking(false);
            }
        } else if (isSneaking() && (shieldInHand || shieldInOffhand)) {
            this.setBlocking(true);
        }*/
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        //解决插件异步卸载世界导致的问题
        if (this.level == null || this.level.getProvider() == null) {
            log.warn("Player {} has no valid level", this.getName());
            Level defaultLevel = this.server.getDefaultLevel();
            if (this.level == defaultLevel || defaultLevel == null || defaultLevel.getProvider() == null) {
                this.close(this.getLeaveMessage(), "Default level unload");
            } else {
                this.teleport(defaultLevel.getSafeSpawn(), null);
            }
        }

        boolean hasUpdated = false;
        if (isUsingItem()) {
            if (noShieldTicks < NO_SHIELD_DELAY) {
                noShieldTicks = NO_SHIELD_DELAY;
                hasUpdated = true;
            }
        } else {
            if (noShieldTicks > 0) {
                noShieldTicks -= tickDiff;
                hasUpdated = true;
            }
            if (noShieldTicks < 0) {
                noShieldTicks = 0;
                hasUpdated = true;
            }
        }
        return super.entityBaseTick(tickDiff) || hasUpdated;
    }

    public void checkInteractNearby() {
        int interactDistance = isCreative() ? 5 : 3;
        if (canInteract(this, interactDistance)) {
            EntityInteractable e = getEntityPlayerLookingAt(interactDistance);
            if (e != null) {
                String buttonText = e.getInteractButtonText(this);
                if (buttonText == null) {
                    buttonText = "";
                }
                setButtonText(buttonText);
            } else {
                setButtonText("");
            }
        } else {
            setButtonText("");
        }
    }

    /**
     * Returns the Entity the player is looking at currently
     *
     * @param maxDistance the maximum distance to check for entities
     * @return Entity|null    either NULL if no entity is found or an instance of the entity
     */
    public EntityInteractable getEntityPlayerLookingAt(int maxDistance) {
        EntityInteractable entity = null;

        if (temporalVector != null) {
            Entity[] nearbyEntities = level.getNearbyEntities(boundingBox.grow(maxDistance, maxDistance, maxDistance), this);

            try {
                BlockIterator itr = new BlockIterator(level, getPosition(), getDirectionVector(), getEyeHeight(), maxDistance);
                if (itr.hasNext()) {
                    Block block;
                    while (itr.hasNext()) {
                        block = itr.next();
                        entity = getEntityAtPosition(nearbyEntities, block.getFloorX(), block.getFloorY(), block.getFloorZ());
                        if (entity != null) {
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return entity;
    }

    private static EntityInteractable getEntityAtPosition(Entity[] nearbyEntities, int x, int y, int z) {
        for (Entity nearestEntity : nearbyEntities) {
            if (nearestEntity.getFloorX() == x && nearestEntity.getFloorY() == y && nearestEntity.getFloorZ() == z
                    && nearestEntity instanceof EntityInteractable
                    && ((EntityInteractable) nearestEntity).canDoInteraction()) {
                return (EntityInteractable) nearestEntity;
            }
        }
        return null;
    }

    public void checkNetwork() {
        if (this.protocol < ProtocolInfo.v1_16_100 && !this.isOnline()) {
            return;
        }

        if (!this.isOnline()) {
            return;
        }

        Level nowLevel = this.getLevel();
        nowLevel.providerLock.readLock().lock();

        try {
            if (this.nextChunkOrderRun-- <= 0 || this.chunk == null) {
                this.orderChunks();
            }

            if (!this.loadQueue.isEmpty() || !this.spawned) {
                this.sendNextChunk();
            }
        } finally {
            nowLevel.providerLock.readLock().unlock();
        }
    }

    public boolean canInteract(Vector3 pos, double maxDistance) {
        return this.canInteract(pos, maxDistance, 6.0);
    }

    public boolean canInteract(Vector3 pos, double maxDistance, double maxDiff) {
        if (this.distanceSquared(pos) > maxDistance * maxDistance) {
            return false;
        }

        Vector2 dV = this.getDirectionPlane();
        return (dV.dot(new Vector2(pos.x, pos.z)) - dV.dot(new Vector2(this.x, this.z))) >= -maxDiff;
    }

    private boolean canInteractEntity(Vector3 pos, double maxDistance) {
        if (this.distanceSquared(pos) > Math.pow(maxDistance, 2)) {
            return false;
        }

        Vector2 dV = this.getDirectionPlane();
        return (dV.dot(new Vector2(pos.x, pos.z)) - dV.dot(new Vector2(this.x, this.z))) >= -0.87;
    }

    protected void processLogin() {
        String lowerName = this.username.toLowerCase();
        if (!this.server.isWhitelisted(lowerName)) {
            this.kick(PlayerKickEvent.Reason.NOT_WHITELISTED, server.whitelistReason);
            return;
        } else if (this.isBanned()) {
            String reason = this.server.getNameBans().getEntires().get(lowerName).getReason();
            this.kick(PlayerKickEvent.Reason.NAME_BANNED, "You are banned!" + (reason.isEmpty() ? "" : (" Reason: " + reason)));
            return;
        } else if (!server.strongIPBans && this.server.getIPBans().isBanned(this.getAddress())) {
            this.kick(PlayerKickEvent.Reason.IP_BANNED, "Your IP is banned!");
            return;
        }

        for (Player p : new ArrayList<>(this.server.playerList.values())) {
            if (p != this && p.username != null) {
                if (p.username.equalsIgnoreCase(this.username) || this.getUniqueId().equals(p.getUniqueId())) {
                    p.close("", "disconnectionScreen.loggedinOtherLocation");
                    break;
                }
            }
        }

        CompoundTag nbt;
        File legacyDataFile = new File(server.getDataPath() + "players/" + lowerName + ".dat");
        File dataFile = new File(server.getDataPath() + "players/" + this.uuid.toString() + ".dat");
        if (this.server.savePlayerDataByUuid) {
            boolean dataFound = dataFile.exists();
            if (!dataFound && legacyDataFile.exists()) {
                nbt = this.server.getOfflinePlayerData(lowerName, false);
                if (!legacyDataFile.delete()) {
                    this.server.getLogger().warning("Could not delete legacy player data for " + this.username);
                }
            } else {
                nbt = this.server.getOfflinePlayerData(this.uuid, !dataFound);
            }
        } else {
            boolean legacyMissing = !legacyDataFile.exists();
            if (legacyMissing && dataFile.exists()) {
                nbt = this.server.getOfflinePlayerData(this.uuid, false);
            } else {
                nbt = this.server.getOfflinePlayerData(lowerName, legacyMissing);
            }
        }

        if (nbt == null) {
            this.close(this.getLeaveMessage(), "Invalid data");
            return;
        }

        if (loginChainData.isXboxAuthed() || !server.xboxAuth) {
            server.updateName(this.uuid, this.username);
        }

        this.playedBefore = (nbt.getLong("lastPlayed") - nbt.getLong("firstPlayed")) > 1;

        nbt.putString("NameTag", this.username);

        this.setExperience(nbt.getInt("EXP"), nbt.getInt("expLevel"));

        if (this.server.getForceGamemode()) {
            this.gamemode = this.server.getGamemode();
            nbt.putInt("playerGameType", this.gamemode);
        } else {
            this.gamemode = nbt.getInt("playerGameType") & 0x03;
        }

        this.adventureSettings = new AdventureSettings(this)
                .set(Type.WORLD_IMMUTABLE, isAdventure() || isSpectator())
                .set(Type.MINE, !isAdventure() && !isSpectator())
                .set(Type.BUILD, !isAdventure() && !isSpectator())
                .set(Type.NO_PVM, this.isSpectator())
                .set(Type.AUTO_JUMP, true)
                .set(Type.ALLOW_FLIGHT, isCreative() || isSpectator())
                .set(Type.NO_CLIP, isSpectator())
                .set(Type.FLYING, isSpectator());

        Level level;
        if ((level = this.server.getLevelByName(nbt.getString("Level"))) == null || nbt.getShort("Health") < 1) {
            this.setLevel(this.server.getDefaultLevel());
            nbt.putString("Level", this.level.getName());
            Position sp = this.level.getSpawnLocation();
            nbt.getList("Pos", DoubleTag.class)
                    .add(new DoubleTag("0", sp.x))
                    .add(new DoubleTag("1", sp.y))
                    .add(new DoubleTag("2", sp.z));
        } else {
            this.setLevel(level);
        }

        if (nbt.contains("SpawnLevel")) {
            Level spawnLevel = server.getLevelByName(nbt.getString("SpawnLevel"));
            if (spawnLevel != null) {
                this.spawnPosition = new Position(
                        nbt.getInt("SpawnX"),
                        nbt.getInt("SpawnY"),
                        nbt.getInt("SpawnZ"),
                        level
                );
            }
        }

        if (nbt.contains("SpawnBlockLevel")) {
            Level spawnBlockLevel = server.getLevelByName(nbt.getString("SpawnBlockLevel"));
            if (nbt.contains("SpawnBlockPositionX") && nbt.contains("SpawnBlockPositionY") && nbt.contains("SpawnBlockPositionZ")) {
                this.spawnBlockPosition = new Position(nbt.getInt("SpawnBlockPositionX"), nbt.getInt("SpawnBlockPositionY"), nbt.getInt("SpawnBlockPositionZ"), spawnBlockLevel);
            }
        }

        this.timeSinceRest = nbt.getInt("TimeSinceRest");

        ListTag<StringTag> fogIdentifiers = nbt.getList("fogIdentifiers", StringTag.class);
        ListTag<StringTag> userProvidedFogIds = nbt.getList("userProvidedFogIds", StringTag.class);
        for (int i = 0; i < fogIdentifiers.size(); i++) {
            this.fogStack.add(i, new PlayerFogPacket.Fog(Identifier.tryParse(fogIdentifiers.get(i).data), userProvidedFogIds.get(i).data));
        }


        for (Tag achievement : nbt.getCompound("Achievements").getAllTags()) {
            if (!(achievement instanceof ByteTag)) {
                continue;
            }

            if (((ByteTag) achievement).getData() > 0) {
                this.achievements.add(achievement.getName());
            }
        }

        nbt.putLong("lastPlayed", System.currentTimeMillis() / 1000);

        UUID uuid = getUniqueId();
        nbt.putLong("UUIDLeast", uuid.getLeastSignificantBits());
        nbt.putLong("UUIDMost", uuid.getMostSignificantBits());

        if (this.server.getAutoSave()) {
            if (this.server.savePlayerDataByUuid) {
                this.server.saveOfflinePlayerData(this.uuid, nbt, true);
            } else {
                this.server.saveOfflinePlayerData(this.username, nbt, true);
            }
        }

        this.sendPlayStatus(PlayStatusPacket.LOGIN_SUCCESS);

        ListTag<DoubleTag> posList = nbt.getList("Pos", DoubleTag.class);

        super.init(this.level.getChunk((int) posList.get(0).data >> 4, (int) posList.get(2).data >> 4, true), nbt);

        if (!this.namedTag.contains("foodLevel")) {
            this.namedTag.putInt("foodLevel", 20);
        }

        if (!this.namedTag.contains("foodSaturationLevel")) {
            this.namedTag.putFloat("foodSaturationLevel", 20);
        }

        this.foodData = new PlayerFood(this, this.namedTag.getInt("foodLevel"), this.namedTag.getFloat("foodSaturationLevel"));

        if (this.isSpectator()) {
            this.keepMovement = true;
            this.onGround = false;
        }

        this.forceMovement = this.teleportPosition = this.getPosition();

        ResourcePacksInfoPacket infoPacket = new ResourcePacksInfoPacket();
        infoPacket.resourcePackEntries = this.server.getResourcePackManager().getResourceStack();
        infoPacket.mustAccept = this.server.getForceResources();
        this.dataPacket(infoPacket);
    }

    protected void completeLoginSequence() {
        if (this.loggedIn) {
            this.server.getLogger().debug("(BUG) Tried to call completeLoginSequence but player is already logged in");
            return;
        }

        PlayerLoginEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerLoginEvent(this, "Plugin reason"));
        if (ev.isCancelled()) {
            this.close(this.getLeaveMessage(), ev.getKickMessage());
            return;
        }

        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.entityUniqueId = this.id;
        startGamePacket.entityRuntimeId = this.id;
        startGamePacket.playerGamemode = this.getClientFriendlyGamemode(this.gamemode);
        startGamePacket.x = (float) this.x;
        startGamePacket.y = (float) this.y;
        startGamePacket.z = (float) this.z;
        startGamePacket.yaw = (float) this.yaw;
        startGamePacket.pitch = (float) this.pitch;
        startGamePacket.dimension = (byte) (this.level.getDimension() & 0xff);
        startGamePacket.generator = (byte) ((this.level.getDimension() + 1) & 0xff); //0 旧世界, 1 主世界, 2 下界, 3末地
        startGamePacket.worldGamemode = this.getClientFriendlyGamemode(this.gamemode);
        startGamePacket.difficulty = this.server.getDifficulty();
        startGamePacket.spawnX = (int) this.x;
        startGamePacket.spawnY = (int) this.y;
        startGamePacket.spawnZ = (int) this.z;
        startGamePacket.commandsEnabled = this.enableClientCommand;
        startGamePacket.experiments.addAll(this.getExperiments());
        startGamePacket.gameRules = this.getLevel().getGameRules();
        startGamePacket.worldName = this.getServer().getNetwork().getName();
        startGamePacket.version = this.getLoginChainData().getGameVersion();
        startGamePacket.vanillaVersion = Utils.getVersionByProtocol(this.protocol);
        if (this.getLevel().isRaining()) {
            startGamePacket.rainLevel = this.getLevel().getRainTime();
            if (this.getLevel().isThundering()) {
                startGamePacket.lightningLevel = this.getLevel().getThunderTime();
            }
        }
        startGamePacket.isMovementServerAuthoritative = this.isMovementServerAuthoritative();
        startGamePacket.isServerAuthoritativeBlockBreaking = this.isServerAuthoritativeBlockBreaking();
        startGamePacket.playerPropertyData = EntityProperty.getPlayerPropertyCache();
        this.forceDataPacket(startGamePacket, null);

        this.loggedIn = true;
        this.server.getLogger().info(this.getServer().getLanguage().translateString("nukkit.player.logIn",
                TextFormat.AQUA + this.username + TextFormat.WHITE,
                this.getAddress(),
                String.valueOf(this.getPort()),
                this.protocol + " (" + Utils.getVersionByProtocol(this.protocol) + ")"));

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_CAN_CLIMB, true, false);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_CAN_SHOW_NAMETAG, true, false);
        this.setDataProperty(new ByteEntityData(DATA_ALWAYS_SHOW_NAMETAG, 1), false);

        try {
            if (this.protocol >= ProtocolInfo.v1_8_0) {
                if (this.protocol >= ProtocolInfo.v1_12_0) {
                    if (this.protocol >= ProtocolInfo.v1_16_100) {
                        if (this.protocol >= ProtocolInfo.v1_17_0) {
                            //注册实体属性
                            for (SyncEntityPropertyPacket pk : EntityProperty.getPacketCache()) {
                                this.dataPacket(pk);
                            }
                        }
                        ItemComponentPacket itemComponentPacket = new ItemComponentPacket();
                        if (this.server.enableExperimentMode && !Item.getCustomItemDefinition().isEmpty()) {
                            Int2ObjectOpenHashMap<ItemComponentPacket.Entry> entries = new Int2ObjectOpenHashMap<>();
                            int i = 0;
                            for (var entry : Item.getCustomItemDefinition().entrySet()) {
                                try {
                                    CompoundTag data = entry.getValue().getNbt(this.protocol);
                                    data.putShort("minecraft:identifier", i);
                                    entries.put(i, new ItemComponentPacket.Entry(entry.getKey(), data));
                                    i++;
                                } catch (Exception e) {
                                    log.error("ItemComponentPacket encoding error", e);
                                }
                            }
                            itemComponentPacket.setEntries(entries.values().toArray(ItemComponentPacket.Entry.EMPTY_ARRAY));
                        }
                        this.dataPacket(itemComponentPacket);
                    }
                    this.dataPacket(new BiomeDefinitionListPacket());
                }
                this.dataPacket(new AvailableEntityIdentifiersPacket());
            }

            if (this.protocol >= ProtocolInfo.v1_16_100) {
                this.sendSpawnPos((int) this.x, (int) this.y, (int) this.z, this.level.getDimension());
            }
            this.getLevel().sendTime(this);

            SetDifficultyPacket difficultyPacket = new SetDifficultyPacket();
            difficultyPacket.difficulty = this.server.getDifficulty();
            this.dataPacket(difficultyPacket);

            SetCommandsEnabledPacket commandsPacket = new SetCommandsEnabledPacket();
            commandsPacket.enabled = this.isEnableClientCommand();
            this.dataPacket(commandsPacket);

            this.adventureSettings.update();

            GameRulesChangedPacket gameRulesPK = new GameRulesChangedPacket();
            gameRulesPK.gameRulesMap = level.getGameRules().getGameRules();
            this.dataPacket(gameRulesPK);

            this.server.sendFullPlayerListData(this);
            this.sendAttributes();

            if (this.protocol < ProtocolInfo.v1_16_0 && this.gamemode == Player.SPECTATOR) {
                InventoryContentPacket inventoryContentPacket = new InventoryContentPacket();
                inventoryContentPacket.inventoryId = ContainerIds.CREATIVE;
                this.dataPacket(inventoryContentPacket);
            } else {
                this.inventory.sendCreativeContents();
            }
            this.sendAllInventories();
            this.inventory.sendHeldItemIfNotAir(this);

            // BDS sends armor trim templates and materials before the CraftingDataPacket
            if (this.protocol >= ProtocolInfo.v1_19_80) {
                TrimDataPacket trimDataPacket = new TrimDataPacket();
                trimDataPacket.getMaterials().addAll(TrimFactory.trimMaterials);
                trimDataPacket.getPatterns().addAll(TrimFactory.trimPatterns);
                this.dataPacket(trimDataPacket);
            }

            this.server.sendRecipeList(this);

            if (this.isEnableClientCommand()) {
                this.sendCommandData();
            }

            this.sendPotionEffects(this);

            if (this.isSpectator()) {
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_SILENT, true);
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_HAS_COLLISION, false);
            }
            this.sendData(this, this.dataProperties.clone());

            if (!this.server.checkOpMovement && this.isOp()) {
                this.setCheckMovement(false);
            }

            if (this.isOp() || this.hasPermission("nukkit.textcolor")) {
                this.setRemoveFormat(false);
            }

            this.server.onPlayerCompleteLoginSequence(this);
        } catch (Exception e) {
            this.close("", "Internal Server Error");
            this.server.getLogger().logException(e);
        }
    }

    public void handleDataPacket(DataPacket packet) {
        if (!connected) {
            return;
        }

        byte pid = packet.pid();
        if (!loginVerified && pid != ProtocolInfo.LOGIN_PACKET && pid != ProtocolInfo.BATCH_PACKET && pid != ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET && pid != ProtocolInfo.CLIENT_TO_SERVER_HANDSHAKE_PACKET) {
            server.getLogger().warning("Ignoring " + packet.getClass().getSimpleName() + " from " + getAddress() + " due to player not verified yet");
            if (unverifiedPackets++ > 100) {
                this.close("", "Too many failed login attempts");
            }
            return;
        }

        if (!loggedIn && !PRE_LOGIN_PACKETS.contains(pid)) {
            server.getLogger().warning("Ignoring " + packet.getClass().getSimpleName() + " from " + username + " due to player not logged in yet");
            return;
        }

        if (packet.protocol == 999) {
            packet.protocol = this.protocol;
        }

        DataPacketReceiveEvent ev = new DataPacketReceiveEvent(this, packet);
        this.server.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return;
        }

        if (Nukkit.DEBUG > 2 /*&& !server.isIgnoredPacket(packet.getClass())*/) {
            log.trace("Inbound {}: {}", this.getName(), packet);
        }

        if (DataPacketManager.canProcess(packet.protocol, packet.packetId())) {
            DataPacketManager.processPacket(this.playerHandle, packet);
            return;
        }

        packetswitch:
        switch (pid) {
            case ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET:
                if (this.raknetProtocol < 11) {
                    return;
                }
                if (this.loginPacketReceived) {
                    log.debug("{}: got a RequestNetworkSettingsPacket but player is already logged in", username);
                    return;
                }

                this.protocol = ((RequestNetworkSettingsPacket) packet).protocolVersion;

                NetworkSettingsPacket settingsPacket = new NetworkSettingsPacket();
                PacketCompressionAlgorithm algorithm;
                if (this.server.useSnappy && protocol >= ProtocolInfo.v1_19_30_23) {
                    algorithm = PacketCompressionAlgorithm.SNAPPY;
                } else {
                    algorithm = PacketCompressionAlgorithm.ZLIB;
                }
                settingsPacket.compressionAlgorithm = algorithm;
                settingsPacket.compressionThreshold = 1; // compress everything
                this.forceDataPacket(settingsPacket, () -> {
                    this.networkSession.setCompression(CompressionProvider.from(algorithm, this.raknetProtocol));
                });

                if (!ProtocolInfo.SUPPORTED_PROTOCOLS.contains(this.protocol)) {
                    this.close("", "You are running unsupported Minecraft version");
                    this.server.getLogger().debug(this.getAddress() + " disconnected with unsupported protocol (SupportedProtocols) " + this.protocol);
                    return;
                }
                if (this.protocol < this.server.minimumProtocol) {
                    this.close("", "Support for this Minecraft version is not enabled");
                    this.server.getLogger().debug(this.getAddress() + " disconnected with unsupported protocol (minimumProtocol) " + this.protocol);
                    return;
                } else if (this.server.maximumProtocol >= Math.max(0, this.server.minimumProtocol) && this.protocol > this.server.maximumProtocol) {
                    this.close("", "Support for this Minecraft version is not enabled");
                    this.server.getLogger().debug(this.getAddress() + " disconnected with unsupported protocol (maximumProtocol) " + this.protocol);
                    return;
                }
                break;
            case ProtocolInfo.LOGIN_PACKET:
                if (this.loginPacketReceived) {
                    this.close("", "Invalid login packet");
                    return;
                }

                this.loginPacketReceived = true;

                LoginPacket loginPacket = (LoginPacket) packet;

                this.protocol = loginPacket.getProtocol();

                this.username = TextFormat.clean(loginPacket.username);

                if (!ProtocolInfo.SUPPORTED_PROTOCOLS.contains(this.protocol)) {
                    this.close("", "You are running unsupported Minecraft version");
                    this.server.getLogger().debug(this.username + " disconnected with protocol (SupportedProtocols) " + this.protocol);
                    break;
                }

                if (this.protocol < server.minimumProtocol) {
                    this.close("", "Multiversion support for this Minecraft version is disabled");
                    this.server.getLogger().debug(this.username + " disconnected with protocol (minimumProtocol) " + this.protocol);
                    break;
                } else if (this.server.maximumProtocol >= Math.max(0, this.server.minimumProtocol) && this.protocol > this.server.maximumProtocol) {
                    this.close("", "Support for this Minecraft version is not enabled");
                    this.server.getLogger().debug(this.username + " disconnected with unsupported protocol (maximumProtocol) " + this.protocol);
                    break;
                }

                this.displayName = this.username;
                this.iusername = this.username.toLowerCase();
                this.setDataProperty(new StringEntityData(DATA_NAMETAG, this.username), false);

                this.loginChainData = ClientChainData.read(loginPacket);

                if (!loginChainData.isXboxAuthed() && server.xboxAuth) {
                    this.close("", "disconnectionScreen.notAuthenticated");
                    if (server.banXBAuthFailed) {
                        this.server.getNetwork().blockAddress(this.socketAddress.getAddress(), 5);
                        this.server.getLogger().notice("Blocked " + getAddress() + " for 5 seconds due to failed Xbox auth");
                    }
                    break;
                }

                if (this.server.getOnlinePlayersCount() >= this.server.getMaxPlayers() && this.kick(PlayerKickEvent.Reason.SERVER_FULL, "disconnectionScreen.serverFull")) {
                    break;
                }

                if (this.server.isWaterdogCapable() && loginChainData.getWaterdogIP() != null) {
                    this.socketAddress = new InetSocketAddress(this.loginChainData.getWaterdogIP(), this.getRawPort());
                }

                this.version = loginChainData.getGameVersion();

                this.server.getLogger().debug("Name: " + this.username + " Protocol: " + this.protocol + " Version: " + this.version);

                this.randomClientId = loginPacket.clientId;

                this.uuid = loginPacket.clientUUID;
                this.rawUUID = Binary.writeUUID(this.uuid);

                boolean valid = true;
                int len = loginPacket.username.length();
                if (len > 16 || len < 3 || loginPacket.username.trim().isEmpty()) {
                    valid = false;
                }

                if (valid) {
                    for (int i = 0; i < len; i++) {
                        char c = loginPacket.username.charAt(i);
                        if ((c >= 'a' && c <= 'z') ||
                                (c >= 'A' && c <= 'Z') ||
                                (c >= '0' && c <= '9') ||
                                c == '_' || c == ' '
                        ) {
                            continue;
                        }

                        valid = false;
                        break;
                    }
                }

                if (!valid || Objects.equals(this.iusername, "rcon") || Objects.equals(this.iusername, "console")) {
                    this.close("", "disconnectionScreen.invalidName");
                    break;
                }

                if (!loginPacket.skin.isValid()) {
                    this.close("", "disconnectionScreen.invalidSkin");
                    break;
                }
                Skin skin = loginPacket.skin;
                this.setSkin(skin.isPersona() && !this.getServer().personaSkins ? Skin.NO_PERSONA_SKIN : skin);

                PlayerPreLoginEvent playerPreLoginEvent;
                this.server.getPluginManager().callEvent(playerPreLoginEvent = new PlayerPreLoginEvent(this, "Plugin reason"));
                if (playerPreLoginEvent.isCancelled()) {
                    this.close("", playerPreLoginEvent.getKickMessage());
                    break;
                }

                if (this.isEnableNetworkEncryption()) {
                    this.server.getScheduler().scheduleAsyncTask(new PrepareEncryptionTask(this) {
                        @Override
                        public void onCompletion(Server server) {
                            if (!Player.this.isConnected()) {
                                return;
                            }

                            if (this.getHandshakeJwt() == null || this.getEncryptionKey() == null || this.getEncryptionCipher() == null || this.getDecryptionCipher() == null) {
                                Player.this.close("", "Network Encryption error");
                                return;
                            }

                            ServerToClientHandshakePacket pk = new ServerToClientHandshakePacket();
                            pk.setJwt(this.getHandshakeJwt());
                            Player.this.forceDataPacket(pk, () -> {
                                Player.this.awaitingEncryptionHandshake = true;
                                Player.this.getNetworkSession().setEncryption(this.getEncryptionKey(), this.getEncryptionCipher(), this.getDecryptionCipher());
                            });
                        }
                    });
                } else {
                    this.processPreLogin();
                }
                break;
            case ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET:
                ResourcePackClientResponsePacket responsePacket = (ResourcePackClientResponsePacket) packet;
                switch (responsePacket.responseStatus) {
                    case ResourcePackClientResponsePacket.STATUS_REFUSED:
                        this.close("", "disconnectionScreen.noReason");
                        break;
                    case ResourcePackClientResponsePacket.STATUS_SEND_PACKS:
                        for (ResourcePackClientResponsePacket.Entry entry : responsePacket.packEntries) {
                            ResourcePack resourcePack = this.server.getResourcePackManager().getPackById(entry.uuid);
                            if (resourcePack == null) {
                                this.close("", "disconnectionScreen.resourcePack");
                                break;
                            }

                            ResourcePackDataInfoPacket dataInfoPacket = new ResourcePackDataInfoPacket();
                            dataInfoPacket.packId = resourcePack.getPackId();
                            dataInfoPacket.maxChunkSize = RESOURCE_PACK_CHUNK_SIZE;
                            dataInfoPacket.chunkCount = MathHelper.ceil(resourcePack.getPackSize() / (float) RESOURCE_PACK_CHUNK_SIZE);
                            dataInfoPacket.compressedPackSize = resourcePack.getPackSize();
                            dataInfoPacket.sha256 = resourcePack.getSha256();
                            this.dataPacket(dataInfoPacket);
                        }
                        break;
                    case ResourcePackClientResponsePacket.STATUS_HAVE_ALL_PACKS:
                        ResourcePackStackPacket stackPacket = new ResourcePackStackPacket();
                        stackPacket.mustAccept = this.server.getForceResources() && !this.server.forceResourcesAllowOwnPacks;
                        stackPacket.resourcePackStack = this.server.getResourcePackManager().getResourceStack();
                        stackPacket.experiments.addAll(this.getExperiments());
                        this.dataPacket(stackPacket);
                        break;
                    case ResourcePackClientResponsePacket.STATUS_COMPLETED:
                        this.shouldLogin = true;

                        if (this.preLoginEventTask.isFinished()) {
                            this.preLoginEventTask.onCompletion(server);
                        }
                        break;
                }
                break;
            case ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET:
                ResourcePackChunkRequestPacket requestPacket = (ResourcePackChunkRequestPacket) packet;
                ResourcePack resourcePack = this.server.getResourcePackManager().getPackById(requestPacket.packId);
                if (resourcePack == null) {
                    this.close("", "disconnectionScreen.resourcePack");
                    break;
                }

                ResourcePackChunkDataPacket dataPacket = new ResourcePackChunkDataPacket();
                dataPacket.packId = resourcePack.getPackId();
                dataPacket.chunkIndex = requestPacket.chunkIndex;
                dataPacket.data = resourcePack.getPackChunk(RESOURCE_PACK_CHUNK_SIZE * requestPacket.chunkIndex, RESOURCE_PACK_CHUNK_SIZE);
                dataPacket.progress = (long) RESOURCE_PACK_CHUNK_SIZE * requestPacket.chunkIndex;
                this.dataPacket(dataPacket);
                break;
            case ProtocolInfo.PLAYER_SKIN_PACKET:
                PlayerSkinPacket skinPacket = (PlayerSkinPacket) packet;
                skin = skinPacket.skin;

                if (!skin.isValid()) {
                    this.getServer().getLogger().warning(username + ": PlayerSkinPacket with invalid skin");
                    break;
                }

                PlayerChangeSkinEvent playerChangeSkinEvent = new PlayerChangeSkinEvent(this, skin);
                if (TimeUnit.SECONDS.toMillis(this.server.getPlayerSkinChangeCooldown()) > System.currentTimeMillis() - this.lastSkinChange) {
                    playerChangeSkinEvent.setCancelled(true);
                    Server.getInstance().getLogger().warning("Player " + username + " change skin too quick!");
                }
                this.server.getPluginManager().callEvent(playerChangeSkinEvent);
                if (!playerChangeSkinEvent.isCancelled()) {
                    this.lastSkinChange = System.currentTimeMillis();
                    this.setSkin(skin.isPersona() && !this.getServer().personaSkins ? Skin.NO_PERSONA_SKIN : skin);
                }
                break;
            case ProtocolInfo.PLAYER_INPUT_PACKET:
                if (!this.isAlive() || !this.spawned || this.isMovementServerAuthoritative()) {
                    break;
                }
                if (riding instanceof EntityControllable) {
                    PlayerInputPacket ipk = (PlayerInputPacket) packet;
                    ((EntityControllable) riding).onPlayerInput(this, ipk.motionX, ipk.motionY);
                }
                break;
            case ProtocolInfo.MOVE_PLAYER_PACKET:
                if (this.teleportPosition != null || !this.spawned || this.isMovementServerAuthoritative()) {
                    break;
                }

                MovePlayerPacket movePlayerPacket = (MovePlayerPacket) packet;
                Vector3 newPos = new Vector3(movePlayerPacket.x, movePlayerPacket.y - this.getBaseOffset(), movePlayerPacket.z);
                double dis = newPos.distanceSquared(this);

                if (dis == 0 && movePlayerPacket.yaw % 360 == this.yaw && movePlayerPacket.pitch % 360 == this.pitch) {
                    break;
                }

                if (dis > 100) {
                    this.sendPosition(this, movePlayerPacket.yaw, movePlayerPacket.pitch, MovePlayerPacket.MODE_RESET);
                    break;
                }

                boolean revert = false;
                if (!this.isAlive() || !this.spawned) {
                    revert = true;
                    this.forceMovement = this;
                }

                if (this.forceMovement != null && (newPos.distanceSquared(this.forceMovement) > 0.1 || revert)) {
                    this.sendPosition(this.forceMovement, movePlayerPacket.yaw, movePlayerPacket.pitch, MovePlayerPacket.MODE_RESET);
                } else {

                    movePlayerPacket.yaw %= 360;
                    movePlayerPacket.pitch %= 360;

                    if (movePlayerPacket.yaw < 0) {
                        movePlayerPacket.yaw += 360;
                    }

                    this.setRotation(movePlayerPacket.yaw, movePlayerPacket.pitch);
                    this.newPosition = newPos;
                    this.clientMovements.offer(newPos);
                    this.forceMovement = null;
                }
                break;
            case ProtocolInfo.PLAYER_AUTH_INPUT_PACKET:
                if (!this.isMovementServerAuthoritative()) {
                    return;
                }
                PlayerAuthInputPacket authPacket = (PlayerAuthInputPacket) packet;

                if (!authPacket.getBlockActionData().isEmpty()) {
                    for (PlayerBlockActionData action : authPacket.getBlockActionData().values()) {
                        BlockVector3 blockPos = action.getPosition();
                        BlockFace blockFace = BlockFace.fromIndex(action.getFacing());
                        if (this.lastBlockAction != null && this.lastBlockAction.getAction() == PlayerActionType.PREDICT_DESTROY_BLOCK &&
                                action.getAction() == PlayerActionType.CONTINUE_DESTROY_BLOCK) {
                            this.onBlockBreakStart(blockPos.asVector3(), blockFace);
                        }

                        BlockVector3 lastBreakPos = this.lastBlockAction == null ? null : this.lastBlockAction.getPosition();
                        if (lastBreakPos != null && (lastBreakPos.getX() != blockPos.getX() ||
                                lastBreakPos.getY() != blockPos.getY() || lastBreakPos.getZ() != blockPos.getZ())) {
                            this.onBlockBreakAbort(lastBreakPos.asVector3(), BlockFace.DOWN);
                            this.onBlockBreakStart(blockPos.asVector3(), blockFace);
                        }

                        switch (action.getAction()) {
                            case START_DESTROY_BLOCK -> this.onBlockBreakStart(blockPos.asVector3(), blockFace);
                            case ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> this.onBlockBreakAbort(blockPos.asVector3(), blockFace);
                            case CONTINUE_DESTROY_BLOCK -> this.onBlockBreakContinue(blockPos.asVector3(), blockFace);
                            case PREDICT_DESTROY_BLOCK -> {
                                this.onBlockBreakAbort(blockPos.asVector3(), blockFace);
                                this.onBlockBreakComplete(blockPos, blockFace);
                            }
                        }
                        this.lastBlockAction = action;
                    }
                }

                if (this.teleportPosition != null) {
                    return;
                }

                if (this.riding instanceof EntityMinecartAbstract) {
                    double inputY = authPacket.getMotion().getY();
                    if (inputY >= -1.001 && inputY <= 1.001) {
                        ((EntityMinecartAbstract) riding).setCurrentSpeed(inputY);
                    }
                } else if (this.riding instanceof EntityBoat && authPacket.getInputData().contains(AuthInputAction.IN_CLIENT_PREDICTED_IN_VEHICLE)) {
                    if (this.riding.getId() == authPacket.getPredictedVehicle() && this.riding.isControlling(this)) {
                        if (this.temporalVector.setComponents(authPacket.getPosition().getX(), authPacket.getPosition().getY(), authPacket.getPosition().getZ()).distanceSquared(this.riding) < 100) {
                            ((EntityBoat) this.riding).onInput(authPacket.getPosition().getX(), authPacket.getPosition().getY(), authPacket.getPosition().getZ(), authPacket.getHeadYaw());
                        }
                    }
                }

                if (authPacket.getInputData().contains(AuthInputAction.START_SPRINTING)) {
                    PlayerToggleSprintEvent event = new PlayerToggleSprintEvent(this, true);
                    this.server.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        this.sendData(this);
                    } else {
                        this.setSprinting(true);
                    }
                }

                if (authPacket.getInputData().contains(AuthInputAction.STOP_SPRINTING)) {
                    PlayerToggleSprintEvent event = new PlayerToggleSprintEvent(this, false);
                    this.server.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        this.sendData(this);
                    } else {
                        this.setSprinting(false);
                    }
                }

                if (authPacket.getInputData().contains(AuthInputAction.START_SNEAKING)) {
                    PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this, true);
                    this.server.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        this.sendData(this);
                    } else {
                        this.setSneaking(true);
                    }
                }

                if (authPacket.getInputData().contains(AuthInputAction.STOP_SNEAKING)) {
                    PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this, false);
                    this.server.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        this.sendData(this);
                    } else {
                        this.setSneaking(false);
                    }
                }

                if (authPacket.getInputData().contains(AuthInputAction.START_JUMPING)) {
                    PlayerJumpEvent playerJumpEvent = new PlayerJumpEvent(this);
                    this.server.getPluginManager().callEvent(playerJumpEvent);
                }

                if (authPacket.getInputData().contains(AuthInputAction.START_GLIDING)) {
                    PlayerToggleGlideEvent playerToggleGlideEvent = new PlayerToggleGlideEvent(this, true);
                    this.server.getPluginManager().callEvent(playerToggleGlideEvent);
                    if (playerToggleGlideEvent.isCancelled()) {
                        this.sendData(this);
                    } else {
                        this.setGliding(true);
                    }
                }

                if (authPacket.getInputData().contains(AuthInputAction.STOP_GLIDING)) {
                    PlayerToggleGlideEvent playerToggleGlideEvent = new PlayerToggleGlideEvent(this, false);
                    this.server.getPluginManager().callEvent(playerToggleGlideEvent);
                    if (playerToggleGlideEvent.isCancelled()) {
                        this.sendData(this);
                    } else {
                        this.setGliding(false);
                    }
                }

                if (authPacket.getInputData().contains(AuthInputAction.START_SWIMMING)) {
                    PlayerToggleSwimEvent ptse = new PlayerToggleSwimEvent(this, true);
                    this.server.getPluginManager().callEvent(ptse);
                    if (ptse.isCancelled()) {
                        this.sendData(this);
                    } else {
                        this.setSwimming(true);
                    }
                }

                if (authPacket.getInputData().contains(AuthInputAction.STOP_SWIMMING)) {
                    PlayerToggleSwimEvent ptse = new PlayerToggleSwimEvent(this, false);
                    this.server.getPluginManager().callEvent(ptse);
                    if (ptse.isCancelled()) {
                        this.sendData(this);
                    } else {
                        this.setSwimming(false);
                    }
                }

                if (protocol >= ProtocolInfo.v1_20_10_21 && authPacket.getInputData().contains(AuthInputAction.MISSED_SWING)) {
                    PlayerMissedSwingEvent pmse = new PlayerMissedSwingEvent(this);
                    if (this.isSpectator()) {
                        pmse.setCancelled();
                    }
                    this.server.getPluginManager().callEvent(pmse);
                    if (!pmse.isCancelled()) {
                        level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ATTACK_NODAMAGE, -1, "minecraft:player", false, false);
                    }
                }

                if (protocol >= ProtocolInfo.v1_20_30_24) {
                    if (authPacket.getInputData().contains(AuthInputAction.START_FLYING)) {
                        if (!server.getAllowFlight() && !this.getAdventureSettings().get(Type.ALLOW_FLIGHT)) {
                            this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server");
                            break;
                        }
                        PlayerToggleFlightEvent playerToggleFlightEvent = new PlayerToggleFlightEvent(this, true);
                        if (this.isSpectator()) {
                            playerToggleFlightEvent.setCancelled();
                        }
                        this.getServer().getPluginManager().callEvent(playerToggleFlightEvent);
                        if (playerToggleFlightEvent.isCancelled()) {
                            this.getAdventureSettings().update();
                        } else {
                            this.getAdventureSettings().set(AdventureSettings.Type.FLYING, playerToggleFlightEvent.isFlying());
                        }
                    }

                    if (authPacket.getInputData().contains(AuthInputAction.STOP_FLYING)) {
                        PlayerToggleFlightEvent playerToggleFlightEvent = new PlayerToggleFlightEvent(this, false);
                        if (this.isSpectator()) {
                            playerToggleFlightEvent.setCancelled();
                        }
                        this.getServer().getPluginManager().callEvent(playerToggleFlightEvent);
                        if (playerToggleFlightEvent.isCancelled()) {
                            this.getAdventureSettings().update();
                        } else {
                            this.getAdventureSettings().set(AdventureSettings.Type.FLYING, playerToggleFlightEvent.isFlying());
                        }
                    }

                }

                if (protocol >= ProtocolInfo.v1_20_30_24 //1.20.20.22开始爬行模式不属于实验性玩法
                        || (protocol >= ProtocolInfo.v1_20_10_21 && this.server.enableExperimentMode)) {
                    if (authPacket.getInputData().contains(AuthInputAction.START_CRAWLING)) {
                        PlayerToggleCrawlEvent event = new PlayerToggleCrawlEvent(this, true);
                        this.server.getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setCrawling(true);
                        }
                    }

                    if (authPacket.getInputData().contains(AuthInputAction.STOP_CRAWLING)) {
                        PlayerToggleCrawlEvent event = new PlayerToggleCrawlEvent(this, false);
                        this.server.getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setCrawling(false);
                        }
                    }
                }

                Vector3 clientPosition = authPacket.getPosition().subtract(0, this.getBaseOffset(), 0).asVector3();

                double distSqrt = clientPosition.distanceSquared(this);
                if (distSqrt == 0.0 && authPacket.getYaw() % 360 == this.yaw && authPacket.getPitch() % 360 == this.pitch) {
                    break;
                }

                if (distSqrt > 100) {
                    this.sendPosition(this, authPacket.getYaw(), authPacket.getPitch(), MovePlayerPacket.MODE_RESET);
                    break;
                }

                boolean revertMotion = false;
                if (!this.isAlive() || !this.spawned) {
                    revertMotion = true;
                    this.forceMovement = new Vector3(this.x, this.y, this.z);
                }

                if (this.forceMovement != null && (clientPosition.distanceSquared(this.forceMovement) > 0.1 || revertMotion)) {
                    this.sendPosition(this.forceMovement, authPacket.getYaw(), authPacket.getPitch(), MovePlayerPacket.MODE_RESET);
                } else {
                    float yaw = authPacket.getYaw() % 360;
                    float pitch = authPacket.getPitch() % 360;
                    if (yaw < 0) {
                        yaw += 360;
                    }

                    this.setRotation(yaw, pitch);
                    this.newPosition = clientPosition;
                    this.clientMovements.offer(clientPosition);
                    this.forceMovement = null;
                }
                break;
            case ProtocolInfo.MOVE_ENTITY_ABSOLUTE_PACKET:
                //1.20.60开始使用AuthInputAction.IN_CLIENT_PREDICTED_IN_VEHICLE
                if (protocol < ProtocolInfo.v1_20_60) {
                    MoveEntityAbsolutePacket moveEntityAbsolutePacket = (MoveEntityAbsolutePacket) packet;
                    if (!this.spawned || this.riding == null || this.riding.getId() != moveEntityAbsolutePacket.eid || !this.riding.isControlling(this)) {
                        break;
                    }
                    if (this.riding instanceof EntityBoat) {
                        if (this.temporalVector.setComponents(moveEntityAbsolutePacket.x, moveEntityAbsolutePacket.y, moveEntityAbsolutePacket.z).distanceSquared(this.riding) < 1000) {
                            ((EntityBoat) this.riding).onInput(moveEntityAbsolutePacket.x, moveEntityAbsolutePacket.y, moveEntityAbsolutePacket.z, moveEntityAbsolutePacket.headYaw);
                        }
                    }
                }
                break;
            case ProtocolInfo.ADVENTURE_SETTINGS_PACKET:
                if (this.protocol >= ProtocolInfo.v1_19_30_23) {
                    return;
                }
                AdventureSettingsPacket adventureSettingsPacket = (AdventureSettingsPacket) packet;
                if (adventureSettingsPacket.entityUniqueId != this.getId()) {
                    break;
                }
                if (!server.getAllowFlight() && adventureSettingsPacket.getFlag(AdventureSettingsPacket.FLYING) && !this.getAdventureSettings().get(Type.ALLOW_FLIGHT)
                        || adventureSettingsPacket.getFlag(AdventureSettingsPacket.NO_CLIP) && !this.getAdventureSettings().get(Type.NO_CLIP)) {
                    this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server", true, "type=AdventureSettingsPacket, flags=ALLOW_FLIGHT: " + adventureSettingsPacket.getFlag(AdventureSettingsPacket.ALLOW_FLIGHT) + ", FLYING: " + adventureSettingsPacket.getFlag(AdventureSettingsPacket.ALLOW_FLIGHT));
                    break;
                }
                PlayerToggleFlightEvent playerToggleFlightEvent = new PlayerToggleFlightEvent(this, adventureSettingsPacket.getFlag(AdventureSettingsPacket.FLYING));
                if (this.isSpectator()) {
                    playerToggleFlightEvent.setCancelled();
                }
                this.server.getPluginManager().callEvent(playerToggleFlightEvent);
                if (playerToggleFlightEvent.isCancelled()) {
                    this.adventureSettings.update();
                } else {
                    this.adventureSettings.set(Type.FLYING, playerToggleFlightEvent.isFlying());
                }
                break;
            case ProtocolInfo.MOB_EQUIPMENT_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                MobEquipmentPacket mobEquipmentPacket = (MobEquipmentPacket) packet;

                Inventory inv = this.getWindowById(mobEquipmentPacket.windowId);

                if (inv == null) {
                    this.server.getLogger().debug(this.getName() + " has no open container with window ID " + mobEquipmentPacket.windowId);
                    return;
                }

                Item item = inv.getItem(mobEquipmentPacket.hotbarSlot);

                if (!item.equals(mobEquipmentPacket.item)) {
                    this.server.getLogger().debug(this.getName() + " tried to equip " + mobEquipmentPacket.item + " but have " + item + " in target slot");
                    inv.sendContents(this);
                    return;
                }

                if (inv instanceof PlayerInventory) {
                    ((PlayerInventory) inv).equipItem(mobEquipmentPacket.hotbarSlot);
                }

                this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);

                break;
            case ProtocolInfo.PLAYER_ACTION_PACKET:
                PlayerActionPacket playerActionPacket = (PlayerActionPacket) packet;
                if (!this.spawned || !this.isAlive() && playerActionPacket.action != PlayerActionPacket.ACTION_RESPAWN) {
                    break;
                }

                playerActionPacket.entityId = this.id;
                Vector3 pos = this.temporalVector.setComponents(playerActionPacket.x, playerActionPacket.y, playerActionPacket.z);
                BlockFace face = BlockFace.fromIndex(playerActionPacket.face);

                actionswitch:
                switch (playerActionPacket.action) {
                    case PlayerActionPacket.ACTION_START_BREAK:
                        if (this.isServerAuthoritativeBlockBreaking()) break;
                        this.onBlockBreakStart(pos, face);
                        break;
                    case PlayerActionPacket.ACTION_ABORT_BREAK:
                    case PlayerActionPacket.ACTION_STOP_BREAK:
                        if (this.isServerAuthoritativeBlockBreaking()) break;
                        this.onBlockBreakAbort(pos, face);
                        break;
                    case PlayerActionPacket.ACTION_GET_UPDATED_BLOCK:
                    case PlayerActionPacket.ACTION_DROP_ITEM:
                        break;
                    case PlayerActionPacket.ACTION_STOP_SLEEPING:
                        this.stopSleep();
                        break;
                    case PlayerActionPacket.ACTION_RESPAWN:
                        if (!this.spawned || this.isAlive() || !this.isOnline()) {
                            break;
                        }

                        this.respawn();
                        break;
                    case PlayerActionPacket.ACTION_JUMP:
                        if (this.isMovementServerAuthoritative()) break;
                        if (this.inAirTicks > 40 && this.checkMovement && !server.getAllowFlight() && !this.isCreative() && !this.isSwimming() && !this.isGliding()) {
                            /*if (this.inAirTicks < 150) {
                                PlayerInvalidMoveEvent playerInvalidMoveEvent = new PlayerInvalidMoveEvent(this, true);
                                this.getServer().getPluginManager().callEvent(playerInvalidMoveEvent);
                                if (!playerInvalidMoveEvent.isCancelled()) {
                                    this.motionY = -4;
                                }
                            } else {*/
                            this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server", true, "type=ACTION_JUMP, inAirTicks=" + this.inAirTicks);
                            //}
                            break;
                        }
                        this.server.getPluginManager().callEvent(new PlayerJumpEvent(this));
                        break packetswitch;
                    case PlayerActionPacket.ACTION_START_SPRINT:
                        if (this.isMovementServerAuthoritative()) break;
                        PlayerToggleSprintEvent playerToggleSprintEvent = new PlayerToggleSprintEvent(this, true);
                        this.server.getPluginManager().callEvent(playerToggleSprintEvent);
                        if (playerToggleSprintEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSprinting(true);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_STOP_SPRINT:
                        if (this.isMovementServerAuthoritative()) break;
                        playerToggleSprintEvent = new PlayerToggleSprintEvent(this, false);
                        this.server.getPluginManager().callEvent(playerToggleSprintEvent);
                        if (playerToggleSprintEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSprinting(false);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_START_SNEAK:
                        if (this.isMovementServerAuthoritative()) break;
                        PlayerToggleSneakEvent playerToggleSneakEvent = new PlayerToggleSneakEvent(this, true);
                        this.server.getPluginManager().callEvent(playerToggleSneakEvent);
                        if (playerToggleSneakEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSneaking(true);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_STOP_SNEAK:
                        if (this.isMovementServerAuthoritative()) break;
                        playerToggleSneakEvent = new PlayerToggleSneakEvent(this, false);
                        this.server.getPluginManager().callEvent(playerToggleSneakEvent);
                        if (playerToggleSneakEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSneaking(false);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_DIMENSION_CHANGE_SUCCESS:
                        this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_RESET);
                        this.dummyBossBars.values().forEach(DummyBossBar::reshow);
                        break;
                    case PlayerActionPacket.ACTION_START_GLIDE:
                        if (this.isMovementServerAuthoritative()) break;
                        if (!server.getAllowFlight() && this.checkMovement) {
                            Item chestplate = this.getInventory().getChestplateFast();
                            if ((chestplate == null || chestplate.getId() != ItemID.ELYTRA) && !server.getAllowFlight()) {
                                this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server", true, "type=ACTION_START_GLIDE");
                                break;
                            }
                        }
                        PlayerToggleGlideEvent playerToggleGlideEvent = new PlayerToggleGlideEvent(this, true);
                        this.server.getPluginManager().callEvent(playerToggleGlideEvent);
                        if (playerToggleGlideEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setGliding(true);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_STOP_GLIDE:
                        if (this.isMovementServerAuthoritative()) break;
                        playerToggleGlideEvent = new PlayerToggleGlideEvent(this, false);
                        this.server.getPluginManager().callEvent(playerToggleGlideEvent);
                        if (playerToggleGlideEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setGliding(false);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_CONTINUE_BREAK:
                        if (this.isMovementServerAuthoritative()) break;
                        this.onBlockBreakContinue(pos, face);
                        break;
                    case PlayerActionPacket.ACTION_START_SWIMMING:
                        if (this.isMovementServerAuthoritative()) break;
                        PlayerToggleSwimEvent ptse = new PlayerToggleSwimEvent(this, true);
                        if (!this.isInsideOfWater()) {
                            ptse.setCancelled(true);
                        }
                        this.server.getPluginManager().callEvent(ptse);
                        if (ptse.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSwimming(true);
                        }
                        break;
                    case PlayerActionPacket.ACTION_STOP_SWIMMING:
                        if (this.isMovementServerAuthoritative()) break;
                        ptse = new PlayerToggleSwimEvent(this, false);
                        this.server.getPluginManager().callEvent(ptse);
                        if (ptse.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSwimming(false);
                        }
                        break;
                    case PlayerActionPacket.ACTION_MISSED_SWING:
                        if (this.isMovementServerAuthoritative() || this.protocol < ProtocolInfo.v1_20_10_21) break;
                        PlayerMissedSwingEvent pmse = new PlayerMissedSwingEvent(this);
                        this.server.getPluginManager().callEvent(pmse);
                        if (!pmse.isCancelled()) {
                            this.level.addSound(this, Sound.GAME_PLAYER_ATTACK_NODAMAGE);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_START_CRAWLING:
                        if (this.isMovementServerAuthoritative()
                                || this.protocol < ProtocolInfo.v1_20_10_21
                                || (!this.server.enableExperimentMode && this.protocol < ProtocolInfo.v1_20_30_24)) break;
                        PlayerToggleCrawlEvent playerToggleCrawlEvent = new PlayerToggleCrawlEvent(this, true);
                        this.server.getPluginManager().callEvent(playerToggleCrawlEvent);
                        if (playerToggleCrawlEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setCrawling(true);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_STOP_CRAWLING:
                        if (this.isMovementServerAuthoritative()
                                || this.protocol < ProtocolInfo.v1_20_10_21
                                || (!this.server.enableExperimentMode && this.protocol < ProtocolInfo.v1_20_30_24)) break;
                        playerToggleCrawlEvent = new PlayerToggleCrawlEvent(this, false);
                        this.server.getPluginManager().callEvent(playerToggleCrawlEvent);
                        if (playerToggleCrawlEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setCrawling(false);
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_START_FLYING:
                        if (this.isMovementServerAuthoritative() || protocol < ProtocolInfo.v1_20_30_24) break;
                        if (!server.getAllowFlight() && !this.getAdventureSettings().get(Type.ALLOW_FLIGHT)) {
                            this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server");
                            break;
                        }
                        playerToggleFlightEvent = new PlayerToggleFlightEvent(this, true);
                        this.getServer().getPluginManager().callEvent(playerToggleFlightEvent);
                        if (playerToggleFlightEvent.isCancelled()) {
                            this.getAdventureSettings().update();
                        } else {
                            this.getAdventureSettings().set(AdventureSettings.Type.FLYING, playerToggleFlightEvent.isFlying());
                        }
                        break packetswitch;
                    case PlayerActionPacket.ACTION_STOP_FLYING:
                        if (this.isMovementServerAuthoritative() || protocol < ProtocolInfo.v1_20_30_24) break;
                        playerToggleFlightEvent = new PlayerToggleFlightEvent(this, false);
                        this.getServer().getPluginManager().callEvent(playerToggleFlightEvent);
                        if (playerToggleFlightEvent.isCancelled()) {
                            this.getAdventureSettings().update();
                        } else {
                            this.getAdventureSettings().set(AdventureSettings.Type.FLYING, playerToggleFlightEvent.isFlying());
                        }
                        break packetswitch;
                }

                this.setUsingItem(false);
                break;
            case ProtocolInfo.MODAL_FORM_RESPONSE_PACKET:
                this.formOpen = false;

                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                ModalFormResponsePacket modalFormPacket = (ModalFormResponsePacket) packet;

                if (formWindows.containsKey(modalFormPacket.formId)) {
                    FormWindow window = formWindows.remove(modalFormPacket.formId);
                    window.setResponse(modalFormPacket.data.trim());

                    for (FormResponseHandler handler : window.getHandlers()) {
                        handler.handle(this, modalFormPacket.formId);
                    }

                    PlayerFormRespondedEvent event = new PlayerFormRespondedEvent(this, modalFormPacket.formId, window);
                    getServer().getPluginManager().callEvent(event);
                } else if (serverSettings.containsKey(modalFormPacket.formId)) {
                    FormWindow window = serverSettings.get(modalFormPacket.formId);
                    window.setResponse(modalFormPacket.data.trim());

                    for (FormResponseHandler handler : window.getHandlers()) {
                        handler.handle(this, modalFormPacket.formId);
                    }

                    PlayerSettingsRespondedEvent event = new PlayerSettingsRespondedEvent(this, modalFormPacket.formId, window);
                    getServer().getPluginManager().callEvent(event);

                    if (!event.isCancelled() && window instanceof FormWindowCustom)
                        ((FormWindowCustom) window).setElementsFromResponse();
                }

                break;

            case ProtocolInfo.INTERACT_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                //this.craftingType = CRAFTING_SMALL;

                InteractPacket interactPacket = (InteractPacket) packet;

                if (interactPacket.target == 0 && interactPacket.action == InteractPacket.ACTION_MOUSEOVER) {
                    this.setButtonText("");
                    break;
                }

                Entity targetEntity = interactPacket.target == this.getId() ? this : this.level.getEntity(interactPacket.target);

                if (interactPacket.action != InteractPacket.ACTION_OPEN_INVENTORY && (targetEntity == null || !this.isAlive() || !targetEntity.isAlive())) {
                    break;
                }

                if (interactPacket.action != InteractPacket.ACTION_OPEN_INVENTORY && (targetEntity instanceof EntityItem || targetEntity instanceof EntityArrow || targetEntity instanceof EntityXPOrb)) {
                    //this.kick(PlayerKickEvent.Reason.INVALID_PVE, "Attempting to interact with an invalid entity");
                    this.server.getLogger().warning(this.getServer().getLanguage().translateString("nukkit.player.invalidEntity", this.username));
                    break;
                }

                switch (interactPacket.action) {
                    case InteractPacket.ACTION_OPEN_INVENTORY:
                        if (targetEntity instanceof EntityChestBoat chestBoat) {
                            if (this.protocol >= ProtocolInfo.v1_19_0) {
                                this.addWindow(chestBoat.getInventory());
                            }
                            break;
                        } else if (targetEntity != this) {
                            break;
                        }
                        if (this.protocol >= 407) {
                            //Optional<Inventory> topWindow = this.getTopWindow();
                            if (!this.inventoryOpen/* && !(topWindow.isPresent() && topWindow.get().getViewers().contains(this))*/) {
                                this.inventoryOpen = this.inventory.open(this);
                            }
                        }
                        break;
                    case InteractPacket.ACTION_MOUSEOVER:
                        if (interactPacket.target == 0 && this.protocol >= 313) {
                            break packetswitch;
                        }
                        String buttonText = "";
                        if (targetEntity instanceof EntityInteractable) {
                            buttonText = ((EntityInteractable) targetEntity).getInteractButtonText(this);
                            if (buttonText == null) {
                                buttonText = "";
                            }
                        }
                        this.setButtonText(buttonText);

                        this.getServer().getPluginManager().callEvent(new PlayerMouseOverEntityEvent(this, targetEntity));
                        break;
                    case InteractPacket.ACTION_VEHICLE_EXIT:
                        if (!(targetEntity instanceof EntityRideable) || this.riding != targetEntity) {
                            break;
                        }

                        ((EntityRideable) riding).dismountEntity(this);
                        break;
                }
                break;
            case ProtocolInfo.BLOCK_PICK_REQUEST_PACKET:
                BlockPickRequestPacket pickRequestPacket = (BlockPickRequestPacket) packet;
                Block block = this.level.getBlock(pickRequestPacket.x, pickRequestPacket.y, pickRequestPacket.z, false);
                if (block.distanceSquared(this) > 1000) {
                    this.getServer().getLogger().debug(username + ": Block pick request for a block too far away");
                    return;
                }
                item = block.toItem();
                if (pickRequestPacket.addUserData) {
                    BlockEntity blockEntity = this.getLevel().getBlockEntityIfLoaded(this.temporalVector.setComponents(pickRequestPacket.x, pickRequestPacket.y, pickRequestPacket.z));
                    if (blockEntity != null) {
                        CompoundTag nbt = blockEntity.getCleanedNBT();
                        if (nbt != null) {
                            item.setCustomBlockData(nbt);
                            item.setLore("+(DATA)");
                        }
                    }
                }

                PlayerBlockPickEvent pickEvent = new PlayerBlockPickEvent(this, block, item);
                if (this.isSpectator()) {
                    pickEvent.setCancelled();
                }

                this.server.getPluginManager().callEvent(pickEvent);

                if (!pickEvent.isCancelled()) {
                    boolean itemExists = false;
                    int itemSlot = -1;
                    for (int slot = 0; slot < this.inventory.getSize(); slot++) {
                        if (this.inventory.getItem(slot).equals(pickEvent.getItem())) {
                            if (slot < this.inventory.getHotbarSize()) {
                                this.inventory.setHeldItemSlot(slot);
                            } else {
                                itemSlot = slot;
                            }
                            itemExists = true;
                            break;
                        }
                    }

                    for (int slot = 0; slot < this.inventory.getHotbarSize(); slot++) {
                        if (this.inventory.getItem(slot).isNull()) {
                            if (!itemExists && this.isCreative()) {
                                this.inventory.setHeldItemSlot(slot);
                                this.inventory.setItemInHand(pickEvent.getItem());
                                break packetswitch;
                            } else if (itemSlot > -1) {
                                this.inventory.setHeldItemSlot(slot);
                                this.inventory.setItemInHand(this.inventory.getItem(itemSlot));
                                this.inventory.clear(itemSlot, true);
                                break packetswitch;
                            }
                        }
                    }

                    if (!itemExists && this.isCreative()) {
                        Item itemInHand = this.inventory.getItemInHand();
                        this.inventory.setItemInHand(pickEvent.getItem());
                        if (!this.inventory.isFull()) {
                            for (int slot = 0; slot < this.inventory.getSize(); slot++) {
                                if (this.inventory.getItem(slot).isNull()) {
                                    this.inventory.setItem(slot, itemInHand);
                                    break;
                                }
                            }
                        }
                    } else if (itemSlot > -1) {
                        Item itemInHand = this.inventory.getItemInHand();
                        this.inventory.setItemInHand(this.inventory.getItem(itemSlot));
                        this.inventory.setItem(itemSlot, itemInHand);
                    }
                }
                break;
            case ProtocolInfo.ANIMATE_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                AnimatePacket animatePacket = (AnimatePacket) packet;

                // prevent client send illegal packet to server and broadcast to other client and make other client crash
                if (animatePacket.action == null // illegal action id
                        || animatePacket.action == AnimatePacket.Action.WAKE_UP // these actions are only for server to client
                        || animatePacket.action == AnimatePacket.Action.CRITICAL_HIT
                        || animatePacket.action == AnimatePacket.Action.MAGIC_CRITICAL_HIT) {
                    break; // maybe we should cancel the event here? but if client send too many packets, server will lag
                }

                PlayerAnimationEvent animationEvent = new PlayerAnimationEvent(this, ((AnimatePacket) packet).action);
                this.server.getPluginManager().callEvent(animationEvent);
                if (animationEvent.isCancelled()) {
                    break;
                }

                AnimatePacket.Action animation = animationEvent.getAnimationType();

                switch (animation) {
                    case ROW_RIGHT:
                    case ROW_LEFT:
                        if (this.riding instanceof EntityBoat) {
                            ((EntityBoat) this.riding).onPaddle(animation, ((AnimatePacket) packet).rowingTime);
                        }
                        break;
                }

                if (animationEvent.getAnimationType() == AnimatePacket.Action.SWING_ARM) {
                    this.setNoShieldTicks(NO_SHIELD_DELAY);
                }

                animatePacket.eid = this.getId();
                animatePacket.action = animationEvent.getAnimationType();
                Server.broadcastPacket(this.getViewers().values(), animatePacket);
                break;
            case ProtocolInfo.ENTITY_EVENT_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                EntityEventPacket entityEventPacket = (EntityEventPacket) packet;

                if (entityEventPacket.event != EntityEventPacket.ENCHANT) {
                    this.craftingType = CRAFTING_SMALL;
                }

                switch (entityEventPacket.event) {
                    case EntityEventPacket.EATING_ITEM:
                        if (entityEventPacket.data == 0 || entityEventPacket.eid != this.id) {
                            break;
                        }

                        entityEventPacket.isEncoded = false;
                        entityEventPacket.originProtocol = this.protocol;
                        this.dataPacket(entityEventPacket);
                        Server.broadcastPacket(this.getViewers().values(), entityEventPacket);
                        break;
                    case EntityEventPacket.ENCHANT:
                        if (entityEventPacket.eid != this.id) {
                            break;
                        }

                        if (this.protocol >= ProtocolInfo.v1_16_0) {
                            Inventory inventory = this.getWindowById(ANVIL_WINDOW_ID);
                            if (inventory instanceof AnvilInventory) {
                                ((AnvilInventory) inventory).setCost(-entityEventPacket.data);
                            }
                            break;
                        }

                        int levels = entityEventPacket.data; // Sent as negative number of levels lost
                        if (levels < 0) {
                            this.setExperience(this.exp, this.expLevel + levels);
                        }
                        break;
                }
                break;
            case ProtocolInfo.COMMAND_REQUEST_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }
                this.craftingType = CRAFTING_SMALL;
                CommandRequestPacket commandRequestPacket = (CommandRequestPacket) packet;
                PlayerCommandPreprocessEvent playerCommandPreprocessEvent = new PlayerCommandPreprocessEvent(this, commandRequestPacket.command + ' ');
                this.server.getPluginManager().callEvent(playerCommandPreprocessEvent);
                if (playerCommandPreprocessEvent.isCancelled()) {
                    break;
                }

                this.server.dispatchCommand(playerCommandPreprocessEvent.getPlayer(), playerCommandPreprocessEvent.getMessage().substring(1));
                break;
            case ProtocolInfo.TEXT_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                TextPacket textPacket = (TextPacket) packet;

                if (textPacket.type == TextPacket.TYPE_CHAT) {
                    String chatMessage = textPacket.message;
                    int breakLine = chatMessage.indexOf('\n');
                    // Chat messages shouldn't contain break lines so ignore text afterwards
                    if (breakLine != -1) {
                        chatMessage = chatMessage.substring(0, breakLine);
                    }
                    this.chat(chatMessage);
                }
                break;
            case ProtocolInfo.CONTAINER_CLOSE_PACKET:
                ContainerClosePacket containerClosePacket = (ContainerClosePacket) packet;
                if (!this.spawned || (containerClosePacket.windowId == ContainerIds.INVENTORY && !inventoryOpen && this.protocol >= 407)) {
                    break;
                }

                if (this.windowIndex.containsKey(containerClosePacket.windowId)) {
                    this.server.getPluginManager().callEvent(new InventoryCloseEvent(this.windowIndex.get(containerClosePacket.windowId), this));
                    if (containerClosePacket.windowId == ContainerIds.INVENTORY) this.inventoryOpen = false;
                    this.closingWindowId = containerClosePacket.windowId;
                    this.removeWindow(this.windowIndex.get(containerClosePacket.windowId), true);
                    this.closingWindowId = Integer.MIN_VALUE;
                }
                if (containerClosePacket.windowId == -1) {
                    this.craftingType = CRAFTING_SMALL;
                    this.resetCraftingGridType();
                    this.addWindow(this.craftingGrid, ContainerIds.NONE);
                    if (this.protocol >= 407) {
                        ContainerClosePacket pk = new ContainerClosePacket();
                        pk.windowId = -1;
                        pk.wasServerInitiated = false;
                        this.dataPacket(pk);
                    }
                    //TODO Find out why the correct id is not returned
                    TradeInventory tradeInventory = this.getTradeInventory();
                    if (tradeInventory != null) {
                        this.removeWindow(tradeInventory, true);
                    }
                }
                break;
            case ProtocolInfo.BLOCK_ENTITY_DATA_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }
                BlockEntityDataPacket blockEntityDataPacket = (BlockEntityDataPacket) packet;
                this.craftingType = CRAFTING_SMALL;
                this.resetCraftingGridType();

                pos = this.temporalVector.setComponents(blockEntityDataPacket.x, blockEntityDataPacket.y, blockEntityDataPacket.z);
                if (pos.distanceSquared(this) > 10000) {
                    break;
                }

                BlockEntity t = this.level.getBlockEntity(pos);
                if (t instanceof BlockEntitySpawnable) {
                    CompoundTag nbt;
                    try {
                        nbt = NBTIO.read(blockEntityDataPacket.namedTag, ByteOrder.LITTLE_ENDIAN, true);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (!((BlockEntitySpawnable) t).updateCompoundTag(nbt, this)) {
                        ((BlockEntitySpawnable) t).spawnTo(this);
                    }
                }
                break;
            case ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET:
                RequestChunkRadiusPacket requestChunkRadiusPacket = (RequestChunkRadiusPacket) packet;
                ChunkRadiusUpdatedPacket chunkRadiusUpdatePacket = new ChunkRadiusUpdatedPacket();
                this.chunkRadius = Math.max(3, Math.min(requestChunkRadiusPacket.radius, this.viewDistance));
                chunkRadiusUpdatePacket.radius = this.chunkRadius;
                this.dataPacket(chunkRadiusUpdatePacket);
                break;
            case ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET:
                SetPlayerGameTypePacket setPlayerGameTypePacket = (SetPlayerGameTypePacket) packet;
                if (setPlayerGameTypePacket.gamemode != this.gamemode) {
                    if (!this.hasPermission("nukkit.command.gamemode")) {
                        this.kick(PlayerKickEvent.Reason.INVALID_PACKET, "Invalid SetPlayerGameTypePacket", true, "type=SetPlayerGameTypePacket");
                        /*SetPlayerGameTypePacket setPlayerGameTypePacket1 = new SetPlayerGameTypePacket();
                        setPlayerGameTypePacket1.gamemode = this.gamemode & 0x01;
                        this.dataPacket(setPlayerGameTypePacket1);
                        this.adventureSettings.update();*/
                        break;
                    }
                    this.setGamemode(setPlayerGameTypePacket.gamemode, true);
                    Command.broadcastCommandMessage(this, new TranslationContainer("commands.gamemode.success.self", Server.getGamemodeString(this.gamemode)));
                }
                break;
            case ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET:
                ItemFrameDropItemPacket itemFrameDropItemPacket = (ItemFrameDropItemPacket) packet;
                Vector3 vector3 = this.temporalVector.setComponents(itemFrameDropItemPacket.x, itemFrameDropItemPacket.y, itemFrameDropItemPacket.z);
                if (vector3.distanceSquared(this) < 1000) {
                    BlockEntity itemFrame = this.level.getBlockEntityIfLoaded(vector3);
                    if (itemFrame instanceof BlockEntityItemFrame) {
                        ((BlockEntityItemFrame) itemFrame).dropItem(this);
                    }
                }
                break;
            case ProtocolInfo.MAP_INFO_REQUEST_PACKET:
                MapInfoRequestPacket pk = (MapInfoRequestPacket) packet;
                ItemMap mapItem = null;

                for (Item item1 : this.offhandInventory.getContents().values()) {
                    if (item1 instanceof ItemMap map && map.getMapId() == pk.mapId) {
                        mapItem = map;
                    }
                }

                if (mapItem == null) {
                    for (Item item1 : this.inventory.getContents().values()) {
                        if (item1 instanceof ItemMap map && map.getMapId() == pk.mapId) {
                            mapItem = map;
                        }
                    }
                }

                if (mapItem == null) {
                    for (BlockEntity be : this.level.getBlockEntities().values()) {
                        if (be instanceof BlockEntityItemFrame itemFrame1) {

                            if (itemFrame1.getItem() instanceof ItemMap && ((ItemMap) itemFrame1.getItem()).getMapId() == pk.mapId) {
                                ((ItemMap) itemFrame1.getItem()).sendImage(this);
                                break;
                            }
                        }
                    }
                } else {
                    PlayerMapInfoRequestEvent event;
                    getServer().getPluginManager().callEvent(event = new PlayerMapInfoRequestEvent(this, mapItem));

                    if (!event.isCancelled()) {
                        if (mapItem.trySendImage(this)) {
                            return;
                        }

                        ItemMap finalMapItem = mapItem;
                        this.server.getScheduler().scheduleAsyncTask(new AsyncTask() {
                            @Override
                            public void onRun() {
                                finalMapItem.renderMap(Player.this.getLevel(), (Player.this.getFloorX() / 128) << 7, (Player.this.getFloorZ() / 128) << 7, 1);
                                finalMapItem.sendImage(Player.this);
                            }
                        });
                    }
                }
                break;
            case ProtocolInfo.LEVEL_SOUND_EVENT_PACKET:
            case ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V1:
            case ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V2:
                if (this.isSpectator()) {
                    //暂时保留，低版本客户端没有旁观模式，可能会发送这些数据包
                    if (((LevelSoundEventPacket) packet).sound == LevelSoundEventPacket.SOUND_HIT || ((LevelSoundEventPacket) packet).sound == LevelSoundEventPacket.SOUND_ATTACK_NODAMAGE || ((LevelSoundEventPacket) packet).sound == LevelSoundEventPacket.SOUND_ATTACK || ((LevelSoundEventPacket) packet).sound == LevelSoundEventPacket.SOUND_ATTACK_STRONG) {
                        break;
                    }
                }
                this.level.addChunkPacket(this.getChunkX(), this.getChunkZ(), packet);
                break;
            case ProtocolInfo.INVENTORY_TRANSACTION_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    log.debug("Player {} sent inventory transaction packet while not spawned or not alive", this.username);
                    break packetswitch;
                }

                if (this.isSpectator()) {
                    this.sendAllInventories();
                    break;
                }

                InventoryTransactionPacket transactionPacket = (InventoryTransactionPacket) packet;
                // Nasty hack because the client won't change the right packet in survival when creating netherite stuff,
                // so we are emulating what Mojang should be sending
                if (getWindowById(SMITHING_WINDOW_ID) instanceof SmithingInventory smithingInventory) {
                    // When players in creative mode are about to see the result, a fixed packet can help.
                    // One of those actions in the inventory transaction packet contains to-do sourceType,
                    // which can serve as a symbol to detect whether player is upgrading an item or not.
                    boolean creativeSmithingAboutToGetResult = this.isCreative() && (transactionPacket.transactionType == InventoryTransactionPacket.TYPE_NORMAL) && Arrays.stream(transactionPacket.actions).anyMatch(action -> action.sourceType == NetworkInventoryAction.SOURCE_TODO);
                    if ((transactionPacket.transactionType == InventoryTransactionPacket.TYPE_MISMATCH) || creativeSmithingAboutToGetResult) {
                        if (!smithingInventory.getResult().isNull()) {
                            InventoryTransactionPacket fixedPacket = new InventoryTransactionPacket();
                            fixedPacket.isRepairItemPart = true;
                            fixedPacket.actions = new NetworkInventoryAction[8];

                            Item fromIngredient = smithingInventory.getIngredient().clone();
                            Item toIngredient = fromIngredient.decrement(1);

                            Item fromEquipment = smithingInventory.getEquipment().clone();
                            Item toEquipment = fromEquipment.decrement(1);

                            Item fromTemplate = smithingInventory.getTemplate().clone();
                            Item toTemplate = fromTemplate.decrement(1);

                            Item fromResult = Item.get(Item.AIR);
                            Item toResult = smithingInventory.getResult().clone();

                            NetworkInventoryAction action = new NetworkInventoryAction();
                            action.windowId = ContainerIds.UI;
                            action.inventorySlot = SmithingInventory.SMITHING_INGREDIENT_UI_SLOT;
                            action.oldItem = fromIngredient.clone();
                            action.newItem = toIngredient.clone();
                            fixedPacket.actions[0] = action;

                            action = new NetworkInventoryAction();
                            action.windowId = ContainerIds.UI;
                            action.inventorySlot = SmithingInventory.SMITHING_EQUIPMENT_UI_SLOT;
                            action.oldItem = fromEquipment.clone();
                            action.newItem = toEquipment.clone();
                            fixedPacket.actions[1] = action;


                            action = new NetworkInventoryAction();
                            action.windowId = ContainerIds.UI;
                            action.inventorySlot = SmithingInventory.SMITHING_TEMPLATE_UI_SLOT;
                            action.oldItem = fromTemplate.clone();
                            action.newItem = toTemplate.clone();
                            fixedPacket.actions[2] = action;

                            int emptyPlayerSlot = -1;
                            for (int slot = 0; slot < inventory.getSize(); slot++) {
                                if (inventory.getItem(slot).isNull()) {
                                    emptyPlayerSlot = slot;
                                    break;
                                }
                            }
                            if (emptyPlayerSlot == -1) {
                                sendAllInventories();
                                getCursorInventory().sendContents(this);
                            } else {
                                action = new NetworkInventoryAction();
                                action.windowId = ContainerIds.INVENTORY;
                                action.inventorySlot = emptyPlayerSlot; // Cursor
                                action.oldItem = Item.get(Item.AIR);
                                action.newItem = toResult.clone();
                                fixedPacket.actions[3] = action;

                                action = new NetworkInventoryAction();
                                action.sourceType = NetworkInventoryAction.SOURCE_TODO;
                                action.windowId = NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT;
                                action.inventorySlot = 2; // result
                                action.oldItem = toResult.clone();
                                action.newItem = fromResult.clone();
                                fixedPacket.actions[4] = action;

                                action = new NetworkInventoryAction();
                                action.sourceType = NetworkInventoryAction.SOURCE_TODO;
                                action.windowId = NetworkInventoryAction.SOURCE_TYPE_ANVIL_INPUT;
                                action.inventorySlot = 0; // equipment
                                action.oldItem = toEquipment.clone();
                                action.newItem = fromEquipment.clone();
                                fixedPacket.actions[5] = action;

                                action = new NetworkInventoryAction();
                                action.sourceType = NetworkInventoryAction.SOURCE_TODO;
                                action.windowId = NetworkInventoryAction.SOURCE_TYPE_ANVIL_MATERIAL;
                                action.inventorySlot = 1; // material
                                action.oldItem = toIngredient.clone();
                                action.newItem = fromIngredient.clone();
                                fixedPacket.actions[6] = action;

                                action = new NetworkInventoryAction();
                                action.sourceType = NetworkInventoryAction.SOURCE_TODO;
                                action.windowId = NetworkInventoryAction.SOURCE_TYPE_ANVIL_MATERIAL;
                                action.inventorySlot = 3; // template
                                action.oldItem = toTemplate.clone();
                                action.newItem = fromTemplate.clone();
                                fixedPacket.actions[7] = action;

                                transactionPacket = fixedPacket;
                            }
                        }
                    }
                }

                List<InventoryAction> actions = new ArrayList<>();
                for (NetworkInventoryAction networkInventoryAction : transactionPacket.actions) {
                    InventoryAction a = networkInventoryAction.createInventoryAction(this);

                    if (a == null) {
                        this.getServer().getLogger().debug("Unmatched inventory action from " + this.username + ": " + networkInventoryAction);
                        this.getCursorInventory().sendContents(this);
                        this.sendAllInventories();
                        break packetswitch;
                    }

                    actions.add(a);
                }

                if (transactionPacket.isCraftingPart) {
                    if (this.craftingTransaction == null) {
                        this.craftingTransaction = new CraftingTransaction(this, actions);
                    } else {
                        for (InventoryAction action : actions) {
                            this.craftingTransaction.addAction(action);
                        }
                    }

                    if (this.craftingTransaction.getPrimaryOutput() != null && this.craftingTransaction.canExecute()) {
                        try {
                            this.craftingTransaction.execute();
                        } catch (Exception e) {
                            this.server.getLogger().debug("Executing crafting transaction failed");
                        }
                        this.craftingTransaction = null;
                    }
                    return;
                } else if (this.protocol >= ProtocolInfo.v1_16_0 && transactionPacket.isEnchantingPart) {
                    if (this.enchantTransaction == null) {
                        this.enchantTransaction = new EnchantTransaction(this, actions);
                    } else {
                        for (InventoryAction action : actions) {
                            this.enchantTransaction.addAction(action);
                        }
                    }
                    if (this.enchantTransaction.canExecute()) {
                        this.enchantTransaction.execute();
                        this.enchantTransaction = null;
                    }
                    return;
                } else if (this.protocol >= ProtocolInfo.v1_16_0 && transactionPacket.isRepairItemPart) {
                    Sound sound = null;
                    if (SmithingTransaction.checkForItemPart(actions)) {
                        if (this.smithingTransaction == null) {
                            this.smithingTransaction = new SmithingTransaction(this, actions);
                        } else {
                            for (InventoryAction action : actions) {
                                this.smithingTransaction.addAction(action);
                            }
                        }
                        if (this.smithingTransaction.canExecute()) {
                            try {
                                if (this.smithingTransaction.execute()) {
                                    sound = Sound.SMITHING_TABLE_USE;
                                }
                            } finally {
                                this.smithingTransaction = null;
                            }
                        }
                    } else {
                        if (this.repairItemTransaction == null) {
                            this.repairItemTransaction = new RepairItemTransaction(this, actions);
                        } else {
                            for (InventoryAction action : actions) {
                                this.repairItemTransaction.addAction(action);
                            }
                        }
                        if (this.repairItemTransaction.canExecute()) {
                            this.repairItemTransaction.execute();
                            this.repairItemTransaction = null;
                        }
                    }

                    if (sound != null) {
                        Collection<Player> players = level.getChunkPlayers(getChunkX(), getChunkZ()).values();
                        players.remove(this);
                        if (!players.isEmpty()) {
                            level.addSound(this, sound, 1f, 1f, players);
                        }
                    }
                    return;
                } else if (transactionPacket.isTradeItemPart) {
                    if (this.tradingTransaction == null) {
                        this.tradingTransaction = new TradingTransaction(this, actions);
                    } else {
                        for (InventoryAction action : actions) {
                            this.tradingTransaction.addAction(action);
                        }
                    }
                    if (this.tradingTransaction.canExecute()) {
                        this.tradingTransaction.execute();

                        for (Inventory inventory : this.tradingTransaction.getInventories()) {

                            if (inventory instanceof TradeInventory tradeInventory) {
                                EntityVillager ent = tradeInventory.getHolder();
                                ent.namedTag.putBoolean("traded", true);
                                for (Tag tag : ent.getRecipes().getAll()) {
                                    CompoundTag ta = (CompoundTag) tag;
                                    if (ta.getCompound("buyA").getShort("id") == tradeInventory.getItem(0).getId()) {
                                        int tradeXP = ta.getInt("traderExp");
                                        this.addExperience(ta.getByte("rewardExp"));
                                        ent.addExperience(tradeXP);
                                        this.level.addSound(this, Sound.RANDOM_ORB, 0,3f, this);
                                    }
                                }
                            }
                        }

                        this.tradingTransaction = null;
                    }
                    return;
                } else if (this.craftingTransaction != null) {
                    if (craftingTransaction.checkForCraftingPart(actions)) {
                        for (InventoryAction action : actions) {
                            craftingTransaction.addAction(action);
                        }
                        return;
                    } else {
                        this.server.getLogger().debug("Got unexpected normal inventory action with incomplete crafting transaction from " + this.username + ", refusing to execute crafting");
                        if (this.protocol >= ProtocolInfo.v1_16_0) {
                            this.removeAllWindows(false);
                            this.getCursorInventory().sendContents(this);
                            this.sendAllInventories();
                        }
                        this.craftingTransaction = null;
                    }
                } else if (this.protocol >= ProtocolInfo.v1_16_0 && this.enchantTransaction != null) {
                    if (enchantTransaction.checkForEnchantPart(actions)) {
                        for (InventoryAction action : actions) {
                            enchantTransaction.addAction(action);
                        }
                        return;
                    } else {
                        this.server.getLogger().debug("Got unexpected normal inventory action with incomplete enchanting transaction from " + this.username + ", refusing to execute enchant " + transactionPacket.toString());
                        this.removeAllWindows(false);
                        this.getCursorInventory().sendContents(this);
                        this.sendAllInventories();
                        this.enchantTransaction = null;
                    }
                } else if (this.protocol >= ProtocolInfo.v1_16_0 && this.repairItemTransaction != null) {
                    if (RepairItemTransaction.checkForRepairItemPart(actions)) {
                        for (InventoryAction action : actions) {
                            this.repairItemTransaction.addAction(action);
                        }
                        return;
                    } else {
                        this.server.getLogger().debug("Got unexpected normal inventory action with incomplete repair item transaction from " + this.username + ", refusing to execute repair item " + transactionPacket.toString());
                        this.removeAllWindows(false);
                        this.getCursorInventory().sendContents(this);
                        this.sendAllInventories();
                        this.repairItemTransaction = null;
                    }
                } else if (this.protocol >= ProtocolInfo.v1_16_0 && this.smithingTransaction != null) {
                    if (SmithingTransaction.checkForItemPart(actions)) {
                        for (InventoryAction action : actions) {
                            this.smithingTransaction.addAction(action);
                        }
                        return;
                    } else {
                        log.debug("Got unexpected normal inventory action with incomplete smithing table transaction from {}, refusing to execute use the smithing table {}", this.getName(), transactionPacket.toString());
                        this.removeAllWindows(false);
                        this.sendAllInventories();
                        this.smithingTransaction = null;
                    }
                }

                switch (transactionPacket.transactionType) {
                    case InventoryTransactionPacket.TYPE_NORMAL:
                        InventoryTransaction transaction = new InventoryTransaction(this, actions);

                        if (!transaction.execute()) {
                            this.server.getLogger().debug("Failed to execute inventory transaction from " + this.username + " with actions: " + Arrays.toString(transactionPacket.actions));
                            failedTransactions++;
                            if (failedTransactions > 15) { //撤回合成事件时，如果玩家点的太快会到12
                                this.close("", "Too many failed inventory transactions");
                            }
                            break packetswitch;
                        }

                        break packetswitch;
                    case InventoryTransactionPacket.TYPE_MISMATCH:
                        if (transactionPacket.actions.length > 0) {
                            this.server.getLogger().debug("Expected 0 actions for mismatch, got " + transactionPacket.actions.length + ", " + Arrays.toString(transactionPacket.actions));
                        }
                        this.getCursorInventory().sendContents(this);
                        this.sendAllInventories();
                        break packetswitch;
                    case InventoryTransactionPacket.TYPE_USE_ITEM:
                        UseItemData useItemData;
                        BlockVector3 blockVector;
                        int type;

                        try {
                            useItemData = (UseItemData) transactionPacket.transactionData;
                            blockVector = useItemData.blockPos;
                            face = useItemData.face;
                            type = useItemData.actionType;
                        } catch (Exception ignored) {
                            break packetswitch;
                        }

                        if (inventory.getHeldItemIndex() != useItemData.hotbarSlot) {
                            inventory.equipItem(useItemData.hotbarSlot);
                        }

                        switch (type) {
                            case InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_BLOCK:
                                boolean spamming = !server.doNotLimitInteractions
                                        && lastRightClickPos != null
                                        && System.currentTimeMillis() - lastRightClickTime < 100.0
                                        && blockVector.distanceSquared(lastRightClickPos) < 0.00001;

                                lastRightClickPos = blockVector.asVector3();
                                lastRightClickTime = System.currentTimeMillis();

                                // Hack: Fix client spamming right clicks
                                if (spamming && this.getInventory().getItemInHandFast().getBlockId() == BlockID.AIR) {
                                    return;
                                }

                                this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false);

                                if (!(this.distance(blockVector.asVector3()) > (this.isCreative() ? 13 : 7))) {
                                    if (this.isCreative()) {
                                        if (this.level.useItemOn(blockVector.asVector3(), inventory.getItemInHand(), face, useItemData.clickPos.x, useItemData.clickPos.y, useItemData.clickPos.z, this) != null) {
                                            break packetswitch;
                                        }
                                    } else if (inventory.getItemInHand().equals(useItemData.itemInHand)) {
                                        Item i = inventory.getItemInHand();
                                        Item oldItem = i.clone();
                                        if ((i = this.level.useItemOn(blockVector.asVector3(), i, face, useItemData.clickPos.x, useItemData.clickPos.y, useItemData.clickPos.z, this)) != null) {
                                            if (!i.equals(oldItem) || i.getCount() != oldItem.getCount()) {
                                                if (oldItem.getId() == i.getId() || i.getId() == 0) {
                                                    inventory.setItemInHand(i);
                                                } else {
                                                    server.getLogger().debug("Tried to set item " + i.getId() + " but " + this.username + " had item " + oldItem.getId() + " in their hand slot");
                                                }
                                                inventory.sendHeldItem(this.getViewers().values());
                                            }
                                            break packetswitch;
                                        }
                                    } else {
                                        inventory.sendHeldItem(this);
                                    }
                                }

                                if (blockVector.distanceSquared(this) > 10000) {
                                    break packetswitch;
                                }

                                Block target = this.level.getBlock(blockVector.asVector3());
                                block = target.getSide(face);

                                this.level.sendBlocks(new Player[]{this}, new Block[]{target, block}, UpdateBlockPacket.FLAG_NOGRAPHIC);
                                this.level.sendBlocks(new Player[]{this}, new Block[]{target.getLevelBlockAtLayer(1), block.getLevelBlockAtLayer(1)}, UpdateBlockPacket.FLAG_NOGRAPHIC, 1);

                                if (target instanceof BlockDoor) {
                                    BlockDoor door = (BlockDoor) target;

                                    Block part;

                                    if ((door.getDamage() & 0x08) > 0) {
                                        part = target.down();

                                        if (part.getId() == target.getId()) {
                                            target = part;
                                            this.level.sendBlocks(new Player[]{this}, new Block[]{target}, UpdateBlockPacket.FLAG_NOGRAPHIC);
                                            this.level.sendBlocks(new Player[]{this}, new Block[]{target.getLevelBlockAtLayer(1)}, UpdateBlockPacket.FLAG_NOGRAPHIC, 1);
                                        }
                                    }
                                }
                                break packetswitch;
                            case InventoryTransactionPacket.USE_ITEM_ACTION_BREAK_BLOCK:
                                if (!this.spawned || !this.isAlive()) {
                                    break packetswitch;
                                }

                                this.resetCraftingGridType();

                                Item i = this.getInventory().getItemInHand();

                                Item oldItem = i.clone();

                                if (this.canInteract(blockVector.add(0.5, 0.5, 0.5), this.isCreative() ? 13 : 7) && (i = this.level.useBreakOn(blockVector.asVector3(), face, i, this, true)) != null) {
                                    if (this.isSurvival() || this.isAdventure()) {
                                        this.foodData.updateFoodExpLevel(0.005);
                                        if (!i.equals(oldItem) || i.getCount() != oldItem.getCount()) {
                                            if (oldItem.getId() == i.getId() || i.getId() == 0) {
                                                inventory.setItemInHand(i);
                                            } else {
                                                server.getLogger().debug("Tried to set item " + i.getId() + " but " + this.username + " had item " + oldItem.getId() + " in their hand slot");
                                            }
                                            inventory.sendHeldItem(this.getViewers().values());
                                        }
                                    }
                                    break packetswitch;
                                }

                                inventory.sendContents(this);
                                inventory.sendHeldItem(this);

                                if (blockVector.distanceSquared(this) < 10000) {
                                    target = this.level.getBlock(blockVector.asVector3());
                                    this.level.sendBlocks(new Player[]{this}, new Block[]{target}, UpdateBlockPacket.FLAG_ALL_PRIORITY);

                                    BlockEntity blockEntity = this.level.getBlockEntity(blockVector.asVector3());
                                    if (blockEntity instanceof BlockEntitySpawnable) {
                                        ((BlockEntitySpawnable) blockEntity).spawnTo(this);
                                    }
                                }

                                break packetswitch;
                            case InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR:
                                Vector3 directionVector = this.getDirectionVector();

                                if (inventory.getHeldItemIndex() != useItemData.hotbarSlot) {
                                    inventory.equipItem(useItemData.hotbarSlot);
                                }

                                item = this.inventory.getItemInHand();

                                if (item instanceof ItemCrossbow) {
                                    if (!item.onClickAir(this, directionVector)) {
                                        return; // Shoot
                                    }
                                }

                                if (!item.equalsFast(useItemData.itemInHand)) {
                                    this.inventory.sendHeldItem(this);
                                    break packetswitch;
                                }

                                PlayerInteractEvent interactEvent = new PlayerInteractEvent(this, item, directionVector, face, Action.RIGHT_CLICK_AIR);

                                this.server.getPluginManager().callEvent(interactEvent);

                                if (interactEvent.isCancelled()) {
                                    this.inventory.sendHeldItem(this);
                                    break packetswitch;
                                }

                                if (item.onClickAir(this, directionVector)) {
                                    if (this.isSurvival() || this.isAdventure()) {
                                        if (item.getId() == 0 || this.inventory.getItemInHandFast().getId() == item.getId()) {
                                            this.inventory.setItemInHand(item);
                                        } else {
                                            server.getLogger().debug("Tried to set item " + item.getId() + " but " + this.username + " had item " + this.inventory.getItemInHandFast().getId() + " in their hand slot");
                                        }
                                    }

                                    if (!this.isUsingItem()) {
                                        this.setUsingItem(true);
                                        break packetswitch;
                                    }

                                    // Used item
                                    int ticksUsed = this.server.getTick() - this.startAction;
                                    this.setUsingItem(false);
                                    if (!item.onUse(this, ticksUsed)) {
                                        this.inventory.sendContents(this);
                                    }
                                }

                                break packetswitch;
                            default:
                                break;
                        }
                        break;
                    case InventoryTransactionPacket.TYPE_USE_ITEM_ON_ENTITY:
                        UseItemOnEntityData useItemOnEntityData = (UseItemOnEntityData) transactionPacket.transactionData;

                        Entity target = this.level.getEntity(useItemOnEntityData.entityRuntimeId);
                        if (target == null) {
                            return;
                        }

                        type = useItemOnEntityData.actionType;

                        if (inventory.getHeldItemIndex() != useItemOnEntityData.hotbarSlot) {
                            inventory.equipItem(useItemOnEntityData.hotbarSlot);
                        }

                        if (!useItemOnEntityData.itemInHand.equalsFast(this.inventory.getItemInHand())) {
                            this.inventory.sendHeldItem(this);
                        }

                        item = this.inventory.getItemInHand();

                        switch (type) {
                            case InventoryTransactionPacket.USE_ITEM_ON_ENTITY_ACTION_INTERACT:
                                PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(this, target, item, useItemOnEntityData.clickPos);
                                if (this.isSpectator()) playerInteractEntityEvent.setCancelled();
                                getServer().getPluginManager().callEvent(playerInteractEntityEvent);

                                if (playerInteractEntityEvent.isCancelled()) {
                                    break;
                                }

                                if (target.onInteract(this, item, useItemOnEntityData.clickPos) && (this.isSurvival() || this.isAdventure())) {
                                    if (item.isTool()) {
                                        if (item.useOn(target) && item.getDamage() >= item.getMaxDurability()) {
                                            level.addSoundToViewers(this, Sound.RANDOM_BREAK);
                                            level.addParticle(new ItemBreakParticle(this, item));
                                            item = new ItemBlock(Block.get(BlockID.AIR));
                                        }
                                    } else {
                                        if (item.count > 1) {
                                            item.count--;
                                        } else {
                                            item = new ItemBlock(Block.get(BlockID.AIR));
                                        }
                                    }

                                    if (item.getId() == 0 || this.inventory.getItemInHandFast().getId() == item.getId()) {
                                        this.inventory.setItemInHand(item);
                                    } else {
                                        server.getLogger().debug("Tried to set item " + item.getId() + " but " + this.username + " had item " + this.inventory.getItemInHandFast().getId() + " in their hand slot");
                                    }
                                }
                                break;
                            case InventoryTransactionPacket.USE_ITEM_ON_ENTITY_ACTION_ATTACK:
                                if (target.getId() == this.getId()) {
                                    this.kick(PlayerKickEvent.Reason.INVALID_PVP, "Tried to attack invalid player");
                                    return;
                                }

                                if (!this.canInteractEntity(target, isCreative() ? 8 : 5)) {
                                    break;
                                } else if (target instanceof Player) {
                                    if ((((Player) target).gamemode & 0x01) > 0) {
                                        break;
                                    } else if (!this.server.pvpEnabled) {
                                        break;
                                    }
                                }

                                Enchantment[] enchantments = item.getEnchantments();

                                float itemDamage = item.getAttackDamage();
                                for (Enchantment enchantment : enchantments) {
                                    itemDamage += enchantment.getDamageBonus(target);
                                }

                                Map<DamageModifier, Float> damage = new EnumMap<>(DamageModifier.class);
                                damage.put(DamageModifier.BASE, itemDamage);

                                float knockBack = 0.3f;
                                Enchantment knockBackEnchantment = item.getEnchantment(Enchantment.ID_KNOCKBACK);
                                if (knockBackEnchantment != null) {
                                    knockBack += knockBackEnchantment.getLevel() * 0.1f;
                                }

                                EntityDamageByEntityEvent entityDamageByEntityEvent = new EntityDamageByEntityEvent(this, target, DamageCause.ENTITY_ATTACK, damage, knockBack, enchantments);
                                entityDamageByEntityEvent.setBreakShield(item.canBreakShield());
                                if (this.isSpectator()) entityDamageByEntityEvent.setCancelled();
                                if ((target instanceof Player) && !this.level.getGameRules().getBoolean(GameRule.PVP)) {
                                    entityDamageByEntityEvent.setCancelled();
                                }

                                if (!target.attack(entityDamageByEntityEvent)) {
                                    if (item.isTool() && !this.isCreative()) {
                                        this.inventory.sendContents(this);
                                    }
                                    break;
                                }

                                for (Enchantment enchantment : item.getEnchantments()) {
                                    enchantment.doPostAttack(this, target);
                                }

                                if (item.isTool() && !this.isCreative()) {
                                    if (item.useOn(target) && item.getDamage() >= item.getMaxDurability()) {
                                        level.addSoundToViewers(this, Sound.RANDOM_BREAK);
                                        level.addParticle(new ItemBreakParticle(this, item));
                                        this.inventory.setItemInHand(Item.get(0));
                                    } else {
                                        if (item.getId() == 0 || this.inventory.getItemInHandFast().getId() == item.getId()) {
                                            this.inventory.setItemInHand(item);
                                        } else {
                                            server.getLogger().debug("Tried to set item " + item.getId() + " but " + this.username + " had item " + this.inventory.getItemInHandFast().getId() + " in their hand slot");
                                        }
                                    }
                                }
                                return;
                            default:
                                break;
                        }

                        break;
                    case InventoryTransactionPacket.TYPE_RELEASE_ITEM:
                        if (this.isSpectator()) {
                            this.sendAllInventories();
                            break packetswitch;
                        }
                        ReleaseItemData releaseItemData = (ReleaseItemData) transactionPacket.transactionData;

                        try {
                            type = releaseItemData.actionType;
                            switch (type) {
                                case InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE:
                                    if (this.isUsingItem()) {
                                        item = this.inventory.getItemInHand();
                                        int ticksUsed = this.server.getTick() - this.startAction;
                                        if (!item.onRelease(this, ticksUsed)) {
                                            this.inventory.sendContents(this);
                                        }
                                        this.setUsingItem(false);
                                    } else {
                                        this.inventory.sendContents(this);
                                    }
                                    return;
                                case InventoryTransactionPacket.RELEASE_ITEM_ACTION_CONSUME:
                                    if (this.protocol >= 388)
                                        break; // Usage of potions on 1.13 and later is handled at ItemPotion#onUse
                                    Item itemInHand = this.inventory.getItemInHand();
                                    PlayerItemConsumeEvent consumeEvent = new PlayerItemConsumeEvent(this, itemInHand);

                                    if (itemInHand.getId() == Item.POTION) {
                                        this.server.getPluginManager().callEvent(consumeEvent);
                                        if (consumeEvent.isCancelled()) {
                                            this.inventory.sendContents(this);
                                            break;
                                        }
                                        Potion potion = Potion.getPotion(itemInHand.getDamage());

                                        if (this.gamemode == SURVIVAL || this.gamemode == ADVENTURE) {
                                            this.getInventory().decreaseCount(this.getInventory().getHeldItemIndex());
                                            this.inventory.addItem(new ItemGlassBottle());
                                        }

                                        if (potion != null) {
                                            potion.applyPotion(this);
                                        }
                                    } else { // Food
                                        this.server.getPluginManager().callEvent(consumeEvent);
                                        if (consumeEvent.isCancelled()) {
                                            this.inventory.sendContents(this);
                                            break;
                                        }

                                        Food food = Food.getByRelative(itemInHand);
                                        if (food != null && food.eatenBy(this)) {
                                            this.getInventory().decreaseCount(this.getInventory().getHeldItemIndex());
                                        }
                                    }
                                    return;
                                default:
                                    break;
                            }
                        } finally {
                            this.setUsingItem(false);
                        }
                        break;
                    default:
                        this.inventory.sendContents(this);
                        break;
                }
                break;
            case ProtocolInfo.PLAYER_HOTBAR_PACKET:
                PlayerHotbarPacket hotbarPacket = (PlayerHotbarPacket) packet;

                if (hotbarPacket.windowId != ContainerIds.INVENTORY) {
                    return;
                }

                this.inventory.equipItem(hotbarPacket.selectedHotbarSlot);
                break;
            case ProtocolInfo.SERVER_SETTINGS_REQUEST_PACKET:
                PlayerServerSettingsRequestEvent settingsRequestEvent = new PlayerServerSettingsRequestEvent(this, new HashMap<>(this.serverSettings));
                this.getServer().getPluginManager().callEvent(settingsRequestEvent);

                if (!settingsRequestEvent.isCancelled()) {
                    settingsRequestEvent.getSettings().forEach((id, window) -> {
                        ServerSettingsResponsePacket re = new ServerSettingsResponsePacket();
                        re.formId = id;
                        re.data = window.getJSONData();
                        this.dataPacket(re);
                    });
                }
                break;
            case ProtocolInfo.RESPAWN_PACKET:
                if (this.isAlive() || this.protocol < 388) {
                    break;
                }

                RespawnPacket respawnPacket = (RespawnPacket) packet;
                if (respawnPacket.respawnState == RespawnPacket.STATE_CLIENT_READY_TO_SPAWN) {
                    RespawnPacket respawn1 = new RespawnPacket();
                    respawn1.x = (float) this.getX();
                    respawn1.y = (float) this.getY();
                    respawn1.z = (float) this.getZ();
                    respawn1.respawnState = RespawnPacket.STATE_READY_TO_SPAWN;
                    this.dataPacket(respawn1);
                }
                break;
            case ProtocolInfo.BOOK_EDIT_PACKET:
                BookEditPacket bookEditPacket = (BookEditPacket) packet;
                Item oldBook = this.inventory.getItem(bookEditPacket.inventorySlot);
                if (oldBook.getId() != Item.BOOK_AND_QUILL) {
                    return;
                }

                if (bookEditPacket.text != null && bookEditPacket.text.length() > 256) {
                    this.getServer().getLogger().debug(username + ": BookEditPacket with too long text");
                    return;
                }

                Item newBook = oldBook.clone();
                boolean success;
                switch (bookEditPacket.action) {
                    case REPLACE_PAGE:
                        success = ((ItemBookAndQuill) newBook).setPageText(bookEditPacket.pageNumber, bookEditPacket.text);
                        break;
                    case ADD_PAGE:
                        success = ((ItemBookAndQuill) newBook).insertPage(bookEditPacket.pageNumber, bookEditPacket.text);
                        break;
                    case DELETE_PAGE:
                        success = ((ItemBookAndQuill) newBook).deletePage(bookEditPacket.pageNumber);
                        break;
                    case SWAP_PAGES:
                        success = ((ItemBookAndQuill) newBook).swapPages(bookEditPacket.pageNumber, bookEditPacket.secondaryPageNumber);
                        break;
                    case SIGN_BOOK:
                        if (bookEditPacket.title == null || bookEditPacket.author == null || bookEditPacket.xuid == null || bookEditPacket.title.length() > 64 || bookEditPacket.author.length() > 64 || bookEditPacket.xuid.length() > 64) {
                            this.getServer().getLogger().debug(username + ": Invalid BookEditPacket action SIGN_BOOK: title/author/xuid is too long");
                            return;
                        }
                        newBook = Item.get(Item.WRITTEN_BOOK, 0, 1, oldBook.getCompoundTag());
                        success = ((ItemBookWritten) newBook).signBook(bookEditPacket.title, bookEditPacket.author, bookEditPacket.xuid, ItemBookWritten.GENERATION_ORIGINAL);
                        break;
                    default:
                        return;
                }

                if (success) {
                    PlayerEditBookEvent editBookEvent = new PlayerEditBookEvent(this, oldBook, newBook, bookEditPacket.action);
                    this.server.getPluginManager().callEvent(editBookEvent);
                    if (!editBookEvent.isCancelled()) {
                        this.inventory.setItem(bookEditPacket.inventorySlot, editBookEvent.getNewBook());
                    }
                }
                break;
            case ProtocolInfo.FILTER_TEXT_PACKET:
                FilterTextPacket filterTextPacket = (FilterTextPacket) packet;
                if (filterTextPacket.text == null || filterTextPacket.text.length() > 64) {
                    this.getServer().getLogger().debug(username + ": FilterTextPacket with too long text");
                    return;
                }
                FilterTextPacket textResponsePacket = new FilterTextPacket();
                textResponsePacket.text = filterTextPacket.text;
                textResponsePacket.fromServer = true;
                this.dataPacket(textResponsePacket);
                break;
            case ProtocolInfo.SET_DIFFICULTY_PACKET:
                if (!this.spawned || !this.hasPermission("nukkit.command.difficulty")) {
                    return;
                }
                server.setDifficulty(((SetDifficultyPacket) packet).difficulty);
                SetDifficultyPacket difficultyPacket = new SetDifficultyPacket();
                difficultyPacket.difficulty = server.getDifficulty();
                Server.broadcastPacket(server.getOnlinePlayers().values(), difficultyPacket);
                Command.broadcastCommandMessage(this, new TranslationContainer("commands.difficulty.success", String.valueOf(server.getDifficulty())));
                break;
            case ProtocolInfo.PACKET_VIOLATION_WARNING_PACKET:
                PacketViolationWarningPacket PVWpk = (PacketViolationWarningPacket) packet;
                if (pkIDs == null) {
                    pkIDs = Arrays.stream(ProtocolInfo.class.getDeclaredFields()).filter(field -> field.getType() == Byte.TYPE);
                }
                Optional<String> PVWpkName = pkIDs
                        .filter(field -> {
                            try {
                                return field.getByte(null) == ((PacketViolationWarningPacket) packet).packetId;
                            } catch (IllegalAccessException e) {
                                return false;
                            }
                        }).map(Field::getName).findFirst();
                this.getServer().getLogger().warning("PacketViolationWarningPacket" + PVWpkName.map(name -> " for packet " + name).orElse(" UNKNOWN") + " from " + this.username + " (Protocol " + this.protocol + "): " + PVWpk.toString());
                break;
            case ProtocolInfo.EMOTE_PACKET:
                if (!this.spawned || server.getTick() - this.lastEmote < 20) {
                    return;
                }
                this.lastEmote = server.getTick();
                EmotePacket emotePacket = (EmotePacket) packet;
                if (emotePacket.runtimeId != this.id) {
                    server.getLogger().warning(this.username + " tried to send EmotePacket with invalid entity id: " + emotePacket.runtimeId + "!=" + this.id);
                    return;
                }
                this.emote(emotePacket);
                break;
            default:
                break;
        }
    }

    private void onBlockBreakContinue(Vector3 pos, BlockFace face) {
        if (this.isBreakingBlock()) {
            Block block = this.level.getBlock(pos, false);
            this.level.addParticle(new PunchBlockParticle(pos, block, face));
        }
    }

    private void onBlockBreakStart(Vector3 pos, BlockFace face) {
        BlockVector3 blockPos = pos.asBlockVector3();
        long currentBreak = System.currentTimeMillis();
        // HACK: Client spams multiple left clicks so we need to skip them.
        if ((this.lastBreakPosition.equals(blockPos) && (currentBreak - this.lastBreak) < 10) || pos.distanceSquared(this) > 100) {
            return;
        }

        Block target = this.level.getBlock(pos);
        PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(this, this.inventory.getItemInHand(), target, face,
                target.getId() == 0 ? Action.LEFT_CLICK_AIR : Action.LEFT_CLICK_BLOCK);
        this.getServer().getPluginManager().callEvent(playerInteractEvent);
        if (playerInteractEvent.isCancelled()) {
            this.inventory.sendHeldItem(this);
            return;
        }

        if (target.onTouch(this, playerInteractEvent.getAction()) != 0) {
            return;
        }

        Block block = target.getSide(face);
        if (block.getId() == Block.FIRE) {
            this.level.setBlock(block, Block.get(BlockID.AIR), true);
            this.level.addLevelSoundEvent(block, LevelSoundEventPacket.SOUND_EXTINGUISH_FIRE);
            return;
        }

        if (!this.isCreative()) {
            double breakTime = Math.ceil(target.calculateBreakTime(this.inventory.getItemInHand(), this) * 20);
            if (breakTime > 0) {
                LevelEventPacket pk = new LevelEventPacket();
                pk.evid = LevelEventPacket.EVENT_BLOCK_START_BREAK;
                pk.x = (float) pos.x;
                pk.y = (float) pos.y;
                pk.z = (float) pos.z;
                pk.data = (int) (65535 / breakTime);
                this.getLevel().addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, pk);

                // 优化反矿透时玩家的挖掘体验
                if (this.getLevel().antiXrayEnabled()) {
                    Vector3[] vector3s = new Vector3[5];
                    int index = 0;
                    for (BlockFace each : BlockFace.values()) {
                        if (each == face) {
                            continue;
                        }
                        int tmpX = target.getFloorX() + each.getXOffset();
                        int tmpY = target.getFloorY() + each.getYOffset();
                        int tmpZ = target.getFloorZ() + each.getZOffset();
                        if (Level.xrayableBlocks[this.getLevel().getBlockIdAt(tmpX, tmpY, tmpZ)]) {
                            vector3s[index] = new Vector3(tmpX, tmpY, tmpZ);
                            index++;
                        }
                    }
                    this.getLevel().sendBlocks(new Player[]{this}, vector3s, UpdateBlockPacket.FLAG_ALL);
                }
            }
        }

        this.breakingBlock = target;
        this.lastBreak = currentBreak;
        this.lastBreakPosition = blockPos;
    }

    private void onBlockBreakAbort(Vector3 pos, BlockFace face) {
        if (pos.distanceSquared(this) < 100) {
            LevelEventPacket pk = new LevelEventPacket();
            pk.evid = LevelEventPacket.EVENT_BLOCK_STOP_BREAK;
            pk.x = (float) pos.x;
            pk.y = (float) pos.y;
            pk.z = (float) pos.z;
            pk.data = 0;
            this.getLevel().addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, pk);
        }
        this.breakingBlock = null;
    }

    private void onBlockBreakComplete(BlockVector3 blockPos, BlockFace face) {
        if (!this.spawned || !this.isAlive()) {
            return;
        }

        this.resetCraftingGridType();

        Item handItem = this.getInventory().getItemInHand();
        Item clone = handItem.clone();

        boolean canInteract = this.canInteract(blockPos.add(0.5, 0.5, 0.5), this.isCreative() ? 13 : 7);
        if (canInteract) {
            handItem = this.level.useBreakOn(blockPos.asVector3(), face, handItem, this, true);
            if (handItem == null) {
                this.level.sendBlocks(new Player[]{this}, new Vector3[]{blockPos.asVector3()}, UpdateBlockPacket.FLAG_ALL_PRIORITY);
            } else if (this.isSurvival()) {
                this.getFoodData().updateFoodExpLevel(0.005);
                if (handItem.equals(clone) && handItem.getCount() == clone.getCount()) {
                    return;
                }

                if (clone.getId() == handItem.getId() || handItem.getId() == 0) {
                    inventory.setItemInHand(handItem);
                } else {
                    server.getLogger().debug("Tried to set item " + handItem.getId() + " but " + this.username + " had item " + clone.getId() + " in their hand slot");
                }
                inventory.sendHeldItem(this.getViewers().values());
            }
            return;
        }

        inventory.sendContents(this);
        inventory.sendHeldItem(this);

        if (blockPos.distanceSquared(this) < 100) {
            Block target = this.level.getBlock(blockPos.asVector3());
            this.level.sendBlocks(new Player[]{this}, new Block[]{target}, UpdateBlockPacket.FLAG_ALL_PRIORITY);

            BlockEntity blockEntity = this.level.getBlockEntity(blockPos.asVector3());
            if (blockEntity instanceof BlockEntitySpawnable) {
                ((BlockEntitySpawnable) blockEntity).spawnTo(this);
            }
        }
    }

    /**
     * Sends a chat message as this player
     *
     * @param message message to send
     * @return successful
     */
    public boolean chat(String message) {
        this.resetCraftingGridType();
        this.craftingType = CRAFTING_SMALL;

        if (this.removeFormat) {
            message = TextFormat.clean(message, true);
        }

        int maxMsgLength = this.protocol >= ProtocolInfo.v1_18_0 ? 512 : 255;

        for (String msg : message.split("\n")) {
            if (!msg.trim().isEmpty() && msg.length() <= maxMsgLength) {
                PlayerChatEvent chatEvent = new PlayerChatEvent(this, msg);
                this.server.getPluginManager().callEvent(chatEvent);
                if (!chatEvent.isCancelled()) {
                    this.server.broadcastMessage(this.getServer().getLanguage().translateString(chatEvent.getFormat(), new String[]{chatEvent.getPlayer().displayName, chatEvent.getMessage()}), chatEvent.getRecipients());
                }
            }
        }

        return true;
    }

    public void emote(EmotePacket emote) {
        for (Player player : this.getViewers().values()) {
            if (player.protocol >= ProtocolInfo.v1_16_0) {
                player.dataPacket(emote);
            }
        }
    }

    public boolean kick() {
        return this.kick("");
    }

    public boolean kick(String reason, boolean isAdmin) {
        return this.kick(PlayerKickEvent.Reason.UNKNOWN, reason, isAdmin);
    }

    public boolean kick(String reason) {
        return kick(PlayerKickEvent.Reason.UNKNOWN, reason);
    }

    public boolean kick(PlayerKickEvent.Reason reason) {
        return this.kick(reason, true);
    }

    public boolean kick(PlayerKickEvent.Reason reason, String reasonString) {
        return this.kick(reason, reasonString, true);
    }

    public boolean kick(PlayerKickEvent.Reason reason, boolean isAdmin) {
        return this.kick(reason, reason.toString(), isAdmin);
    }

    public boolean kick(PlayerKickEvent.Reason reason, String reasonString, boolean isAdmin) {
        return kick(reason, reasonString, isAdmin, "");
    }

    public boolean kick(PlayerKickEvent.Reason reason, String reasonString, boolean isAdmin, String extraData) {
        PlayerKickEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerKickEvent(this, reason, reasonString, this.getLeaveMessage(), extraData));
        if (!ev.isCancelled()) {
            String message;
            if (isAdmin) {
                if (!this.isBanned()) {
                    message = "Kicked!" + (!reasonString.isEmpty() ? " Reason: " + reasonString : "");
                } else {
                    message = reasonString;
                }
            } else {
                if (reasonString.isEmpty()) {
                    message = "disconnectionScreen.noReason";
                } else {
                    message = reasonString;
                }
            }

            this.close(ev.getQuitMessage(), message);

            return true;
        }

        return false;
    }

    public void setViewDistance(int distance) {
        this.chunkRadius = distance;

        ChunkRadiusUpdatedPacket pk = new ChunkRadiusUpdatedPacket();
        pk.radius = distance;

        this.dataPacket(pk);
    }

    public int getViewDistance() {
        return this.chunkRadius;
    }

    @Override
    public void sendMessage(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_RAW;
        pk.message = this.server.getLanguage().translateString(message);
        this.dataPacket(pk);
    }

    @Override
    public void sendMessage(TextContainer message) {
        if (message instanceof TranslationContainer) {
            this.sendTranslation(message.getText(), ((TranslationContainer) message).getParameters());
            return;
        }
        this.sendMessage(message.getText());
    }

    public void sendTranslation(String message) {
        this.sendTranslation(message, new String[0]);
    }

    public void sendTranslation(String message, String[] parameters) {
        TextPacket pk = new TextPacket();
        if (!this.server.isLanguageForced()) {
            pk.type = TextPacket.TYPE_TRANSLATION;
            pk.message = this.server.getLanguage().translateString(message, parameters, "nukkit.");
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = this.server.getLanguage().translateString(parameters[i], parameters, "nukkit.");
            }
            pk.parameters = parameters;
        } else {
            pk.type = TextPacket.TYPE_RAW;
            pk.message = this.server.getLanguage().translateString(message, parameters);
        }
        this.dataPacket(pk);
    }

    public void sendChat(String message) {
        this.sendChat("", message);
    }

    public void sendChat(String source, String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_CHAT;
        pk.source = source;
        pk.message = this.server.getLanguage().translateString(message);
        this.dataPacket(pk);
    }

    public void sendPopup(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_POPUP;
        pk.message = message;
        this.dataPacket(pk);
    }

    public void sendPopup(String message, String subtitle) {
        this.sendPopup(message);
    }

    public void sendTip(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_TIP;
        pk.message = message;
        this.dataPacket(pk);
    }

    public void clearTitle() {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_CLEAR;
        this.dataPacket(pk);
    }

    /**
     * Resets both title animation times and subtitle for the next shown title
     */
    public void resetTitleSettings() {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_RESET;
        this.dataPacket(pk);
    }

    public void setSubtitle(String subtitle) {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_SUBTITLE;
        pk.text = Strings.isNullOrEmpty(subtitle) ? " " : subtitle;
        this.dataPacket(pk);
    }

    public void setTitleAnimationTimes(int fadein, int duration, int fadeout) {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_ANIMATION_TIMES;
        pk.fadeInTime = fadein;
        pk.stayTime = duration;
        pk.fadeOutTime = fadeout;
        this.dataPacket(pk);
    }

    private void setTitle(String text) {
        SetTitlePacket packet = new SetTitlePacket();
        packet.text = text;
        packet.type = SetTitlePacket.TYPE_TITLE;
        this.dataPacket(packet);
    }

    public void sendTitle(String title) {
        this.sendTitle(title, null, 20, 20, 5);
    }

    public void sendTitle(String title, String subtitle) {
        this.sendTitle(title, subtitle, 20, 20, 5);
    }

    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        this.setTitleAnimationTimes(fadeIn, stay, fadeOut);
        if (!Strings.isNullOrEmpty(subtitle)) {
            this.setSubtitle(subtitle);
        }
        // Title won't send if an empty string is used
        this.setTitle(Strings.isNullOrEmpty(title) ? " " : title);
    }

    public void sendActionBar(String title) {
        this.sendActionBar(title, 1, 0, 1);
    }

    public void sendActionBar(String title, int fadein, int duration, int fadeout) {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_ACTION_BAR;
        pk.text = Strings.isNullOrEmpty(title) ? " " : title;
        pk.fadeInTime = fadein;
        pk.stayTime = duration;
        pk.fadeOutTime = fadeout;
        this.dataPacket(pk);
    }

    /**
     * 设置指定itemCategory物品的冷却显示效果，注意该方法仅为客户端显示效果，冷却逻辑实现仍需自己实现
     * <p>
     * Set the cooling display effect of the specified itemCategory items, note that this method is only for client-side display effect, cooling logic implementation still needs to be implemented by itself
     *
     * @param coolDown     the cool down
     * @param itemCategory the item category
     */
    public void setItemCoolDown(int coolDown, String itemCategory) {
        if (this.protocol < ProtocolInfo.v1_18_10) {
            return;
        }
        PlayerStartItemCoolDownPacket pk = new PlayerStartItemCoolDownPacket();
        pk.setCoolDownDuration(coolDown);
        pk.setItemCategory(itemCategory);
        this.dataPacket(pk);
    }

    /**
     * 发送一个弹出式消息框给玩家
     * <p>
     * Send a Toast message box to the player
     *
     * @param title   the title
     * @param content the content
     */
    public void sendToast(String title, String content) {
        title = Strings.isNullOrEmpty(title) ? " " : title;
        content = Strings.isNullOrEmpty(content) ? " " : content;
        if (this.protocol < ProtocolInfo.v1_19_0) {
            this.sendTitle(title, content);
            return;
        }
        ToastRequestPacket pk = new ToastRequestPacket();
        pk.title = title;
        pk.content = content;
        this.dataPacket(pk);
    }

    @Override
    public void spawnToAll() {
        // 在1.20.0中同一tick连续执行despawnFromAll和spawnToAll会导致玩家移动不可见
        if (this.server.getTick() == this.lastDespawnFromAllTick) {
            this.needSpawnToAll = true;
            return;
        }
        super.spawnToAll();
    }

    @Override
    public void despawnFromAll() {
        super.despawnFromAll();
        this.lastDespawnFromAllTick = this.server.getTick();
    }

    @Override
    public void close() {
        this.close("");
    }

    public void close(String message) {
        this.close(message, "generic");
    }

    public void close(String message, String reason) {
        this.close(message, reason, true);
    }

    public void close(String message, String reason, boolean notify) {
        this.close(new TextContainer(message), reason, notify);
    }

    public void close(TextContainer message) {
        this.close(message, "generic");
    }

    public void close(TextContainer message, String reason) {
        this.close(message, reason, true);
    }

    public void close(TextContainer message, String reason, boolean notify) {
        if (this.connected && !this.closed) {
            if (notify && !reason.isEmpty()) {
                DisconnectPacket pk = new DisconnectPacket();
                pk.message = reason;
                this.forceDataPacket(pk, null);
            }

            this.connected = false;
            PlayerQuitEvent ev = null;
            if (this.username != null && !this.username.isEmpty()) {
                this.server.getPluginManager().callEvent(ev = new PlayerQuitEvent(this, message, true, reason));
                if (this.loggedIn && ev.getAutoSave()) {
                    this.save();
                }
                if (this.fishing != null) {
                    this.stopFishing(false);
                }
            }

            for (Player player : new ArrayList<>(this.server.playerList.values())) {
                if (!player.canSee(this)) {
                    player.showPlayer(this);
                }
            }

            this.hiddenPlayers.clear();

            this.removeAllWindows(true);

            this.unloadChunks(false);

            super.close();

            this.interfaz.close(this, notify ? reason : "");

            if (this.loggedIn) {
                this.server.removeOnlinePlayer(this);
                this.loggedIn = false;
            }

            if (ev != null && !Objects.equals(this.username, "") && this.spawned && !Objects.equals(ev.getQuitMessage().toString(), "")) {
                this.server.broadcastMessage(ev.getQuitMessage());
            }

            this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
            this.spawned = false;
            this.server.getLogger().info(this.getServer().getLanguage().translateString("nukkit.player.logOut",
                    TextFormat.AQUA + (this.username == null ? "" : this.username) + TextFormat.WHITE,
                    this.getAddress(),
                    String.valueOf(this.getPort()),
                    this.getServer().getLanguage().translateString(reason)));
            this.windows.clear();
            this.hasSpawned.clear();
            this.spawnPosition = null;

            if (this.riding instanceof EntityRideable) {
                this.riding.passengers.remove(this);
            }

            this.riding = null;
        }

        if (this.perm != null) {
            this.perm.clearPermissions();
            this.perm = null;
        }

        this.inventory = null;
        this.chunk = null;

        this.server.removePlayer(this);

        if (this.loggedIn) {
            this.server.getLogger().warning("(BUG) Player still logged in");
            this.interfaz.close(this, notify ? reason : "");
            this.server.removeOnlinePlayer(this);
            this.loggedIn = false;
        }
        this.clientMovements.clear();
    }

    /**
     * Save player data to disk
     */
    public void save() {
        this.save(false);
    }

    /**
     * Save player data to disk
     *
     * @param async save asynchronously
     */
    public void save(boolean async) {
        if (this.closed) {
            throw new IllegalStateException("Tried to save closed player");
        }

        super.saveNBT();

        if (this.level != null) {
            this.namedTag.putString("Level", this.level.getFolderName());
            if (this.spawnPosition != null && this.spawnPosition.getLevel() != null) {
                this.namedTag.putString("SpawnLevel", this.spawnPosition.getLevel().getFolderName());
                this.namedTag.putInt("SpawnX", (int) this.spawnPosition.x);
                this.namedTag.putInt("SpawnY", (int) this.spawnPosition.y);
                this.namedTag.putInt("SpawnZ", (int) this.spawnPosition.z);
            }

            if (spawnBlockPosition == null) {
                namedTag.remove("SpawnBlockPositionX").remove("SpawnBlockPositionY").remove("SpawnBlockPositionZ").remove("SpawnBlockLevel");
            } else if (spawnBlockPosition.isValid()) {
                namedTag.putInt("SpawnBlockPositionX", spawnBlockPosition.getFloorX())
                        .putInt("SpawnBlockPositionY", spawnBlockPosition.getFloorY())
                        .putInt("SpawnBlockPositionZ", spawnBlockPosition.getFloorZ())
                        .putString("SpawnBlockLevel", this.spawnBlockPosition.getLevel().getFolderName());
            }

            CompoundTag achievements = new CompoundTag();
            for (String achievement : this.achievements) {
                achievements.putByte(achievement, 1);
            }

            this.namedTag.putCompound("Achievements", achievements);

            this.namedTag.putInt("playerGameType", this.gamemode);
            this.namedTag.putLong("lastPlayed", System.currentTimeMillis() / 1000);

            this.namedTag.putString("lastIP", this.getAddress());

            this.namedTag.putInt("EXP", this.exp);
            this.namedTag.putInt("expLevel", this.expLevel);

            this.namedTag.putInt("foodLevel", this.foodData.getLevel());
            this.namedTag.putFloat("foodSaturationLevel", this.foodData.getFoodSaturationLevel());

            this.namedTag.putInt("TimeSinceRest", this.timeSinceRest);

            ListTag<StringTag> fogIdentifiers = new ListTag<>("fogIdentifiers");
            ListTag<StringTag> userProvidedFogIds = new ListTag<>("userProvidedFogIds");
            this.fogStack.forEach(fog -> {
                fogIdentifiers.add(new StringTag("", fog.identifier().toString()));
                userProvidedFogIds.add(new StringTag("", fog.userProvidedId()));
            });
            this.namedTag.putList(fogIdentifiers);
            this.namedTag.putList(userProvidedFogIds);

            if (!this.username.isEmpty() && this.namedTag != null) {
                if (this.server.savePlayerDataByUuid) {
                    this.server.saveOfflinePlayerData(this.uuid, this.namedTag, async);
                } else {
                    this.server.saveOfflinePlayerData(this.username, this.namedTag, async);
                }
            }
        }
    }

    /**
     * Get player's username
     *
     * @return username
     */
    public String getName() {
        return this.username;
    }

    public LangCode getLanguageCode() {
        return LangCode.valueOf(this.getLoginChainData().getLanguageCode());
    }

    @Override
    public void kill() {
        if (!this.spawned) {
            return;
        }

        boolean showMessages = this.level.getGameRules().getBoolean(GameRule.SHOW_DEATH_MESSAGES);
        String message = "";
        List<String> params = new ArrayList<>();
        EntityDamageEvent cause = this.getLastDamageCause();

        if (showMessages) {
            params.add(this.displayName);

            switch (cause == null ? DamageCause.CUSTOM : cause.getCause()) {
                case ENTITY_ATTACK:
                case THORNS:
                    if (cause instanceof EntityDamageByEntityEvent) {
                        Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                        killer = e;
                        if (e instanceof Player) {
                            message = "death.attack.player";
                            params.add(((Player) e).displayName);
                            break;
                        } else if (e instanceof EntityLiving) {
                            message = "death.attack.mob";
                            params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                            break;
                        } else {
                            params.add("Unknown");
                        }
                    }
                    break;
                case PROJECTILE:
                    if (cause instanceof EntityDamageByEntityEvent) {
                        Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                        killer = e;
                        if (e instanceof Player) {
                            message = "death.attack.arrow";
                            params.add(((Player) e).displayName);
                        } else if (e instanceof EntityLiving) {
                            message = "death.attack.arrow";
                            params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                            break;
                        } else {
                            params.add("Unknown");
                        }
                    }
                    break;
                case VOID:
                    message = "death.attack.outOfWorld";
                    break;
                case FALL:
                    if (cause.getFinalDamage() > 2) {
                        message = "death.fell.accident.generic";
                        break;
                    }
                    message = "death.attack.fall";
                    break;

                case SUFFOCATION:
                    message = "death.attack.inWall";
                    break;

                case LAVA:
                    message = "death.attack.lava";
                    break;

                case MAGMA:
                    message = "death.attack.magma";
                    break;

                case FIRE:
                    message = "death.attack.onFire";
                    break;

                case FIRE_TICK:
                    message = "death.attack.inFire";
                    break;

                case DROWNING:
                    message = "death.attack.drown";
                    break;

                case CONTACT:
                    if (cause instanceof EntityDamageByBlockEvent) {
                        int id = ((EntityDamageByBlockEvent) cause).getDamager().getId();
                        if (id == Block.CACTUS) {
                            message = "death.attack.cactus";
                        } else if (id == Block.ANVIL) {
                            message = "death.attack.anvil";
                        }
                    }
                    break;

                case BLOCK_EXPLOSION:
                case ENTITY_EXPLOSION:
                    if (cause instanceof EntityDamageByEntityEvent) {
                        Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                        killer = e;
                        if (e instanceof Player) {
                            message = "death.attack.explosion.player";
                            params.add(((Player) e).displayName);
                        } else if (e instanceof EntityLiving) {
                            message = "death.attack.explosion.player";
                            params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                            break;
                        } else {
                            message = "death.attack.explosion";
                        }
                    } else {
                        message = "death.attack.explosion";
                    }
                    break;
                case MAGIC:
                    message = "death.attack.magic";
                    break;
                case LIGHTNING:
                    message = "death.attack.lightningBolt";
                    break;
                case HUNGER:
                    message = "death.attack.starve";
                    break;
                default:
                    message = "death.attack.generic";
                    break;
            }
        }

        PlayerDeathEvent ev = new PlayerDeathEvent(this, this.getDrops(), new TranslationContainer(message, params.toArray(new String[0])), this.expLevel);
        ev.setKeepInventory(this.level.gameRules.getBoolean(GameRule.KEEP_INVENTORY));
        ev.setKeepExperience(ev.getKeepInventory()); // Same as above
        this.server.getPluginManager().callEvent(ev);

        if (!ev.isCancelled()) {
            if (this.fishing != null) {
                this.stopFishing(false);
            }

            this.extinguish();
            this.removeAllEffects();
            this.health = 0;
            this.scheduleUpdate();
            this.timeSinceRest = 0;

            if (this.getKiller() != null && this.getKiller() instanceof EntityWalkingMob && ((EntityWalkingMob) this.getKiller()).isAngryTo == this.getId()) {
                ((EntityWalkingMob) this.getKiller()).isAngryTo = -1; // Reset golem target
                if (this.getKiller() instanceof EntityWolf) {
                    ((EntityWolf) this.getKiller()).setAngry(false);
                }
            }

            if (!ev.getKeepInventory() && this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
                for (Item item : ev.getDrops()) {
                    if (!item.hasEnchantment(Enchantment.ID_VANISHING_CURSE)) {
                        this.level.dropItem(this, item, null, true, 40);
                    }
                }

                if (this.inventory != null) {
                    this.inventory.clearAll();
                }

                // Offhand inventory is already cleared in inventory.clearAll()

                if (this.playerUIInventory != null) {
                    this.playerUIInventory.clearAll();
                }
            } else {
                // 发包给客户端清除不死图腾，防止影响自杀等操作
                if (this.getOffhandInventory().getItemFast(0) instanceof ItemTotem) {
                    InventorySlotPacket pk = new InventorySlotPacket();
                    pk.slot = 0;
                    pk.item = Item.AIR_ITEM;
                    int id = this.getWindowId(this.getOffhandInventory());
                    if (id != -1) {
                        pk.inventoryId = id;
                        this.dataPacket(pk);
                    }
                }
                int id = this.getWindowId(this.getInventory());
                if (id != -1) {
                    for (Map.Entry<Integer, Item> entry : this.getInventory().getContents().entrySet()) {
                        if (entry.getValue() instanceof ItemTotem) {
                            InventorySlotPacket pk = new InventorySlotPacket();
                            pk.slot = entry.getKey();
                            pk.item = Item.AIR_ITEM;
                            pk.inventoryId = id;
                            this.dataPacket(pk);
                        }
                    }
                }
            }

            if (!ev.getKeepExperience() && this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
                if (this.isSurvival() || this.isAdventure()) {
                    int exp = ev.getExperience() * 7;
                    if (exp > 100) exp = 100;
                    this.getLevel().dropExpOrb(this, exp);
                }
                this.setExperience(0, 0);
            }

            if (showMessages && !ev.getDeathMessage().toString().isEmpty()) {
                this.server.broadcast(ev.getDeathMessage(), Server.BROADCAST_CHANNEL_USERS);

                if (this.protocol >= ProtocolInfo.v1_19_10) {
                    DeathInfoPacket pk = new DeathInfoPacket();
                    if (ev.getDeathMessage() instanceof TranslationContainer) {
                        pk.messageTranslationKey = this.server.getLanguage().translateString(ev.getDeathMessage().getText(), ((TranslationContainer) ev.getDeathMessage()).getParameters(), null);
                    } else {
                        pk.messageTranslationKey = ev.getDeathMessage().getText();
                    }
                    this.dataPacket(pk);
                }
            }

            RespawnPacket pk = new RespawnPacket();
            Position pos = this.getSpawn();
            pk.x = (float) pos.x;
            pk.y = (float) pos.y;
            pk.z = (float) pos.z;
            pk.respawnState = RespawnPacket.STATE_SEARCHING_FOR_SPAWN;
            this.dataPacket(pk);

            if (level.getGameRules().getBoolean(GameRule.DO_IMMEDIATE_RESPAWN)) {
                SetHealthPacket healthPk = new SetHealthPacket();
                healthPk.health = this.getMaxHealth();
                this.dataPacket(healthPk);
            }
        }
    }

    protected void respawn() {
        if (this.server.isHardcore()) {
            this.setBanned(true);
            return;
        }

        this.craftingType = CRAFTING_SMALL;
        this.resetCraftingGridType();

        this.checkSpawnBlockPosition();
        if (this.spawnBlockPosition != null && this.spawnBlockPosition.isValid()) {
            Block spawnBlock = this.spawnBlockPosition.getLevelBlock();
            if (spawnBlock.getId() == BlockID.RESPAWN_ANCHOR) {
                BlockRespawnAnchor respawnAnchor = (BlockRespawnAnchor) spawnBlock;
                respawnAnchor.setCharge(respawnAnchor.getCharge() - 1);
                respawnAnchor.getLevel().setBlock(respawnAnchor, spawnBlock);
                respawnAnchor.getLevel().scheduleUpdate(respawnAnchor, 10);
                respawnAnchor.getLevel().addSound(this, Sound.RESPAWN_ANCHOR_DEPLETE, 1, 1, this);
            }
        }
        PlayerRespawnEvent playerRespawnEvent = new PlayerRespawnEvent(this, this.getSpawn());
        this.server.getPluginManager().callEvent(playerRespawnEvent);

        Position respawnPos = playerRespawnEvent.getRespawnPosition();

        this.teleport(respawnPos, null);

        if (this.protocol < 388) {
            RespawnPacket respawnPacket = new RespawnPacket();
            respawnPacket.x = (float) respawnPos.x;
            respawnPacket.y = (float) respawnPos.y;
            respawnPacket.z = (float) respawnPos.z;
            this.dataPacket(respawnPacket);
        }

        this.sendExperience();
        this.sendExperienceLevel();

        this.setSprinting(false);
        this.setSneaking(false);

        this.extinguish();
        this.setDataProperty(new ShortEntityData(Player.DATA_AIR, 400), false);
        this.deadTicks = 0;
        this.noDamageTicks = 60;

        this.removeAllEffects();
        this.setHealth(this.getMaxHealth());
        this.foodData.setLevel(20, 20);

        this.sendData(this);

        this.setMovementSpeed(DEFAULT_SPEED);

        this.adventureSettings.update();
        this.inventory.sendContents(this);
        this.inventory.sendArmorContents(this);
        this.offhandInventory.sendContents(this);

        this.spawnToAll();
        this.scheduleUpdate();
    }

    @Override
    public void setHealth(float health) {
        if (health < 1) {
            health = 0;
        }

        super.setHealth(health);

        // HACK: solve the client-side absorption bug
        if (this.spawned) {
            UpdateAttributesPacket pk = new UpdateAttributesPacket();
            pk.entries = new Attribute[]{Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getAbsorption() % 2 != 0 ? this.getMaxHealth() + 1 : this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0)};
            pk.entityId = this.id;
            this.dataPacket(pk);
        }
    }

    @Override
    public void setMaxHealth(int maxHealth) {
        super.setMaxHealth(maxHealth);

        if (this.spawned) {
            UpdateAttributesPacket pk = new UpdateAttributesPacket();
            pk.entries = new Attribute[]{Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getAbsorption() % 2 != 0 ? this.getMaxHealth() + 1 : this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0)};
            pk.entityId = this.id;
            this.dataPacket(pk);
        }
    }

    public int getExperience() {
        return this.exp;
    }

    public int getExperienceLevel() {
        return this.expLevel;
    }

    public void addExperience(int add) {
        if (add == 0) return;
        int added = this.exp + add;
        int level = this.expLevel;
        int most = calculateRequireExperience(level);
        while (added >= most) {
            added -= most;
            level++;
            most = calculateRequireExperience(level);
        }
        this.setExperience(added, level);
    }

    public static int calculateRequireExperience(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        } else if (level >= 15) {
            return 37 + (level - 15) * 5;
        } else {
            return 7 + (level << 1);
        }
    }

    public void setExperience(int exp) {
        setExperience(exp, this.expLevel);
    }

    public void setExperience(int exp, int level) {
        PlayerExperienceChangeEvent ev = new PlayerExperienceChangeEvent(this, this.exp, this.expLevel, exp, level);
        this.server.getPluginManager().callEvent(ev);

        if (ev.isCancelled()) {
            return;
        }

        this.exp = ev.getNewExperience();
        this.expLevel = ev.getNewExperienceLevel();

        this.sendExperienceLevel(this.expLevel);
        this.sendExperience(this.exp);
    }

    public void sendExperience() {
        sendExperience(this.exp);
    }

    public void sendExperience(int exp) {
        if (this.spawned) {
            this.setAttribute(Attribute.getAttribute(Attribute.EXPERIENCE).setValue(Math.max(0f, Math.min(1f, ((float) exp) / calculateRequireExperience(this.expLevel)))));
        }
    }

    public void sendExperienceLevel() {
        sendExperienceLevel(this.expLevel);
    }

    public void sendExperienceLevel(int level) {
        if (this.spawned) {
            this.setAttribute(Attribute.getAttribute(Attribute.EXPERIENCE_LEVEL).setValue(level));
        }
    }

    public void setAttribute(Attribute attribute) {
        UpdateAttributesPacket pk = new UpdateAttributesPacket();
        pk.entries = new Attribute[]{attribute};
        pk.entityId = this.id;
        this.dataPacket(pk);
    }

    @Override
    public void setMovementSpeed(float speed) {
        setMovementSpeed(speed, true);
    }

    public void setMovementSpeed(float speed, boolean send) {
        super.setMovementSpeed(speed);
        if (this.spawned && send) {
            this.setAttribute(Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(speed).setDefaultValue(speed));
        }
    }

    public void sendMovementSpeed(float speed) {
        Attribute attribute = Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(speed);
        this.setAttribute(attribute);
    }

    public Entity getKiller() {
        return killer;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (!this.isAlive()) {
            return false;
        }

        if (this.isSpectator() || (this.isCreative() && source.getCause() != DamageCause.SUICIDE)) {
            source.setCancelled();
            return false;
        } else if (source.getCause() == DamageCause.FALL && this.getAllowFlight()) {
            source.setCancelled();
            return false;
        } else if (source.getCause() == DamageCause.FALL) {
            Position pos = this.getPosition().floor().add(0.5, -1, 0.5);
            int block = this.getLevel().getBlockIdAt(chunk, (int) pos.x, (int) pos.y, (int) pos.z);
            if (block == Block.SLIME_BLOCK || block == Block.COBWEB) {
                if (!this.isSneaking()) {
                    source.setCancelled();
                    this.resetFallDistance();
                    return false;
                }
            }
        }

        if (super.attack(source)) {
            if (this.getLastDamageCause() == source && this.spawned) {
                if (source instanceof EntityDamageByEntityEvent) {
                    Entity damager = ((EntityDamageByEntityEvent) source).getDamager();
                    if (damager instanceof Player) {
                        ((Player) damager).foodData.updateFoodExpLevel(0.1);
                    }
                }
                EntityEventPacket pk = new EntityEventPacket();
                pk.eid = this.id;
                pk.event = EntityEventPacket.HURT_ANIMATION;
                this.dataPacket(pk);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Drops an item on the ground in front of the player. Returns if the item drop was successful.
     *
     * @param item to drop
     * @return bool if the item was dropped or if the item was null
     */
    public boolean dropItem(Item item) {
        if (!this.spawned || !this.isAlive()) {
            return false;
        }

        if (item.isNull()) {
            this.server.getLogger().debug(this.username + " attempted to drop a null item (" + item + ')');
            return true;
        }

        Vector3 motion = this.getDirectionVector().multiply(0.4);

        this.level.dropItem(this.add(0, 1.3, 0), item, motion, 40);

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false);
        return true;
    }

    /**
     * Drops an item on the ground in front of the player. Returns the dropped item.
     *
     * @param item to drop
     * @return EntityItem if the item was dropped or null if the item was null
     */
    public EntityItem dropAndGetItem(Item item) {
        if (!this.spawned || !this.isAlive()) {
            return null;
        }

        if (item.isNull()) {
            this.server.getLogger().debug(this.getName() + " attempted to drop a null item (" + item + ')');
            return null;
        }

        Vector3 motion = this.getDirectionVector().multiply(0.4);

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false);

        return this.level.dropAndGetItem(this.add(0, 1.3, 0), item, motion, 40);
    }

    public void sendPosition(Vector3 pos) {
        this.sendPosition(pos, this.yaw);
    }

    public void sendPosition(Vector3 pos, double yaw) {
        this.sendPosition(pos, yaw, this.pitch);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch) {
        this.sendPosition(pos, yaw, pitch, MovePlayerPacket.MODE_NORMAL);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch, int mode) {
        this.sendPosition(pos, yaw, pitch, mode, null);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch, int mode, Player[] targets) {
        MovePlayerPacket pk = new MovePlayerPacket();
        pk.eid = this.getId();
        pk.x = (float) pos.x;
        pk.y = (float) (pos.y + this.getBaseOffset());
        pk.z = (float) pos.z;
        pk.headYaw = (float) yaw;
        pk.pitch = (float) pitch;
        pk.yaw = (float) yaw;
        pk.mode = mode;
        pk.onGround = this.onGround;

        if (this.riding != null) {
            pk.ridingEid = this.riding.getId();
            pk.mode = MovePlayerPacket.MODE_PITCH;
        }

        this.ySize = 0;

        if (targets != null) {
            Server.broadcastPacket(targets, pk);
        } else {
            this.dataPacket(pk);
        }
    }

    public void sendPosition(double x, double y, double z, double yaw, double pitch, int mode, Collection<Player> targets) {
        MovePlayerPacket pk = new MovePlayerPacket();
        pk.eid = this.getId();
        pk.x = (float) x;
        pk.y = (float) y + this.getBaseOffset();
        pk.z = (float) z;
        pk.headYaw = (float) yaw;
        pk.pitch = (float) pitch;
        pk.yaw = (float) yaw;
        pk.mode = mode;
        pk.onGround = this.onGround;

        if (this.riding != null) {
            pk.ridingEid = this.riding.getId();
            pk.mode = MovePlayerPacket.MODE_PITCH;
        }

        this.ySize = 0;

        if (targets != null) {
            Server.broadcastPacket(targets, pk);
        } else {
            this.dataPacket(pk);
        }
    }

    @Override
    protected void checkChunks() {
        if (this.chunk == null || (this.chunk.getX() != ((int) this.x >> 4) || this.chunk.getZ() != ((int) this.z >> 4))) {
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }
            this.chunk = this.level.getChunk((int) this.x >> 4, (int) this.z >> 4, true);

            if (!this.justCreated) {
                Map<Integer, Player> newChunk = this.level.getChunkPlayers((int) this.x >> 4, (int) this.z >> 4);
                newChunk.remove(this.loaderId);

                for (Player player : new ArrayList<>(this.hasSpawned.values())) {
                    if (!newChunk.containsKey(player.loaderId)) {
                        this.despawnFrom(player);
                    } else {
                        newChunk.remove(player.loaderId);
                    }
                }

                for (Player player : newChunk.values()) {
                    this.spawnTo(player);
                }
            }

            if (this.chunk == null) {
                return;
            }

            this.chunk.addEntity(this);
        }
    }

    protected boolean checkTeleportPosition() {
        return checkTeleportPosition(false);
    }

    protected boolean checkTeleportPosition(boolean enderPearl) {
        if (this.teleportPosition != null) {
            int chunkX = (int) this.teleportPosition.x >> 4;
            int chunkZ = (int) this.teleportPosition.z >> 4;

            for (int X = -1; X <= 1; ++X) {
                for (int Z = -1; Z <= 1; ++Z) {
                    long index = Level.chunkHash(chunkX + X, chunkZ + Z);
                    if (!this.usedChunks.containsKey(index) || !this.usedChunks.get(index)) {
                        return false;
                    }
                }
            }

            this.spawnToAll();
            if (!enderPearl) {
                this.forceMovement = this.teleportPosition;
            }
            this.teleportPosition = null;
            return true;
        }

        return false;
    }

    protected void sendPlayStatus(int status) {
        sendPlayStatus(status, false);
    }

    protected void sendPlayStatus(int status, boolean immediate) {
        PlayStatusPacket pk = new PlayStatusPacket();
        pk.status = status;

        if (immediate) {
            this.forceDataPacket(pk, null);
        } else {
            this.dataPacket(pk);
        }
    }

    @Override
    public boolean teleport(Location location, TeleportCause cause) {
        if (!this.isOnline()) {
            return false;
        }

        Location from = this.getLocation();
        Location to = location;

        if (cause != null) {
            PlayerTeleportEvent event = new PlayerTeleportEvent(this, from, to, cause);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            to = event.getTo();
        }

        // HACK: solve the client-side teleporting bug (inside into the block)
        if (super.teleport(to.getY() == to.getFloorY() ? to.add(0, 0.00001, 0) : to, null)) { // null to prevent fire of duplicate EntityTeleportEvent
            //this.removeAllWindows();
            //this.formOpen = false;

            this.teleportPosition = this;
            if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                this.forceMovement = this.teleportPosition;
            }

            if (this.dimensionChangeInProgress) {
                this.dimensionChangeInProgress = false;
            } else {
                this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_TELEPORT);
                this.checkTeleportPosition(cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
                this.dummyBossBars.values().forEach(DummyBossBar::reshow);
            }

            this.resetFallDistance();
            this.nextChunkOrderRun = 0;
            this.resetClientMovement();

            this.stopFishing(false);
            return true;
        }

        return false;
    }

    protected void forceSendEmptyChunks() {
        int chunkPositionX = this.getFloorX() >> 4;
        int chunkPositionZ = this.getFloorZ() >> 4;
        for (int x = -chunkRadius; x < chunkRadius; x++) {
            for (int z = -chunkRadius; z < chunkRadius; z++) {
                LevelChunkPacket chunk = new LevelChunkPacket();
                chunk.chunkX = chunkPositionX + x;
                chunk.chunkZ = chunkPositionZ + z;
                chunk.dimension = this.level.getDimension();
                chunk.data = new byte[0];
                this.dataPacket(chunk);
            }
        }
    }

    public void teleportImmediate(Location location) {
        this.teleportImmediate(location, TeleportCause.PLUGIN);
    }

    public void teleportImmediate(Location location, TeleportCause cause) {
        Location from = this.getLocation();
        if (super.teleport(location.add(0, 0.00001, 0), cause)) {
            this.removeAllWindows();

            if (from.getLevel().getId() != location.getLevel().getId()) { // Different level, update compass position
                SetSpawnPositionPacket pk = new SetSpawnPositionPacket();
                pk.spawnType = SetSpawnPositionPacket.TYPE_WORLD_SPAWN;
                Position spawn = location.getLevel().getSpawnLocation();
                pk.x = spawn.getFloorX();
                pk.y = spawn.getFloorY();
                pk.z = spawn.getFloorZ();
                pk.dimension = location.getLevel().getDimension();
                this.dataPacket(pk);
            }

            this.forceMovement = this;
            this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_RESET);

            this.resetFallDistance();
            this.orderChunks();
            this.nextChunkOrderRun = 0;
            this.resetClientMovement();

            //DummyBossBar
            this.getDummyBossBars().values().forEach(DummyBossBar::reshow);
            // Weather
            this.getLevel().sendWeather(this);
            // Update time
            this.getLevel().sendTime(this);
        }
    }

    /**
     * Shows a new FormWindow to the player
     * You can find out FormWindow result by listening to PlayerFormRespondedEvent
     *
     * @param window to show
     * @return form id to use in {@link PlayerFormRespondedEvent}
     */
    public int showFormWindow(FormWindow window) {
        return showFormWindow(window, this.formWindowCount++);
    }

    /**
     * Shows a new FormWindow to the player
     * You can find out FormWindow result by listening to PlayerFormRespondedEvent
     *
     * @param window to show
     * @param id     form id
     * @return form id to use in {@link PlayerFormRespondedEvent}
     */
    public int showFormWindow(FormWindow window, int id) {
        if (formOpen) return 0;
        ModalFormRequestPacket packet = new ModalFormRequestPacket();
        packet.formId = id;
        packet.data = window.getJSONData();
        this.formWindows.put(packet.formId, window);
        this.dataPacket(packet);
        this.formOpen = true;
        return id;
    }

    public void showDialogWindow(FormWindowDialog dialog) {
        showDialogWindow(dialog, true);
    }

    /**
     * 向玩家展示一个NPC对话框.
     * <p>
     * Show dialog window to the player.
     *
     * @param dialog NPC对话框<br>the dialog
     * @param book   如果为true,将会立即更新该{@link FormWindowDialog#getSceneName()}<br>If true, the {@link FormWindowDialog#getSceneName()} will be updated immediately.
     */
    public void showDialogWindow(FormWindowDialog dialog, boolean book) {
        String actionJson = dialog.getButtonJSONData();

        if (book && dialogWindows.getIfPresent(dialog.getSceneName()) != null) dialog.updateSceneName();
        dialog.getBindEntity().setDataProperty(new ByteEntityData(Entity.DATA_HAS_NPC_COMPONENT, 1));
        dialog.getBindEntity().setDataProperty(new StringEntityData(Entity.DATA_NPC_SKIN_DATA, dialog.getSkinData()));
        dialog.getBindEntity().setDataProperty(new StringEntityData(Entity.DATA_NPC_ACTIONS, actionJson));
        dialog.getBindEntity().setDataProperty(new StringEntityData(Entity.DATA_INTERACTIVE_TAG, dialog.getContent()));

        NPCDialoguePacket packet = new NPCDialoguePacket();
        packet.setUniqueEntityId(dialog.getEntityId());
        packet.setAction(NPCDialoguePacket.Action.OPEN);
        packet.setDialogue(dialog.getContent());
        packet.setNpcName(dialog.getTitle());
        if (book) packet.setSceneName(dialog.getSceneName());
        packet.setActionJson(dialog.getButtonJSONData());
        if (book) this.dialogWindows.put(dialog.getSceneName(), dialog);
        this.dataPacket(packet);
    }

    /**
     * Shows a new setting page in game settings
     * You can find out settings result by listening to PlayerFormRespondedEvent
     *
     * @param window to show on settings page
     * @return form id to use in {@link PlayerFormRespondedEvent}
     */
    public int addServerSettings(FormWindow window) {
        int id = this.formWindowCount++;

        this.serverSettings.put(id, window);
        return id;
    }

    /**
     * Creates and sends a BossBar to the player
     *
     * @param text   The BossBar message
     * @param length The BossBar percentage
     * @return bossBarId  The BossBar ID, you should store it if you want to remove or update the BossBar later
     */
    public long createBossBar(String text, int length) {
        return this.createBossBar(new DummyBossBar.Builder(this).text(text).length(length).build());
    }

    /**
     * Creates and sends a BossBar to the player
     *
     * @param dummyBossBar DummyBossBar Object (Instantiate it by the Class Builder)
     * @return bossBarId  The BossBar ID, you should store it if you want to remove or update the BossBar later
     * @see DummyBossBar.Builder
     */
    public long createBossBar(DummyBossBar dummyBossBar) {
        this.dummyBossBars.put(dummyBossBar.getBossBarId(), dummyBossBar);
        dummyBossBar.create();
        return dummyBossBar.getBossBarId();
    }

    /**
     * Get a DummyBossBar object
     *
     * @param bossBarId The BossBar ID
     * @return DummyBossBar object
     * @see DummyBossBar#setText(String) Set BossBar text
     * @see DummyBossBar#setLength(float) Set BossBar length
     * @see DummyBossBar#setColor(BossBarColor) Set BossBar color
     */
    public DummyBossBar getDummyBossBar(long bossBarId) {
        return this.dummyBossBars.getOrDefault(bossBarId, null);
    }

    /**
     * Get all DummyBossBar objects
     *
     * @return DummyBossBars Map
     */
    public Map<Long, DummyBossBar> getDummyBossBars() {
        return dummyBossBars;
    }

    /**
     * Updates a BossBar
     *
     * @param text      The new BossBar message
     * @param length    The new BossBar length
     * @param bossBarId The BossBar ID
     */
    public void updateBossBar(String text, int length, long bossBarId) {
        if (this.dummyBossBars.containsKey(bossBarId)) {
            DummyBossBar bossBar = this.dummyBossBars.get(bossBarId);
            bossBar.setText(text);
            bossBar.setLength(length);
        }
    }

    /**
     * Removes a BossBar
     *
     * @param bossBarId The BossBar ID
     */
    public void removeBossBar(long bossBarId) {
        if (this.dummyBossBars.containsKey(bossBarId)) {
            this.dummyBossBars.get(bossBarId).destroy();
            this.dummyBossBars.remove(bossBarId);
        }
    }

    public int getWindowId(Inventory inventory) {
        if (this.windows.containsKey(inventory)) {
            return this.windows.get(inventory);
        }

        return -1;
    }

    public Inventory getWindowById(int id) {
        return this.windowIndex.get(id);
    }

    public int addWindow(Inventory inventory) {
        return this.addWindow(inventory, null);
    }

    public int addWindow(Inventory inventory, Integer forceId) {
        return addWindow(inventory, forceId, false);
    }

    public int addWindow(Inventory inventory, Integer forceId, boolean isPermanent) {
        return addWindow(inventory, forceId, isPermanent, false);
    }

    public int addWindow(Inventory inventory, Integer forceId, boolean isPermanent, boolean alwaysOpen) {
        if (this.windows.containsKey(inventory)) {
            return this.windows.get(inventory);
        }
        int cnt;
        if (forceId == null) {
            this.windowCnt = cnt = Math.max(MINIMUM_OTHER_WINDOW_ID, ++this.windowCnt % 99);
        } else {
            cnt = forceId;
        }
        this.windows.forcePut(inventory, cnt);

        if (isPermanent) {
            this.permanentWindows.add(cnt);
        }

        if (this.spawned && !this.inventoryOpen && inventory.open(this)) {
            return cnt;
        } else if (!alwaysOpen) {
            this.removeWindow(inventory);

            return -1;
        } else {
            inventory.getViewers().add(this);
        }

        return cnt;
    }

    public Optional<Inventory> getTopWindow() {
        for (Entry<Inventory, Integer> entry : this.windows.entrySet()) {
            if (!this.permanentWindows.contains(entry.getValue())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public void removeWindow(Inventory inventory) {
        this.removeWindow(inventory, false);
    }

    protected void removeWindow(Inventory inventory, boolean isResponse) {
        inventory.close(this);
        // TODO: This needs a proper fix
        // Requiring isResponse here causes issues with inventory events and an item duplication glitch
        if (/*isResponse &&*/ !this.permanentWindows.contains(this.getWindowId(inventory))) {
            this.windows.remove(inventory);
        }
    }

    public void sendAllInventories() {
        for (Inventory inv : this.windows.keySet()) {
            inv.sendContents(this);

            if (inv instanceof PlayerInventory) {
                ((PlayerInventory) inv).sendArmorContents(this);
            }
        }
    }

    protected void addDefaultWindows() {
        this.addWindow(this.getInventory(), ContainerIds.INVENTORY, true, true);

        this.playerUIInventory = new PlayerUIInventory(this);
        this.addWindow(this.playerUIInventory, ContainerIds.UI, true);
        this.addWindow(this.offhandInventory, ContainerIds.OFFHAND, true, true);

        this.craftingGrid = this.playerUIInventory.getCraftingGrid();
        this.addWindow(this.craftingGrid, ContainerIds.NONE);
    }

    public PlayerUIInventory getUIInventory() {
        return playerUIInventory;
    }

    public PlayerCursorInventory getCursorInventory() {
        return this.playerUIInventory.getCursorInventory();
    }

    public CraftingGrid getCraftingGrid() {
        return this.craftingGrid;
    }

    public TradeInventory getTradeInventory() {
        for (Inventory inv : this.windows.keySet()) {
            if (inv instanceof TradeInventory) {
                return (TradeInventory) inv;
            }
        }
        return null;
    }

    public void setCraftingGrid(CraftingGrid grid) {
        this.craftingGrid = grid;
        this.addWindow(grid, ContainerIds.NONE);
    }

    public void resetCraftingGridType() {
        if (this.craftingGrid != null) {
            Item[] drops = this.inventory.addItem(this.craftingGrid.getContents().values().toArray(Item.EMPTY_ARRAY));

            if (drops.length > 0) {
                for (Item drop : drops) {
                    this.dropItem(drop);
                }
            }

            drops = this.inventory.addItem(this.getCursorInventory().getItem(0));
            if (drops.length > 0) {
                for (Item drop : drops) {
                    this.dropItem(drop);
                }
            }

            this.playerUIInventory.clearAll();

            if (this.craftingGrid instanceof BigCraftingGrid) {
                this.craftingGrid = this.playerUIInventory.getCraftingGrid();
                this.addWindow(this.craftingGrid, ContainerIds.NONE);
            }

            this.craftingType = CRAFTING_SMALL;
        }
    }

    /**
     * Remove all windows
     */
    public void removeAllWindows() {
        removeAllWindows(false);
    }

    /**
     * Remove all windows
     *
     * @param permanent remove permanent windows
     */
    public void removeAllWindows(boolean permanent) {
        for (Entry<Integer, Inventory> entry : new ArrayList<>(this.windowIndex.entrySet())) {
            if (!permanent && this.permanentWindows.contains(entry.getKey())) {
                continue;
            }

            this.removeWindow(entry.getValue());
        }
    }

    public int getClosingWindowId() {
        return this.closingWindowId;
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        this.server.getPlayerMetadata().setMetadata(this, metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return this.server.getPlayerMetadata().getMetadata(this, metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return this.server.getPlayerMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        this.server.getPlayerMetadata().removeMetadata(this, metadataKey, owningPlugin);
    }

    @Override
    public void onChunkChanged(FullChunk chunk) {
        this.usedChunks.remove(Level.chunkHash(chunk.getX(), chunk.getZ()));
    }

    @Override
    public void onChunkLoaded(FullChunk chunk) {
    }

    @Override
    public void onChunkPopulated(FullChunk chunk) {
    }

    @Override
    public void onChunkUnloaded(FullChunk chunk) {
    }

    @Override
    public void onBlockChanged(Vector3 block) {
    }

    @Override
    public int getLoaderId() {
        return this.loaderId;
    }

    @Override
    public boolean isLoaderActive() {
        return this.connected;
    }

    @Deprecated
    public static BatchPacket getChunkCacheFromData(int protocol, int chunkX, int chunkZ, int subChunkCount, byte[] payload) {
        log.warn("Player#getChunkCacheFromData(protocol, chunkX, chunkZ, subChunkCount, payload) is deprecated");
        return getChunkCacheFromData(protocol, chunkX, chunkZ, subChunkCount, payload, 0);
    }

    /**
     * Get chunk cache from data
     *
     * @param protocol      protocol version
     * @param chunkX        chunk x
     * @param chunkZ        chunk z
     * @param subChunkCount sub chunk count
     * @param payload       data
     * @return BatchPacket
     */
    public static BatchPacket getChunkCacheFromData(int protocol, int chunkX, int chunkZ, int subChunkCount, byte[] payload, int dimension) {
        LevelChunkPacket pk = new LevelChunkPacket();
        pk.chunkX = chunkX;
        pk.chunkZ = chunkZ;
        pk.dimension = dimension;
        pk.subChunkCount = subChunkCount;
        pk.data = payload;
        pk.protocol = protocol;
        pk.tryEncode();

        BatchPacket batch = new BatchPacket();
        byte[][] batchPayload = new byte[2][];
        byte[] buf = pk.getBuffer();
        batchPayload[0] = Binary.writeUnsignedVarInt(buf.length);
        batchPayload[1] = buf;
        try {
            if (Server.getInstance().useSnappy && protocol >= ProtocolInfo.v1_19_30_23) {
                batch.payload = SnappyCompression.compress(Binary.appendBytes(batchPayload));
            } else if (protocol >= ProtocolInfo.v1_16_0) {
                batch.payload = Zlib.deflateRaw(Binary.appendBytes(batchPayload), Server.getInstance().networkCompressionLevel);
            } else {
                batch.payload = Zlib.deflatePre16Packet(Binary.appendBytes(batchPayload), Server.getInstance().networkCompressionLevel);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return batch;
    }

    /**
     * Check whether food is enabled or not
     *
     * @return food enabled
     */
    public boolean isFoodEnabled() {
        return !(this.isCreative() || this.isSpectator()) && this.foodEnabled;
    }

    /**
     * Enable or disable food
     *
     * @param foodEnabled food enabled
     */
    public void setFoodEnabled(boolean foodEnabled) {
        this.foodEnabled = foodEnabled;
    }

    /**
     * Get player's food data
     *
     * @return food data
     */
    public PlayerFood getFoodData() {
        return this.foodData;
    }

    /**
     * Send dimension change
     *
     * @param dimension dimension id
     */
    public void setDimension(int dimension) {
        this.dimensionChangeInProgress = true;

        ChangeDimensionPacket changeDimensionPacket = new ChangeDimensionPacket();
        changeDimensionPacket.dimension = dimension;
        changeDimensionPacket.x = (float) this.x;
        changeDimensionPacket.y = (float) this.y;
        changeDimensionPacket.z = (float) this.z;
        changeDimensionPacket.respawn = !this.isAlive();
        this.dataPacket(changeDimensionPacket);

        if (this.protocol >= ProtocolInfo.v1_8_0) {
            NetworkChunkPublisherUpdatePacket pk0 = new NetworkChunkPublisherUpdatePacket();
            pk0.position = new BlockVector3((int) this.x, (int) this.y, (int) this.z);
            pk0.radius = this.chunkRadius << 4;
            this.dataPacket(pk0);
        }

        if (this.protocol >= ProtocolInfo.v1_19_50_20) {
            this.needDimensionChangeACK = true;
        }
    }

    @Override
    protected void preSwitchLevel() {
        // Make sure batch packets from the previous world gets through first
        this.networkSession.flush();

        // Remove old chunks
        this.unloadChunks(true);
    }

    @Override
    protected void afterSwitchLevel() {
        // Send spawn to update compass position
        SetSpawnPositionPacket spawnPosition = new SetSpawnPositionPacket();
        spawnPosition.spawnType = SetSpawnPositionPacket.TYPE_WORLD_SPAWN;
        Position spawn = level.getSpawnLocation();
        spawnPosition.x = spawn.getFloorX();
        spawnPosition.y = spawn.getFloorY();
        spawnPosition.z = spawn.getFloorZ();
        spawnPosition.dimension = level.getDimension();
        this.dataPacket(spawnPosition);

        // Update time and weather
        level.sendTime(this);
        level.sendWeather(this);

        // Update game rules
        GameRulesChangedPacket packet = new GameRulesChangedPacket();
        packet.gameRulesMap = level.getGameRules().getGameRules();
        this.dataPacket(packet);

        // Reset sleeping timer
        this.timeSinceRest = 0;
    }

    /**
     * Enable or disable movement check
     *
     * @param checkMovement movement check enabled
     */
    public void setCheckMovement(boolean checkMovement) {
        this.checkMovement = checkMovement;
    }

    /**
     * @return player movement checks enabled
     */
    public boolean isCheckingMovement() {
        return this.checkMovement;
    }

    /**
     * Set locale
     *
     * @param locale locale
     */
    public synchronized void setLocale(Locale locale) {
        this.locale.set(locale);
    }

    /**
     * Get locale
     *
     * @return locale
     */
    public synchronized Locale getLocale() {
        return this.locale.get();
    }

    @Override
    public void setSprinting(boolean value) {
        if (isSprinting() != value) {
            super.setSprinting(value);
            this.setMovementSpeed(value ? getMovementSpeed() * 1.3f : getMovementSpeed() / 1.3f, false);
        }
    }

    @Override
    protected boolean canShortSneak() {
        return this.protocol >= ProtocolInfo.v1_20_10_21;
    }

    @Override
    public void setSneaking(boolean value) {
        if (isSneaking() != value) {
            super.setSneaking(value);
            this.setMovementSpeed(value ? getMovementSpeed() * 0.3f : getMovementSpeed() / 0.3f, false);
        }
    }

    @Override
    public void setCrawling(boolean value) {
        if (isCrawling() != value) {
            super.setCrawling(value);
            this.setMovementSpeed(value ? getMovementSpeed() * 0.3f : getMovementSpeed() / 0.3f, false);
        }
    }

    /**
     * Transfer player to other server
     *
     * @param address target server address
     */
    public void transfer(InetSocketAddress address) {
        transfer(address.getAddress().getHostAddress(), address.getPort());
    }

    /**
     * Transfer player to other server
     *
     * @param hostName target server address
     * @param port     target server port
     */
    public void transfer(String hostName, int port) {
        TransferPacket pk = new TransferPacket();
        pk.address = hostName;
        pk.port = port;
        this.dataPacket(pk);
    }

    /**
     * Get player's LoginChainData
     *
     * @return login chain data
     */
    public LoginChainData getLoginChainData() {
        return this.loginChainData;
    }

    /**
     * Try to pick up an entity
     *
     * @param entity target
     * @param near   near
     * @return success
     */
    public boolean pickupEntity(Entity entity, boolean near) {
        if (!this.spawned || !this.isAlive() || !this.isOnline() || this.isSpectator() || entity.isClosed()) {
            return false;
        }

        if (near) {
            if (entity instanceof EntityArrow entityArrow && entityArrow.hadCollision) {
                Item item;
                if (entityArrow.namedTag != null && entityArrow.namedTag.containsCompound("item")) {
                    CompoundTag tag = entityArrow.namedTag.getCompound("item");
                    item = Item.get(tag.getInt("id"), tag.getInt("Damage"), tag.getInt("Count"));
                    if (tag.containsCompound("tag")) {
                        item.setCompoundTag(tag.getCompound("tag"));
                    }
                } else {
                    item = new ItemArrow();
                }
                if (!this.isCreative() && !this.inventory.canAddItem(item)) {
                    return false;
                }

                InventoryPickupArrowEvent ev = new InventoryPickupArrowEvent(this.inventory, entityArrow);

                int pickupMode = entityArrow.getPickupMode();
                if (pickupMode == EntityArrow.PICKUP_NONE || (pickupMode == EntityArrow.PICKUP_CREATIVE && !this.isCreative())) {
                    ev.setCancelled();
                }

                this.server.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    return false;
                }

                TakeItemEntityPacket pk = new TakeItemEntityPacket();
                pk.entityId = this.getId();
                pk.target = entity.getId();
                Server.broadcastPacket(entity.getViewers().values(), pk);
                this.dataPacket(pk);

                if (!this.isCreative()) {
                    this.inventory.addItem(item.clone());
                }
                entity.close();
                return true;
            }
            if (entity instanceof EntityThrownTrident) {
                // Check Trident is returning to shooter
                if (!((EntityThrownTrident) entity).hadCollision) {
                    if (entity.isNoClip()) {
                        if (!((EntityProjectile) entity).shootingEntity.equals(this)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }

                if (!((EntityThrownTrident) entity).isPlayer()) {
                    return false;
                }

                Item item = ((EntityThrownTrident) entity).getItem();
                if (!this.isCreative() && !this.inventory.canAddItem(item)) {
                    return false;
                }

                InventoryPickupTridentEvent ev = new InventoryPickupTridentEvent(this.inventory, (EntityThrownTrident) entity);

                int pickupMode = ((EntityThrownTrident) entity).getPickupMode();
                if (pickupMode == EntityThrownTrident.PICKUP_NONE || (pickupMode == EntityThrownTrident.PICKUP_CREATIVE && !this.isCreative())) {
                    ev.setCancelled();
                }

                this.server.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    return false;
                }

                TakeItemEntityPacket pk = new TakeItemEntityPacket();
                pk.entityId = this.getId();
                pk.target = entity.getId();
                Server.broadcastPacket(entity.getViewers().values(), pk);
                this.dataPacket(pk);

                if (!((EntityThrownTrident) entity).isCreative()) {
                    if (inventory.getItem(((EntityThrownTrident) entity).getFavoredSlot()).getId() == Item.AIR) {
                        inventory.setItem(((EntityThrownTrident) entity).getFavoredSlot(), item.clone());
                    } else {
                        inventory.addItem(item.clone());
                    }
                }
                entity.close();
                return true;
            }
            if (entity instanceof EntityItem) {
                if (((EntityItem) entity).getPickupDelay() <= 0) {
                    Item item = ((EntityItem) entity).getItem();

                    if (item != null) {
                        if (!this.isCreative() && !this.inventory.canAddItem(item)) {
                            return false;
                        }

                        InventoryPickupItemEvent ev;
                        this.server.getPluginManager().callEvent(ev = new InventoryPickupItemEvent(this.inventory, (EntityItem) entity));
                        if (ev.isCancelled()) {
                            return false;
                        }

                        if (server.achievementsEnabled) {
                            switch (item.getId()) {
                                case Item.WOOD, Item.WOOD2 -> this.awardAchievement("mineWood");
                                case Item.DIAMOND -> this.awardAchievement("diamond");
                            }
                        }

                        TakeItemEntityPacket pk = new TakeItemEntityPacket();
                        pk.entityId = this.getId();
                        pk.target = entity.getId();
                        Server.broadcastPacket(entity.getViewers().values(), pk);
                        this.dataPacket(pk);

                        this.inventory.addItem(item.clone());
                        entity.close();
                        return true;
                    }
                }
            }
        }

        if (pickedXPOrb < server.getTick() && entity instanceof EntityXPOrb xpOrb && this.boundingBox.isVectorInside(entity)) {
            if (xpOrb.getPickupDelay() <= 0) {
                int exp = xpOrb.getExp();
                entity.close();
                this.getLevel().addSound(new ExperienceOrbSound(this));
                pickedXPOrb = server.getTick();

                ArrayList<Integer> itemsWithMending = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    if (inventory.getArmorItem(i).hasEnchantment(Enchantment.ID_MENDING)) {
                        itemsWithMending.add(inventory.getSize() + i);
                    }
                }
                if (inventory.getItemInHandFast().hasEnchantment(Enchantment.ID_MENDING)) {
                    itemsWithMending.add(inventory.getHeldItemIndex());
                }
                if (!itemsWithMending.isEmpty()) {
                    int itemToRepair = itemsWithMending.get(Utils.random.nextInt(itemsWithMending.size()));
                    Item toRepair = inventory.getItem(itemToRepair);
                    if (toRepair instanceof ItemTool || toRepair instanceof ItemArmor) {
                        if (toRepair.getDamage() > 0) {
                            int dmg = toRepair.getDamage() - 2;
                            if (dmg < 0) {
                                dmg = 0;
                            }
                            toRepair.setDamage(dmg);
                            inventory.setItem(itemToRepair, toRepair);
                            return true;
                        }
                    }
                }

                this.addExperience(exp);
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        if ((this.hash == 0) || (this.hash == 485)) {
            this.hash = (485 + (getUniqueId() != null ? getUniqueId().hashCode() : 0));
        }

        return this.hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Player)) {
            return false;
        }
        Player other = (Player) obj;
        return Objects.equals(this.getUniqueId(), other.getUniqueId()) && this.getId() == other.getId();
    }

    public boolean isBreakingBlock() {
        return this.breakingBlock != null;
    }

    /**
     * Show a window of a XBOX account's profile
     *
     * @param xuid XUID
     */
    public void showXboxProfile(String xuid) {
        ShowProfilePacket pk = new ShowProfilePacket();
        pk.xuid = xuid;
        this.dataPacket(pk);
    }

    /**
     * Start fishing
     *
     * @param fishingRod fishing rod item
     */
    public void startFishing(Item fishingRod) {
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", x))
                        .add(new DoubleTag("", y + this.getEyeHeight()))
                        .add(new DoubleTag("", z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", -Math.sin(yaw / 180 + Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                        .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                        .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", (float) yaw))
                        .add(new FloatTag("", (float) pitch)));
        double f = 1.1;
        EntityFishingHook fishingHook = new EntityFishingHook(chunk, nbt, this);
        fishingHook.setMotion(new Vector3(-Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f, -Math.sin(FastMath.toRadians(pitch)) * f * f,
                Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f));
        ProjectileLaunchEvent ev = new ProjectileLaunchEvent(fishingHook);
        this.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            fishingHook.close();
        } else {
            this.fishing = fishingHook;
            fishingHook.rod = fishingRod;
            fishingHook.checkLure();
            fishingHook.spawnToAll();
            if (protocol >= ProtocolInfo.v1_20_0_23) {
                this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_THROW, -1, "minecraft:player", false, false);
            }
        }
    }

    /**
     * Stop fishing
     *
     * @param click clicked or forced
     */
    public void stopFishing(boolean click) {
        if (this.fishing != null && click) {
            fishing.reelLine();
        } else if (this.fishing != null) {
            this.fishing.close();
        }

        this.fishing = null;
    }

    @Override
    public boolean doesTriggerPressurePlate() {
        return this.gamemode != SPECTATOR;
    }

    public int getNoShieldTicks() {
        return this.noShieldTicks;
    }

    public void setNoShieldTicks(int noShieldTicks) {
        this.noShieldTicks = noShieldTicks;
    }

    @Override
    protected void onBlock(Entity entity, EntityDamageEvent event, boolean animate) {
        super.onBlock(entity, event, animate);
        if (event.isBreakShield()) {
            this.setNoShieldTicks(event.getShieldBreakCoolDown());
            this.setItemCoolDown(event.getShieldBreakCoolDown(), "shield");
        }
        if (animate) {
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_BLOCKED_USING_DAMAGED_SHIELD, true);
            this.getServer().getScheduler().scheduleTask(null, () -> {
                if (this.isOnline()) {
                    this.setDataFlag(DATA_FLAGS, DATA_FLAG_BLOCKED_USING_DAMAGED_SHIELD, false);
                }
            });
        }
    }

    /**
     * Get ticks since sleeping in the current world last time
     *
     * @return ticks since sleeping
     */
    public int getTimeSinceRest() {
        return timeSinceRest;
    }

    /**
     * Set ticks since sleeping in the current world last time
     *
     * @param ticks ticks since sleeping
     */
    public void setTimeSinceRest(int ticks) {
        this.timeSinceRest = ticks;
    }

    public NetworkPlayerSession getNetworkSession() {
        return this.networkSession;
    }

    protected void processPreLogin() {
        this.loginVerified = true;
        final Player playerInstance = this;

        this.preLoginEventTask = new AsyncTask() {
            private PlayerAsyncPreLoginEvent event;

            @Override
            public void onRun() {
                this.event = new PlayerAsyncPreLoginEvent(username, uuid, loginChainData, playerInstance.getSkin(), playerInstance.getAddress(), playerInstance.getPort());
                server.getPluginManager().callEvent(this.event);
            }

            @Override
            public void onCompletion(Server server) {
                if (!playerInstance.connected) {
                    return;
                }

                if (this.event.getLoginResult() == PlayerAsyncPreLoginEvent.LoginResult.KICK) {
                    playerInstance.close(this.event.getKickMessage(), this.event.getKickMessage());
                } else if (playerInstance.shouldLogin) {
                    playerInstance.setSkin(this.event.getSkin());
                    playerInstance.completeLoginSequence();
                    for (Consumer<Server> action : this.event.getScheduledActions()) {
                        action.accept(server);
                    }
                }
            }
        };

        this.server.getScheduler().scheduleAsyncTask(this.preLoginEventTask);
        this.processLogin();
    }

    public boolean shouldLogin() {
        return this.shouldLogin;
    }

    @Override
    public String toString() {
        return "Player(name='" + getName() + "', location=" + super.toString() + ')';
    }

    @Override
    public void setAirTicks(int ticks) {
        if (this.airTicks != ticks) {
            if (this.spawned || ticks > this.airTicks) { // Don't consume air before spawned
                this.airTicks = ticks;
                this.setDataPropertyAndSendOnlyToSelf(new ShortEntityData(DATA_AIR, ticks));
            }
        }
    }

    private static boolean canGoThrough(Block block) {
        switch (block.getId()) {
            case BlockID.GLASS:
            case BlockID.ICE:
            case BlockID.GLOWSTONE:
            case BlockID.BEACON:
            case BlockID.SEA_LANTERN:
            case BlockID.STAINED_GLASS:
            case BlockID.HARD_GLASS:
            case BlockID.HARD_STAINED_GLASS:
            case BlockID.BARRIER: {
                return true;
            }
        }
        return false;
    }

    /**
     * 将物品添加到玩家的主要库存中，并将任何多余的物品丢在地上。
     * <p>
     * Add items to the player's main inventory and drop any excess items on the ground.
     *
     * @param items The items to give to the player.
     */
    public void giveItem(Item... items) {
        for (Item failed : getInventory().addItem(items)) {
            getLevel().dropItem(this, failed);
        }
    }

    public boolean isMovementServerAuthoritative() {
        return this.server.serverAuthoritativeMovementMode == 1 && this.protocol >= ProtocolInfo.v1_17_0;
    }

    public boolean isServerAuthoritativeBlockBreaking() {
        return this.server.serverAuthoritativeBlockBreaking && this.isMovementServerAuthoritative();
    }

    public boolean isEnableNetworkEncryption() {
        return protocol >= ProtocolInfo.v1_7_0 && this.server.encryptionEnabled /*&& loginChainData.isXboxAuthed()*/;
    }

    private List<ExperimentData> getExperiments() {
        List<ExperimentData> experiments = new ObjectArrayList<>();
        //TODO Multiversion 当新版本删除部分实验性玩法时，这里也需要加上判断
        if (this.server.enableExperimentMode) {
            experiments.add(new ExperimentData("data_driven_items", true));
            experiments.add(new ExperimentData("experimental_custom_ui", true));
            experiments.add(new ExperimentData("upcoming_creator_features", true));
            experiments.add(new ExperimentData("experimental_molang_features", true));
            if (protocol >= ProtocolInfo.v1_20_0_23) {
                experiments.add(new ExperimentData("cameras", true));
                if (protocol >= ProtocolInfo.v1_20_10_21 && protocol < ProtocolInfo.v1_20_30_24) {
                    experiments.add(new ExperimentData("short_sneaking", true));
                }
            }
        }
        return experiments;
    }

    @Override
    public void display(IScoreboard scoreboard, DisplaySlot slot) {
        SetDisplayObjectivePacket pk = new SetDisplayObjectivePacket();
        pk.displaySlot = slot;
        pk.objectiveName = scoreboard.getObjectiveName();
        pk.displayName = scoreboard.getDisplayName();
        pk.criteriaName = scoreboard.getCriteriaName();
        pk.sortOrder = scoreboard.getSortOrder();
        this.dataPacket(pk);

        SetScorePacket pk2 = new SetScorePacket();
        pk2.infos = scoreboard.getLines().values().stream()
                .map(IScoreboardLine::toNetworkInfo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        pk2.action = SetScorePacket.Action.SET;
        this.dataPacket(pk2);

        PlayerScorer scorer = new PlayerScorer(this);
        IScoreboardLine line = scoreboard.getLine(scorer);
        if (slot == DisplaySlot.BELOW_NAME && line != null) {
            this.setScoreTag(line.getScore() + " " + scoreboard.getDisplayName());
        }
    }

    @Override
    public void hide(DisplaySlot slot) {
        SetDisplayObjectivePacket pk = new SetDisplayObjectivePacket();
        pk.displaySlot = slot;
        pk.objectiveName = "";
        pk.displayName = "";
        pk.criteriaName = "";
        pk.sortOrder = SortOrder.ASCENDING;
        this.dataPacket(pk);

        if (slot == DisplaySlot.BELOW_NAME) {
            this.setScoreTag("");
        }
    }


    @Override
    public void removeScoreboard(IScoreboard scoreboard) {
        RemoveObjectivePacket pk = new RemoveObjectivePacket();
        pk.objectiveName = scoreboard.getObjectiveName();

        this.dataPacket(pk);
    }

    @Override
    public void removeLine(IScoreboardLine line) {
        SetScorePacket packet = new SetScorePacket();
        packet.action = SetScorePacket.Action.REMOVE;
        SetScorePacket.ScoreInfo networkInfo = line.toNetworkInfo();
        if (networkInfo != null)
            packet.infos.add(networkInfo);
        this.dataPacket(packet);

        PlayerScorer scorer = new PlayerScorer(this);
        if (line.getScorer().equals(scorer) && line.getScoreboard().getViewers(DisplaySlot.BELOW_NAME).contains(this)) {
            this.setScoreTag("");
        }
    }

    @Override
    public void updateScore(IScoreboardLine line) {
        SetScorePacket packet = new SetScorePacket();
        packet.action = SetScorePacket.Action.SET;
        SetScorePacket.ScoreInfo networkInfo = line.toNetworkInfo();
        if (networkInfo != null) packet.infos.add(networkInfo);
        this.dataPacket(packet);

        PlayerScorer scorer = new PlayerScorer(this);
        if (line.getScorer().equals(scorer) && line.getScoreboard().getViewers(DisplaySlot.BELOW_NAME).contains(this)) {
            this.setScoreTag(line.getScore() + " " + line.getScoreboard().getDisplayName());
        }
    }
}
