package cn.nukkit.inventory.request;

import cn.nukkit.*;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Additional adversarial controls for the count compatibility change; no production mocks of recipe matching. */
@Timeout(15)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class CraftRecipeAutoSafetyControlsTest {
    static CraftingManager manager;
    static final GameVersion VERSION = GameVersion.byProtocol(2169, false);
    enum Scenario {
        MIXED_COUNTS, UNDER_CONSUME, ONE_RECIPE_FOR_TWO_OUTPUTS, EXTRA_CONSUME, WRONG_INGREDIENT,
        EXTRA_ITEM, BAD_STACK_ID, CANCEL_CRAFT, CANCEL_LAST_CONSUME,
        REFUSE_LAST_CONSUME, REPEATED_SOURCE_EXHAUSTION, INTEGER_WRAP_BOUNDARY
    }
    @BeforeAll static void init() { MockServer.init(); manager = new CraftingManager(); }
    @AfterAll static void restoreDefaults() { MockServer.reset(); }

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
