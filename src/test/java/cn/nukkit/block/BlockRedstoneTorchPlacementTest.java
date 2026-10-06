package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.redstone.RedstoneUpdateEvent;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockRedstoneTorchPlacementTest {
    @Test
    void floorTorchNotifiesAComponentThroughTheBlockAbove() {
        Fixture f = new Fixture(BlockFace.UP, false, true);
        assertTrue(f.place());
        assertTrue(f.probe.updated, "placement must notify the component beyond the powered solid block");
        assertTrue(f.probe.powered, "the notified component must observe the installed torch's power");
        f.verifyFiveNotifications();
    }

    @Test
    void wallTorchesNotifyWithoutUpdatingThroughTheirAttachment() {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Fixture f = new Fixture(face, false, true);
            assertTrue(f.place(), face.toString());
            assertTrue(f.probe.updated, face.toString());
            assertTrue(f.probe.powered, face.toString());
            f.verifyFiveNotifications();
        }
    }

    @Test
    void poweredAttachmentSwitchesOffAndNotifiesOnlyOnce() {
        Fixture f = new Fixture(BlockFace.UP, true, true);
        assertTrue(f.place());
        assertEquals(Block.UNLIT_REDSTONE_TORCH, f.at(f.torch).getId());
        assertTrue(f.probe.updated);
        assertFalse(f.probe.powered);
        f.verifyFiveNotifications();
    }

    @Test
    void rejectedPlacementDoesNotSendRedstoneNotifications() {
        Fixture f = new Fixture(BlockFace.UP, false, false);
        assertFalse(f.place());
        assertFalse(f.probe.updated);
        verify(f.level, never()).updateAroundRedstone(any(Vector3.class), nullable(BlockFace.class));
    }

    @Test
    void feedbackKeepsTheScheduledDelayAndCancellableRedstoneEvent() {
        for (boolean cancelled : new boolean[]{false, true}) {
            Fixture f = new Fixture(BlockFace.UP, false, true);
            AtomicBoolean attachmentPower = new AtomicBoolean();
            when(f.level.isSidePowered(any(Vector3.class), any(BlockFace.class)))
                    .thenAnswer(i -> attachmentPower.get());
            f.probe.afterUpdate = () -> attachmentPower.set(true);
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(f.level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            doAnswer(i -> {
                ((RedstoneUpdateEvent) i.getArgument(0)).setCancelled(cancelled);
                return null;
            }).when(plugins).callEvent(any(RedstoneUpdateEvent.class));

            assertTrue(f.place());
            assertTrue(attachmentPower.get());
            assertEquals(Block.REDSTONE_TORCH, f.at(f.torch).getId(), "feedback must wait for the scheduled update");
            verify(f.level, atLeastOnce()).scheduleUpdate(f.torch, 2);
            f.torch.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            verify(plugins).callEvent(any(RedstoneUpdateEvent.class));
            assertEquals(cancelled ? Block.REDSTONE_TORCH : Block.UNLIT_REDSTONE_TORCH, f.at(f.torch).getId());
        }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Map<String, Block> blocks = new HashMap<>();
        final BlockFace face;
        final BlockRedstoneTorch torch = new BlockRedstoneTorch();
        final Probe probe = new Probe();
        final Block target;

        Fixture(BlockFace face, boolean poweredAttachment, boolean accepted) {
            this.face = face;
            position(torch, new Vector3(0, 64, 0));
            target = position(new BlockStone(), torch.getSideVec(face.getOpposite()));
            blocks.put(key(target), target);
            put(new BlockStone(), new Vector3(0, 65, 0));
            put(probe, new Vector3(0, 66, 0));
            when(level.getBlock(any(Vector3.class))).thenAnswer(i -> at(i.getArgument(0)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    at(new Vector3(i.<Integer>getArgument(0), i.<Integer>getArgument(1), i.<Integer>getArgument(2))));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(i -> {
                if (!accepted) return false;
                put(i.getArgument(1), i.getArgument(0));
                return true;
            });
            when(level.isSidePowered(any(Vector3.class), any(BlockFace.class))).thenReturn(poweredAttachment);
            doCallRealMethod().when(level).updateAroundRedstone(any(Vector3.class), nullable(BlockFace.class));
            when(level.isBlockPowered(any(Vector3.class))).thenCallRealMethod();
            when(level.getRedstonePower(any(Vector3.class), any(BlockFace.class))).thenCallRealMethod();
            when(level.getStrongPower(any(Vector3.class))).thenCallRealMethod();
            when(level.getStrongPower(any(Vector3.class), any(BlockFace.class))).thenCallRealMethod();
        }

        boolean place() {
            Block air = position(new BlockAir(), torch);
            return torch.place(new ItemBlock(torch), air, target, face, 0.5, 0.5, 0.5, null);
        }

        void verifyFiveNotifications() {
            verify(level, times(5)).updateAroundRedstone(any(Vector3.class), nullable(BlockFace.class));
            Vector3 attachment = torch.getSideVec(torch.getBlockFace().getOpposite());
            verify(level, never()).updateAroundRedstone(argThat(p -> key(p).equals(key(attachment))), nullable(BlockFace.class));
        }

        Block at(Vector3 pos) {
            return blocks.getOrDefault(key(pos), position(new BlockAir(), pos));
        }

        void put(Block block, Vector3 pos) { blocks.put(key(pos), position(block, pos)); }

        <T extends Block> T position(T block, Vector3 pos) {
            block.x = pos.x; block.y = pos.y; block.z = pos.z; block.level = level;
            return block;
        }
    }

    private static final class Probe extends BlockStone {
        boolean updated;
        boolean powered;
        Runnable afterUpdate = () -> { };
        @Override public int onUpdate(int type) {
            if (type == Level.BLOCK_UPDATE_REDSTONE) {
                updated = true;
                powered = level.isBlockPowered(this);
                afterUpdate.run();
            }
            return 0;
        }
    }

    private static String key(Vector3 p) { return p.getFloorX() + ":" + p.getFloorY() + ":" + p.getFloorZ(); }
}
