package cn.nukkit;

import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.types.PlayerActionType;
import cn.nukkit.network.protocol.types.PlayerBlockActionData;
import cn.nukkit.math.BlockVector3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlayerEmptyBlockActionsTest {
    @Test void emptyActionsReuseImmutableResult() {
        var packet=new PlayerAuthInputPacket();
        assertSame(packet.getDecodedBlockActions(),packet.getDecodedBlockActions());
        assertSame(Player.orderBlockActions(List.of()),Player.orderBlockActions(List.of()));
        assertThrows(UnsupportedOperationException.class,()->Player.orderBlockActions(List.of()).add(
                new PlayerBlockActionData(PlayerActionType.START_DESTROY_BLOCK,new BlockVector3(),1)));
    }
    @Test void legacyMapActionsRemainAvailableForFallback() {
        var packet=new PlayerAuthInputPacket();
        var action=new PlayerBlockActionData(PlayerActionType.START_DESTROY_BLOCK,new BlockVector3(),1);
        packet.getBlockActionData().put(action.getAction(),action);
        assertTrue(packet.getDecodedBlockActions().isEmpty());
        assertEquals(List.of(action),Player.orderBlockActions(packet.getBlockActionData().values()));
    }
}
