package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.mob.EntityWitch;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Loads actual entities from saved NBT, including callbacks run inside Entity's constructor. */
class EntitySavedMobStateTest {
    private static final int[] ARMOR = {
            Item.DIAMOND_HELMET, Item.DIAMOND_CHESTPLATE, Item.DIAMOND_LEGGINGS, Item.DIAMOND_BOOTS
    };

    @BeforeEach
    void initialize() {
        MockServer.init();
        Effect.init();
        clearInvocations(MockServer.get().getLogger());
    }

    @ParameterizedTest
    @CsvSource({"1, 0.12", "2, 0.085"})
    void savedMovementEffectSurvivesTheWholeWitchConstructor(int effectId, float expectedSpeed) {
        EntityWitch witch = new EntityWitch(chunk(), nbt().putList(new ListTag<CompoundTag>("ActiveEffects")
                .add(effect(effectId))));

        assertFalse(witch.closed);
        assertTrue(witch.hasEffect(effectId));
        assertEquals(1, witch.getMovementSpeedModifiers().size());
        assertEquals(expectedSpeed, witch.getMovementSpeed(), 0.000001f);

        witch.removeEffect(effectId);
        assertTrue(witch.getMovementSpeedModifiers().isEmpty());
        assertEquals(Player.DEFAULT_SPEED, witch.getMovementSpeed(), 0.000001f);
    }

    @Test
    void bothSavedMovementEffectsAndTheirSpeedSurviveSaveAndReload() {
        EntityWitch witch = new EntityWitch(chunk(), nbt().putList(new ListTag<CompoundTag>("ActiveEffects")
                .add(effect(Effect.SPEED)).add(effect(Effect.SLOWNESS))));
        assertEquals(0.102f, witch.getMovementSpeed(), 0.000001f);
        assertEquals(2, witch.getMovementSpeedModifiers().size());

        witch.saveNBT();
        EntityWitch restored = new EntityWitch(chunk(), witch.namedTag.clone());
        assertEquals(0.102f, restored.getMovementSpeed(), 0.000001f);
        assertEquals(2, restored.getMovementSpeedModifiers().size());
        restored.removeEffect(Effect.SPEED);
        assertEquals(0.085f, restored.getMovementSpeed(), 0.000001f);
        restored.removeEffect(Effect.SLOWNESS);
        assertEquals(Player.DEFAULT_SPEED, restored.getMovementSpeed(), 0.000001f);
    }

    @Test
    void newMobWithoutEffectsKeepsTheDefaultSpeedAndAcceptsLaterModifiers() {
        EntityWitch witch = new EntityWitch(chunk(), nbt());
        assertEquals(Player.DEFAULT_SPEED, witch.getMovementSpeed());
        assertTrue(witch.getMovementSpeedModifiers().isEmpty());
        witch.addEffect(Effect.getEffect(Effect.SPEED).setAmplifier(1).setDuration(600));
        assertEquals(0.12f, witch.getMovementSpeed(), 0.000001f);
    }

    @Test
    void playerWithDeferredEntityInitializationStillHasMovementStateAfterConstruction() {
        Player player = new Player(mock(SourceInterface.class), 1L, new InetSocketAddress("127.0.0.1", 1));
        assertEquals(Player.DEFAULT_SPEED, player.getMovementSpeed());
        assertTrue(player.getMovementSpeedModifiers().isEmpty());
    }

    @Test
    void legacyArmorWithoutSlotUsesItsListPosition() {
        EntityZombie zombie = zombie(armor(null, 0), armor(null, 1), armor(null, 2), armor(null, 3));
        assertArmor(zombie, ARMOR);
        verify(MockServer.get().getLogger(), never()).error(anyString());
        verify(MockServer.get().getLogger(), never()).warning(anyString());
    }

    @Test
    void explicitSlotsCanArriveInAnyOrder() {
        EntityZombie zombie = zombie(armor(3, 3), armor(1, 1), armor(0, 0), armor(2, 2));
        assertArmor(zombie, ARMOR);
        verify(MockServer.get().getLogger(), never()).error(anyString());
        verify(MockServer.get().getLogger(), never()).warning(anyString());
    }

    @Test
    void sparseExplicitArmorLeavesTheOtherSlotsEmpty() {
        assertArmor(zombie(armor(3, 3), armor(1, 1)), Item.AIR, ARMOR[1], Item.AIR, ARMOR[3]);
    }

    @Test
    void mixedExplicitAndPositionalArmorUsesTheOriginalListIndex() {
        assertArmor(zombie(armor(3, 3), armor(null, 1), armor(null, 2), armor(0, 0)), ARMOR);
    }

    @Test
    void invalidSlotDoesNotDiscardTheValidEntriesAfterIt() {
        assertArmor(zombie(armor(-1, 0), armor(2, 2), armor(4, 0), armor(1, 1), armor(3, 3)),
                Item.AIR, ARMOR[1], ARMOR[2], ARMOR[3]);
        verify(MockServer.get().getLogger()).warning(
                "Skipped 2 zombie armor entries with invalid or duplicate slots; valid entries retained");
        verify(MockServer.get().getLogger(), never()).error(anyString());
    }

    @Test
    void duplicateKeepsTheFirstItemAndDoesNotDiscardOtherSlots() {
        assertArmor(zombie(armor(0, 0), armor(0, 3), armor(2, 2), armor(1, 1), armor(3, 3)), ARMOR);
    }

    @Test
    void duplicateOfAnExplicitEmptySlotDoesNotFillIt() {
        assertArmor(zombie(NBTIO.putItemHelper(Item.AIR_ITEM, 0), armor(0, 0), armor(3, 3)),
                Item.AIR, Item.AIR, Item.AIR, ARMOR[3]);
    }

    @Test
    void positionalEntryBeyondTheFourArmorSlotsDoesNotReplaceAnyItem() {
        assertArmor(zombie(armor(null, 0), armor(null, 1), armor(null, 2), armor(null, 3), armor(null, 0)), ARMOR);
    }

    @Test
    void explicitlyEmptyArmorStaysEmpty() {
        assertArmor(zombie(), Item.AIR, Item.AIR, Item.AIR, Item.AIR);
    }

    @Test
    void legacyArmorIsSavedWithExplicitSlotsAndRestoresItsDamageAndCustomNbt() {
        EntityZombie zombie = zombie(armor(null, 0), armor(null, 1), armor(null, 2), armor(null, 3));
        zombie.saveNBT();
        ListTag<CompoundTag> saved = zombie.namedTag.getList("Armor", CompoundTag.class);
        assertEquals(4, saved.size());
        for (int slot = 0; slot < 4; slot++) {
            assertTrue(saved.get(slot).contains("Slot"));
            assertEquals(slot, saved.get(slot).getByte("Slot"));
        }
        EntityZombie restored = new EntityZombie(chunk(), zombie.namedTag.clone());
        assertArmor(restored, ARMOR);
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(slot + 5, restored.armor[slot].getDamage());
            assertEquals("armor-" + slot, restored.armor[slot].getNamedTag().getString("test_marker"));
        }
    }

    private static CompoundTag armor(Integer slot, int armorIndex) {
        Item item = Item.get(ARMOR[armorIndex], armorIndex + 5, 1);
        item.setNamedTag(new CompoundTag().putString("test_marker", "armor-" + armorIndex));
        return NBTIO.putItemHelper(item, slot);
    }

    private static EntityZombie zombie(CompoundTag... entries) {
        ListTag<CompoundTag> armor = new ListTag<>("Armor");
        for (CompoundTag entry : entries) {
            armor.add(entry);
        }
        return new EntityZombie(chunk(), nbt().putList(armor));
    }

    private static void assertArmor(EntityZombie zombie, int... ids) {
        assertFalse(zombie.closed);
        assertEquals(4, zombie.armor.length);
        for (int slot = 0; slot < 4; slot++) {
            assertNotNull(zombie.armor[slot]);
            assertEquals(ids[slot], zombie.armor[slot].getId(), "armor slot " + slot);
        }
    }

    private static CompoundTag effect(int effectId) {
        return new CompoundTag().putByte("Id", effectId).putByte("Amplifier", 1)
                .putInt("Duration", 600).putBoolean("ShowParticles", true);
    }

    private static CompoundTag nbt() {
        return Entity.getDefaultNBT(new Vector3(0.5, 64, 0.5));
    }

    private static FullChunk chunk() {
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(MockServer.get());
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        level.isBeingConverted = true;
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        return chunk;
    }
}
