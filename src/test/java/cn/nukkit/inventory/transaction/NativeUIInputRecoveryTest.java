package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class NativeUIInputRecoveryTest {
    @BeforeAll static void initialize() { NativeWorkstationOverflowTest.initialize(); }
    @AfterEach void reset() { MockServer.reset(); }
    static List<Integer> inputSlots() {
        var slots=new ArrayList<Integer>();
        for(int slot=0;slot<54;slot++) if(PlayerUIInventory.isPersistentSlot(slot)) slots.add(slot);
        return slots;
    }
    static NativeWorkstationOverflowTest.Fixture restoredInputs() throws Exception {
        var f=NativePlayerProfileLifecycleTest.fixture(); f.player.getInventory().clearAll(); f.player.getUIInventory().clearAll();
        for(int slot:inputSlots()) f.player.getUIInventory().setItemForce(slot,NativePlayerProfileLifecycleTest.marked(slot));
        for(int slot=41;slot<=50;slot++) f.player.getUIInventory().setItemForce(slot,NativePlayerProfileLifecycleTest.marked(slot));
        f.player.saveNBT(); CompoundTag profile=f.player.namedTag.copy();
        assertEquals(inputSlots().size(),profile.getList("PlayerUIItems",CompoundTag.class).size());
        f.player.reload(profile);
        for(int slot:inputSlots()) assertTrue(NativePlayerProfileLifecycleTest.marked(slot).equalsExact(f.player.getUIInventory().getItem(slot)));
        return f;
    }
    @Test void everyOfficialNativeInputIsReachableThroughTheFirstOrdinaryResetAfterReload() throws Exception {
        var f=restoredInputs();
        f.player.resetCraftingGridType();
        assertTrue(f.player.getUIInventory().getContents().isEmpty());
        for(int slot:inputSlots()) {
            Item original=NativePlayerProfileLifecycleTest.marked(slot);
            int main=f.player.getInventory().getContents().values().stream().filter(i->i.equals(original,true,true)).mapToInt(Item::getCount).sum();
            int world=f.world.values().stream().filter(e->!e.closed).map(e->((cn.nukkit.entity.item.EntityItem)e).getItem())
                    .filter(i->i.equals(original,true,true)).mapToInt(Item::getCount).sum();
            assertEquals(original.getCount(),main+world,"restored input slot "+slot+" remains reachable exactly once");
        }
    }
    @Test void fullInventoryAndWorldRefusalKeepEveryRestoredInputForAHealthyOrdinaryRetry() throws Exception {
        var f=restoredInputs();
        for(int slot=0;slot<f.player.getInventory().getSize();slot++) f.player.getInventory().setItemForce(slot,Item.get(Item.DIAMOND,0,64));
        f.events(event->{if(event instanceof ItemSpawnEvent spawn) spawn.getEntity().close();});
        f.player.resetCraftingGridType();
        assertEquals(inputSlots().size(),f.player.getUIInventory().getContents().size());
        assertTrue(f.world.isEmpty()); assertTrue(f.chunkEntities.isEmpty());
        f.events(event->{ }); f.player.resetCraftingGridType();
        assertTrue(f.player.getUIInventory().getContents().isEmpty());
        assertEquals(inputSlots().size()*3,f.publishedCount());
    }
    @Test void acceptedStonecutterOverflowEndsTheNativeActiveUseState() throws Exception {
        var f=new NativeWorkstationOverflowTest.Fixture(NativeWorkstationOverflowTest.Route.STONECUTTER_CLOSE);
        f.player.setUsingItem(true);
        assertTrue(f.player.isUsingItem());
        assertTrue(f.run());
        assertEquals(f.expected.getCount(),f.publishedCount());
        assertFalse(f.player.isUsingItem(),"native Player.dropItem ended item use after the accepted overflow");
    }
    @Test void mainInventoryOnlyReturnPreservesTheNativeActiveUseState() throws Exception {
        var f=new NativeWorkstationOverflowTest.Fixture(NativeWorkstationOverflowTest.Route.STONECUTTER_CLOSE);
        f.player.getInventory().setItemForce(0,Item.AIR_ITEM);f.player.setUsingItem(true);
        assertTrue(f.run());assertEquals(0,f.publishedCount());assertTrue(f.player.isUsingItem());
    }
    @Test void refusedOverflowKeepsTheActiveUseStateAndInputForRetry() throws Exception {
        var f=new NativeWorkstationOverflowTest.Fixture(NativeWorkstationOverflowTest.Route.STONECUTTER_CLOSE);
        f.player.setUsingItem(true);
        f.events(event->{if(event instanceof ItemSpawnEvent spawn) spawn.getEntity().close();});
        f.run();f.assertRetained();assertTrue(f.player.isUsingItem());
    }
    @Test void ordinaryAnvilResetOverflowPreservesItsExistingActiveUseState() throws Exception {
        var f=new NativeWorkstationOverflowTest.Fixture(NativeWorkstationOverflowTest.Route.ANVIL_CLOSE);
        f.player.setUsingItem(true);assertTrue(f.run());assertTrue(f.player.isUsingItem());
    }
    @Test void aCustomPlayerResetHookStillObservesItsAuthoredNativeInputs() throws Exception {
        var f=NativePlayerProfileLifecycleTest.fixture();
        var network=org.mockito.Mockito.mock(cn.nukkit.network.SourceInterface.class);
        org.mockito.Mockito.when(network.getSession(org.mockito.ArgumentMatchers.any(java.net.InetSocketAddress.class)))
                .thenReturn(org.mockito.Mockito.mock(cn.nukkit.network.session.NetworkPlayerSession.class));
        var custom=new CustomPlayer(network,f.level,f.player.chunk);
        var station=new cn.nukkit.inventory.AnvilInventory(custom.getUIInventory(),new cn.nukkit.level.Position(0,64,0,f.level));
        custom.bind(station,cn.nukkit.Player.ANVIL_WINDOW_ID);station.setItemForce(0,NativePlayerProfileLifecycleTest.marked(1));
        custom.removeWindow(station);
        assertEquals(3,custom.observed,"the existing reset override observes inputs before their native return");
    }
    static final class CustomPlayer extends NativeWorkstationOverflowTest.NativePlayer {
        int observed=-1;
        CustomPlayer(cn.nukkit.network.SourceInterface source,cn.nukkit.level.Level level,cn.nukkit.level.format.FullChunk chunk) {super(source,level,chunk);}
        @Override public void resetCraftingGridType() {
            var station=this.getWindowById(cn.nukkit.Player.ANVIL_WINDOW_ID);
            if(station!=null) this.observed=station.getItem(0).getCount();
            super.resetCraftingGridType();
        }
    }

    @Test void fillingTwoExistingStacksPreservesTheirDistinctNetworkIdentities() throws Exception {
        var f=NativePlayerProfileLifecycleTest.fixture(); f.station.setItemForce(0,Item.AIR_ITEM);
        var source=NativePlayerProfileLifecycleTest.marked(1);
        Item first=source.clone(),second=source.clone(); first.setCount(63);second.setCount(63);
        first.autoAssignStackNetworkId();second.autoAssignStackNetworkId();source.autoAssignStackNetworkId();
        f.player.getInventory().setItemForce(0,first);f.player.getInventory().setItemForce(1,second);
        f.player.getInventory().setItemForce(2,Item.AIR_ITEM);f.player.getUIInventory().setItemForce(1,source);
        int firstId=f.player.getInventory().getItem(0).getStackNetId(),secondId=f.player.getInventory().getItem(1).getStackNetId();
        assertNotEquals(firstId,secondId);
        f.player.resetCraftingGridType();
        assertEquals(64,f.player.getInventory().getItem(0).getCount());assertEquals(64,f.player.getInventory().getItem(1).getCount());
        assertEquals(firstId,f.player.getInventory().getItem(0).getStackNetId());assertEquals(secondId,f.player.getInventory().getItem(1).getStackNetId());
        assertNotEquals(firstId,f.player.getInventory().getItem(2).getStackNetId());assertNotEquals(secondId,f.player.getInventory().getItem(2).getStackNetId());
        assertEquals(1,f.player.getInventory().getItem(2).getCount());
    }
}
