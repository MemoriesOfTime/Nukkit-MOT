package cn.nukkit;

import cn.nukkit.block.BlockStone;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.PlayerActionType;
import cn.nukkit.network.protocol.types.PlayerBlockActionData;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.cloudburstmc.protocol.bedrock.data.PlayerActionType.BLOCK_PREDICT_DESTROY;
import static org.cloudburstmc.protocol.bedrock.data.PlayerActionType.START_BREAK;

/** Exercises reference-encoded input through the real player handler and its authoritative break path. */
class PlayerAuthInputBlockActionsTest extends AbstractPacketRegressionTest {

    private static Stream<Arguments> rejectedBlocks() {
        return Stream.of(589, 844, 2168, 2169)
                .flatMap(protocol -> Stream.of(Arguments.of(protocol, 1), Arguments.of(protocol, 2)));
    }

    @ParameterizedTest(name = "v{0}, rejected block {1}")
    @MethodSource("rejectedBlocks")
    void everyPredictedBreakIsJudgedAndOnlyRejectedBlockIsRestored(int protocol, int rejectedX) {
        Harness harness = new Harness(protocol, rejectedX);
        PlayerAuthInputPacket packet = input(protocol,
                action(BLOCK_PREDICT_DESTROY, 1), action(BLOCK_PREDICT_DESTROY, 2));

        harness.player.handleDataPacket(packet);

        assertEquals(List.of(1, 2), harness.breaks,
                "Both predicted removals must reach the authoritative break check, including repeated action types");
        assertEquals(List.of(rejectedX), harness.restored,
                "A rejected prediction must resend that block without restoring the accepted neighbor");
    }

    @ParameterizedTest(name = "v{0}")
    @ValueSource(ints = {589, 844, 2168, 2169})
    void interleavedStartsStayBetweenTheBreaksTheySeparate(int protocol) {
        Harness harness = new Harness(protocol, -1);
        PlayerAuthInputPacket packet = input(protocol,
                action(BLOCK_PREDICT_DESTROY, 1), action(START_BREAK, 2),
                action(BLOCK_PREDICT_DESTROY, 2), action(START_BREAK, 3));

        harness.player.handleDataPacket(packet);

        assertEquals(List.of("break:1", "start:2", "break:2", "start:3"), harness.trace,
                "Grouping all completions first must not move the second completion ahead of its own start");
        assertEquals(List.of(), harness.restored);
    }

    @ParameterizedTest(name = "legacy map v{0}")
    @ValueSource(ints = {589, 844, 2168, 2169})
    void manuallyPopulatedLegacyMapStillCompletesBeforeStartingNextBlock(int protocol) {
        Harness harness = new Harness(protocol, 1);
        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.setPosition(new cn.nukkit.math.Vector3f(0, 65.62f, 0));
        packet.getBlockActionData().put(PlayerActionType.START_DESTROY_BLOCK,
                new PlayerBlockActionData(PlayerActionType.START_DESTROY_BLOCK, new BlockVector3(2, 64, 0), 1));
        packet.getBlockActionData().put(PlayerActionType.PREDICT_DESTROY_BLOCK,
                new PlayerBlockActionData(PlayerActionType.PREDICT_DESTROY_BLOCK, new BlockVector3(1, 64, 0), 1));

        harness.player.handleDataPacket(packet);

        assertEquals(List.of("break:1", "start:2"), harness.trace);
        assertEquals(List.of(1), harness.restored);
    }

    @ParameterizedTest(name = "decoded snapshot v{0}")
    @ValueSource(ints = {589, 844, 2168, 2169})
    void decodedSequenceIsAReadOnlySnapshotWithoutMutablePacketAliases(int protocol) {
        Harness harness = new Harness(protocol, -1);
        PlayerAuthInputPacket packet = input(protocol, action(BLOCK_PREDICT_DESTROY, 1),
                action(BLOCK_PREDICT_DESTROY, 2), action(START_BREAK, 3));
        List<PlayerBlockActionData> snapshot = packet.getDecodedBlockActions();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(snapshot.size() - 1));
        snapshot.get(0).getPosition().setComponents(4, 64, 0);
        snapshot.get(1).setPosition(new BlockVector3(5, 64, 0));
        snapshot.get(2).setAction(PlayerActionType.PREDICT_DESTROY_BLOCK);

        assertEquals(List.of(1, 2, 3), packet.getDecodedBlockActions().stream()
                .map(action -> action.getPosition().getX()).toList());
        assertEquals(2, packet.getBlockActionData().get(PlayerActionType.PREDICT_DESTROY_BLOCK)
                .getPosition().getX(), "snapshot mutations must not alias the legacy map");
        harness.player.handleDataPacket(packet);
        assertEquals(List.of("break:1", "break:2", "start:3"), harness.trace,
                "snapshot mutations must not change replay or trigger lossy legacy-map fallback");
    }

    private static Stream<Arguments> legacyMapEdits() {
        return Stream.of(589, 844, 2168, 2169)
                .flatMap(protocol -> Stream.of(LegacyMapEdit.values()).map(edit -> Arguments.of(protocol, edit)));
    }

    @ParameterizedTest(name = "legacy listener v{0}: {1}")
    @MethodSource("legacyMapEdits")
    void receiveListenersCanStillChangeDecodedActionsThroughLegacyMap(int protocol, LegacyMapEdit edit) {
        Harness harness = new Harness(protocol, 4);
        PlayerAuthInputPacket packet = input(protocol, action(BLOCK_PREDICT_DESTROY, 1),
                action(BLOCK_PREDICT_DESTROY, 2), action(START_BREAK, 3));
        harness.packetListener = received -> {
            PlayerBlockActionData replacement = new PlayerBlockActionData(
                    PlayerActionType.PREDICT_DESTROY_BLOCK, new BlockVector3(4, 64, 0), 1);
            switch (edit) {
                case CLEAR -> received.getBlockActionData().clear();
                case REMOVE -> received.getBlockActionData().remove(PlayerActionType.PREDICT_DESTROY_BLOCK);
                case PUT -> received.getBlockActionData().put(PlayerActionType.PREDICT_DESTROY_BLOCK, replacement);
                case SET_MAP -> {
                    var replacementMap = new EnumMap<PlayerActionType, PlayerBlockActionData>(PlayerActionType.class);
                    replacementMap.put(PlayerActionType.PREDICT_DESTROY_BLOCK, replacement);
                    received.setBlockActionData(replacementMap);
                }
                case SET_POSITION -> received.getBlockActionData().get(PlayerActionType.PREDICT_DESTROY_BLOCK)
                        .setPosition(new BlockVector3(4, 64, 0));
                case MUTATE_POSITION -> received.getBlockActionData().get(PlayerActionType.PREDICT_DESTROY_BLOCK)
                        .getPosition().setComponents(4, 64, 0);
            }
        };

        harness.player.handleDataPacket(packet);

        List<String> expectedTrace = switch (edit) {
            case CLEAR -> List.of();
            case REMOVE -> List.of("start:3");
            case SET_MAP -> List.of("break:4");
            default -> List.of("break:4", "start:3");
        };
        List<Integer> expectedBreaks = edit == LegacyMapEdit.CLEAR || edit == LegacyMapEdit.REMOVE
                ? List.of() : List.of(4);
        assertEquals(expectedTrace, harness.trace,
                "An event listener's map edit must override the decoded actions before execution");
        assertEquals(expectedBreaks, harness.breaks, "Removed or replaced predictions must never execute");
        assertEquals(expectedBreaks, harness.restored, "The replacement still uses the authoritative rejection path");
    }

    private enum LegacyMapEdit {
        CLEAR, REMOVE, PUT, SET_MAP, SET_POSITION, MUTATE_POSITION
    }

    private PlayerAuthInputPacket input(int protocol,
            org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData... actions) {
        var packet = new org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket();
        packet.setRotation(Vector3f.ZERO);
        packet.setPosition(Vector3f.from(0, 65.62f, 0));
        packet.setMotion(Vector2f.ZERO);
        packet.setInputMode(InputMode.MOUSE);
        packet.setPlayMode(ClientPlayMode.NORMAL);
        packet.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
        packet.setTick(100);
        packet.setDelta(Vector3f.ZERO);
        packet.setAnalogMoveVector(Vector2f.ZERO);
        packet.setInteractRotation(Vector2f.ZERO);
        packet.setCameraOrientation(Vector3f.ZERO);
        packet.setRawMoveVector(Vector2f.ZERO);
        packet.getInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
        packet.getPlayerActions().addAll(List.of(actions));

        // Cloudburst currently exposes 2168, not 2169; their auth-input wire shape is shared.
        PlayerAuthInputPacket decoded = crossEncode(packet, PlayerAuthInputPacket::new,
                protocol == 2169 ? 2168 : protocol);
        if (protocol == 2169) {
            PlayerAuthInputPacket latest = new PlayerAuthInputPacket();
            latest.protocol = protocol;
            latest.gameVersion = GameVersion.byProtocol(protocol, false);
            latest.setBuffer(decoded.getBuffer());
            latest.getUnsignedVarInt();
            latest.decode();
            decoded = latest;
        }
        assertEquals(decoded.getCount(), decoded.getOffset(), "The complete input payload must be consumed");
        return decoded;
    }

    private static org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData action(
            org.cloudburstmc.protocol.bedrock.data.PlayerActionType type, int x) {
        var action = new org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData();
        action.setAction(type);
        action.setBlockPosition(Vector3i.from(x, 64, 0));
        action.setFace(1);
        return action;
    }

    private static final class Harness {
        final TestPlayer player;
        final List<Integer> breaks = new ArrayList<>();
        final List<Integer> restored = new ArrayList<>();
        final List<String> trace = new ArrayList<>();
        Consumer<PlayerAuthInputPacket> packetListener = packet -> {};

        Harness(int protocol, int rejectedX) {
            MockServer.reset();
            Server server = MockServer.get();
            server.serverAuthoritativeMovementMode = 1;
            Level level = Mockito.mock(Level.class);
            PluginManager manager = server.getPluginManager();
            Mockito.when(server.getDefaultLevel()).thenReturn(level);
            Mockito.when(level.getBlock(Mockito.any(Vector3.class))).thenAnswer(invocation -> {
                Vector3 position = invocation.getArgument(0);
                BlockStone block = new BlockStone();
                block.setComponents(position.x, position.y, position.z);
                return block;
            });
            Mockito.doAnswer(invocation -> {
                if (invocation.getArgument(0) instanceof DataPacketReceiveEvent event
                        && event.getPacket() instanceof PlayerAuthInputPacket packet) {
                    packetListener.accept(packet);
                }
                if (invocation.getArgument(0) instanceof PlayerInteractEvent event) {
                    String start = "start:" + event.getBlock().getFloorX();
                    // The handler can call start twice consecutively when the target changes.
                    // Only the causal position of the start matters, not its millisecond throttle.
                    if (trace.isEmpty() || !start.equals(trace.get(trace.size() - 1))) {
                        trace.add(start);
                    }
                }
                return null;
            }).when(manager).callEvent(Mockito.any());

            SourceInterface source = Mockito.mock(SourceInterface.class);
            NetworkPlayerSession session = Mockito.mock(NetworkPlayerSession.class);
            Mockito.when(source.getSession(Mockito.any(InetSocketAddress.class)))
                    .thenReturn(session);
            player = new TestPlayer(source, protocol);
            Mockito.when(level.useBreakOn(Mockito.any(Vector3.class), Mockito.eq(BlockFace.UP),
                    Mockito.any(Item.class), Mockito.same(player), Mockito.eq(true))).thenAnswer(invocation -> {
                Vector3 position = invocation.getArgument(0);
                int x = position.getFloorX();
                breaks.add(x);
                trace.add("break:" + x);
                return x == rejectedX ? null : invocation.getArgument(2);
            });
            Mockito.doAnswer(invocation -> {
                Player[] recipients = invocation.getArgument(0);
                assertEquals(List.of(player), List.of(recipients));
                Vector3[] positions = invocation.getArgument(1);
                for (Vector3 position : positions) {
                    assertEquals(64, position.getFloorY());
                    assertEquals(0, position.getFloorZ());
                    restored.add(position.getFloorX());
                }
                return null;
            }).when(level).sendBlocks(Mockito.any(Player[].class), Mockito.any(Vector3[].class),
                    Mockito.eq(UpdateBlockPacket.FLAG_ALL_PRIORITY));
        }
    }

    private static final class TestPlayer extends Player {
        TestPlayer(SourceInterface source, int protocol) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            this.protocol = protocol;
            this.gameVersion = GameVersion.byProtocol(protocol, false);
            this.spawned = true;
            this.loggedIn = true;
            this.loginVerified = true;
            this.gamemode = Player.CREATIVE;
            this.health = 20;
            this.y = 64;
            this.temporalVector = new Vector3();
            this.inventory = Mockito.mock(PlayerInventory.class);
            Item handItem = Item.get(Item.AIR);
            Mockito.when(this.inventory.getItemInHand()).thenReturn(handItem);
            this.adventureSettings = new AdventureSettings(this);
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }
    }
}
