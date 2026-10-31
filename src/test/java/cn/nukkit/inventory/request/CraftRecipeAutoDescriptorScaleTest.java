package cn.nukkit.inventory.request;

import cn.nukkit.*;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.descriptor.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Complete auto-crafting request regressions using native recipes and inventories. */
@Timeout(15)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class CraftRecipeAutoDescriptorScaleTest {
    static CraftingManager manager;
    static final GameVersion VERSION = GameVersion.byProtocol(2169, false);
    static final int REQUEST_ID = 806;

    @BeforeAll static void init() {
        MockServer.init();
        manager = new CraftingManager();
    }

    @AfterAll static void restoreDefaults() { MockServer.reset(); }

    static Stream<Arguments> blockCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String name : List.of("diamond", "emerald", "iron", "gold", "netherite")) {
            for (int times : List.of(1, 7, 64)) {
                for (boolean scaled : List.of(false, true)) {
                    cases.add(Arguments.of(name, times, scaled));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} x{1}, scaled-descriptor-control={2}")
    @MethodSource("blockCases")
    void advertisedBlockRecipeConservesItemsAndAcknowledgesOutput(
            String material, int times, boolean scaledDescriptors) {
        exercise(material, times, scaledDescriptors, false);
    }

    @ParameterizedTest(name = "wrong ingredient, scaled-descriptor-control={0}")
    @ValueSource(booleans = {false, true})
    void wrongIngredientCannotMintBlocks(boolean scaledDescriptors) {
        exercise("diamond", 7, scaledDescriptors, true);
    }

    static void exercise(String material, int times, boolean scaledDescriptors, boolean invalid) {
        exercise(material, times, scaledDescriptors, invalid, false);
    }

    static void exercise(String material, int times, boolean scaledDescriptors,
                         boolean invalid, boolean singleStack) {
        MockServer.reset();
        Item outputTemplate = Item.fromString("minecraft:" + material + "_block");
        Item ingredient = Item.fromString("minecraft:" + switch (material) {
            case "iron", "gold", "netherite" -> material + "_ingot";
            default -> material;
        });
        assertFalse(outputTemplate.isNull());
        assertFalse(ingredient.isNull());
        CraftingRecipe recipe = manager.getRecipes().stream()
                .filter(CraftingRecipe.class::isInstance).map(CraftingRecipe.class::cast)
                .filter(r -> r.getResult().equals(outputTemplate))
                .filter(r -> r.getResult().getCount() == 1 && r.getExtraResults().isEmpty())
                .filter(r -> r.getIngredientsAggregate().stream().allMatch(i -> i.equals(ingredient)))
                .filter(r -> r.getIngredientsAggregate().stream().mapToInt(Item::getCount).sum() == 9)
                .findFirst().orElseThrow(() -> new AssertionError("Missing published recipe for " + material));
        int recipeId = recipe instanceof ShapedRecipe shaped
                ? shaped.getNetworkId() : ((ShapelessRecipe) recipe).getNetworkId();
        assertSame(recipe, manager.getRecipeByNetworkId(recipeId));

        Player player = mock(Player.class);
        player.protocol = 2169;
        player.spawned = true;
        player.craftingType = Player.CRAFTING_BIG;
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        when(player.getServer()).thenReturn(MockServer.get());
        when(player.getGameVersion()).thenReturn(VERSION);
        when(player.getName()).thenReturn("bulk-crafting-test");
        when(player.isAlive()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getUIInventory()).thenReturn(ui);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getCraftingGrid()).thenReturn(ui.getBigCraftingGrid());
        when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        when(player.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(player));
        when(player.getTopWindow()).thenReturn(Optional.empty());
        when(MockServer.get().getPluginManager()).thenReturn(mock(PluginManager.class));
        when(MockServer.get().getCraftingManager()).thenReturn(manager);
        when(MockServer.get().isPrimaryThread()).thenReturn(true);

        // Nine real 64-item stacks, one per recipe cell. No over-sized synthetic stacks.
        // Each CONSUME is a total amount; recipe descriptors use count1 or scaled control.
        List<ItemDescriptorWithCount> descriptors = new ArrayList<>();
        List<Item> before = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Item input = invalid && i == 0 ? Item.fromString("minecraft:gold_ingot") : ingredient.clone();
            input.setCount(singleStack ? 10 : 64);
            input.autoAssignStackNetworkId();
            if (!singleStack || i == 0) {
                assertTrue(inventory.setItem(singleStack ? 0 : i, input, false));
                before.add(inventory.getItem(singleStack ? 0 : i).clone());
            }
            descriptors.add(new ItemDescriptorWithCount(
                    new DefaultDescriptor(ingredient.getId(), ingredient.getDamage()),
                    scaledDescriptors ? times : 1));
        }
        List<ItemStackRequestAction> actions = new ArrayList<>();
        actions.add(new AutoCraftRecipeAction(recipeId, times, times, descriptors));
        for (int i = 0; i < 9; i++) {
            int slot = singleStack ? 0 : i;
            int stackId = singleStack && i > 0 ? -REQUEST_ID : inventory.getItem(slot).getStackNetId();
            actions.add(new ConsumeAction(times, new ItemStackRequestSlotData(
                    singleStack ? ContainerSlotType.HOTBAR : ContainerSlotType.INVENTORY,
                    slot, stackId, null)));
        }
        actions.add(new PlaceAction(times,
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT, 50, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 20, 0, null)));

        ItemStackRequestHandler.handleRequests(player, List.of(new ItemStackRequest(
                REQUEST_ID, actions.toArray(ItemStackRequestAction[]::new), new String[0])));
        ArgumentCaptor<DataPacket> packets = ArgumentCaptor.forClass(DataPacket.class);
        verify(player, atLeastOnce()).dataPacket(packets.capture());
        ItemStackResponsePacket response = packets.getAllValues().stream()
                .filter(ItemStackResponsePacket.class::isInstance).map(ItemStackResponsePacket.class::cast)
                .reduce((a, b) -> b).orElseThrow();
        assertEquals(1, response.entries.size());
        var entry = response.entries.get(0);
        assertEquals(REQUEST_ID, entry.getRequestId());
        boolean accepted = entry.getResult() == ItemStackResponseStatus.OK;

        // Assert conservation and authoritative responses on both acceptance and refusal.
        for (int i = 0; i < (singleStack ? 1 : 9); i++) {
            if (!accepted) assertTrue(before.get(i).equalsExact(inventory.getItem(i)), "refusal changed source " + i);
            else assertEquals(singleStack ? 10 - 9 * times : 64 - times,
                    inventory.getItem(i).getCount(), "wrong debit at " + i);
        }
        assertTrue(ui.getItem(50).isNull(), "result must not remain ghosted in CREATED_OUTPUT");
        if (accepted) {
            assertTrue(outputTemplate.equals(inventory.getItem(20)));
            assertEquals(times, inventory.getItem(20).getCount());
            var ack = entry.getContainers().stream()
                    .filter(c -> c.getContainer() == ContainerSlotType.INVENTORY)
                    .flatMap(c -> c.getItems().stream()).filter(s -> s.getSlot() == 20)
                    .findFirst().orElseThrow(() -> new AssertionError("No authoritative destination slot ACK"));
            assertEquals(times, ack.getCount());
            assertEquals(inventory.getItem(20).getStackNetId(), ack.getStackNetworkId());
            int remainingUnits = inventory.getContents().values().stream()
                    .filter(i -> i.equals(ingredient)).mapToInt(Item::getCount).sum();
            assertEquals(singleStack ? 10 : 9 * 64, remainingUnits + 9 * inventory.getItem(20).getCount());
        } else {
            assertTrue(inventory.getItem(20).isNull());
            assertTrue(entry.getContainers().isEmpty(), "ERROR must not ACK invented output");
        }
        assertEquals(invalid ? ItemStackResponseStatus.ERROR : ItemStackResponseStatus.OK,
                entry.getResult(), material + " x" + times + " scaled=" + scaledDescriptors);
    }
}
