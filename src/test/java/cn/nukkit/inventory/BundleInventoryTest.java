package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.item.*;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BundleInventoryTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void saveNbtKeepsBundleContentsAndCloneDoesNotShareInventoryInstance() {
        ItemBundle bundle = new ItemBundle();
        int bundleId = bundle.getBundleId();
        BundleInventory inventory = bundle.getInventory();

        assertTrue(inventory.setItem(0, Item.get(Item.STONE, 0, 16), false));

        ListTag<CompoundTag> storedItems = bundle.getNamedTag()
                .getList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT, CompoundTag.class);
        assertEquals(1, storedItems.size());

        ItemBundle clone = bundle.clone();
        assertEquals(bundleId, clone.getBundleId());
        assertNotSame(inventory, clone.getInventory());
        assertEquals(Item.STONE, clone.getInventory().getItem(0).getId());
        assertEquals(16, clone.getInventory().getItem(0).getCount());
    }

    @Test
    void rejectsItemsThatWouldOverfillTheBundle() {
        ItemBundle bundle = new ItemBundle();
        BundleInventory inventory = bundle.getInventory();

        assertFalse(inventory.setItem(0, Item.get(Item.DIRT, 0, 65), false));
        assertTrue(inventory.isEmpty());
    }

    @Test
    void rejectsShulkerBoxesLikeOtherContainerInventories() {
        ItemBundle bundle = new ItemBundle();
        BundleInventory inventory = bundle.getInventory();

        assertFalse(inventory.setItem(0, Item.get(Item.SHULKER_BOX, 0, 1), false));
        assertFalse(inventory.setItem(0, Item.get(Item.UNDYED_SHULKER_BOX, 0, 1), false));
        assertTrue(inventory.isEmpty());
    }

    @Test
    void nestedBundleWeightIncludesInnerContentsAndBaseCost() {
        ItemBundle outer = new ItemBundle();
        ItemBundle inner = new ItemBundle();
        assertTrue(inner.getInventory().setItem(0, Item.get(Item.DIRT, 0, 16), false));

        BundleInventory outerInventory = outer.getInventory();
        assertTrue(outerInventory.setItem(0, inner, false));

        assertEquals(20, outerInventory.getWeight());
        assertTrue(outerInventory.setItem(1, Item.get(Item.STONE, 0, 44), false));
        assertFalse(outerInventory.setItem(2, Item.get(Item.DIRT, 0, 1), false));
    }

    @Test
    void rejectsBundleCycles() {
        ItemBundle outer = new ItemBundle();
        ItemBundle inner = new ItemBundle();

        assertFalse(outer.getInventory().setItem(0, outer, false));
        assertTrue(outer.getInventory().setItem(0, inner, false));
        assertFalse(inner.getInventory().setItem(0, outer, false));
    }

    @Test
    void persistedNamedTagRestoresStoredContentsOnFreshBundleInstance() {
        ItemBundle source = new ItemBundle();
        assertTrue(source.getInventory().setItem(5, Item.get(Item.APPLE, 0, 7), false));

        ItemBundle restored = new ItemBundle();
        restored.setNamedTag(source.getNamedTag().copy());

        assertNotEquals(source.getBundleId(), restored.getBundleId());
        assertEquals(Item.APPLE, restored.getInventory().getItem(5).getId());
        assertEquals(7, restored.getInventory().getItem(5).getCount());
    }

    @Test
    void clickAirDropsStoredItemAndUpdatesNbt() {
        Player player = Mockito.mock(Player.class);
        Level level = Mockito.mock(Level.class);
        Mockito.when(player.dropItem(Mockito.any(Item.class))).thenReturn(true);
        Mockito.when(player.getLevel()).thenReturn(level);

        ItemBundle bundle = new ItemBundle();
        BundleInventory inventory = bundle.getInventory();
        assertTrue(inventory.setItem(3, Item.get(Item.DIRT, 0, 5), false));

        assertTrue(bundle.onClickAir(player, new Vector3(0, 0, 1)));

        ArgumentCaptor<Item> dropped = ArgumentCaptor.forClass(Item.class);
        Mockito.verify(player).dropItem(dropped.capture());
        assertEquals(Item.DIRT, dropped.getValue().getId());
        assertEquals(5, dropped.getValue().getCount());
        assertTrue(inventory.getItem(3).isNull());
        assertEquals(0, bundle.getNamedTag()
                .getList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT, CompoundTag.class)
                .size());
        Mockito.verify(level).addSound(Mockito.eq(player), Mockito.eq(Sound.BUNDLE_DROP_CONTENTS));
    }

    @Test
    void sendsDynamicContainerPacketsOnlyToBundleCapableProtocols() {
        Player oldPlayer = Mockito.mock(Player.class);
        oldPlayer.spawned = true;
        oldPlayer.protocol = ProtocolInfo.v1_21_20;
        Player newPlayer = Mockito.mock(Player.class);
        newPlayer.spawned = true;
        newPlayer.protocol = ProtocolInfo.v1_21_40;

        ItemBundle bundle = new ItemBundle();
        assertTrue(bundle.getInventory().setItem(0, Item.get(Item.STONE, 0, 1), false));

        bundle.getInventory().sendContents(oldPlayer, newPlayer);
        Mockito.verify(oldPlayer, Mockito.never()).dataPacket(Mockito.any(DataPacket.class));
        InventoryContentPacket content = capturePacket(newPlayer, InventoryContentPacket.class);
        assertEquals(BundleInventory.DYNAMIC_REGISTRY_WINDOW_ID, content.inventoryId);
        assertEquals(ContainerSlotType.DYNAMIC_CONTAINER, content.containerNameData.getContainer());
        assertEquals(bundle.getBundleId(), content.containerNameData.getDynamicId());
        assertEquals(Item.BUNDLE, content.storageItem.getNamespaceId());

        Mockito.reset(newPlayer);
        newPlayer.spawned = true;
        newPlayer.protocol = ProtocolInfo.v1_21_40;
        bundle.getInventory().sendSlot(0, oldPlayer, newPlayer);
        Mockito.verify(oldPlayer, Mockito.never()).dataPacket(Mockito.any(DataPacket.class));
        InventorySlotPacket slot = capturePacket(newPlayer, InventorySlotPacket.class);
        assertEquals(BundleInventory.DYNAMIC_REGISTRY_WINDOW_ID, slot.inventoryId);
        assertEquals(ContainerSlotType.DYNAMIC_CONTAINER, slot.containerNameData.getContainer());
        assertEquals(bundle.getBundleId(), slot.containerNameData.getDynamicId());
        assertEquals(Item.BUNDLE, slot.storageItem.getNamespaceId());
    }

    @ParameterizedTest
    @MethodSource("coloredBundleVariants")
    void coloredBundleVariantsShareBundleInventoryBehavior(ItemBundle bundle) {
        assertEquals(1, bundle.getMaxStackSize());
        assertTrue(bundle.isSupportedOn(GameVersion.V1_21_40));
        assertSame(bundle, bundle.getInventory().getHolder());

        assertTrue(bundle.getInventory().setItem(0, Item.get(Item.STONE, 0, 1), false));
        assertEquals(1, bundle.getNamedTag()
                .getList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT, CompoundTag.class)
                .size());
    }

    static Stream<ItemBundle> coloredBundleVariants() {
        return Stream.of(
                new ItemBundleWhite(),
                new ItemBundleLightGray(),
                new ItemBundleGray(),
                new ItemBundleBlack(),
                new ItemBundleBrown(),
                new ItemBundleRed(),
                new ItemBundleOrange(),
                new ItemBundleYellow(),
                new ItemBundleLime(),
                new ItemBundleGreen(),
                new ItemBundleCyan(),
                new ItemBundleLightBlue(),
                new ItemBundleBlue(),
                new ItemBundlePurple(),
                new ItemBundleMagenta(),
                new ItemBundlePink()
        );
    }

    private static <T extends DataPacket> T capturePacket(Player player, Class<T> type) {
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        Mockito.verify(player).dataPacket(captor.capture());
        return assertInstanceOf(type, captor.getValue());
    }
}
