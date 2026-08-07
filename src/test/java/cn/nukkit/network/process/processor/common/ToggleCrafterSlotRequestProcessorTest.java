package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.BlockCrafter;
import cn.nukkit.blockentity.BlockEntityCrafter;
import cn.nukkit.inventory.CrafterInventory;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.ToggleCrafterSlotRequestPacket;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToggleCrafterSlotRequestProcessorTest {

    @Test
    void ignoresToggleWhenPlayerIsNotViewingCrafterInventory() {
        Fixture fixture = createFixture(false, -1);
        ToggleCrafterSlotRequestPacket packet = packet(3, true);

        ToggleCrafterSlotRequestProcessor.INSTANCE.handle(new PlayerHandle(fixture.player), packet);

        verify(fixture.inventory, never()).setSlotDisabled(3, true);
    }

    @Test
    void togglesSlotWhenPlayerIsViewingCrafterInventory() {
        Fixture fixture = createFixture(true, 12);
        ToggleCrafterSlotRequestPacket packet = packet(3, true);
        when(fixture.inventory.setSlotDisabled(3, true)).thenReturn(true);

        ToggleCrafterSlotRequestProcessor.INSTANCE.handle(new PlayerHandle(fixture.player), packet);

        verify(fixture.inventory).setSlotDisabled(3, true);
    }

    private static Fixture createFixture(boolean viewing, int windowId) {
        Player player = mock(Player.class);
        Level level = mock(Level.class);
        BlockCrafter block = mock(BlockCrafter.class);
        BlockEntityCrafter blockEntity = mock(BlockEntityCrafter.class);
        CrafterInventory inventory = mock(CrafterInventory.class);

        when(player.getLevel()).thenReturn(level);
        when(player.getWindowId(inventory)).thenReturn(windowId);
        when(level.getBlock(any(Vector3.class))).thenReturn(block);
        doReturn(blockEntity).when(block).getOrCreateBlockEntity();
        when(blockEntity.getInventory()).thenReturn(inventory);
        when(inventory.getViewers()).thenReturn(viewing ? Set.of(player) : Collections.emptySet());

        return new Fixture(player, inventory);
    }

    private static ToggleCrafterSlotRequestPacket packet(int slot, boolean disabled) {
        ToggleCrafterSlotRequestPacket packet = new ToggleCrafterSlotRequestPacket();
        packet.blockPosition = new Vector3f(0, 0, 0);
        packet.slot = (byte) slot;
        packet.disabled = disabled;
        return packet;
    }

    private record Fixture(Player player, CrafterInventory inventory) {
    }
}
