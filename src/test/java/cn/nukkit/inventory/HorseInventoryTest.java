package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.passive.EntityHorseBase;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemNamespaceId;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

class HorseInventoryTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void armorSlotAcceptsStringBackedHorseArmor() {
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class);
        Mockito.when(horse.canWearHorseArmor()).thenReturn(true);
        Mockito.when(horse.getViewers()).thenReturn(Map.of());
        HorseInventory inventory = new HorseInventory(horse, 0);

        Item copper = Item.fromString(ItemNamespaceId.COPPER_HORSE_ARMOR);
        Item netherite = Item.fromString(ItemNamespaceId.NETHERITE_HORSE_ARMOR);

        assertTrue(copper.isHorseArmor());
        assertTrue(netherite.isHorseArmor());
        assertTrue(inventory.setItem(HorseInventory.SLOT_ARMOR, copper, false));
        assertEquals(ItemNamespaceId.COPPER_HORSE_ARMOR, inventory.getItem(HorseInventory.SLOT_ARMOR).getNamespaceId());
        assertTrue(inventory.setItem(HorseInventory.SLOT_ARMOR, netherite, false));
        assertEquals(ItemNamespaceId.NETHERITE_HORSE_ARMOR, inventory.getItem(HorseInventory.SLOT_ARMOR).getNamespaceId());
    }

    @Test
    void horseArmorAccessorsReadInventorySlot() throws Exception {
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class, Mockito.CALLS_REAL_METHODS);
        Mockito.doReturn(true).when(horse).canWearHorseArmor();
        Mockito.doReturn(Map.of()).when(horse).getViewers();
        HorseInventory inventory = new HorseInventory(horse, 0);
        setHorseInventory(horse, inventory);

        Item diamond = Item.get(Item.DIAMOND_HORSE_ARMOR, 0, 1);
        horse.setHorseArmor(diamond);

        assertTrue(horse.hasHorseArmor());
        assertEquals(Item.DIAMOND_HORSE_ARMOR, inventory.getItem(HorseInventory.SLOT_ARMOR).getId());
        assertEquals(Item.DIAMOND_HORSE_ARMOR, horse.getHorseArmor().getId());

        Item copper = Item.fromString(ItemNamespaceId.COPPER_HORSE_ARMOR);
        assertTrue(inventory.setItem(HorseInventory.SLOT_ARMOR, copper, false));

        assertTrue(horse.hasHorseArmor());
        assertEquals(ItemNamespaceId.COPPER_HORSE_ARMOR, horse.getHorseArmor().getNamespaceId());
    }

    @Test
    void armorSlotBroadcastIncludesBodyField() {
        Player viewer = Mockito.mock(Player.class);
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class);
        Mockito.when(horse.canWearHorseArmor()).thenReturn(true);
        Mockito.when(horse.getId()).thenReturn(77L);
        Mockito.when(horse.getViewers()).thenReturn(Map.of(1, viewer));
        HorseInventory inventory = new HorseInventory(horse, 0);

        Item armor = Item.get(Item.DIAMOND_HORSE_ARMOR, 0, 1);
        assertTrue(inventory.setItem(HorseInventory.SLOT_ARMOR, armor, false));

        MobArmorEquipmentPacket packet = capturePacket(viewer, MobArmorEquipmentPacket.class);
        assertEquals(77L, packet.eid);
        assertEquals(Item.DIAMOND_HORSE_ARMOR, packet.slots[1].getId());
        assertEquals(Item.DIAMOND_HORSE_ARMOR, packet.body.getId());
    }

    private static void setHorseInventory(EntityHorseBase horse, HorseInventory inventory) throws Exception {
        Field field = EntityHorseBase.class.getDeclaredField("horseInventory");
        field.setAccessible(true);
        field.set(horse, inventory);
    }

    private static <T extends DataPacket> T capturePacket(Player player, Class<T> type) {
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        verify(player, atLeastOnce()).dataPacket(captor.capture());
        for (DataPacket packet : captor.getAllValues()) {
            if (type.isInstance(packet)) {
                return type.cast(packet);
            }
        }
        fail("Expected packet " + type.getSimpleName());
        return null;
    }
}
