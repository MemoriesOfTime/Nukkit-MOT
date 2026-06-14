package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.ICommandBlock;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.ByteEntityData;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.data.StringEntityData;
import cn.nukkit.event.command.CommandBlockExecuteEvent;
import cn.nukkit.inventory.CommandBlockMinecartInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.lang.CommandOutputContainer;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.permission.Permission;
import cn.nukkit.permission.PermissionAttachment;
import cn.nukkit.permission.PermissionAttachmentInfo;
import cn.nukkit.permission.PermissibleBase;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.MinecartType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;

/**
 * Command block minecart. Executes its command when activated by a powered
 * activator rail, with a cooldown to avoid repeated execution per tick.
 * <p>
 * Adapted from JE {@code MinecartCommandBlock} / {@code BaseCommandBlock} and
 * the block variant {@code BlockEntityCommandBlock}.
 */
public class EntityMinecartCommandBlock extends EntityMinecartAbstract
        implements ICommandBlock, InventoryHolder {

    public static final int NETWORK_ID = 100;

    /** Cooldown (in server ticks) between activations on a powered activator rail (JE: 4). */
    private static final int ACTIVATION_DELAY = 4;

    protected String command = "";
    protected int successCount;
    protected boolean trackOutput = true;
    protected String lastOutput = "";
    protected long lastExecution;
    /** Custom name used as the CommandSender name (separate from the entity display name). */
    protected String customName = "";
    /**
     * Tracks whether the current command execution already received a precise
     * success count via {@link #sendCommandOutput}. See
     * {@link cn.nukkit.blockentity.BlockEntityCommandBlock#receivedOutputSuccessCount}
     * for the rationale.
     */
    protected boolean receivedOutputSuccessCount;

    private int lastActivated = Integer.MIN_VALUE;
    protected CommandBlockMinecartInventory inventory;
    protected final PermissibleBase perm = new PermissibleBase(this);

    public EntityMinecartCommandBlock(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        setDisplayBlock(Block.get(Block.COMMAND_BLOCK), false);
        setName("Minecart with Command Block");
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public MinecartType getType() {
        return MinecartType.MINECART_COMMAND_BLOCK;
    }

    @Override
    public boolean isRideable() {
        return false;
    }

    @Override
    public String getInteractButtonText() {
        return "";
    }

    @Override
    public int getMode() {
        // Minecart command blocks have no mode variants.
        return ICommandBlock.MODE_NORMAL;
    }

    @Override
    public boolean isPowered() {
        return false;
    }

    @Override
    public boolean isAuto() {
        return false;
    }

    @Override
    public boolean isConditional() {
        return false;
    }

    @Override
    public boolean isConditionMet() {
        // Minecart command blocks are never conditional, so their condition is always met.
        return true;
    }

    @Override
    public int getTickDelay() {
        return 0;
    }

    @Override
    public long getLastExecution() {
        return lastExecution;
    }

    @Override
    public boolean isTrackingOutput() {
        return trackOutput;
    }

    @Override
    public String getCommand() {
        return command;
    }

    @Override
    public int getSuccessCount() {
        return successCount;
    }

    public void setCommand(String command) {
        this.command = command == null ? "" : command;
        this.successCount = 0;
        this.setDataProperty(new StringEntityData(Entity.DATA_COMMAND_BLOCK_COMMAND, this.command));
    }

    public void setTrackOutput(boolean trackOutput) {
        this.trackOutput = trackOutput;
        this.setDataProperty(new ByteEntityData(Entity.DATA_COMMAND_BLOCK_TRACK_OUTPUT, this.trackOutput ? 1 : 0));
    }

    public void setSuccessCount(int successCount) {
        this.successCount = Math.max(0, successCount);
    }

    /**
     * Sets the CommandSender custom name (what the client edits in the "Hover Note" field).
     * This is distinct from the entity display name.
     */
    public void setCustomName(String name) {
        this.customName = name == null ? "" : name;
    }

    @Override
    public void initEntity() {
        super.initEntity();

        if (this.namedTag.contains(ICommandBlock.TAG_COMMAND)) {
            this.command = this.namedTag.getString(ICommandBlock.TAG_COMMAND);
        }
        if (this.namedTag.contains(ICommandBlock.TAG_SUCCESS_COUNT)) {
            this.successCount = this.namedTag.getInt(ICommandBlock.TAG_SUCCESS_COUNT);
        }
        if (this.namedTag.contains(ICommandBlock.TAG_TRACK_OUTPUT)) {
            this.trackOutput = this.namedTag.getBoolean(ICommandBlock.TAG_TRACK_OUTPUT);
        }
        if (this.namedTag.contains(ICommandBlock.TAG_LAST_OUTPUT)) {
            this.lastOutput = this.namedTag.getString(ICommandBlock.TAG_LAST_OUTPUT);
        }
        if (this.namedTag.contains(ICommandBlock.TAG_CUSTOM_NAME)) {
            this.customName = this.namedTag.getString(ICommandBlock.TAG_CUSTOM_NAME);
        }

        this.inventory = new CommandBlockMinecartInventory(this);

        // Sync command block entity data so the client renders the block and shows the latest output.
        this.setDataProperty(new ByteEntityData(Entity.DATA_COMMAND_BLOCK_TRACK_OUTPUT, this.trackOutput ? 1 : 0));
        this.setDataProperty(new StringEntityData(Entity.DATA_COMMAND_BLOCK_COMMAND, this.command));
        this.setDataProperty(new StringEntityData(Entity.DATA_COMMAND_BLOCK_LAST_OUTPUT, this.lastOutput == null ? "" : this.lastOutput));
        this.setDataProperty(new IntEntityData(Entity.DATA_COMMAND_BLOCK_TICK_DELAY, 0));
        this.setDataProperty(new ByteEntityData(Entity.DATA_COMMAND_BLOCK_EXECUTE_ON_FIRST_TICK, 0));
    }

    /**
     * Called automatically by {@link EntityMinecartAbstract#entityBaseTick} when
     * the minecart sits on a powered activator rail. Mirrors JE's 4-tick activation
     * cooldown before running the command.
     */
    @Override
    public void activate(int x, int y, int z, boolean flag) {
        if (this.server.getTick() - this.lastActivated >= ACTIVATION_DELAY) {
            this.performCommand();
            this.lastActivated = this.server.getTick();
        }
    }

    /**
     * Runs the stored command once. Independent of chains and conditions (a minecart
     * command block has neither). Aligned with JE {@code BaseCommandBlock.performCommand}.
     */
    protected void performCommand() {
        if (!this.level.getGameRules().getBoolean(GameRule.COMMAND_BLOCKS_ENABLED)) {
            return;
        }
        long serverTick = this.level.getCurrentTick();
        if (this.lastExecution == serverTick) {
            // JE: a command block only executes once per game tick.
            return;
        }

        String cmd = this.command;
        this.successCount = 0;
        if (cmd != null && !cmd.trim().isEmpty()) {
            if ("Searge".equalsIgnoreCase(cmd)) {
                // JE easter egg
                this.lastOutput = "#itzlipofutzli";
                this.successCount = 1;
            } else {
                this.lastOutput = "";
                CommandBlockExecuteEvent event = new CommandBlockExecuteEvent(this.level.getBlock(this), cmd);
                this.server.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    this.lastExecution = serverTick;
                    return;
                }
                try {
                    // Reset before dispatch: sendCommandOutput (called inside
                    // dispatchCommand for ParamTree commands) may capture the
                    // precise number of affected targets.
                    this.receivedOutputSuccessCount = false;
                    boolean result = this.server.dispatchCommand(this, event.getCommand());
                    if (!this.receivedOutputSuccessCount) {
                        this.successCount = result ? 1 : 0;
                    }
                } catch (Exception e) {
                    this.successCount = 0;
                    this.server.getLogger().warning("Command block minecart error at " + this.getLocation(), e);
                }
            }
        }

        this.lastExecution = serverTick;
        this.setDataProperty(new StringEntityData(Entity.DATA_COMMAND_BLOCK_LAST_OUTPUT, this.lastOutput == null ? "" : this.lastOutput));
    }

    /**
     * Minecart command blocks never participate in chains, so this is a direct execution.
     */
    @Override
    public boolean execute(int chain) {
        this.performCommand();
        return true;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (player.isCreative() && this.level.getGameRules().getBoolean(GameRule.COMMAND_BLOCKS_ENABLED)) {
            player.addWindow(this.getInventory());
            return true;
        }
        return false;
    }

    @Override
    public boolean mountEntity(Entity entity, byte mode) {
        return false;
    }

    @Override
    public void dropItem() {
        if (this.lastDamageCause instanceof cn.nukkit.event.entity.EntityDamageByEntityEvent damageEvent) {
            Entity damager = damageEvent.getDamager();
            if (damager instanceof Player && ((Player) damager).isCreative()) {
                return;
            }
        }
        this.level.dropItem(this, Item.get(Item.COMMAND_BLOCK_MINECART));
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putString(ICommandBlock.TAG_COMMAND, this.command == null ? "" : this.command);
        this.namedTag.putInt(ICommandBlock.TAG_SUCCESS_COUNT, this.successCount);
        this.namedTag.putBoolean(ICommandBlock.TAG_TRACK_OUTPUT, this.trackOutput);
        this.namedTag.putString(ICommandBlock.TAG_LAST_OUTPUT, this.lastOutput == null ? "" : this.lastOutput);
        this.namedTag.putList(new ListTag<>(ICommandBlock.TAG_LAST_OUTPUT_PARAMS));
        this.namedTag.putLong(ICommandBlock.TAG_LAST_EXECUTION, this.lastExecution);
        this.namedTag.putInt(ICommandBlock.TAG_VERSION, ICommandBlock.CURRENT_VERSION);
        if (this.customName != null && !this.customName.isEmpty()) {
            this.namedTag.putString(ICommandBlock.TAG_CUSTOM_NAME, this.customName);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void close() {
        if (!this.closed) {
            if (this.inventory != null) {
                for (Player player : new HashSet<>(this.inventory.getViewers())) {
                    player.removeWindow(this.inventory);
                }
            }
            super.close();
        }
    }

    // ---- CommandSender implementation ----

    @Override
    public Server getServer() {
        return this.server;
    }

    @Override
    public String getName() {
        return this.customName == null || this.customName.isEmpty() ? "!" : this.customName;
    }

    @Override
    public boolean isOp() {
        return true;
    }

    @Override
    public void setOp(boolean value) {
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
    public void sendMessage(String message) {
        if (this.trackOutput) {
            this.lastOutput = message;
        }
        if (this.level != null && this.level.getGameRules().getBoolean(GameRule.COMMAND_BLOCK_OUTPUT)) {
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
        // Capture the precise success count reported by the command, matching
        // Bedrock success count semantics. See BlockEntityCommandBlock for details.
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
}
