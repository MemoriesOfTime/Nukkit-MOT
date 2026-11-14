package cn.nukkit.inventory;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryAddItemTruthfulCreditTest {
    @BeforeAll static void initialize(){MockServer.init();}
    @AfterEach void resetEvents(){reset(Server.getInstance().getPluginManager());}
    @Test void trueSetterWithSmallerNewStackMustLeaveUnacceptedUnits() throws Exception { check(0,2); }
    @Test void trueSetterWithSmallerMergedStackMustLeaveUnacceptedUnits() throws Exception { check(8,9); }
    private void check(int before,int changedTarget) throws Exception {
        Player player=mock(Player.class,CALLS_REAL_METHODS);
        PlayerInventory inventory=new PlayerInventory(player);
        Field server=Entity.class.getDeclaredField("server");server.setAccessible(true);server.set(player,Server.getInstance());
        Field nativeInventory=EntityHumanType.class.getDeclaredField("inventory");nativeInventory.setAccessible(true);nativeInventory.set(player,inventory);
        player.spawned=true;
        doReturn(true).when(player).isOnline();doReturn(true).when(player).dataPacket(any(DataPacket.class));
        doReturn(Collections.emptyMap()).when(player).getViewers();
        for(int slot=1;slot<inventory.getSize();slot++)inventory.setItemForce(slot,Item.get(Item.STONE,0,64));
        if(before>0)inventory.setItemForce(0,Item.get(Item.EMERALD,0,before));
        var plugins=Server.getInstance().getPluginManager();
        doAnswer(call->{EntityInventoryChangeEvent event=call.getArgument(0);if(event.getSlot()==0)event.setNewItem(Item.get(Item.EMERALD,0,changedTarget));return null;})
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        Item[] left=inventory.addItem(Item.get(Item.EMERALD,0,4));
        int written=inventory.getItem(0).getCount()-before;
        assertEquals(0,written,"a callback replacement must be refused before the native slot write");
        assertEquals(4,written+Arrays.stream(left).mapToInt(Item::getCount).sum(),"addItem must report only the units really accepted by a true setter");
    }
}
