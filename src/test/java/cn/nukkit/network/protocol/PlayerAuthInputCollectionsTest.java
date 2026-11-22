package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.AuthInputAction;
import cn.nukkit.network.protocol.types.PlayerActionType;
import cn.nukkit.network.protocol.types.PlayerBlockActionData;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAuthInputCollectionsTest extends AbstractPacketRegressionTest {

    private static Stream<Arguments> supportedVersions() {
        return filteredVersionsRange(ProtocolInfo.v1_20_0, ProtocolInfo.v1_26_60 + 1);
    }

    @ParameterizedTest(name = "v{0}")
    @MethodSource("supportedVersions")
    void ordinaryMovementLeavesTheLegacyActionMapEmptyAndMutable(int protocol) {
        var reference = referencePacket();
        reference.getInputData().add(PlayerAuthInputData.UP);
        PlayerAuthInputPacket packet = crossEncode(reference, PlayerAuthInputPacket::new, protocol);
        assertEquals(packet.getCount(), packet.getOffset());
        assertEquals(123L, packet.getTick());
        assertTrue(packet.getInputData().contains(AuthInputAction.UP));
        assertTrue(packet.getBlockActionData().isEmpty());
        assertTrue(packet.getDecodedBlockActions().isEmpty());

        var action = new PlayerBlockActionData(PlayerActionType.START_DESTROY_BLOCK, null, -1);
        assertNull(packet.getBlockActionData().put(action.getAction(), action));
        assertSame(action, packet.getBlockActionData().get(action.getAction()));
        assertTrue(packet.getDecodedBlockActions().isEmpty());
    }

    @Test
    void shallowCloneBeforeFirstDecodeSharesReplayState() {
        var original = new PlayerAuthInputPacket();
        var clone = (PlayerAuthInputPacket) original.clone();
        var reference = referencePacket();
        reference.getInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
        reference.getPlayerActions().add(referenceAction(1));
        reference.getPlayerActions().add(referenceAction(2));

        crossEncode(reference, () -> clone, ProtocolInfo.v1_26_60);
        assertEquals(clone.getCount(), clone.getOffset());
        assertSame(original.getBlockActionData(), clone.getBlockActionData());
        assertEquals(List.of(1, 2), original.getDecodedBlockActions().stream()
                .map(action -> action.getPosition().getX()).toList());
        assertEquals(original.getDecodedBlockActions(), clone.getDecodedBlockActions());

        crossEncode(referencePacket(), () -> original, ProtocolInfo.v1_26_60);
        assertEquals(original.getCount(), original.getOffset());
        assertTrue(clone.getBlockActionData().isEmpty());
        assertTrue(clone.getDecodedBlockActions().isEmpty());
    }

    private static org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket referencePacket() {
        var packet = new org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket();
        packet.setRotation(Vector3f.ZERO);
        packet.setPosition(Vector3f.from(1.25f, 64, -3.5f));
        packet.setMotion(Vector2f.ZERO);
        packet.setInputMode(InputMode.MOUSE);
        packet.setPlayMode(ClientPlayMode.NORMAL);
        packet.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
        packet.setTick(123L);
        packet.setDelta(Vector3f.ZERO);
        packet.setAnalogMoveVector(Vector2f.ZERO);
        packet.setInteractRotation(Vector2f.ZERO);
        packet.setCameraOrientation(Vector3f.ZERO);
        packet.setRawMoveVector(Vector2f.ZERO);
        return packet;
    }

    private static org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData referenceAction(int x) {
        var action = new org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData();
        action.setAction(org.cloudburstmc.protocol.bedrock.data.PlayerActionType.BLOCK_PREDICT_DESTROY);
        action.setBlockPosition(Vector3i.from(x, 64, 0));
        action.setFace(1);
        return action;
    }
}
