package cn.nukkit.entity.mob;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityTameable;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.utils.DyeColor;
import cn.nukkit.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityWolfTamingFeedbackTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void successfulTrialEmitsOnlySuccessAndPreservesTamingState() {
        Fixture f = new Fixture();
        Item bones = Item.get(Item.BONE, 0, 4);
        try (MockedStatic<Utils> random = mockStatic(Utils.class)) {
            random.when(() -> Utils.rand(1, 3)).thenReturn(3);
            random.when(() -> Utils.rand(1, 7)).thenReturn(5);

            assertTrue(f.wolf.onInteract(f.player, bones, new Vector3()));

            random.verify(() -> Utils.rand(1, 3));
            random.verify(() -> Utils.rand(1, 7));
        }

        assertOnlyFeedback(f, EntityEventPacket.TAME_SUCCESS);
        assertSame(f.player, f.wolf.getOwner());
        assertTrue(f.wolf.hasOwner());
        assertTrue(f.wolf.getDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_TAMED));
        assertEquals(f.player.getId(), f.wolf.getDataPropertyLong(Entity.DATA_OWNER_EID));
        assertEquals(20, f.wolf.getMaxHealth());
        assertEquals(20, f.wolf.getHealth());
        assertEquals(DyeColor.RED.getWoolData(), f.wolf.getDataPropertyByte(Entity.DATA_COLOUR));
        verify(f.level).dropExpOrb(f.wolf, 5);
        assertEquals(4, bones.getCount(), "Player owns the single bone debit after a handled interaction");
        verify(f.player, never()).getInventory();

        f.wolf.saveNBT();
        assertEquals(f.player.getName(), f.wolf.namedTag.getString(EntityTameable.NAMED_TAG_OWNER));
        assertEquals(f.player.getUniqueId().toString(),
                f.wolf.namedTag.getString(EntityTameable.NAMED_TAG_OWNER_UUID));
        assertEquals(DyeColor.RED.getDyeData(), f.wolf.namedTag.getByte("CollarColor"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void unsuccessfulTrialEmitsOnlyFailureAndDoesNotTame(int roll) {
        Fixture f = new Fixture();
        Item bones = Item.get(Item.BONE, 0, 4);
        try (MockedStatic<Utils> random = mockStatic(Utils.class)) {
            random.when(() -> Utils.rand(1, 3)).thenReturn(roll);

            assertTrue(f.wolf.onInteract(f.player, bones, new Vector3()));

            random.verify(() -> Utils.rand(1, 3));
            random.verify(() -> Utils.rand(1, 7), never());
        }

        assertOnlyFeedback(f, EntityEventPacket.TAME_FAIL);
        assertNull(f.wolf.getOwner());
        assertFalse(f.wolf.hasOwner());
        assertFalse(f.wolf.getDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_TAMED));
        assertEquals(8, f.wolf.getMaxHealth());
        assertEquals(8, f.wolf.getHealth());
        assertEquals(DyeColor.BLUE.getWoolData(), f.wolf.getDataPropertyByte(Entity.DATA_COLOUR));
        verify(f.level, never()).dropExpOrb(any(Vector3.class), anyInt());
        assertEquals(4, bones.getCount());
        verify(f.player, never()).getInventory();
    }

    @Test
    void angryWolfDoesNotEnterTamingTrial() {
        Fixture f = new Fixture();
        f.wolf.setAngry(true);

        try (MockedStatic<Utils> random = mockStatic(Utils.class)) {
            assertFalse(f.wolf.onInteract(f.player, Item.get(Item.BONE), new Vector3()));
            random.verifyNoInteractions();
        }

        assertTrue(f.wolf.isAngry());
        assertFalse(f.wolf.hasOwner());
        verify(f.player, never()).dataPacket(any(EntityEventPacket.class));
        verify(f.level, never()).dropExpOrb(any(Vector3.class), anyInt());
    }

    @Test
    void ownedWolfDoesNotEnterTamingTrial() {
        Fixture f = new Fixture();
        f.wolf.setOwner(f.player);

        try (MockedStatic<Utils> random = mockStatic(Utils.class)) {
            assertFalse(f.wolf.onInteract(f.player, Item.get(Item.BONE), new Vector3()));
            random.verifyNoInteractions();
        }

        assertSame(f.player, f.wolf.getOwner());
        verify(f.player, never()).dataPacket(any(EntityEventPacket.class));
        verify(f.level, never()).dropExpOrb(any(Vector3.class), anyInt());
    }

    private static void assertOnlyFeedback(Fixture f, int event) {
        ArgumentCaptor<EntityEventPacket> feedback = ArgumentCaptor.forClass(EntityEventPacket.class);
        verify(f.player).dataPacket(feedback.capture());
        assertEquals(event, feedback.getValue().event);
        assertEquals(f.wolf.getId(), feedback.getValue().eid);
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        final EntityWolf wolf;

        Fixture() {
            when(player.getId()).thenReturn(123L);
            when(player.isOnline()).thenReturn(true);
            when(player.getName()).thenReturn("wolf-owner");
            when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000123"));
            lenient().when(level.getServer()).thenReturn(MockServer.get());
            lenient().when(level.getMinBlockY()).thenReturn(-64);
            lenient().when(level.getMaxBlockY()).thenReturn(319);
            lenient().when(level.getGameRules()).thenReturn(GameRules.getDefault());
            lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
            lenient().when(level.getNearbyEntities(any(), any())).thenReturn(new Entity[0]);
            lenient().when(level.getNearbyEntities(any(), any(), anyBoolean(), anyBoolean()))
                    .thenReturn(new Entity[0]);
            lenient().when(level.getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt())).thenReturn(0);
            lenient().when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            try {
                Field converting = Level.class.getDeclaredField("isBeingConverted");
                converting.setAccessible(true);
                converting.setBoolean(level, true);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }

            FullChunk chunk = mock(FullChunk.class);
            LevelProvider provider = mock(LevelProvider.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(provider.getLevel()).thenReturn(level);
            wolf = new EntityWolf(chunk, baseNbt());
        }
    }

    private static CompoundTag baseNbt() {
        return new CompoundTag()
                .putByte("CollarColor", DyeColor.BLUE.getDyeData())
                .putList(new ListTag<>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)))
                .putList(new ListTag<>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)));
    }
}
