package cn.nukkit.inventory.transaction;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class NativeWorkstationClientWindowTest {
    @BeforeAll static void initialize(){NativeWorkstationOverflowTest.initialize();}
    @AfterEach void reset(){MockServer.reset();}
    @ParameterizedTest @EnumSource(value=GameVersion.class,mode=EnumSource.Mode.MATCH_ANY,names={"V1_(20|21|26)_[0-9]+"})
    void nativeOverflowAndEveryPhysicalSmithingInputUseTheActualSessionVersion(GameVersion version)throws Exception {
        var f=new NativeWorkstationOverflowTest.Fixture(NativeWorkstationOverflowTest.Route.STONECUTTER_RESULT);
        f.player.setSessionVersion(version);
        Field network=cn.nukkit.Player.class.getDeclaredField("networkSession");network.setAccessible(true);
        var session=(NetworkPlayerSession)network.get(f.player);var sent=new ArrayList<DataPacket>();
        doAnswer(call->{
            DataPacket packet=call.getArgument(0);assertEquals(version,packet.gameVersion);assertEquals(version.getProtocol(),packet.protocol);
            packet.tryEncode();assertTrue(packet.getBuffer().length>0);sent.add(packet);return null;
        }).when(session).sendPacket(any(DataPacket.class));
        assertTrue(f.run());assertEquals(f.expected.getCount(),f.publishedCount());assertTrue(f.station.getItem(0).isNull());
        for(int slot=51;slot<=53;slot++)f.player.getUIInventory().setItemForce(slot,NativePlayerProfileLifecycleTest.marked(slot));
        sent.clear();f.player.resetCraftingGridType();
        for(int slot=51;slot<=53;slot++) {
            int physical=slot;
            assertTrue(sent.stream().anyMatch(packet->packet instanceof InventorySlotPacket change&&change.slot==physical&&change.item.isNull()),"physical UI slot "+slot);
        }
    }
}
