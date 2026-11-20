package cn.nukkit;

import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerInteractEvent.Action;
import cn.nukkit.inventory.transaction.data.ReleaseItemData;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.ItemCrossbow;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

/**
 * 区分弩的新操作与持续输入，保证一个装填会话只完成一次。
 * <p>
 * Separates fresh crossbow input from continuation, completing each charge session once.
 */
final class CrossbowInput {
    enum Source { INVENTORY_TRANSACTION, AUTH_INPUT }

    private final Player player;
    private boolean handling;
    private boolean charging;
    private int startedAtTick;
    private int slot;
    private long revision;
    private ItemCrossbow chargingItem;
    private Level chargingLevel;
    private int chargingGamemode;
    private long generation;
    private long legacyBlockedRevision = -1;
    private Source source;

    CrossbowInput(Player player) {
        this.player = player;
    }

    boolean start(ItemCrossbow crossbow) {
        if (crossbow.isLoaded()) legacyBlockedRevision = -1;
        return handle(crossbow, true, true, player.getDirection());
    }

    void release(ReleaseItemData data) {
        var inventory = player.getInventory();
        try {
            if (inventory.getHeldItemIndex() == data.hotbarSlot
                    && inventory.getItemInHand() instanceof ItemCrossbow crossbow
                    && data.itemInHand != null && crossbow.equalsFast(data.itemInHand)) {
                if (charging) handle(crossbow, false, false, player.getDirection());
                legacyBlockedRevision = -1;
            }
        } finally {
            player.setUsingItem(false);
        }
    }

    void click(UseItemData data) {
        click(data, Source.INVENTORY_TRANSACTION);
    }

    void click(UseItemData data, Source inputSource) {
        if (source != null && source != inputSource) return;
        var inventory = player.getInventory();
        if (data.hand != 0 || (data.hasTriggerType && (data.triggerType < UseItemData.TRIGGER_UNKNOWN
                || data.triggerType > UseItemData.TRIGGER_SIMULATION_TICK))) {
            return;
        }
        if (inventory.getHeldItemIndex() != data.hotbarSlot
                || !(inventory.getItemInHand() instanceof ItemCrossbow crossbow)
                || data.itemInHand == null || !crossbow.equalsFast(data.itemInHand)) {
            inventory.sendHeldItem(player);
            return;
        }
        boolean continuation = data.hasTriggerType
                && data.triggerType == UseItemData.TRIGGER_SIMULATION_TICK;
        if (charging && data.hasTriggerType && data.triggerType == UseItemData.TRIGGER_PLAYER_INPUT) {
            return;
        }
        boolean loaded = crossbow.isLoaded();
        // 旧协议沿用点击完成装填后再次点击发射的语义；只有显式 UNKNOWN 需要操作边界。
        // Legacy clients fire on a later click; only an explicit UNKNOWN requires an input boundary.
        boolean ambiguous = data.hasTriggerType && data.triggerType == UseItemData.TRIGGER_UNKNOWN;
        if (loaded && ambiguous && legacyBlockedRevision == inventory.getHeldItemRevision()) return;
        boolean freshClick = !continuation && (!charging || !data.hasTriggerType);
        if (handle(crossbow, freshClick, false, data.face)) {
            // 两种传输没有共享操作 ID；连接内固定来源，避免跨入口重复执行。
            // The transports share no operation ID; pin the source for this connection.
            source = inputSource;
            if (!loaded && crossbow.isLoaded()) legacyBlockedRevision = inventory.getHeldItemRevision();
        }
    }

    void reset() {
        generation++;
        charging = false;
        chargingItem = null;
        chargingLevel = null;
    }

    boolean isCharging() {
        return charging;
    }

    long generation() {
        return generation;
    }

    private boolean handle(ItemCrossbow crossbow, boolean freshClick, boolean startOnly, BlockFace face) {
        if (handling || !player.isConnected() || !player.spawned || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        var inventory = player.getInventory();
        if (charging && (slot != inventory.getHeldItemIndex()
                || revision != inventory.getHeldItemRevision()
                || chargingLevel != player.getLevel() || chargingGamemode != player.getGamemode()
                || !chargingItem.equalsExact(crossbow))) {
            player.setUsingItem(false);
            return false;
        }
        if (crossbow.isLoaded() && (!freshClick || startOnly)) {
            return false;
        }
        if (!crossbow.isLoaded() && !charging && !freshClick) {
            return false;
        }
        if (charging && startOnly) {
            return false;
        }
        if (charging && player.getServer().getTick() - startedAtTick < crossbow.getChargeDuration()) {
            return false;
        }
        if (freshClick && !player.tryAcceptClickAir()) {
            return false;
        }
        handling = true;
        try {
            int inputSlot = inventory.getHeldItemIndex();
            long inputRevision = inventory.getHeldItemRevision();
            long inputGeneration = generation;
            Level inputLevel = player.getLevel();
            int inputGamemode = player.getGamemode();
            if (freshClick) {
                PlayerInteractEvent event = new PlayerInteractEvent(player, crossbow,
                        player.getDirectionVector(), face, Action.RIGHT_CLICK_AIR);
                player.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled() || !isCurrentInput(crossbow, inputSlot, inputRevision,
                        inputGeneration, inputLevel, inputGamemode)) {
                    player.setUsingItem(false);
                    inventory.sendHeldItem(player);
                    return false;
                }
            }
            if (crossbow.isLoaded()) {
                crossbow.onClickAir(player, player.getDirectionVector());
            } else if (!charging) {
                if (!crossbow.onClickAir(player, player.getDirectionVector())
                        || !isCurrentInput(crossbow, inputSlot, inputRevision,
                        inputGeneration, inputLevel, inputGamemode)) {
                    player.setUsingItem(false);
                    inventory.sendHeldItem(player);
                    return false;
                }
                startedAtTick = player.getServer().getTick();
                slot = inputSlot;
                revision = inputRevision;
                chargingItem = (ItemCrossbow) crossbow.clone();
                chargingLevel = inputLevel;
                chargingGamemode = inputGamemode;
                charging = true;
                player.setUsingItem(true);
                player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_CROSSBOW_LOADING_START);
            } else {
                int ticksUsed = player.getServer().getTick() - startedAtTick;
                player.setUsingItem(false);
                if (isCurrentInput(crossbow, inputSlot, inputRevision,
                        inputGeneration + 1, inputLevel, inputGamemode)) {
                    crossbow.onUse(player, ticksUsed);
                }
            }
            return true;
        } finally {
            handling = false;
        }
    }

    private boolean isCurrentInput(ItemCrossbow crossbow, int inputSlot, long inputRevision,
                                   long inputGeneration, Level inputLevel, int inputGamemode) {
        var inventory = player.getInventory();
        return player.isConnected() && player.spawned && player.isAlive()
                && inputSlot == inventory.getHeldItemIndex() && inputRevision == inventory.getHeldItemRevision()
                && inputGeneration == generation && inputLevel == player.getLevel()
                && inputGamemode == player.getGamemode() && crossbow.equalsExact(inventory.getItemInHand());
    }
}
