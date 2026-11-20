package cn.nukkit.network.process.processor.v113;

import cn.nukkit.*;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.UseItemPacket_v113;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 1.1 clients (protocol 113) have no transaction stream: the legacy processor must dispatch the air use
 * and commit the resulting stack itself. Otherwise armour, maps, rods and consumables never work there.
 * <p>
 * Regression: the air branch used to dispatch {@code onClickAir} for projectile items only.
 */
class UseItemProcessorV113Test {

    private TestPlayer player;
    private Level level;

    @BeforeEach
    void setUp() {
        MockServer.reset();
        Server server = MockServer.get();
        player = new TestPlayer(server);
        level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        player.level = level;
        HoldItem.airUsed = false;
    }

    @AfterEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void airUseEquipsArmor() {
        player.getInventory().setItem(0, Item.get(ItemID.IRON_HELMET));

        UseItemProcessor_v113.INSTANCE.handle(new PlayerHandle(player), airUse());

        assertEquals(ItemID.IRON_HELMET, player.getInventory().getHelmetFast().getId());
    }

    @Test
    void airUseCreatesAMap() {
        player.getInventory().setItem(0, Item.get(ItemID.EMPTY_MAP));

        UseItemProcessor_v113.INSTANCE.handle(new PlayerHandle(player), airUse());

        // ItemMap 每次构造都带新的 map_uuid，只能按 id 找，不能按 equals。
        // ItemMap carries a fresh map_uuid per instance, so match by id rather than equals.
        boolean createdMap = player.getInventory().getContents().values().stream()
                .anyMatch(i -> i.getId() == Item.MAP);
        assertTrue(createdMap, "the filled map is added to the inventory");
        assertTrue(player.getInventory().getItemInHandFast().isNull(), "the empty map is consumed");
    }

    @Test
    void airUseCommitsTheConsumedStack() {
        player.getInventory().setItem(0, new ConsumableItem());

        UseItemProcessor_v113.INSTANCE.handle(new PlayerHandle(player), airUse());

        assertTrue(player.getInventory().getItemInHandFast().isNull(), "the decremented stack must reach the inventory");
    }

    @Test
    void holdToUseItemsKeepTheHoldPath() {
        player.getInventory().setItem(0, new HoldItem());

        UseItemProcessor_v113.INSTANCE.handle(new PlayerHandle(player), airUse());

        assertFalse(HoldItem.airUsed);
        assertTrue(player.isUsingItem());
    }

    private UseItemPacket_v113 airUse() {
        UseItemPacket_v113 packet = new UseItemPacket_v113();
        packet.face = -1;
        packet.item = player.getInventory().getItemInHand();
        return packet;
    }

    private static final class ConsumableItem extends Item {

        private ConsumableItem() {
            super(ItemID.SNOWBALL, 0, 1, "Snowball");
        }

        @Override
        public boolean onClickAir(Player player, Vector3 directionVector) {
            this.setCount(this.getCount() - 1);
            return true;
        }
    }

    private static final class HoldItem extends Item {

        private static boolean airUsed;

        private HoldItem() {
            super(ItemID.GOLDEN_APPLE, 0, 1, "Golden Apple");
        }

        @Override
        public boolean canRelease() {
            return true;
        }

        @Override
        public boolean onClickAir(Player player, Vector3 directionVector) {
            airUsed = true;
            return true;
        }
    }

    private static final class TestPlayer extends Player {

        private TestPlayer(Server server) {
            super(source(), 1L, new InetSocketAddress("127.0.0.1", 19132));
            this.server = server;
            protocol = ProtocolInfo.v1_1_0;
            gameVersion = GameVersion.byProtocol(ProtocolInfo.v1_1_0, false);
            spawned = loggedIn = loginVerified = true;
            gamemode = SURVIVAL;
            health = 20;
            inventory = new PlayerInventory(this);
            offhandInventory = new PlayerOffhandInventory(this);
            adventureSettings = new AdventureSettings(this);
            temporalVector = new Vector3();
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }
    }

    private static SourceInterface source() {
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        return source;
    }
}
