package cn.nukkit.item.food;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSuspiciousStew;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SuspiciousStewFoodTest {
    @BeforeEach
    void setUp() {
        MockServer.init();
        MockServer.reset();
        Effect.init();
    }

    @ParameterizedTest
    @CsvSource({
            "0,16,100", "1,8,100", "2,18,140", "3,15,140", "4,19,220", "5,23,6",
            "6,23,6", "7,12,60", "8,10,140", "9,20,140", "10,16,100", "11,15,140", "12,9,140"
    })
    void allMetadataEatAsBedrockFoodAndApplyLevelOneEffect(int meta, int effectId, int duration) {
        Food food = Food.getByRelative(Item.SUSPICIOUS_STEW, meta);
        assertNotNull(food, "missing suspicious stew metadata " + meta);
        assertEquals(32, new ItemSuspiciousStew(meta).getUseDuration());
        assertEquals(6, food.getRestoreFood());
        assertEquals(7.2f, food.getRestoreSaturation(), 0.0001f);

        Player player = mock(Player.class);
        when(player.getServer()).thenReturn(MockServer.get());
        PlayerFood foodData = mock(PlayerFood.class);
        when(player.getFoodData()).thenReturn(foodData);
        when(player.getViewers()).thenReturn(Map.of());
        PlayerInventory inventory = new PlayerInventory(player);
        when(player.getInventory()).thenReturn(inventory);

        assertTrue(food.eatenBy(player));
        verify(foodData).addFoodLevel(food);
        verify(player).addEffect(argThat(effect -> effect.getId() == effectId
                && effect.getDuration() == duration && effect.getAmplifier() == 0),
                eq(EntityPotionEffectEvent.Cause.FOOD));
        assertEquals(1, inventory.getContents().values().stream()
                .filter(item -> item.getId() == Item.BOWL).mapToInt(Item::getCount).sum());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {5, 6})
    void saturationStewActuallyRestoresAdditionalFoodAcrossItsSixTicks(int meta) {
        Player player = mock(Player.class);
        when(player.getServer()).thenReturn(MockServer.get());
        when(player.getViewers()).thenReturn(Map.of());
        PlayerFood foodData = new PlayerFood(player, 0, 0);
        when(player.getFoodData()).thenReturn(foodData);
        PlayerInventory inventory = new PlayerInventory(player);
        when(player.getInventory()).thenReturn(inventory);

        assertTrue(Food.getByRelative(Item.SUSPICIOUS_STEW, meta).eatenBy(player));
        assertEquals(6, foodData.getLevel());
        assertEquals(6, foodData.getFoodSaturationLevel(), 0.0001f);
        org.mockito.ArgumentCaptor<Effect> effectCaptor = org.mockito.ArgumentCaptor.forClass(Effect.class);
        verify(player).addEffect(effectCaptor.capture(), eq(EntityPotionEffectEvent.Cause.FOOD));
        Effect effect = effectCaptor.getValue();
        assertEquals(Effect.SATURATION, effect.getId());
        assertEquals(6, effect.getDuration());
        for (int tick = 0; tick < 6; tick++) {
            assertTrue(effect.canTick(), "saturation must apply every live tick");
            effect.applyEffect(player);
            effect.setDuration(effect.getDuration() - 1);
            assertEquals(7 + tick, foodData.getLevel());
            assertEquals(7 + tick, foodData.getFoodSaturationLevel(), 0.0001f);
        }
        assertEquals(0, effect.getDuration());
        assertEquals(12, foodData.getLevel());
        assertEquals(12, foodData.getFoodSaturationLevel(), 0.0001f);
    }

    @Test
    void stewCanStartEatingAtFullHunger() {
        Player player = mock(Player.class);
        when(player.canEat(true)).thenReturn(false);
        when(player.getGameVersion()).thenReturn(GameVersion.V1_26_50);
        when(player.isSurvival()).thenReturn(true);
        assertTrue(new ItemSuspiciousStew(0).onClickAir(player, new Vector3()));
    }

    @Test
    void stewPreservesAllExistingProtocolBoundaries() {
        for (int meta = 0; meta <= 9; meta++) {
            assertTrue(new ItemSuspiciousStew(meta).isSupportedOn(GameVersion.V1_20_0));
        }
        assertFalse(new ItemSuspiciousStew(10).isSupportedOn(GameVersion.V1_19_80));
        assertTrue(new ItemSuspiciousStew(10).isSupportedOn(GameVersion.V1_20_0));
        for (int meta : new int[]{11, 12}) {
            assertFalse(new ItemSuspiciousStew(meta).isSupportedOn(GameVersion.V1_20_0));
            assertFalse(new ItemSuspiciousStew(meta).isSupportedOn(GameVersion.V1_21_40));
            assertTrue(new ItemSuspiciousStew(meta).isSupportedOn(GameVersion.V1_21_50));
            assertTrue(new ItemSuspiciousStew(meta).isSupportedOn(GameVersion.V1_26_50));
        }
    }

    @Test
    void malformedStewMetadataIsNotAdvertisedOrEdible() {
        assertFalse(new ItemSuspiciousStew(-1).isSupportedOn(GameVersion.V1_26_50));
        assertFalse(new ItemSuspiciousStew(13).isSupportedOn(GameVersion.V1_26_50));
        assertFalse(new ItemSuspiciousStew(65535).isSupportedOn(GameVersion.V1_26_50));
        assertFalse(new ItemSuspiciousStew((Integer) null).isSupportedOn(GameVersion.V1_26_50));
        assertNull(Food.getByRelative(Item.SUSPICIOUS_STEW, 13));
    }
}
