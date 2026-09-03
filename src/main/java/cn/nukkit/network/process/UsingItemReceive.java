package cn.nukkit.network.process;

import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.types.AuthInputAction;

/**
 * Shared receive-side rules for {@code Player.setUsingItem}.
 * <p>
 * Official Bedrock / Java-via-ViaBedrock start hold-to-use from AuthInput
 * {@link AuthInputAction#START_USING_ITEM} (and, on older client-authoritative
 * movement, PlayerAction {@link PlayerActionPacket#ACTION_START_USING_ITEM}).
 * PlayerAction {@link PlayerActionPacket#ACTION_START_ITEM_USE_ON} /
 * {@link PlayerActionPacket#ACTION_STOP_ITEM_USE_ON} are block item-use-on
 * (client-auth mobile food+block interact) and must not start the hold; they
 * only skip the default {@code setUsingItem(false)} fallthrough.
 * MOT previously parsed those start bits and then ignored them, so eating/drawing
 * never set {@code DATA_FLAG_ACTION} and other players never saw the animation.
 * <p>
 * This helper is package-visible from {@code Player} and unit-tested without a live
 * session so the many cancel paths (equalsFast, second CLICK_AIR, sprint, default
 * PlayerAction fallthrough, same-slot MobEquipment) stay explicit.
 */
public final class UsingItemReceive {

    private UsingItemReceive() {
    }

    public static boolean isHoldToUseItem(Item item) {
        return item != null && item.getId() != 0 && item.canRelease();
    }

    public static boolean isClickAirUse(int transactionType, Object transactionData) {
        if (transactionType != InventoryTransactionPacket.TYPE_USE_ITEM) {
            return false;
        }
        if (!(transactionData instanceof UseItemData useItemData)) {
            return false;
        }
        return useItemData.actionType == InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR;
    }

    /**
     * A second CLICK_AIR while already using is treated as {@code onUse(ticksUsed)} and
     * always {@code setUsingItem(false)}. Keep that as the duration-ready finish, but
     * ignore an immediate duplicate start so Java USE_ITEM + USE_ITEM_ON cannot abort
     * auto-complete after 0 ticks.
     */
    public static boolean shouldIgnoreDuplicateClickAirStart(boolean alreadyUsing, boolean holdToUse,
                                                             int ticksUsed) {
        return alreadyUsing && holdToUse && ticksUsed < 2;
    }

    public static boolean shouldStartUsingFromClickAir(boolean spawnedAlive, boolean spectatorBlocked,
                                                       boolean alreadyUsing, boolean holdToUse,
                                                       boolean onClickAir) {
        return spawnedAlive && !spectatorBlocked && !alreadyUsing && holdToUse && onClickAir;
    }

    public static boolean authInputStartsUsingItem(PlayerAuthInputPacket packet) {
        return packet != null
                && packet.getInputData() != null
                && packet.getInputData().contains(AuthInputAction.START_USING_ITEM);
    }

    /**
     * AuthInput START_USING_ITEM is the official start bit. MOT used to parse and ignore
     * it. Start from the <em>server</em> held stack, not the optional attached item TX,
     * because NetEase 860 Java clients do not attach AuthInput item interaction for food/bow.
     */
    public static boolean shouldStartUsingFromAuthInput(boolean spawnedAlive, boolean spectatorBlocked,
                                                        boolean alreadyUsing, boolean holdToUse,
                                                        boolean startUsingItemFlag) {
        return spawnedAlive && !spectatorBlocked && !alreadyUsing && holdToUse && startUsingItemFlag;
    }

    /**
     * START_SPRINTING used to always {@code setUsingItem(false)}. Java eat/draw can
     * cancel sprint on the same tick Via emits StartUsingItem; if MOT still sees
     * both bits, keep using. A later START_SPRINTING while already eating still
     * cancels the hold, matching vanilla sprint-cancel-eat.
     */
    public static boolean shouldKeepUsingDespiteStartSprinting(boolean javaClient, boolean holdToUse,
                                                               boolean startUsingItemFlag) {
        return javaClient && startUsingItemFlag && holdToUse;
    }

    public static boolean isStartUsingPlayerAction(int action) {
        return action == PlayerActionPacket.ACTION_START_USING_ITEM;
    }

    /**
     * Block item-use-on (PlayerAction 28/29). Not a hold-to-use start; skip the
     * default {@code setUsingItem(false)} fallthrough only.
     */
    public static boolean isItemUseOnPlayerAction(int action) {
        return action == PlayerActionPacket.ACTION_START_ITEM_USE_ON
                || action == PlayerActionPacket.ACTION_STOP_ITEM_USE_ON;
    }

    /**
     * Unknown PlayerAction values used to fall through to {@code setUsingItem(false)}.
     * ViaProxy Java clients send START_USING_ITEM and START/STOP item-use-on as
     * compatibility packets that must not trigger that legacy fallthrough.
     */
    public static boolean shouldClearUsingOnUnhandledPlayerAction(boolean javaClient, int action) {
        return !javaClient || (!isStartUsingPlayerAction(action) && !isItemUseOnPlayerAction(action));
    }

    /**
     * MobEquipment always cleared using, including the same-slot confirmation Java sends
     * before CLICK_AIR. Only a real hotbar switch is an interrupt for ViaProxy Java.
     * Native Bedrock retains the old unconditional clear behavior.
     * <p>
     * A Java hotbar change is already an interrupt in {@code shouldClearUsingOnMobEquipment}.
     * Duplicate Java CLICK_AIR on the first two ticks is handled by
     * {@code shouldIgnoreDuplicateClickAirStart}. Early Java RELEASE_USE_ITEM is a
     * real interrupt and must not be retained here.
     */
    public static boolean shouldClearUsingOnMobEquipment(boolean javaClient, boolean alreadyUsing,
                                                         int currentHeldIndex, int packetHotbarSlot) {
        return alreadyUsing && (!javaClient || currentHeldIndex != packetHotbarSlot);
    }

    /**
     * ViaProxy Java emits CLICK_BLOCK while the client-side use animation is active.
     * Native Bedrock must continue through the normal Level.useItemOn path. A changed
     * hotbar slot always ends the old use session before the block action is handled.
     */
    public static boolean shouldKeepUsingOnClickBlock(boolean javaClient, boolean alreadyUsing,
                                                       boolean holdToUse, boolean heldSlotChanged) {
        return javaClient && alreadyUsing && holdToUse && !heldSlotChanged;
    }
}
