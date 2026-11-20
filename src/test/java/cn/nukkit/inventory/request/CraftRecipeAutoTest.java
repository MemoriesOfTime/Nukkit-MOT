package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Complete auto-crafting request regressions using native recipes and inventories. */
@Timeout(15)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class CraftRecipeAutoTest {
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

    /** Additional adversarial controls for the count compatibility change; no production mocks of recipe matching. */
    enum Scenario {
        MIXED_COUNTS, UNDER_CONSUME, ONE_RECIPE_FOR_TWO_OUTPUTS, EXTRA_CONSUME, WRONG_INGREDIENT,
        EXTRA_ITEM, BAD_STACK_ID, CANCEL_CRAFT, CANCEL_LAST_CONSUME,
        REFUSE_LAST_CONSUME, REPEATED_SOURCE_EXHAUSTION, INTEGER_WRAP_BOUNDARY
    }

    @ParameterizedTest(name = "{0}") @EnumSource(Scenario.class)
    void wholeRequestPreservesSafety(Scenario scenario) {
        int times = scenario == Scenario.INTEGER_WRAP_BOUNDARY ? 4
                : scenario == Scenario.ONE_RECIPE_FOR_TWO_OUTPUTS ? 2 : 3;
        Fixture f = new Fixture(times, scenario == Scenario.REFUSE_LAST_CONSUME);
        CraftingRecipe recipe = manager.getRecipes().stream().filter(CraftingRecipe.class::isInstance)
                .map(CraftingRecipe.class::cast)
                .filter(r -> r.getResult().equals(Item.fromString("minecraft:diamond_block")))
                .filter(r -> r.getResult().getCount() == 1 && r.getExtraResults().isEmpty())
                .filter(r -> r.getIngredientsAggregate().stream().allMatch(i -> i.getId() == Item.DIAMOND))
                .filter(r -> r.getIngredientsAggregate().stream().mapToInt(Item::getCount).sum() == 9)
                .findFirst().orElseThrow();
        int id = recipe instanceof ShapedRecipe shaped ? shaped.getNetworkId() : ((ShapelessRecipe) recipe).getNetworkId();
        List<ItemDescriptorWithCount> descriptors = new ArrayList<>();
        List<ItemStackRequestAction> consumes = new ArrayList<>();
        List<Item> before = new ArrayList<>();
        boolean repeated = scenario == Scenario.REPEATED_SOURCE_EXHAUSTION;
        int sources = scenario == Scenario.UNDER_CONSUME ? 8
                : scenario == Scenario.EXTRA_CONSUME || scenario == Scenario.EXTRA_ITEM ? 10 : 9;
        for (int i = 0; i < sources; i++) {
            boolean wrong = (scenario == Scenario.WRONG_INGREDIENT && i == 8)
                    || (scenario == Scenario.EXTRA_ITEM && i == 9);
            int slot = repeated ? 0 : i;
            Item item = Item.get(wrong ? Item.GOLD_INGOT : Item.DIAMOND, 0, repeated ? 10 : 64).autoAssignStackNetworkId();

            if (!repeated || i == 0) {
                assertTrue(f.inventory.setItem(slot, item, false));
                before.add(f.inventory.getItem(slot).clone());
            }
            int descriptorCount = scenario == Scenario.MIXED_COUNTS && i % 2 == 0 ? times : 1;
            // Deliberately malformed in-memory count; not representable by protocol2169's short.
            // A naive int multiplication by4 wraps1073741825 to4 and could falsely admit it.
            if (scenario == Scenario.INTEGER_WRAP_BOUNDARY) descriptorCount = 1073741825;
            descriptors.add(new ItemDescriptorWithCount(new DefaultDescriptor(item.getId(), 0), descriptorCount));
            int count = scenario == Scenario.ONE_RECIPE_FOR_TWO_OUTPUTS ? 1 : times;
            int networkId = repeated && i > 0 ? -8806 : f.inventory.getItem(slot).getStackNetId();
            if (i == 0 && scenario == Scenario.BAD_STACK_ID) networkId += 123;
            consumes.add(new ConsumeAction(count, new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, slot, networkId, null)));
        }
        AtomicInteger craftEvents = new AtomicInteger(), consumeEvents = new AtomicInteger();
        doAnswer(call -> {
            Object event = call.getArgument(0);
            if (event instanceof CraftItemEvent craft) {
                craftEvents.incrementAndGet();
                if (scenario == Scenario.CANCEL_CRAFT) craft.setCancelled();

            }
            if (event instanceof InventoryTransactionEvent transaction) {
                if (consumeEvents.incrementAndGet() == 9 && scenario == Scenario.CANCEL_LAST_CONSUME) transaction.setCancelled();
            }
            return null;
        }).when(f.plugins).callEvent(any());
        f.inventory.armed = true;
        List<ItemStackRequestAction> actions = new ArrayList<>();
        actions.add(new AutoCraftRecipeAction(id, times, times, descriptors));
        actions.addAll(consumes);
        actions.add(new PlaceAction(times, new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT, 50, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 20, 0, null)));
        ItemStackRequestHandler.handleRequests(f.player, List.of(new ItemStackRequest(8806,
                actions.toArray(ItemStackRequestAction[]::new), new String[0])));
        ArgumentCaptor<DataPacket> packets = ArgumentCaptor.forClass(DataPacket.class);
        verify(f.player, atLeastOnce()).dataPacket(packets.capture());
        ItemStackResponsePacket packet = packets.getAllValues().stream().filter(ItemStackResponsePacket.class::isInstance)
                .map(ItemStackResponsePacket.class::cast).reduce((a, b) -> b).orElseThrow();
        assertEquals(1, packet.entries.size());
        var reply = packet.entries.get(0);
        assertEquals(8806, reply.getRequestId());
        boolean success = false;
        assertEquals(success ? ItemStackResponseStatus.OK : ItemStackResponseStatus.ERROR, reply.getResult());
        for (int i = 0; i < before.size(); i++) {
            if (success) assertEquals(64 - times, f.inventory.getItem(i).getCount());
            else {
                assertTrue(before.get(i).equalsExact(f.inventory.getItem(i)), "source content changed at" + i);
                assertEquals(before.get(i).getStackNetId(), f.inventory.getItem(i).getStackNetId(), "source identity changed at" + i);
            }
        }
        assertTrue(f.ui.getItem(50).isNull());
        if (success) {
            assertEquals(times, f.inventory.getItem(20).getCount());
        } else {
            assertTrue(f.inventory.getItem(20).isNull());
            assertTrue(reply.getContainers().isEmpty());
        }
        assertEquals(1, craftEvents.get(), "The native processor emits the event before plan validation");
        if (scenario == Scenario.CANCEL_LAST_CONSUME) assertEquals(9, consumeEvents.get());
        if (scenario == Scenario.REFUSE_LAST_CONSUME) assertTrue(f.inventory.refused, "refusal hook must actually be exercised");
        if (repeated) assertEquals(3, consumeEvents.get(), "three debits happen before the fourth consume sees only one item");
    }

    @Test void multiplierFallbackDoesNotBroadenDynamicMultiRecipe() {
        Fixture f = new Fixture(2, false);
        AtomicInteger dynamicValidation = new AtomicInteger();
        MultiRecipe dynamic = new MultiRecipe(UUID.fromString("00000000-0000-0000-0000-000000008806")) {
            @Override public boolean canExecute(Player player, Item output, List<Item> inputs) {
                dynamicValidation.incrementAndGet(); return true;
            }
        };
        manager.registerMultiRecipe(dynamic);
        List<ItemDescriptorWithCount> descriptors = new ArrayList<>();
        List<ItemStackRequestAction> actions = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Item item = Item.get(Item.DIAMOND, 0, 64).autoAssignStackNetworkId();
            assertTrue(f.inventory.setItem(i, item, false));
            descriptors.add(new ItemDescriptorWithCount(new DefaultDescriptor(Item.DIAMOND, 0), 1));
        }
        AutoCraftRecipeAction craft = new AutoCraftRecipeAction(dynamic.getNetworkId(), 2, 2, descriptors);
        actions.add(craft);
        actions.add(new CraftResultsDeprecatedAction(new Item[]{Item.fromString("minecraft:diamond_block")}, 1));
        for (int i = 0; i < 2; i++) actions.add(new ConsumeAction(2,
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, i, f.inventory.getItem(i).getStackNetId(), null)));
        ItemStackRequestContext context = new ItemStackRequestContext(new ItemStackRequest(8807,
                actions.toArray(ItemStackRequestAction[]::new), new String[0]));
        context.setCurrentActionIndex(0);
        ActionResponse result = new CraftRecipeAutoProcessor().handle(craft, f.player, context);
        assertNotNull(result); assertFalse(result.success());
        assertEquals(0, dynamicValidation.get(), "excluded new form must not reach dynamic recipe validation");
        assertEquals(64, f.inventory.getItem(0).getCount());
        assertEquals(64, f.inventory.getItem(1).getCount());
        assertTrue(f.ui.getItem(50).isNull());
    }

    static Stream<Arguments> protocolModels() {
        return Stream.of(GameVersion.V1_20_0, GameVersion.V1_21_20, GameVersion.V1_26_50)
                .flatMap(version -> Stream.of(false, true).flatMap(scaled ->
                        Stream.of(false, true).map(extra -> Arguments.of(version, scaled, extra))));
    }

    /** Post-decode handler models, not proof that an old client emits this wire representation. */
    @ParameterizedTest(name = "protocol={0}, totalDescriptors={1}, extraConsume={2}")
    @MethodSource("protocolModels")
    void protocolEndpointsPreserveTotalCompatibilityAndConservation(GameVersion version, boolean scaled, boolean extra) {
        int times = 3, requestId = 8810;
        Fixture f = new Fixture(times, false, version);
        CraftingRecipe recipe = recipeFor("minecraft:diamond_block", Item.DIAMOND, 9);
        List<ItemDescriptorWithCount> descriptors = new ArrayList<>();
        List<ItemStackRequestAction> actions = new ArrayList<>();
        List<Item> before = new ArrayList<>();
        for (int i = 0; i < (extra ? 10 : 9); i++) {
            assertTrue(f.inventory.setItem(i, Item.get(Item.DIAMOND, 0, 64).autoAssignStackNetworkId(), false));
            before.add(f.inventory.getItem(i).clone());
            descriptors.add(new ItemDescriptorWithCount(new DefaultDescriptor(Item.DIAMOND, 0), scaled ? times : 1));
            actions.add(new ConsumeAction(times, new ItemStackRequestSlotData(ContainerSlotType.INVENTORY,
                    i, f.inventory.getItem(i).getStackNetId(), null)));
        }
        actions.add(0, new AutoCraftRecipeAction(recipeId(recipe), times, times, descriptors));
        actions.add(placeOutput(times));
        ItemStackResponsePacket packet = runRequest(f, requestId, actions);
        assertEquals(version.getProtocol(), packet.protocol);
        assertEquals(version, packet.gameVersion);
        var reply = packet.entries.get(0);
        assertEquals(extra ? ItemStackResponseStatus.ERROR : ItemStackResponseStatus.OK, reply.getResult());
        for (int i = 0; i < before.size(); i++) {
            if (extra) {
                assertTrue(before.get(i).equalsExact(f.inventory.getItem(i)));
                assertEquals(before.get(i).getStackNetId(), f.inventory.getItem(i).getStackNetId());
            } else assertEquals(64 - times, f.inventory.getItem(i).getCount());
        }
        assertTrue(f.ui.getItem(50).isNull());
        if (extra) {
            assertTrue(f.inventory.getItem(20).isNull());
            assertTrue(reply.getContainers().isEmpty());
        } else {
            assertTrue(recipe.getResult().equals(f.inventory.getItem(20)));
            assertEquals(times, f.inventory.getItem(20).getCount());
            assertDestinationAck(f, packet, times);
            int remaining = f.inventory.getContents().values().stream().filter(i -> i.getId() == Item.DIAMOND)
                    .mapToInt(Item::getCount).sum();
            assertEquals(9 * 64, remaining + 9 * f.inventory.getItem(20).getCount());
        }
    }

    /** Repeated source references consume the total quantity exactly. */
    @Test void bucketSixFromOneRealStackConsumesEighteenExactly() {
        int times = 6, requestId = 8811;
        Fixture f = new Fixture(times, false);
        CraftingRecipe recipe = recipeFor("minecraft:bucket", Item.IRON_INGOT, 3);
        assertTrue(f.inventory.setItem(0, Item.get(Item.IRON_INGOT, 0, 18).autoAssignStackNetworkId(), false));
        List<ItemDescriptorWithCount> descriptors = new ArrayList<>();
        List<ItemStackRequestAction> actions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            descriptors.add(new ItemDescriptorWithCount(new DefaultDescriptor(Item.IRON_INGOT, 0), 1));
            actions.add(new ConsumeAction(times, new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0,
                    i == 0 ? f.inventory.getItem(0).getStackNetId() : -requestId, null)));
        }
        actions.add(0, new AutoCraftRecipeAction(recipeId(recipe), times, times, descriptors));
        actions.add(placeOutput(times));
        AtomicInteger craftEvents = new AtomicInteger(), consumeEvents = new AtomicInteger();
        doAnswer(call -> {
            Object event = call.getArgument(0);
            if (event instanceof CraftItemEvent craft) {
                craftEvents.incrementAndGet();
                // Upstream exposes a read-only recipe snapshot before plan validation.
                assertEquals(3, Arrays.stream(craft.getInput()).mapToInt(Item::getCount).sum());
                assertEquals(recipe.getResult().getCount(), craft.getTransaction().getPrimaryOutput().getCount());
                assertEquals(18, actions.stream().filter(ConsumeAction.class::isInstance)
                        .map(ConsumeAction.class::cast).mapToInt(ConsumeAction::getCount).sum());
                assertEquals(18, f.inventory.getItem(0).getCount(), "the event precedes all actual debits");
            }
            if (event instanceof InventoryTransactionEvent) consumeEvents.incrementAndGet();
            return null;
        }).when(f.plugins).callEvent(any());
        ItemStackResponsePacket packet = runRequest(f, requestId, actions);
        assertEquals(ItemStackResponseStatus.OK, packet.entries.get(0).getResult());
        assertEquals(1, craftEvents.get());
        assertEquals(4, consumeEvents.get(), "three CONSUMEs plus final PLACE must execute");
        assertTrue(f.inventory.getItem(0).isNull());
        assertTrue(f.ui.getItem(50).isNull());
        assertTrue(recipe.getResult().equals(f.inventory.getItem(20)));
        assertEquals(times, f.inventory.getItem(20).getCount());
        int remainingIron = f.inventory.getContents().values().stream().filter(i -> i.getId() == Item.IRON_INGOT)
                .mapToInt(Item::getCount).sum();
        assertEquals(18, remainingIron + 3 * f.inventory.getItem(20).getCount());
        assertDestinationAck(f, packet, times);
    }

    static CraftingRecipe recipeFor(String output, int inputId, int inputCount) {
        Item template = Item.fromString(output);
        assertFalse(template.isNull());
        return manager.getRecipes().stream().filter(CraftingRecipe.class::isInstance).map(CraftingRecipe.class::cast)
                .filter(r -> r.getResult().equals(template) && r.getResult().getCount() == 1 && r.getExtraResults().isEmpty())
                .filter(r -> r.getIngredientsAggregate().stream().allMatch(i -> i.getId() == inputId))
                .filter(r -> r.getIngredientsAggregate().stream().mapToInt(Item::getCount).sum() == inputCount)
                .findFirst().orElseThrow();
    }

    static int recipeId(CraftingRecipe recipe) {
        return recipe instanceof ShapedRecipe shaped ? shaped.getNetworkId() : ((ShapelessRecipe) recipe).getNetworkId();
    }

    static PlaceAction placeOutput(int count) {
        return new PlaceAction(count, new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT, 50, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 20, 0, null));
    }

    static ItemStackResponsePacket runRequest(Fixture f, int id, List<ItemStackRequestAction> actions) {
        ItemStackRequestHandler.handleRequests(f.player, List.of(new ItemStackRequest(id,
                actions.toArray(ItemStackRequestAction[]::new), new String[0])));
        ArgumentCaptor<DataPacket> packets = ArgumentCaptor.forClass(DataPacket.class);
        verify(f.player, atLeastOnce()).dataPacket(packets.capture());
        ItemStackResponsePacket packet = packets.getAllValues().stream().filter(ItemStackResponsePacket.class::isInstance)
                .map(ItemStackResponsePacket.class::cast).reduce((a, b) -> b).orElseThrow();
        assertEquals(1, packet.entries.size());
        assertEquals(id, packet.entries.get(0).getRequestId());
        return packet;
    }

    static void assertDestinationAck(Fixture f, ItemStackResponsePacket packet, int count) {
        var ack = packet.entries.get(0).getContainers().stream().filter(c -> c.getContainer() == ContainerSlotType.INVENTORY)
                .flatMap(c -> c.getItems().stream()).filter(s -> s.getSlot() == 20).findFirst().orElseThrow();
        assertEquals(count, ack.getCount());
        assertEquals(f.inventory.getItem(20).getStackNetId(), ack.getStackNetworkId());
    }

    static class RefusingInventory extends PlayerInventory {
        final int refusedCount;
        final boolean refusalEnabled;
        boolean armed, refused;
        RefusingInventory(Player player, int times, boolean enabled) { super(player); refusedCount = 64 - times; refusalEnabled = enabled; }
        @Override public boolean setItem(int slot, Item item, boolean send) {
            if (armed && refusalEnabled && slot == 8 && !item.isNull() && item.getCount() == refusedCount) { refused = true; return false; }
            return super.setItem(slot, item, send);
        }
    }
    static class Fixture {
        final Player player = mock(Player.class);
        final PlayerUIInventory ui = new PlayerUIInventory(player);
        final PluginManager plugins = mock(PluginManager.class);
        final RefusingInventory inventory;
        Fixture(int times, boolean refuse) {
            this(times, refuse, VERSION);
        }
        Fixture(int times, boolean refuse, GameVersion version) {
            MockServer.reset();
            inventory = new RefusingInventory(player, times, refuse);
            player.protocol = version.getProtocol(); player.spawned = true; player.craftingType = Player.CRAFTING_BIG;
            when(player.getServer()).thenReturn(MockServer.get()); when(player.getGameVersion()).thenReturn(version);
            when(player.getName()).thenReturn("bulk-crafting-safety"); when(player.isAlive()).thenReturn(true); when(player.isOnline()).thenReturn(true);
            when(player.getUIInventory()).thenReturn(ui); when(player.getInventory()).thenReturn(inventory);
            when(player.getCraftingGrid()).thenReturn(ui.getBigCraftingGrid()); when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
            when(player.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(player)); when(player.getTopWindow()).thenReturn(Optional.empty());
            when(MockServer.get().getPluginManager()).thenReturn(plugins); when(MockServer.get().getCraftingManager()).thenReturn(manager);
            when(MockServer.get().isPrimaryThread()).thenReturn(true);
        }
    }
}
