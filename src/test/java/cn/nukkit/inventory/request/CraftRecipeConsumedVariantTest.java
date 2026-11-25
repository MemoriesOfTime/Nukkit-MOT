package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.special.FireworkRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CraftRecipeConsumedVariantTest {
    @BeforeAll static void initialize() { MockServer.init(); Item.initCreativeItems(); }
    @BeforeEach void resetServer() { MockServer.reset(); }

    @Test void autoMultiRecipeBatchSnapshotAndDeliveryScaleExactlyOnce() { exerciseFireworks(2, false); }
    @Test void cancelledAutoMultiRecipeBatchConsumesNothing() { exerciseFireworks(2, true); }
    @Test void singleAutoMultiRecipeKeepsItsOrdinaryOutputCount() { exerciseFireworks(1, false); }

    @Test void taggedInputBeforePlainInputKeepsTheirActualNbtAndCounts() {
        Item tagged = tagged("first", 2);
        Item plain = Item.get(Item.PAPER, 0, 3);
        Item[] snapshot = snapshot(List.of(tagged, plain));
        assertEquals(2, snapshot.length);
        assertEquals(2, snapshot[0].getCount());
        assertEquals("first", snapshot[0].getNamedTag().getString("serial"));
        assertEquals(3, snapshot[1].getCount());
        assertFalse(snapshot[1].hasCompoundTag());
        assertEquals(2, tagged.getCount());
        assertEquals(3, plain.getCount());
    }

    @Test void plainInputBeforeTaggedInputKeepsTheirActualNbtAndCounts() {
        Item[] snapshot = snapshot(List.of(Item.get(Item.PAPER, 0, 3), tagged("second", 2)));
        assertEquals(2, snapshot.length);
        assertEquals(3, snapshot[0].getCount());
        assertFalse(snapshot[0].hasCompoundTag());
        assertEquals(2, snapshot[1].getCount());
        assertEquals("second", snapshot[1].getNamedTag().getString("serial"));
    }

    @Test void equalNbtInputsStillAggregateWithoutBorrowingCallerStacks() {
        Item first = tagged("same", 2);
        Item second = tagged("same", 3);
        Item[] snapshot = snapshot(List.of(first, second));
        assertEquals(1, snapshot.length);
        assertEquals(5, snapshot[0].getCount());
        assertEquals("same", snapshot[0].getNamedTag().getString("serial"));
        assertEquals(2, first.getCount());
        assertEquals(3, second.getCount());
    }

    @Test void differentNbtInputsKeepBothSerials() {
        Item[] snapshot = snapshot(List.of(tagged("first", 2), tagged("second", 3)));
        assertEquals(2, snapshot.length);
        assertEquals("first", snapshot[0].getNamedTag().getString("serial"));
        assertEquals("second", snapshot[1].getNamedTag().getString("serial"));
        assertEquals(5, Arrays.stream(snapshot).mapToInt(Item::getCount).sum());
    }

    @Test void listenerInputEditsCannotMutateTheConsumedCallerStack() {
        Fixture fixture = new Fixture();
        Item source = tagged("original", 2);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof CraftItemEvent event) {
                event.getInput()[0].setCount(99);
                event.getInput()[0].setNamedTag(new CompoundTag().putString("serial", "listener"));
            }
            return null;
        }).when(fixture.plugins).callEvent(any());

        Item result = CraftRecipeActionProcessor.fireCraftItemEvent(fixture.player, new FireworkRecipe(),
                Item.get(Item.FIREWORKS, 0, 3), List.of(source), 1);

        assertNotNull(result);
        assertEquals(2, source.getCount());
        assertEquals("original", source.getNamedTag().getString("serial"));
    }

    private static Item[] snapshot(List<Item> consumed) {
        Fixture fixture = new Fixture();
        AtomicReference<Item[]> seen = new AtomicReference<>();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof CraftItemEvent event) seen.set(event.getInput());
            return null;
        }).when(fixture.plugins).callEvent(any());
        assertNotNull(CraftRecipeActionProcessor.fireCraftItemEvent(fixture.player, new FireworkRecipe(),
                Item.get(Item.FIREWORKS, 0, 3), consumed, 1));
        return seen.get();
    }

    private static Item tagged(String serial, int count) {
        return Item.get(Item.PAPER, 0, count).setNamedTag(new CompoundTag().putString("serial", serial));
    }

    private static void exerciseFireworks(int times, boolean cancel) {
        Fixture fixture = new Fixture();
        FireworkRecipe recipe = new FireworkRecipe();
        when(fixture.manager.getRecipeByNetworkId(recipe.getNetworkId())).thenReturn(recipe);
        CraftingGrid grid = fixture.ui.getCraftingGrid();
        grid.setItem(0, Item.get(Item.PAPER, 0, 64).autoAssignStackNetworkId(), false);
        grid.setItem(1, Item.get(Item.GUNPOWDER, 0, 64).autoAssignStackNetworkId(), false);
        AtomicInteger events = new AtomicInteger();
        AtomicInteger outputCount = new AtomicInteger();
        AtomicInteger consumedCount = new AtomicInteger();
        doAnswer(call -> {
            if (!(call.getArgument(0) instanceof CraftItemEvent event)) return null;
            events.incrementAndGet();
            consumedCount.set(Arrays.stream(event.getInput()).mapToInt(Item::getCount).sum());
            Item output = event.getTransaction().getPrimaryOutput().clone();
            outputCount.set(output.getCount());
            if (cancel) event.setCancelled();
            else {
                output.setNamedTag(output.getOrCreateNamedTag().putInt("batch_snapshot", output.getCount()));
                event.getTransaction().setPrimaryOutput(output);
            }
            return null;
        }).when(fixture.plugins).callEvent(any());
        ItemFirework output = (ItemFirework) Item.get(Item.FIREWORKS, 0, 3);
        output.setFlight(1);
        AutoCraftRecipeAction craft = new AutoCraftRecipeAction(recipe.getNetworkId(), times, times,
                List.of(new ItemDescriptorWithCount(new DefaultDescriptor(Item.PAPER, 0), times),
                        new ItemDescriptorWithCount(new DefaultDescriptor(Item.GUNPOWDER, 0), times)));
        ItemStackRequest request = new ItemStackRequest(1, new ItemStackRequestAction[]{craft,
                new CraftResultsDeprecatedAction(new Item[]{output}, 1),
                new ConsumeAction(times, new ItemStackRequestSlotData(ContainerSlotType.CRAFTING_INPUT, 28,
                        grid.getItem(0).getStackNetId(), null)),
                new ConsumeAction(times, new ItemStackRequestSlotData(ContainerSlotType.CRAFTING_INPUT, 29,
                        grid.getItem(1).getStackNetId(), null)),
                new CreateAction(0),
                new PlaceAction(3 * times, new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                        new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 5, 0, null))}, new String[0]);

        ItemStackRequestHandler.handleRequests(fixture.player, List.of(request));

        ArgumentCaptor<DataPacket> packets = ArgumentCaptor.forClass(DataPacket.class);
        verify(fixture.player, atLeastOnce()).dataPacket(packets.capture());
        ItemStackResponsePacket response = packets.getAllValues().stream()
                .filter(ItemStackResponsePacket.class::isInstance).map(ItemStackResponsePacket.class::cast)
                .reduce((first, second) -> second).orElseThrow();
        assertEquals(cancel ? ItemStackResponseStatus.ERROR : ItemStackResponseStatus.OK, response.entries.get(0).getResult());
        assertEquals(1, events.get());
        assertEquals(2 * times, consumedCount.get());
        assertEquals(3 * times, outputCount.get(), "event output represents the whole paid batch");
        assertEquals(cancel ? 64 : 64 - times, grid.getItem(0).getCount());
        assertEquals(cancel ? 64 : 64 - times, grid.getItem(1).getCount());
        Item delivered = fixture.inventory.getItem(5);
        if (cancel) assertTrue(delivered.isNull());
        else {
            assertEquals(3 * times, delivered.getCount(), "the event helper must return a per-craft output for later scaling");
            assertEquals(3 * times, delivered.getNamedTag().getInt("batch_snapshot"));
        }
        assertTrue(fixture.ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
    }

    private static final class Fixture {
        final Player player = mock(Player.class);
        final PlayerUIInventory ui = new PlayerUIInventory(player);
        final PlayerInventory inventory = new PlayerInventory(player);
        final PluginManager plugins = mock(PluginManager.class);
        final CraftingManager manager = mock(CraftingManager.class);

        Fixture() {
            player.protocol = GameVersion.V1_26_20.getProtocol();
            when(player.getServer()).thenReturn(MockServer.get());
            when(player.getName()).thenReturn("craft-variant-test");
            when(player.getGameVersion()).thenReturn(GameVersion.V1_26_20);
            when(player.getUIInventory()).thenReturn(ui);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
            when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
            PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
            when(player.getOffhandInventory()).thenReturn(offhand);
            when(player.getTopWindow()).thenReturn(Optional.empty());
            when(MockServer.get().getPluginManager()).thenReturn(plugins);
            when(MockServer.get().getCraftingManager()).thenReturn(manager);
        }
    }
}
