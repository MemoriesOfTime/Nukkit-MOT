package cn.nukkit.network.process;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.AuthInputAction;
import cn.nukkit.network.protocol.types.AuthInteractionModel;
import cn.nukkit.network.protocol.types.ClientPlayMode;
import cn.nukkit.network.protocol.types.InputMode;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MOT previously parsed AuthInput START_USING_ITEM / PlayerAction 37 and then
 * ignored them (default {@code setUsingItem(false)}). Java clients animate locally,
 * so other players never saw eat/draw. PlayerAction 28/29 skip that clear but do
 * not start the hold. These cases lock the receive-side rules.
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
    void startUsingSharedGate() {
        assertTrue(UsingItemReceive.shouldStartUsing(true, false, false, true));
        assertFalse(UsingItemReceive.shouldStartUsing(false, false, false, true));
        assertFalse(UsingItemReceive.shouldStartUsing(true, true, false, true));
        assertFalse(UsingItemReceive.shouldStartUsing(true, false, true, true));
        assertFalse(UsingItemReceive.shouldStartUsing(true, false, false, false));
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
    void keepUsingOnlyForJavaSprintAndStartUsingItem() {
        assertTrue(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(true, true, true));
        assertFalse(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(true, true, false));
        assertFalse(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(false, true, true));
        assertFalse(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(true, false, true));
    }

    @Test
    void playerActionStartUsingDoesNotFallThroughToClear() {
        assertFalse(UsingItemReceive.isStartUsingPlayerAction(PlayerActionPacket.ACTION_START_ITEM_USE_ON));
        assertTrue(UsingItemReceive.isStartUsingPlayerAction(PlayerActionPacket.ACTION_START_USING_ITEM));
        assertEquals(37, PlayerActionPacket.ACTION_START_USING_ITEM);
        assertTrue(UsingItemReceive.isItemUseOnPlayerAction(PlayerActionPacket.ACTION_START_ITEM_USE_ON));
        assertTrue(UsingItemReceive.isItemUseOnPlayerAction(PlayerActionPacket.ACTION_STOP_ITEM_USE_ON));
        assertFalse(UsingItemReceive.isItemUseOnPlayerAction(PlayerActionPacket.ACTION_START_USING_ITEM));
        assertFalse(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(true, PlayerActionPacket.ACTION_START_ITEM_USE_ON));
        assertFalse(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(true, PlayerActionPacket.ACTION_STOP_ITEM_USE_ON));
        assertFalse(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(true, PlayerActionPacket.ACTION_START_USING_ITEM));
        assertTrue(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(true, PlayerActionPacket.ACTION_ABORT_BREAK));
        assertTrue(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(false, PlayerActionPacket.ACTION_START_ITEM_USE_ON));
        assertTrue(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(false, PlayerActionPacket.ACTION_START_USING_ITEM));
        assertTrue(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(false, 99));
    }

    @Test
    void sameSlotMobEquipmentKeepsUsingOnlyForJavaClient() {
        assertFalse(UsingItemReceive.shouldClearUsingOnMobEquipment(true, true, 0, 0));
        assertTrue(UsingItemReceive.shouldClearUsingOnMobEquipment(true, true, 0, 1));
        assertTrue(UsingItemReceive.shouldClearUsingOnMobEquipment(false, true, 0, 0));
        assertFalse(UsingItemReceive.shouldClearUsingOnMobEquipment(true, false, 0, 1));
    }

    @Test
    void keepUsingOnClickBlockOnlyForJavaClient() {
        assertTrue(UsingItemReceive.shouldKeepUsingOnClickBlock(true, true, true, false));
        assertFalse(UsingItemReceive.shouldKeepUsingOnClickBlock(false, true, true, false));
        assertFalse(UsingItemReceive.shouldKeepUsingOnClickBlock(true, false, true, false));
        assertFalse(UsingItemReceive.shouldKeepUsingOnClickBlock(true, true, false, false));
        assertFalse(UsingItemReceive.shouldKeepUsingOnClickBlock(true, true, true, true));
    }

    @Test
    void predictedFullConsumptionOnlyForSingleInstantUseItem() {
        assertTrue(UsingItemReceive.isPredictedFullConsumption(Item.get(ItemID.SNOWBALL), Item.get(Item.AIR)));
        assertFalse(UsingItemReceive.isPredictedFullConsumption(Item.get(ItemID.SNOWBALL, 0, 2), Item.get(Item.AIR)));
        assertFalse(UsingItemReceive.isPredictedFullConsumption(Item.get(ItemID.SNOWBALL), Item.get(ItemID.SNOWBALL)));
        assertFalse(UsingItemReceive.isPredictedFullConsumption(Item.get(ItemID.APPLE), Item.get(Item.AIR)));
        assertFalse(UsingItemReceive.isPredictedFullConsumption(null, Item.get(Item.AIR)));
        assertFalse(UsingItemReceive.isPredictedFullConsumption(Item.get(ItemID.SNOWBALL), null));
    }

    @Test
    void clickAirAcceptsMatchingOrPredictedFullConsumption() {
        Item snowball = Item.get(ItemID.SNOWBALL);
        assertTrue(UsingItemReceive.shouldUseClickAirItem(snowball, Item.get(ItemID.SNOWBALL, 0, 2)));
        assertTrue(UsingItemReceive.shouldUseClickAirItem(snowball, Item.get(Item.AIR)));
        assertFalse(UsingItemReceive.shouldUseClickAirItem(Item.get(ItemID.SNOWBALL, 0, 2), Item.get(Item.AIR)));
        assertFalse(UsingItemReceive.shouldUseClickAirItem(snowball, Item.get(ItemID.EGG)));
        assertFalse(UsingItemReceive.shouldUseClickAirItem(snowball, null));
        assertFalse(UsingItemReceive.shouldUseClickAirItem(null, snowball));
    }


    @Test
    void netease860AuthInputStartUsingItemDecodesAndWouldSetUsingItem() {
        PlayerAuthInputPacket packet = decodeNetEase860StartUsingItem();
        boolean decodedStart = UsingItemReceive.authInputStartsUsingItem(packet);

        assertTrue(decodedStart, "NetEase 860 START_USING_ITEM wire bit must decode");
        assertTrue(UsingItemReceive.shouldStartUsingFromAuthInput(true, false, false, true, decodedStart));
        assertTrue(UsingItemReceive.shouldKeepUsingDespiteStartSprinting(true, true, decodedStart));
        assertFalse(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(
                true, PlayerActionPacket.ACTION_START_USING_ITEM));
        assertFalse(UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(
                true, PlayerActionPacket.ACTION_START_ITEM_USE_ON));
        assertEquals(ProtocolInfo.v1_21_124, packet.protocol);
        assertEquals(GameVersion.V1_21_124_NETEASE, packet.gameVersion);
    }

    /**
     * Encode a NetEase 860 PlayerAuthInput payload with START_USING_ITEM on the
     * extra-flag-shifted wire bit (ordinal 53 + 1 extra flag for protocol 860).
     */
    private static PlayerAuthInputPacket decodeNetEase860StartUsingItem() {
        int extraFlags = 2; // protocol 860 is >= 819, NetEase inserts two hidden flags
        int startUsingOrdinal = AuthInputAction.START_USING_ITEM.ordinal();
        long wireBit = 1L << (startUsingOrdinal + extraFlags);
        BinaryStream stream = new BinaryStream();
        stream.putLFloat(10.5f);
        stream.putLFloat(20.5f);
        stream.putVector3f(1.25f, 64.0f, -3.5f);
        stream.putLFloat(0.25f);
        stream.putLFloat(-0.5f);
        stream.putLFloat(30.5f);
        stream.putUnsignedVarLong(wireBit);
        stream.putUnsignedVarInt(InputMode.TOUCH.ordinal());
        stream.putUnsignedVarInt(ClientPlayMode.NORMAL.ordinal());
        stream.putUnsignedVarInt(AuthInteractionModel.CROSSHAIR.ordinal());
        stream.putVector2f(5.0f, 6.0f);
        stream.putUnsignedVarLong(123L);
        stream.putVector3f(0.1f, 0.2f, 0.3f);
        stream.putBoolean(false); // cameraDeparted
        stream.putVector2f(0.6f, -0.4f);
        stream.putVector3f(0.0f, 1.0f, 0.0f);
        stream.putVector2f(-0.25f, 0.75f);
        // fixed NetEase tail since 766
        stream.putBoolean(false);
        stream.putLFloat(0.5f);
        stream.putLFloat(-0.5f);
        stream.putBoolean(false);
        stream.putBoolean(true);
        stream.putByte(0);

        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.protocol = ProtocolInfo.v1_21_124;
        packet.gameVersion = GameVersion.V1_21_124_NETEASE;
        packet.setBuffer(stream.getBuffer());
        packet.decode();
        return packet;
    }
}
