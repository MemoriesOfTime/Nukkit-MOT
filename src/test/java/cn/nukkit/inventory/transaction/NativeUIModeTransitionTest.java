package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.event.player.PlayerGameModeChangeEvent;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.item.Item;
import java.util.List;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class NativeUIModeTransitionTest {
    @BeforeAll static void initialize(){NativeWorkstationOverflowTest.initialize();}
    @AfterEach void reset(){MockServer.reset();}
    static NativeWorkstationOverflowTest.Fixture fixture()throws Exception{
        var f=NativePlayerProfileLifecycleTest.fixture();f.player.configureModeChange();return f;
    }
    @ParameterizedTest @ValueSource(strings={"world","source","claim"})
    void refusedNativeReturnCannotPublishCreativeOverFiniteInputs(String refusal)throws Exception {
        var f=fixture();
        f.events(event->{
            if(refusal.equals("world")&&event instanceof ItemSpawnEvent spawn)spawn.getEntity().close();
            if(refusal.equals("source")&&event instanceof EntityInventoryChangeEvent change&&change.getNewItem().isNull())change.setCancelled();
        });
        try(var claim=refusal.equals("claim")?InventoryMutationGuard.tryClaimProfile(List.of(f.player.getInventory(),f.player.getUIInventory())):null){
            if(refusal.equals("claim"))assertNotNull(claim);
            assertFalse(f.player.setGamemode(Player.CREATIVE));
            assertEquals(Player.SURVIVAL,f.player.getGamemode());
            assertTrue(f.input.equalsExact(f.station.getItem(0)));assertTrue(f.world.isEmpty());
            assertSame(f.station,f.player.getWindowById(f.windowId));
        }
        f.assertRetained();
        f.events(event->{ });assertTrue(f.player.setGamemode(Player.CREATIVE));
        assertEquals(f.expected.getCount(),f.publishedCount());assertTrue(f.station.getItem(0).isNull());
        f.player.resetCraftingGridType();assertEquals(f.expected.getCount(),f.publishedCount());
    }
    @Test void emptyUiDuringAnUncommittedMainWriteCannotAdmitAReentrantModeChange()throws Exception {
        var f=fixture();f.player.getInventory().setItemForce(0,Item.AIR_ITEM);
        AtomicBoolean requested=new AtomicBoolean(),changed=new AtomicBoolean(true);
        f.events(event->{if(event instanceof EntityInventoryChangeEvent change&&change.getNewItem().getId()==f.input.getId()&&requested.compareAndSet(false,true)){
            assertTrue(f.player.getUIInventory().getContents().isEmpty(),"the provisional source has already been debited");
            changed.set(f.player.setGamemode(Player.CREATIVE));
        }});
        assertTrue(f.run());assertTrue(requested.get());assertFalse(changed.get());assertEquals(Player.SURVIVAL,f.player.getGamemode());
        assertEquals(f.input.getCount(),f.player.getInventory().getItem(0).getCount());assertTrue(f.station.getItem(0).isNull());
    }
    @Test void acceptedNativeOverflowIsReturnedUnderTheOldModeBeforeCreativeIsPublished()throws Exception {
        var f=fixture();AtomicInteger drops=new AtomicInteger();
        f.events(event->{if(event instanceof ItemSpawnEvent){assertEquals(Player.SURVIVAL,f.player.getGamemode());drops.incrementAndGet();}});
        assertTrue(f.player.setGamemode(Player.CREATIVE));assertEquals(Player.CREATIVE,f.player.getGamemode());
        assertEquals(1,drops.get());assertEquals(f.expected.getCount(),f.publishedCount());assertTrue(f.station.getItem(0).isNull());
    }
    @Test void cancelledNativeModeEventKeepsItsIntactFiniteUiAndPublishesNothing()throws Exception {
        var f=fixture();f.events(event->{if(event instanceof PlayerGameModeChangeEvent change)change.setCancelled();});
        assertFalse(f.player.setGamemode(Player.CREATIVE));assertEquals(Player.SURVIVAL,f.player.getGamemode());f.assertRetained();
    }
}
