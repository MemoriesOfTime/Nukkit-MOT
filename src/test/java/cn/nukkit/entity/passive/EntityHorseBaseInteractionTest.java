package cn.nukkit.entity.passive;

import cn.nukkit.item.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class EntityHorseBaseInteractionTest {

    @Test
    void recognizesOnlyCurrentlyUsableHorseItems() {
        EntityHorseBase horse = mock(EntityHorseBase.class, CALLS_REAL_METHODS);
        doReturn(false).when(horse).isInLoveCooldown();

        assertTrue(horse.isItemHandledByHorse(Item.get(Item.WHEAT, 0, 1)));
        doReturn(true).when(horse).isInLoveCooldown();
        assertFalse(horse.isItemHandledByHorse(Item.get(Item.WHEAT, 0, 1)));

        doReturn(true).when(horse).canBeSaddled();
        assertTrue(horse.isItemHandledByHorse(Item.get(Item.SADDLE, 0, 1)));
        doReturn(true).when(horse).isSaddled();
        assertFalse(horse.isItemHandledByHorse(Item.get(Item.SADDLE, 0, 1)));
        doReturn(false).when(horse).canBeSaddled();
        assertFalse(horse.isItemHandledByHorse(Item.get(Item.SADDLE, 0, 1)));

        doReturn(true).when(horse).canWearHorseArmor();
        assertTrue(horse.isItemHandledByHorse(Item.get(Item.DIAMOND_HORSE_ARMOR, 0, 1)));
        doReturn(true).when(horse).hasHorseArmor();
        assertFalse(horse.isItemHandledByHorse(Item.get(Item.DIAMOND_HORSE_ARMOR, 0, 1)));
        doReturn(false).when(horse).canWearHorseArmor();
        assertFalse(horse.isItemHandledByHorse(Item.get(Item.DIAMOND_HORSE_ARMOR, 0, 1)));

        assertFalse(horse.isItemHandledByHorse(Item.get(Item.CHEST, 0, 1)));
        EntityLlama llama = mock(EntityLlama.class, CALLS_REAL_METHODS);
        assertFalse(llama.isItemHandledByHorse(Item.get(Item.DIAMOND_HORSE_ARMOR, 0, 1)));
    }

    @Test
    void donkeyHandlesChestOnlyWhenAdultAndUnchested() {
        EntityDonkey donkey = mock(EntityDonkey.class, CALLS_REAL_METHODS);
        doReturn(false).when(donkey).isBaby();
        doReturn(false).when(donkey).isChested();
        Item chest = Item.get(Item.CHEST, 0, 1);

        assertTrue(donkey.isItemHandledByHorse(chest));

        doReturn(true).when(donkey).isChested();
        assertFalse(donkey.isItemHandledByHorse(chest));
        doReturn(false).when(donkey).isChested();
        doReturn(true).when(donkey).isBaby();
        assertFalse(donkey.isItemHandledByHorse(chest));
    }
}
