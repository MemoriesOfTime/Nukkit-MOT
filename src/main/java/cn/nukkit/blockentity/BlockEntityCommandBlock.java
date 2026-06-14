package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.event.command.CommandBlockExecuteEvent;
import cn.nukkit.inventory.CommandBlockInventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.lang.CommandOutputContainer;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.permission.Permission;
import cn.nukkit.permission.PermissionAttachment;
import cn.nukkit.permission.PermissionAttachmentInfo;
import cn.nukkit.permission.PermissibleBase;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class BlockEntityCommandBlock extends BlockEntitySpawnable
        implements ICommandBlock, InventoryHolder {

    protected boolean conditionalMode;
    protected boolean auto;
    protected String command = "";
    protected long lastExecution;
    protected boolean trackOutput = true;
    protected String lastOutput = "";
    protected ListTag<StringTag> lastOutputParams = new ListTag<>();
    protected int lastOutputCommandMode;
    protected boolean lastOutputCondionalMode;
    protected boolean lastOutputRedstoneMode;
    protected int successCount;
    protected boolean conditionMet;
    protected boolean powered;
    protected int tickDelay;
    protected boolean executingOnFirstTick;
    protected int currentTick;
    protected boolean wasActiveLastTick;
    /**
     * Tracks whether the current command execution already received a precise
     * success count via {@link #sendCommandOutput}. Commands that report their
     * result through {@link cn.nukkit.command.utils.CommandLogger} (e.g. give,
     * kill, effect) call sendCommandOutput during dispatchCommand with the exact
     * number of affected targets. When set, {@link #execute(int)} keeps that
     * value instead of falling back to the 0/1 boolean dispatch result.
     */
    protected boolean receivedOutputSuccessCount;

    protected final PermissibleBase perm = new PermissibleBase(this);
    protected CommandBlockInventory inventory;

    public BlockEntityCommandBlock(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        super.initBlockEntity();
        this.inventory = new CommandBlockInventory(this);

        if (this.namedTag.containsString(ICommandBlock.TAG_COMMAND)) {
            this.command = this.namedTag.getString(ICommandBlock.TAG_COMMAND);
        }
        if (this.namedTag.containsInt(ICommandBlock.TAG_SUCCESS_COUNT)) {
            this.successCount = this.namedTag.getInt(ICommandBlock.TAG_SUCCESS_COUNT);
        }
        if (this.namedTag.contains(ICommandBlock.TAG_LAST_OUTPUT)) {
            this.lastOutput = this.namedTag.getString(ICommandBlock.TAG_LAST_OUTPUT);
        }
        if (this.namedTag.containsList(ICommandBlock.TAG_LAST_OUTPUT_PARAMS)) {
            this.lastOutputParams = this.namedTag.getList(ICommandBlock.TAG_LAST_OUTPUT_PARAMS, StringTag.class);
        }
        if (this.namedTag.containsByte(ICommandBlock.TAG_TRACK_OUTPUT)) {
            this.trackOutput = this.namedTag.getBoolean(ICommandBlock.TAG_TRACK_OUTPUT);
        }
        if (this.namedTag.containsNumber(ICommandBlock.TAG_LAST_EXECUTION)) {
            this.lastExecution = this.namedTag.getLong(ICommandBlock.TAG_LAST_EXECUTION);
        }
        if (this.namedTag.containsByte(ICommandBlock.TAG_AUTO)) {
            this.auto = this.namedTag.getBoolean(ICommandBlock.TAG_AUTO);
        }
        if (this.namedTag.containsByte(ICommandBlock.TAG_CONDITIONAL_MODE)) {
            this.conditionalMode = this.namedTag.getBoolean(ICommandBlock.TAG_CONDITIONAL_MODE);
        }
        if (this.namedTag.containsByte(ICommandBlock.TAG_CONDITION_MET)) {
            this.conditionMet = this.namedTag.getBoolean(ICommandBlock.TAG_CONDITION_MET);
        }
        if (this.namedTag.containsByte(ICommandBlock.TAG_POWERED)) {
            this.powered = this.namedTag.getBoolean(ICommandBlock.TAG_POWERED);
        }
        if (this.namedTag.containsInt(ICommandBlock.TAG_TICK_DELAY)) {
            this.tickDelay = this.namedTag.getInt(ICommandBlock.TAG_TICK_DELAY);
        }
        if (this.namedTag.containsByte(ICommandBlock.TAG_EXECUTE_ON_FIRST_TICK)) {
            this.executingOnFirstTick = this.namedTag.getBoolean(ICommandBlock.TAG_EXECUTE_ON_FIRST_TICK);
        }
        if (this.namedTag.containsString(ICommandBlock.TAG_CUSTOM_NAME)) {
            this.name = this.namedTag.getString(ICommandBlock.TAG_CUSTOM_NAME);
        }

        if (this.getMode() == ICommandBlock.MODE_REPEATING) {
            this.scheduleUpdate();
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putString(ICommandBlock.TAG_COMMAND, this.command);
        this.namedTag.putInt(ICommandBlock.TAG_SUCCESS_COUNT, this.successCount);
        this.namedTag.putString(ICommandBlock.TAG_LAST_OUTPUT, this.lastOutput == null ? "" : this.lastOutput);
        this.namedTag.putList(new ListTag<>(ICommandBlock.TAG_LAST_OUTPUT_PARAMS));
        this.namedTag.putBoolean(ICommandBlock.TAG_TRACK_OUTPUT, this.trackOutput);
        this.namedTag.putLong(ICommandBlock.TAG_LAST_EXECUTION, this.lastExecution);
        this.namedTag.putBoolean(ICommandBlock.TAG_AUTO, this.auto);
        this.namedTag.putBoolean(ICommandBlock.TAG_CONDITIONAL_MODE, this.conditionalMode);
        this.namedTag.putBoolean(ICommandBlock.TAG_CONDITION_MET, this.conditionMet);
        this.namedTag.putBoolean(ICommandBlock.TAG_POWERED, this.powered);
        this.namedTag.putInt(ICommandBlock.TAG_TICK_DELAY, this.tickDelay);
        this.namedTag.putBoolean(ICommandBlock.TAG_EXECUTE_ON_FIRST_TICK, this.executingOnFirstTick);
        this.namedTag.putInt(ICommandBlock.TAG_LP_COMMAND_MODE, this.getMode());
        this.namedTag.putBoolean(ICommandBlock.TAG_LP_CONDIONAL_MODE, this.conditionalMode);
        this.namedTag.putBoolean(ICommandBlock.TAG_LP_REDSTONE_MODE, !this.auto);
        this.namedTag.putInt(ICommandBlock.TAG_VERSION, ICommandBlock.CURRENT_VERSION);
        if (this.name != null && !this.name.isEmpty()) {
            this.namedTag.putString(ICommandBlock.TAG_CUSTOM_NAME, this.name);
        }
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag tag = new CompoundTag()
                .putString("id", BlockEntity.COMMAND_BLOCK)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putBoolean("isMovable", this.movable);
        if (this.command != null && !this.command.isEmpty()) {
            tag.putString(ICommandBlock.TAG_COMMAND, this.command);
        }
        tag.putInt(ICommandBlock.TAG_SUCCESS_COUNT, this.successCount);
        if (this.trackOutput && this.lastOutput != null && !this.lastOutput.isEmpty()) {
            tag.putString(ICommandBlock.TAG_LAST_OUTPUT, this.lastOutput);
        }
        tag.putBoolean(ICommandBlock.TAG_TRACK_OUTPUT, this.trackOutput);
        tag.putLong(ICommandBlock.TAG_LAST_EXECUTION, this.lastExecution);
        tag.putBoolean(ICommandBlock.TAG_AUTO, this.auto);
        tag.putBoolean(ICommandBlock.TAG_CONDITION_MET, this.conditionMet);
        tag.putBoolean(ICommandBlock.TAG_CONDITIONAL_MODE, this.conditionalMode);
        tag.putBoolean(ICommandBlock.TAG_POWERED, this.powered);
        tag.putInt(ICommandBlock.TAG_TICK_DELAY, this.tickDelay);
        tag.putBoolean(ICommandBlock.TAG_EXECUTE_ON_FIRST_TICK, this.executingOnFirstTick);
        tag.putInt(ICommandBlock.TAG_LP_COMMAND_MODE, this.getMode());
        tag.putBoolean(ICommandBlock.TAG_LP_CONDIONAL_MODE, this.conditionalMode);
        tag.putBoolean(ICommandBlock.TAG_LP_REDSTONE_MODE, !this.auto);
        if (this.name != null && !this.name.isEmpty()) {
            tag.putString(ICommandBlock.TAG_CUSTOM_NAME, this.name);
        }
        return tag;
    }

    @Override
    public boolean isBlockEntityValid() {
        int id = this.getLevelBlock().getId();
        return id == BlockID.COMMAND_BLOCK
                || id == BlockID.REPEATING_COMMAND_BLOCK
                || id == BlockID.CHAIN_COMMAND_BLOCK;
    }

    @Override
    public int getMode() {
        int id = this.getLevelBlock().getId();
        if (id == BlockID.REPEATING_COMMAND_BLOCK) {
            return ICommandBlock.MODE_REPEATING;
        } else if (id == BlockID.CHAIN_COMMAND_BLOCK) {
            return ICommandBlock.MODE_CHAIN;
        }
        return ICommandBlock.MODE_NORMAL;
    }

    @Override
    public boolean onUpdate() {
        if (this.getMode() != ICommandBlock.MODE_REPEATING) {
            return false;
        }
        if (!this.auto && !this.powered) {
            this.wasActiveLastTick = false;
            this.currentTick = 0;
            return true;
        }
        if (!this.wasActiveLastTick) {
            this.wasActiveLastTick = true;
            this.currentTick = 0;
            if (this.executingOnFirstTick) {
                this.execute(0);
            }
            return true;
        }
        if (this.currentTick++ < this.tickDelay) {
            return true;
        }
        this.execute(0);
        this.currentTick = 0;
        return true;
    }

    @Override
    public boolean execute(int chain) {
        int maxChain = this.level.getGameRules().getInteger(GameRule.MAX_COMMAND_CHAIN_LENGTH);
        if (chain > maxChain) {
            this.server.getLogger().warning("Command block chain exceeded max length (" + maxChain + ") at " + this.getLocation());
            return false;
        }
        if (!this.level.getGameRules().getBoolean(GameRule.COMMAND_BLOCKS_ENABLED)) {
            return false;
        }
        // Wiki (Trigger and chaining): a block already executed this game tick
        // "does nothing" — it neither runs its command nor updates its success
        // count, and the chain is not propagated further. This mirrors JE's
        // BaseCommandBlock.performCommand early return on equal lastExecution.
        if (this.getLastExecution() == this.level.getCurrentTick()) {
            return false;
        }

        Block levelBlock = this.getLevelBlock();
        BlockFace facing = BlockFace.DOWN;
        if (levelBlock instanceof Faceable faceable) {
            facing = faceable.getBlockFace();
        }

        // Wiki distinguishes three cases when a block is triggered:
        //  - Not activated: propagate the chain only; success count untouched.
        //  - Activated + conditional + predecessor failed: success count 0, no command.
        //  - Activated + condition met (or unconditional): run the command.
        boolean activated = this.isAuto() || this.isPowered();
        if (activated) {
            this.setConditionMet();
        }
        boolean runCommand = activated && (!this.conditionalMode || this.conditionMet);

        if (runCommand) {
            String cmd = this.getCommand();
            if (cmd != null && !cmd.trim().isEmpty()) {
                if ("Searge".equalsIgnoreCase(cmd)) {
                    // JE easter egg (BaseCommandBlock.performCommand)
                    this.lastOutput = "#itzlipofutzli";
                    this.successCount = 1;
                } else {
                    this.lastOutput = "";
                    CommandBlockExecuteEvent event = new CommandBlockExecuteEvent(levelBlock, cmd);
                    this.server.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        this.successCount = 0;
                        return false;
                    }
                    try {
                        // Reset before dispatch: sendCommandOutput (called inside
                        // dispatchCommand for ParamTree commands) may capture the
                        // precise number of affected targets.
                        this.receivedOutputSuccessCount = false;
                        boolean result = this.server.dispatchCommand(this, event.getCommand());
                        if (!this.receivedOutputSuccessCount) {
                            // Legacy command or one that didn't report output:
                            // fall back to the boolean dispatch result (0/1).
                            this.successCount = result ? 1 : 0;
                        }
                    } catch (Exception e) {
                        this.successCount = 0;
                        this.server.getLogger().warning("Command block error at " + this.getLocation(), e);
                    }
                }
            }
        } else if (activated && this.conditionalMode && !this.conditionMet) {
            // Wiki: a conditional block whose predecessor didn't succeed sets its
            // success count to 0 and still triggers the next chain block, but runs
            // no command.
            this.successCount = 0;
        }

        this.propagateChain(facing, chain);

        this.lastExecution = this.level.getCurrentTick();
        this.lastOutputCommandMode = this.getMode();
        this.lastOutputCondionalMode = this.conditionalMode;
        this.lastOutputRedstoneMode = !this.auto;

        this.spawnToAll();
        // Only refresh comparator/diode output signals, not general redstone updates,
        // to avoid re-triggering this command block's own redstone onUpdate (JE uses
        // updateNeighbourForOutputSignal).
        this.level.updateComparatorOutputLevel(this);
        return true;
    }

    protected void propagateChain(BlockFace facing, int chain) {
        Position sidePos = this.getSide(facing);
        Block next = this.level.getBlock(sidePos);
        if (next instanceof cn.nukkit.block.BlockCommandBlockChain) {
            BlockEntity be = this.level.getBlockEntity(sidePos);
            if (be instanceof BlockEntityCommandBlock cb) {
                cb.trigger(chain + 1);
            }
        }
    }

    protected void setConditionMet() {
        if (!this.conditionalMode) {
            this.conditionMet = true;
            return;
        }
        Block levelBlock = this.getLevelBlock();
        BlockFace facing = BlockFace.DOWN;
        if (levelBlock instanceof Faceable faceable) {
            facing = faceable.getBlockFace();
        }
        Position behindPos = this.getSide(facing.getOpposite());
        BlockEntity be = this.level.getBlockEntity(behindPos);
        if (be instanceof BlockEntityCommandBlock cb) {
            this.conditionMet = cb.getSuccessCount() > 0;
        } else {
            this.conditionMet = false;
        }
    }

    @Override
    public CommandBlockInventory getInventory() {
        return inventory;
    }

    @Override
    public void close() {
        if (!this.closed) {
            if (this.inventory != null) {
                for (Player player : new java.util.HashSet<>(this.inventory.getViewers())) {
                    player.removeWindow(this.inventory);
                }
            }
            super.close();
        }
    }

    @Override
    public Server getServer() {
        return this.server;
    }

    @Override
    public String getName() {
        return this.name != null && !this.name.isEmpty() ? this.name : "!";
    }

    public void setName(String name) {
        this.name = name == null || name.isEmpty() ? null : name;
        this.setDirty();
    }

    @Override
    public boolean isOp() {
        return true;
    }

    @Override
    public void setOp(boolean value) {
    }

    @Override
    public void sendMessage(String message) {
        if (this.trackOutput) {
            this.lastOutput = message;
        }
        if (this.level.getGameRules().getBoolean(GameRule.COMMAND_BLOCK_OUTPUT)) {
            this.server.broadcast("§7§o[" + this.getName() + ": " + message + "]",
                    Server.BROADCAST_CHANNEL_ADMINISTRATIVE);
        }
    }

    @Override
    public void sendMessage(TextContainer message) {
        if (message instanceof TranslationContainer translationContainer) {
            this.sendMessage(this.server.getLanguage().translateString(
                    translationContainer.getText(), translationContainer.getParameters()));
        } else {
            this.sendMessage(message.getText());
        }
    }

    @Override
    public void sendCommandOutput(CommandOutputContainer container) {
        // Capture the precise success count reported by the command (e.g. number
        // of affected targets) so it can drive comparator output and conditional
        // chain blocks, matching Bedrock's success count semantics. dispatchCommand
        // only exposes a boolean, so this is the only channel to obtain the value.
        int reported = container.getSuccessCount();
        if (reported > 0) {
            this.successCount = reported;
            this.receivedOutputSuccessCount = true;
        }
        for (cn.nukkit.network.protocol.types.CommandOutputMessage msg : container.getMessages()) {
            String text = this.server.getLanguage().translateString(
                    msg.getMessageId(), msg.getParameters());
            this.sendMessage(text);
        }
    }

    @Override
    public boolean isPlayer() {
        return false;
    }

    @Override
    public boolean isEntity() {
        return false;
    }

    @NotNull
    @Override
    public Position getPosition() {
        return this;
    }

    @NotNull
    @Override
    public Location getLocation() {
        return new Location(this.x, this.y, this.z, this.level);
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
        return this.perm.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return this.perm.hasPermission(permission);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return this.perm.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name) {
        return this.perm.addAttachment(plugin, name);
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
        this.perm.recalculatePermissions();
    }

    @Override
    public Map<String, PermissionAttachmentInfo> getEffectivePermissions() {
        return this.perm.getEffectivePermissions();
    }

    @Override
    public String getCommand() { return command; }

    @Override
    public int getSuccessCount() { return successCount; }

    @Override
    public boolean isPowered() { return powered; }

    @Override
    public boolean isAuto() { return auto; }

    @Override
    public boolean isConditional() { return conditionalMode; }

    @Override
    public boolean isConditionMet() { return conditionMet; }

    @Override
    public int getTickDelay() { return tickDelay; }

    @Override
    public long getLastExecution() { return lastExecution; }

    @Override
    public boolean isTrackingOutput() { return trackOutput; }

    public void setCommand(String command) { this.command = command == null ? "" : command; this.successCount = 0; this.setDirty(); }
    public void setPowered(boolean powered) { this.powered = powered; this.setDirty(); }
    public void setAuto(boolean auto) { this.auto = auto; this.setDirty(); }
    public void setConditional(boolean conditionalMode) { this.conditionalMode = conditionalMode; this.setDirty(); }
    public void setTickDelay(int tickDelay) { this.tickDelay = Math.max(0, tickDelay); this.setDirty(); }
    public void setExecutingOnFirstTick(boolean executingOnFirstTick) { this.executingOnFirstTick = executingOnFirstTick; this.setDirty(); }
    public void setTrackOutput(boolean trackOutput) { this.trackOutput = trackOutput; this.setDirty(); }
    public void setSuccessCount(int successCount) { this.successCount = Math.max(0, successCount); }
}
