package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityDespawnEvent;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.SourceInterface;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class NativePlayerRetirementTest {
    @BeforeAll static void initialize() { NativeWorkstationOverflowTest.initialize(); }
    @AfterEach void reset() { MockServer.reset(); }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void observersCannotAcceptAnUnpersistedCreditAfterTheFinalCheckpoint(boolean networkClose) throws Exception {
        var f=NativePlayerProfileLifecycleTest.fixture(); f.station.setItemForce(0,Item.AIR_ITEM);
        var original=NativePlayerProfileLifecycleTest.marked(0);
        f.player.getInventory().setItemForce(0,original.clone());
        var late=NativePlayerProfileLifecycleTest.marked(38);
        var store=new NativePlayerProfileLifecycleTest.ProfileStore(f);
        var observed=new AtomicInteger(); var accepted=new AtomicBoolean();
        Runnable observer=()-> {
            observed.incrementAndGet();
            assertNotNull(f.player.getInventory(),"native observer still sees the retiring backing");
            accepted.set(f.player.getInventory().setItem(0,late.clone()));
            f.player.close();
        };
        if (networkClose) {
            var field=Player.class.getDeclaredField("interfaz"); field.setAccessible(true);
            SourceInterface source=(SourceInterface)field.get(f.player);
            doAnswer(call->{observer.run();return null;}).when(source).close(eq(f.player),anyString());
        } else {
            f.events(event->{if(event instanceof EntityDespawnEvent despawn && despawn.getEntity()==f.player) observer.run();});
        }
        f.player.close();
        assertEquals(1,observed.get()); assertFalse(accepted.get(),"a late native observer must not accept unpersisted custody");
        assertTrue(f.player.closed); assertNull(f.player.getInventory());
        assertEquals(original.getCount(),store.read().getList("Inventory",CompoundTag.class).getAll().stream()
                .map(NBTIO::getItemHelper).filter(item->item.equals(original,true,true)).mapToInt(Item::getCount).sum());
        assertFalse(store.read().getList("Inventory",CompoundTag.class).getAll().stream()
                .map(NBTIO::getItemHelper).anyMatch(item->item.equals(late,true,true)));
    }
}
