package cn.nukkit.network.protocol;

import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class UseItemTriggerDecodeTest extends AbstractPacketRegressionTest {
    static Stream<Arguments> inputs() {
        return Stream.of(686, 712, 860, 975, 1001, 2168, 2193, 2225)
                .flatMap(protocol -> Arrays.stream(ItemUseTransaction.TriggerType.values())
                        .flatMap(trigger -> Stream.of(false, true)
                                .map(auth -> Arguments.of(protocol, trigger, auth))));
    }

    @ParameterizedTest(name = "v{0} {1}, auth={2}")
    @MethodSource("inputs")
    void referenceFramesPreservePresenceAndTrigger(int protocol, ItemUseTransaction.TriggerType trigger, boolean auth) {
        UseItemData use;
        if (auth) {
            var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket();
            cb.setRotation(Vector3f.ZERO);
            cb.setPosition(Vector3f.ZERO);
            cb.setMotion(Vector2f.ZERO);
            cb.setDelta(Vector3f.ZERO);
            cb.setAnalogMoveVector(Vector2f.ZERO);
            cb.setInteractRotation(Vector2f.ZERO);
            cb.setCameraOrientation(Vector3f.ZERO);
            cb.setRawMoveVector(Vector2f.ZERO);
            cb.setInputMode(InputMode.MOUSE);
            cb.setPlayMode(ClientPlayMode.NORMAL);
            cb.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
            cb.getInputData().add(PlayerAuthInputData.PERFORM_ITEM_INTERACTION);
            cb.setItemUseTransaction(itemUse(trigger));
            PlayerAuthInputPacket decoded = crossEncode(cb, PlayerAuthInputPacket::new, protocol);
            assertEquals(decoded.getCount(), decoded.getOffset());
            use = assertInstanceOf(UseItemData.class, decoded.getItemUseTransaction().transactionData);
        } else {
            ItemUseTransaction transaction = itemUse(trigger);
            var cb = new org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket();
            cb.setTransactionType(InventoryTransactionType.ITEM_USE);
            cb.setActionType(transaction.getActionType());
            cb.setTriggerType(trigger);
            cb.setBlockPosition(transaction.getBlockPosition());
            cb.setBlockFace(transaction.getBlockFace());
            cb.setHotbarSlot(transaction.getHotbarSlot());
            cb.setHand(HandSlot.MAINHAND);
            cb.setItemInHand(ItemData.AIR);
            cb.setPlayerPosition(Vector3f.ZERO);
            cb.setClickPosition(Vector3f.ZERO);
            cb.setBlockDefinition(transaction.getBlockDefinition());
            cb.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
            InventoryTransactionPacket decoded = crossEncode(cb, InventoryTransactionPacket::new, protocol);
            assertEquals(decoded.getCount(), decoded.getOffset());
            use = assertInstanceOf(UseItemData.class, decoded.transactionData);
        }
        assertEquals(protocol >= ProtocolInfo.v1_21_20, use.hasTriggerType);
        assertEquals(protocol >= ProtocolInfo.v1_21_20 ? trigger.ordinal() : UseItemData.TRIGGER_UNKNOWN, use.triggerType);
        assertEquals(InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR, use.actionType);
        assertEquals(3, use.hotbarSlot);
    }

    private static ItemUseTransaction itemUse(ItemUseTransaction.TriggerType trigger) {
        ItemUseTransaction use = new ItemUseTransaction();
        use.setActionType(InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR);
        use.setTriggerType(trigger);
        use.setBlockPosition(Vector3i.ZERO);
        use.setBlockFace(0);
        use.setHotbarSlot(3);
        use.setHand(HandSlot.MAINHAND);
        use.setItemInHand(ItemData.AIR);
        use.setPlayerPosition(Vector3f.ZERO);
        use.setClickPosition(Vector3f.ZERO);
        use.setBlockDefinition(new SimpleBlockDefinition("minecraft:air", 0, NbtMap.EMPTY));
        use.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
        return use;
    }
}
