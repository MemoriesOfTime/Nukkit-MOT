package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual native hopper ticks and real native minecart inventory routes, without any new helper API. */
class NativeHopperCommitOrderingTest {
    @BeforeEach void init() { MockServer.init(); MockServer.reset(); }

    private record Fixture(BlockEntityHopper hopper, BlockEntityChest chest, FullChunk sourceChunk) { }
    private Fixture fixture() {
        Server server = Server.getInstance();
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(java.util.Map.of());
        when(level.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(level);
        FullChunk hopperChunk = mock(FullChunk.class), chestChunk = mock(FullChunk.class);
        when(hopperChunk.getProvider()).thenReturn(provider);
        when(chestChunk.getProvider()).thenReturn(provider);
        var hopper = new BlockEntityHopper(hopperChunk, nbt(BlockEntity.HOPPER, 64).putInt("TransferCooldown", 0));
        var chest = new BlockEntityChest(chestChunk, nbt(BlockEntity.CHEST, 65));
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call -> {
            Vector3 at = call.getArgument(0);
            return at.getFloorY() == 65 ? chest : null;
        });
        assertTrue(chest.getInventory().setItem(0, Item.get(Item.DIAMOND, 0, 5)));
        return new Fixture(hopper, chest, chestChunk);
    }
    private static CompoundTag nbt(String id, int y) {
        return new CompoundTag().putString("id", id).putInt("x", 0).putInt("y", y).putInt("z", 0)
                .putList(new ListTag<CompoundTag>("Items"));
    }
    private static Player failingViewer(cn.nukkit.inventory.Inventory inventory) {
        Player viewer = mock(Player.class);
        viewer.protocol = 2193;
        when(viewer.getWindowId(inventory)).thenReturn(1);
        inventory.getViewers().add(viewer);
        doAnswer(call -> { throw new AssertionError("accepted native viewer failure"); })
                .when(viewer).dataPacket(any(DataPacket.class));
        return viewer;
    }

    @Test void acceptedViewerErrorRetiresCooldownBeforeTheNextNativeTick() {
        var f = fixture();
        failingViewer(f.hopper.getInventory());
        assertThrows(AssertionError.class, f.hopper::onUpdate);
        assertEquals(4, f.chest.getInventory().getItem(0).getCount());
        assertEquals(1, f.hopper.getInventory().getItem(0).getCount());
        assertEquals(8, f.hopper.transferCooldown);
        f.hopper.getInventory().getViewers().clear();
        assertTrue(f.hopper.onUpdate());
        assertEquals(7, f.hopper.transferCooldown);
        assertEquals(4, f.chest.getInventory().getItem(0).getCount());
        assertEquals(1, f.hopper.getInventory().getItem(0).getCount(), "The next tick cannot transfer again after an accepted viewer error");
    }

    @Test void cancelledNativeTransferDoesNotArmAcceptedCooldown() {
        var f = fixture();
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { if (call.getArgument(0) instanceof InventoryMoveItemEvent e) e.setCancelled(); return null; })
                .when(plugins).callEvent(any(Event.class));
        assertFalse(f.hopper.onUpdate());
        assertTrue(f.hopper.transferCooldown <= 0);
        assertEquals(5, f.chest.getInventory().getItem(0).getCount());
        assertTrue(f.hopper.getInventory().isEmpty());
    }

    @Test void precommitSetterErrorDoesNotArmAcceptedCooldown() {
        var f = fixture();
        doAnswer(call -> { throw new AssertionError("native source dirty callback"); }).when(f.sourceChunk).setChanged();
        assertThrows(AssertionError.class, f.hopper::onUpdate);
        assertTrue(f.hopper.transferCooldown <= 0);
        assertEquals(5, f.chest.getInventory().getItem(0).getCount());
        assertTrue(f.hopper.getInventory().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans={false, true})
    void acceptedMinecartRouteIsClearedBeforeViewerFailure(boolean pull) throws Exception {
        var c = new NativeHopperMinecartControlTest().make();
        var source = pull ? c.cart().getInventory() : c.hopper().getInventory();
        var target = pull ? c.hopper().getInventory() : c.cart().getInventory();
        assertTrue(source.setItem(0, Item.get(Item.DIAMOND, 0, 5)));
        c.hopper().setTransferCooldown(0);
        if (pull) c.hopper().setMinecartInvPickupFrom(c.cart()); else c.hopper().setMinecartInvPushTo(c.cart());
        failingViewer(target);
        assertThrows(AssertionError.class, () -> { if (pull) c.hopper().pullItemsFromMinecart(); else c.hopper().pushItemsIntoMinecart(); });
        assertEquals(4, source.getItem(0).getCount());
        assertEquals(1, target.getItem(0).getCount());
        assertEquals(8, c.hopper().transferCooldown);
        assertNull(pull ? c.hopper().getMinecartInvPickupFrom() : c.hopper().getMinecartInvPushTo());
    }

    @ParameterizedTest @ValueSource(booleans={false, true})
    void laterViewerAuthoredMinecartRouteSurvivesAcceptance(boolean viewerFails) throws Exception {
        var c = new NativeHopperMinecartControlTest().make();
        assertTrue(c.hopper().getInventory().setItem(0, Item.get(Item.DIAMOND, 0, 5)));
        c.hopper().setMinecartInvPushTo(c.cart());
        var viewer = failingViewer(c.cart().getInventory());
        doAnswer(call -> {
            assertNull(c.hopper().getMinecartInvPushTo(), "The accepted predecessor route is retired before the viewer callback");
            c.hopper().setMinecartInvPushTo(c.cart());
            if (viewerFails) throw new AssertionError("later independent viewer callback");
            return true;
        }).when(viewer).dataPacket(any(DataPacket.class));
        if (viewerFails) assertThrows(AssertionError.class, c.hopper()::pushItemsIntoMinecart);
        else assertTrue(c.hopper().pushItemsIntoMinecart());
        assertSame(c.cart(), c.hopper().getMinecartInvPushTo(), "A later independent route edit is not overwritten");
        assertEquals(4, c.hopper().getInventory().getItem(0).getCount());
        assertEquals(1, c.cart().getInventory().getItem(0).getCount());
    }
}
