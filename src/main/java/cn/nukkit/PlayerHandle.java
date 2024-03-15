package cn.nukkit;

import cn.nukkit.form.window.FormWindow;
import cn.nukkit.form.window.FormWindowDialog;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.transaction.*;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.PlayerFogPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.DummyBossBar;
import cn.nukkit.utils.LoginChainData;
import com.google.common.cache.Cache;
import com.google.common.collect.BiMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A PlayerHandle is used to access a player's protected data.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class PlayerHandle {
    public final @NotNull Player player;

    public PlayerHandle(@NotNull Player player) {
        this.player = player;
    }

    public int getProtocol() {
        return player.protocol;
    }

    public NetworkPlayerSession getNetworkSession() {
        return player.networkSession;
    }


    public void sendPlayStatus(int status) {
        player.sendPlayStatus(status);
    }

    public void sendPlayStatus(int status, boolean immediate) {
        player.sendPlayStatus(status, immediate);
    }

    /*public long getBreakingBlockTime() {
        return player.breakingBlockTime;
    }

    public void setBreakingBlockTime(long breakingBlockTime) {
        player.breakingBlockTime = breakingBlockTime;
    }

    public double getBlockBreakProgress() {
        return player.blockBreakProgress;
    }

    public void setBlockBreakProgress(double blockBreakProgress) {
        player.blockBreakProgress = blockBreakProgress;
    }*/

    public SourceInterface getInterfaz() {
        return player.interfaz;
    }

    public BiMap<Inventory, Integer> getWindows() {
        return player.windows;
    }

    public BiMap<Integer, Inventory> getWindowIndex() {
        return player.windowIndex;
    }

    public Set<Integer> getPermanentWindows() {
        return player.permanentWindows;
    }

    public Long2ObjectLinkedOpenHashMap<Boolean> getLoadQueue() {
        return player.loadQueue;
    }

    public Map<UUID, Player> getHiddenPlayers() {
        return player.hiddenPlayers;
    }

    /*public int getChunksPerTick() {
        return player.chunksPerTick;
    }

    public int getSpawnThreshold() {
        return player.spawnThreshold;
    }*/

    public int getWindowCnt() {
        return player.windowCnt;
    }

    public void setWindowCnt(int windowCnt) {
        player.windowCnt = windowCnt;
    }

    public void setClosingWindowId(int closingWindowId) {
        player.closingWindowId = closingWindowId;
    }

    /*public int getMessageCounter() {
        return player.messageCounter;
    }

    public void setMessageCounter(int messageCounter) {
        player.messageCounter = messageCounter;
    }*/

    public PlayerUIInventory getPlayerUIInventory() {
        return player.playerUIInventory;
    }

    public void setPlayerUIInventory(PlayerUIInventory playerUIInventory) {
        player.playerUIInventory = playerUIInventory;
    }

    public CraftingTransaction getCraftingTransaction() {
        return player.craftingTransaction;
    }

    public void setCraftingTransaction(CraftingTransaction craftingTransaction) {
        player.craftingTransaction = craftingTransaction;
    }

    public EnchantTransaction getEnchantTransaction() {
        return player.enchantTransaction;
    }

    public void setEnchantTransaction(EnchantTransaction enchantTransaction) {
        player.enchantTransaction = enchantTransaction;
    }

    public RepairItemTransaction getRepairItemTransaction() {
        return player.repairItemTransaction;
    }

    public void setRepairItemTransaction(RepairItemTransaction repairItemTransaction) {
        player.repairItemTransaction = repairItemTransaction;
    }

    /*public GrindstoneTransaction getGrindstoneTransaction() {
        return player.grindstoneTransaction;
    }

    public void setGrindstoneTransaction(GrindstoneTransaction grindstoneTransaction) {
        player.grindstoneTransaction = grindstoneTransaction;
    }*/

    public SmithingTransaction getSmithingTransaction() {
        return player.smithingTransaction;
    }

    public void setSmithingTransaction(SmithingTransaction smithingTransaction) {
        player.smithingTransaction = smithingTransaction;
    }

    public TradingTransaction getTradingTransaction() {
        return player.tradingTransaction;
    }

    public void setTradingTransaction(TradingTransaction tradingTransaction) {
        player.tradingTransaction = tradingTransaction;
    }

    public long getRandomClientId() {
        return player.randomClientId;
    }

    public void setRandomClientId(long randomClientId) {
        player.randomClientId = randomClientId;
    }

    public Vector3 getForceMovement() {
        return player.forceMovement;
    }

    public void setForceMovement(Vector3 forceMovement) {
        player.forceMovement = forceMovement;
    }

    public Vector3 getTeleportPosition() {
        return player.teleportPosition;
    }

    public void setTeleportPosition(Vector3 teleportPosition) {
        player.teleportPosition = teleportPosition;
    }

    public void setConnected(boolean connected) {
        player.connected = connected;
    }

    /*public void setSocketAddress(InetSocketAddress socketAddress) {
        player.socketAddress = socketAddress;
    }*/

    public boolean isRemoveFormat() {
        return player.removeFormat;
    }

    public String getUsername() {
        return player.username;
    }

    public void setUsername(String username) {
        player.username = username;
    }

    public String getIusername() {
        return player.iusername;
    }

    public void setIusername(String iusername) {
        player.iusername = iusername;
    }

    public String getDisplayName() {
        return player.displayName;
    }

    public void setDisplayName(String displayName) {
        player.displayName = displayName;
    }

    public int getStartAction() {
        return player.startAction;
    }

    public void setStartAction(int startAction) {
        player.startAction = startAction;
    }

    public Vector3 getSleeping() {
        return player.sleeping;
    }

    public void setSleeping(Vector3 sleeping) {
        player.sleeping = sleeping;
    }

    /*public Long getClientID() {
        return player.clientID;
    }

    public void setClientID(Long clientID) {
        player.clientID = clientID;
    }

    public int getChunkLoadCount() {
        return player.chunkLoadCount;
    }

    public void setChunkLoadCount(int chunkLoadCount) {
        player.chunkLoadCount = chunkLoadCount;
    }*/

    public int getNextChunkOrderRun() {
        return player.nextChunkOrderRun;
    }

    public void setNextChunkOrderRun(int nextChunkOrderRun) {
        player.nextChunkOrderRun = nextChunkOrderRun;
    }

    public Vector3 getNewPosition() {
        return player.newPosition;
    }

    public void setNewPosition(Vector3 newPosition) {
        player.newPosition = newPosition;
    }

    public int getChunkRadius() {
        return player.chunkRadius;
    }

    public void setChunkRadius(int chunkRadius) {
        player.chunkRadius = chunkRadius;
    }

    public Position getSpawnPosition() {
        return player.spawnPosition;
    }

    public void setSpawnPosition(Position spawnPosition) {
        player.spawnPosition = spawnPosition;
    }

    /*public Position getSpawnBlockPosition() {
        return player.spawnBlockPosition;
    }

    public void setSpawnBlockPosition(Position spawnBlockPosition) {
        player.spawnBlockPosition = spawnBlockPosition;
    }*/

    public void setInAirTicks(int inAirTicks) {
        player.inAirTicks = inAirTicks;
    }

    public int getStartAirTicks() {
        return player.startAirTicks;
    }

    public void setStartAirTicks(int startAirTicks) {
        player.startAirTicks = startAirTicks;
    }

    public boolean isCheckMovement() {
        return player.checkMovement;
    }

    public void setFoodData(PlayerFood foodData) {
        player.foodData = foodData;
    }

    /*public int getLastEnderPearl() {
        return player.lastEnderPearl;
    }

    public void setLastEnderPearl(int lastEnderPearl) {
        player.lastEnderPearl = lastEnderPearl;
    }

    public void setLastChorusFruitTeleport(int lastChorusFruitTeleport) {
        player.lastChorusFruitTeleport = lastChorusFruitTeleport;
    }*/

    public int getFormWindowCount() {
        return player.formWindowCount;
    }

    public void setFormWindowCount(int formWindowCount) {
        player.formWindowCount = formWindowCount;
    }

    public Map<Integer, FormWindow> getFormWindows() {
        return player.formWindows;
    }

    public void setFormWindows(Map<Integer, FormWindow> formWindows) {
        player.formWindows = formWindows;
    }

    public Map<Integer, FormWindow> getServerSettings() {
        return player.serverSettings;
    }

    public void setServerSettings(Map<Integer, FormWindow> serverSettings) {
        player.serverSettings = serverSettings;
    }

    public Cache<String, FormWindowDialog> getDialogWindows() {
        return player.dialogWindows;
    }

    public void setDialogWindows(Cache<String, FormWindowDialog> dialogWindows) {
        player.dialogWindows = dialogWindows;
    }

    public void setDummyBossBars(Map<Long, DummyBossBar> dummyBossBars) {
        player.dummyBossBars = dummyBossBars;
    }

    public boolean isShouldLogin() {
        return player.shouldLogin;
    }

    public void setShouldLogin(boolean shouldLogin) {
        player.shouldLogin = shouldLogin;
    }

    /*public double getLastRightClickTime() {
        return player.lastRightClickTime;
    }

    public void setLastRightClickTime(double lastRightClickTime) {
        player.lastRightClickTime = lastRightClickTime;
    }

    public Vector3 getLastRightClickPos() {
        return player.lastRightClickPos;
    }

    public void setLastRightClickPos(Vector3 lastRightClickPos) {
        player.lastRightClickPos = lastRightClickPos;
    }

    public void setLastInAirTick(int lastInAirTick) {
        player.lastInAirTick = lastInAirTick;
    }

    public int getLastPlayerdLevelUpSoundTime() {
        return player.lastPlayerdLevelUpSoundTime;
    }

    public void setLastPlayerdLevelUpSoundTime(int lastPlayerdLevelUpSoundTime) {
        player.lastPlayerdLevelUpSoundTime = lastPlayerdLevelUpSoundTime;
    }

    public void setLastAttackEntity(Entity lastAttackEntity) {
        player.lastAttackEntity = lastAttackEntity;
    }*/

    public List<PlayerFogPacket.Fog> getFogStack() {
        return player.fogStack;
    }

    public void setFogStack(List<PlayerFogPacket.Fog> fogStack) {
        player.fogStack = fogStack;
    }

    /*public void setLastBeAttackEntity(Entity lastBeAttackEntity) {
        player.lastBeAttackEntity = lastBeAttackEntity;
    }*/

    public void setLoginChainData(LoginChainData loginChainData) {
        player.loginChainData = loginChainData;
    }

    public LoginChainData getLoginChainData() {
        return player.loginChainData;
    }

    public boolean isVerified() {
        return player.loginVerified;
    }

    public void setVerified(boolean verified) {
        player.loginVerified = verified;
    }

    public boolean isAwaitingEncryptionHandshake() {
        return player.awaitingEncryptionHandshake;
    }

    public void setAwaitingEncryptionHandshake(boolean awaitingEncryptionHandshake) {
        player.awaitingEncryptionHandshake = awaitingEncryptionHandshake;
    }

    public AsyncTask getPreLoginEventTask() {
        return player.preLoginEventTask;
    }

    public void setPreLoginEventTask(AsyncTask preLoginEventTask) {
        player.preLoginEventTask = preLoginEventTask;
    }

    public void completeLoginSequence() {
        player.completeLoginSequence();
    }

    public void processLogin() {
        player.processLogin();
    }

    public void processPreLogin() {
        player.processPreLogin();
    }

    public void doFirstSpawn() {
        player.doFirstSpawn();
    }

    public boolean isLoginPacketReceived() {
        return player.loginPacketReceived;
    }
}