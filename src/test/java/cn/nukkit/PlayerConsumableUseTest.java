package cn.nukkit;

import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.ItemSteak;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerConsumableUseTest {

    private final AtomicInteger tick = new AtomicInteger(100);
    private TestPlayer player;

    @BeforeAll
    static void bootstrap() {
        MockServer.init();
        Effect.init();
    }

    private static Stream<Arguments> modernProtocols() {
        return ProtocolInfo.SUPPORTED_PROTOCOLS.stream()
                .filter(protocol -> protocol >= ProtocolInfo.v1_20_0_23)
                .map(Arguments::of);
    }

    @BeforeEach
    void setUp() {
        MockServer.reset();
        Server server = MockServer.get();
        server.doNotLimitInteractions = true;
        server.serverAuthoritativeMovementMode = 1;
        when(server.getTick()).thenAnswer(call -> tick.get());

        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        level.isBeingConverted = true;
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        when(server.getDefaultLevel()).thenReturn(level);
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        player = new TestPlayer(source, level, chunk);
    }

    @AfterEach
    void resetServer() {
        MockServer.reset();
    }

    @ParameterizedTest(name = "v{0}")
    @MethodSource("modernProtocols")
    void repeatedClickKeepsFoodUseUntilServerCompletion(int protocol) {
        useProtocol(protocol);
        ItemSteak steak = new ItemSteak();
        assertTrue(player.getInventory().setItem(0, steak));
        player.setUsingItem(true);

        tick.set(101);
        player.handleInventoryTransactionPacket(clickAir());

        assertTrue(player.isUsingItem(), "an immediate repeat must not end active use");
        tick.set(102);
        player.handleInventoryTransactionPacket(clickAir());

        assertTrue(player.isUsingItem(), "an early repeat must leave the server use timer active");
        tick.set(100 + steak.getUseDuration());
        assertTrue(player.processAutoCompletion(), "the server must still complete the food at its duration");
        assertFalse(player.isUsingItem());
        assertTrue(player.getInventory().getItemInHand().isNull());
    }

    private void useProtocol(int protocol) {
        player.protocol = protocol;
        player.gameVersion = GameVersion.byProtocol(protocol, false);
    }

    private InventoryTransactionPacket clickAir() {
        UseItemData data = new UseItemData();
        data.actionType = InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR;
        data.face = BlockFace.UP;
        data.hotbarSlot = player.getInventory().getHeldItemIndex();
        data.itemInHand = player.getInventory().getItemInHand();
        data.playerPos = new Vector3(player.x, player.y, player.z);
        data.clickPos = new Vector3f();

        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.protocol = player.protocol;
        packet.gameVersion = player.gameVersion;
        packet.transactionType = InventoryTransactionPacket.TYPE_USE_ITEM;
        packet.transactionData = data;
        packet.actions = new NetworkInventoryAction[0];
        return packet;
    }

    private static final class TestPlayer extends Player {

        TestPlayer(SourceInterface source, Level level, FullChunk chunk) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            protocol = GameVersion.getLastVersion().getProtocol();
            gameVersion = GameVersion.getLastVersion();
            spawned = loggedIn = loginVerified = true;
            gamemode = SURVIVAL;
            health = 20;
            this.level = level;
            this.chunk = chunk;
            this.y = 64;
            inventory = new PlayerInventory(this);
            offhandInventory = new PlayerOffhandInventory(this);
            adventureSettings = new AdventureSettings(this);
            foodData = new PlayerFood(this, 20, 20);
            temporalVector = new Vector3();
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }
    }
}
