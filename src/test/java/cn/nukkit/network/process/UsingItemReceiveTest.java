package cn.nukkit.network.process;

import cn.nukkit.MockServer;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.types.AuthInputAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MOT previously parsed AuthInput START_USING_ITEM / PlayerAction 28/29/37 and then
 * ignored them (default {@code setUsingItem(false)}). Java clients animate locally,
 * so other players never saw eat/draw. These cases lock the receive-side rules.
 */
class UsingItemReceiveTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void appleAndBowAreHoldToUse() {
        assertTrue(UsingItemReceive.isHoldToUseItem(Item.get(ItemID.APPLE)));
        assertTrue(UsingItemReceive.isHoldToUseItem(Item.get(ItemID.BOW)));
        assertTrue(UsingItemReceive.isHoldToUseItem(Item.get(ItemID.POTION)));
        assertFalse(UsingItemReceive.isHoldToUseItem(Item.get(ItemID.STICK)));
        assertFalse(UsingItemReceive.isHoldToUseItem(Item.get(0)));
        assertFalse(UsingItemReceive.isHoldToUseItem(null));
    }

    @Test
    void clickAirTypeDetection() {
        UseItemData data = new UseItemData();
        data.actionType = InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR;
        assertTrue(UsingItemReceive.isClickAirUse(InventoryTransactionPacket.TYPE_USE_ITEM, data));
        data.actionType = InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_BLOCK;
        assertFalse(UsingItemReceive.isClickAirUse(InventoryTransactionPacket.TYPE_USE_ITEM, data));
        assertFalse(UsingItemReceive.isClickAirUse(InventoryTransactionPacket.TYPE_RELEASE_ITEM, data));
    }

    @Test
    void rewriteClickAirWhenEqualsFastWouldFail() {
        Item apple = Item.get(ItemID.APPLE);
        Item mismatch = Item.get(ItemID.APPLE);
        mismatch.setDamage(1);
        assertTrue(UsingItemReceive.shouldRewriteClickAirHeldItem(mismatch, apple));
        assertTrue(UsingItemReceive.shouldRewriteClickAirHeldItem(null, apple));
        assertFalse(UsingItemReceive.shouldRewriteClickAirHeldItem(apple.clone(), apple));
        assertFalse(UsingItemReceive.shouldRewriteClickAirHeldItem(Item.get(ItemID.STICK), Item.get(ItemID.STICK)));
    }

    @Test
    void ignoreOnlyImmediateDuplicateClickAir() {
        assertTrue(UsingItemReceive.shouldIgnoreDuplicateClickAirStart(true, true, 0));
        assertTrue(UsingItemReceive.shouldIgnoreDuplicateClickAirStart(true, true, 1));
        assertFalse(UsingItemReceive.shouldIgnoreDuplicateClickAirStart(true, true, 32));
        assertFalse(UsingItemReceive.shouldIgnoreDuplicateClickAirStart(false, true, 0));
        assertFalse(UsingItemReceive.shouldIgnoreDuplicateClickAirStart(true, false, 0));
    }

    @Test
    void authInputStartUsingItemFlag() {
        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.setInputData(EnumSet.of(AuthInputAction.START_USING_ITEM));
        assertTrue(UsingItemReceive.authInputStartsUsingItem(packet));
        packet.setInputData(EnumSet.of(AuthInputAction.PERFORM_ITEM_INTERACTION));
        assertFalse(UsingItemReceive.authInputStartsUsingItem(packet));
        assertFalse(UsingItemReceive.authInputStartsUsingItem(null));
    }

    @Test
    void startFromAuthInputUsesServerHeldConsumable() {
        assertTrue(UsingItemReceive.shouldStartUsingFromAuthInput(true, false, false, true, true));
        assertFalse(UsingItemReceive.shouldStartUsingFromAuthInput(true, false, true, true, true));
        assertFalse(UsingItemReceive.shouldStartUsingFromAuthInput(true, true, false, true, true));
        assertFalse(UsingItemReceive.shouldStartUsingFromAuthInput(false, false, false, true, true));
        assertFalse(UsingItemReceive.shouldStartUsingFromAuthInput(true, false, false, false, true));
        assertFalse(UsingItemReceive.shouldStartUsingFromAuthInput(true, false, false, true, false));
    }

    @Test
    void keepUsingWhenSprintArrivesWithStartUsingItem() {
        assertTrue(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(false, true, true));
        assertTrue(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(true, true, false));
        assertFalse(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(false, false, true));
        assertFalse(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(false, true, false));
    }

    @Test
    void playerActionStartUsingDoesNotFallThroughToClear() {
        assertTrue(UsingItemReceive.isStartUsingPlayerAction(PlayerActionPacket.ACTION_START_ITEM_USE_ON));
        assertTrue(UsingItemReceive.isStartUsingPlayerAction(PlayerActionPacket.ACTION_START_USING_ITEM));
        assertEquals(37, PlayerActionPacket.ACTION_START_USING_ITEM);
        assertTrue(UsingItemReceive.isStopUsingPlayerAction(PlayerActionPacket.ACTION_STOP_ITEM_USE_ON));
        assertFalse(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(PlayerActionPacket.ACTION_START_ITEM_USE_ON));
        assertFalse(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(PlayerActionPacket.ACTION_STOP_ITEM_USE_ON));
        assertFalse(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(PlayerActionPacket.ACTION_START_USING_ITEM));
        assertTrue(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(PlayerActionPacket.ACTION_ABORT_BREAK));
        assertTrue(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(99));
    }

    @Test
    void sameSlotMobEquipmentKeepsUsing() {
        assertFalse(UsingItemReceive.shouldClearUsingOnMobEquipment(true, 0, 0));
        assertTrue(UsingItemReceive.shouldClearUsingOnMobEquipment(true, 0, 1));
        assertFalse(UsingItemReceive.shouldClearUsingOnMobEquipment(false, 0, 1));
    }

    @Test
    void keepUsingWhenClickBlockArrivesDuringHold() {
        assertTrue(UsingItemReceive.shouldKeepUsingOnClickBlock(true, true));
        assertFalse(UsingItemReceive.shouldKeepUsingOnClickBlock(false, true));
        assertFalse(UsingItemReceive.shouldKeepUsingOnClickBlock(true, false));
    }

    @Test
    void keepUsingOnReleaseOnlyForTheFirstTicks() {
        assertTrue(UsingItemReceive.shouldKeepUsingOnEarlyRelease(true, true, 0));
        assertTrue(UsingItemReceive.shouldKeepUsingOnEarlyRelease(true, true, 1));
        assertFalse(UsingItemReceive.shouldKeepUsingOnEarlyRelease(true, true, 2));
        assertFalse(UsingItemReceive.shouldKeepUsingOnEarlyRelease(true, true, 31));
        assertFalse(UsingItemReceive.shouldKeepUsingOnEarlyRelease(false, true, 0));
        assertFalse(UsingItemReceive.shouldKeepUsingOnEarlyRelease(true, false, 0));
    }

    @Test
    void netease860AuthInputStartUsingItemDecodesAndWouldSetUsingItem() {
        assertTrue(UsingItemReceiveProbe.run().startsWith("ok "));
    }
}
