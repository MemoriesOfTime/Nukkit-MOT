package cn.nukkit;

import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.types.AuthInputAction;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerAuthInputAllocationTest {
    @Test
    void ordinaryInputDoesNotReadOrCloneHeldStack() {
        TestPlayer player = player();
        player.handleDataPacket(input(false));
        verify(player.testInventory, never()).getItemInHand();
        assertNull(player.startedWith);
    }

    @Test
    void startUsingKeepsSnapshotBeforeCallingCustomItemCode() {
        TestPlayer player = player();
        MutableProbeItem original = new MutableProbeItem();
        when(player.testInventory.getItemInHand()).thenAnswer(call -> original.clone());
        player.handleDataPacket(input(true));
        verify(player.testInventory, times(1)).getItemInHand();
        assertNotNull(player.startedWith);
        assertNotSame(original, player.startedWith);
        assertEquals(0, original.releaseChecks, "custom item code must never receive the live stack");
        assertEquals(1, ((MutableProbeItem) player.startedWith).releaseChecks);
    }

    @Test
    void inputBeforeInventoryInitializationStillDoesNotThrow() {
        TestPlayer player = player();
        player.clearInventory();
        assertDoesNotThrow(() -> player.handleDataPacket(input(true)));
        assertNull(player.startedWith);
    }

    static PlayerAuthInputPacket input(boolean start) {
        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.setPosition(new Vector3f(0, 65.62f, 0));
        if (start) packet.setInputData(EnumSet.of(AuthInputAction.START_USING_ITEM));
        return packet;
    }

    static TestPlayer player() {
        MockServer.reset();
        Server server = MockServer.get();
        server.serverAuthoritativeMovementMode = 1;
        when(server.getDefaultLevel()).thenReturn(mock(Level.class));
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        return new TestPlayer(source);
    }

    static final class MutableProbeItem extends Item {
        int releaseChecks;
        MutableProbeItem() { super(Item.APPLE, 0, 1, "probe"); }
        @Override public boolean canRelease() { releaseChecks++; return true; }
    }

    static final class TestPlayer extends Player {
        Item startedWith;
        final PlayerInventory testInventory;
        TestPlayer(SourceInterface source) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            this.protocol = 844;
            this.gameVersion = GameVersion.byProtocol(protocol, false);
            this.spawned = this.loggedIn = this.loginVerified = true;
            this.gamemode = CREATIVE;
            this.health = 20;
            this.y = 64;
            this.temporalVector = new Vector3();
            this.inventory = this.testInventory = mock(PlayerInventory.class);
            this.adventureSettings = new AdventureSettings(this);
        }
        void clearInventory() { this.inventory = null; }
        @Override boolean tryStartUsingHeldItem(Item item) { startedWith = item; return true; }
        @Override public boolean dataPacket(DataPacket packet) { return true; }
    }
}
