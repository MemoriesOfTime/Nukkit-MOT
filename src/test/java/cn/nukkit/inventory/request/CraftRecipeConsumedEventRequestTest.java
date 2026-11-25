package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.descriptor.DefaultDescriptor;
import cn.nukkit.network.protocol.types.inventory.descriptor.ItemDescriptorWithCount;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class CraftRecipeConsumedEventRequestTest {
    @BeforeAll
    static void init() {
        MockServer.init();
        Item.initCreativeItems();
    }

    @Test
    void manualCraftPublishesConsumedCountFromFullGridAndDeliversAuthoredNbt() {
        exercise(false, 1, false);
    }

    @Test
    void autoRecipeBookReadsRealPaidStackAndDeliversAuthoredNbt() {
        exercise(true, 1, false);
    }

    @Test
    void manualAndAutoBatchSnapshotsScaleExactlyOnce() {
        // The created-output UI slot retains the vanilla item stack limit. A stackable
        // fixture proves scaling independently of the existing one-minecart capacity.
        exercise(false, 2, false, Item.TNT);
        exercise(true, 2, false, Item.TNT);
    }

    @Test
    void unstackableBatchCannotLoseInputsWhenCreatedSlotCapacityRejectsIt() {
        exercise(false, 2, false);
        exercise(true, 2, false);
    }

    @Test
    void cancellingEitherCraftPathConsumesNoneAndCreatesNoMinecart() {
        exercise(false, 1, true);
        exercise(true, 1, true);
    }

    private static void exercise(boolean auto, int times, boolean cancel) {
        exercise(auto, times, cancel, Item.TNT_MINECART);
    }

    private static void exercise(boolean auto, int times, boolean cancel, int resultId) {
        MockServer.reset();
        Player player = Mockito.mock(Player.class);
        player.protocol = GameVersion.V1_26_20.getProtocol();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        PluginManager plugins = Mockito.mock(PluginManager.class);
        CraftingManager manager = Mockito.mock(CraftingManager.class);
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("craft-test");
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_26_20);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(player));
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(plugins);
        Mockito.when(MockServer.get().getCraftingManager()).thenReturn(manager);
        ShapelessRecipe recipe = new ShapelessRecipe("test:paid_tnt_cart", 10,
                Item.get(resultId), List.of(Item.get(Item.TNT), Item.get(Item.MINECART)));
        Mockito.when(manager.getRecipeByNetworkId(recipe.getNetworkId())).thenReturn(recipe);
        Mockito.when(manager.matchRecipe(anyList(), any(Item.class), anyList())).thenReturn(recipe);

        Inventory source = auto ? inventory : ui.getCraftingGrid();
        source.setItem(0, Item.get(Item.TNT, 0, 64).setNamedTag(new CompoundTag()
                .putString("custom_payload", "special_cart").putString("custom_serial", "fixture-identity"))
                .autoAssignStackNetworkId(), false);
        for (int i = 0; i < times; i++) {
            source.setItem(1 + i, Item.get(Item.MINECART).autoAssignStackNetworkId(), false);
        }
        AtomicInteger seen = new AtomicInteger();
        Mockito.doAnswer(call -> {
            if (!(call.getArgument(0) instanceof CraftItemEvent event)) return null;
            seen.incrementAndGet();
            Item tnt = java.util.Arrays.stream(event.getInput())
                    .filter(item -> item.getId() == Item.TNT).findFirst().orElseThrow();
            assertEquals(times, tnt.getCount(), "event contains consumed amount, not the 64-item grid stack");
            assertEquals("special_cart", tnt.getNamedTag().getString("custom_payload"));
            Item output = event.getTransaction().getPrimaryOutput();
            assertEquals(times, output.getCount(), "batch output and consumed charge amount agree");
            if (cancel) {
                event.setCancelled();
            } else {
                output.setNamedTag(tnt.getNamedTag().copy());
                event.getTransaction().setPrimaryOutput(output);
            }
            return null;
        }).when(plugins).callEvent(any());
        ContainerSlotType inputType = auto ? ContainerSlotType.HOTBAR : ContainerSlotType.CRAFTING_INPUT;
        int offset = auto ? 0 : 28;
        ItemStackRequestAction craft = auto ? new AutoCraftRecipeAction(recipe.getNetworkId(), times, times,
                List.of(new ItemDescriptorWithCount(new DefaultDescriptor(Item.TNT, 0), times),
                        new ItemDescriptorWithCount(new DefaultDescriptor(Item.MINECART, 0), times)))
                : new CraftRecipeAction(recipe.getNetworkId(), times);
        List<ItemStackRequestAction> actions = new ArrayList<>();
        actions.add(craft);
        actions.add(new ConsumeAction(times, new ItemStackRequestSlotData(inputType, offset,
                source.getItem(0).getStackNetId(), null)));
        for (int i = 0; i < times; i++) {
            actions.add(new ConsumeAction(1, new ItemStackRequestSlotData(inputType, offset + 1 + i,
                    source.getItem(1 + i).getStackNetId(), null)));
        }
        for (int i = 0; i < times; i++) {
            actions.add(new PlaceAction(1,
                    new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                            PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                    new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 5 + i, 0, null)));
        }
        ItemStackRequestHandler.handleRequests(player, List.of(new ItemStackRequest(1,
                actions.toArray(ItemStackRequestAction[]::new), new String[0])));
        ArgumentCaptor<DataPacket> packets = ArgumentCaptor.forClass(DataPacket.class);
        Mockito.verify(player, Mockito.atLeastOnce()).dataPacket(packets.capture());
        ItemStackResponsePacket response = packets.getAllValues().stream()
                .filter(ItemStackResponsePacket.class::isInstance).map(ItemStackResponsePacket.class::cast)
                .reduce((first, second) -> second).orElseThrow();
        boolean rejected = cancel || times > Item.get(resultId).getMaxStackSize();
        assertEquals(rejected ? ItemStackResponseStatus.ERROR : ItemStackResponseStatus.OK,
                response.entries.get(0).getResult());
        assertEquals(1, seen.get());
        assertEquals(rejected ? 64 : 64 - times, source.getItem(0).getCount());
        assertEquals("fixture-identity", source.getItem(0).getNamedTag().getString("custom_serial"));
        for (int i = 0; i < times; i++) {
            Item result = inventory.getItem(5 + i);
            if (rejected) {
                assertTrue(result.isNull());
            } else {
                assertEquals(resultId, result.getId());
                assertEquals("special_cart", result.getNamedTag().getString("custom_payload"));
                assertEquals("fixture-identity", result.getNamedTag().getString("custom_serial"));
            }
        }
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
    }
}
