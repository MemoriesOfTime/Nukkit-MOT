package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryPacketRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
        Item.initCreativeItems();
    }

    static Stream<Arguments> versionsAll() {
        return allVersions();
    }

    static Stream<Arguments> versionsFrom407() {
        return filteredVersions(407);
    }

    static Stream<Arguments> versions407To428() {
        return filteredVersionsRange(ProtocolInfo.v1_16_0, ProtocolInfo.v1_16_220);
    }

    static Stream<Arguments> versionsFrom431() {
        return filteredVersions(ProtocolInfo.v1_16_220);
    }

    static Stream<Arguments> versionsFrom776() {
        return filteredVersions(ProtocolInfo.v1_21_60);
    }

    static Stream<Arguments> versionsAtV1_21_70() {
        return Stream.of(
                Arguments.of(ProtocolInfo.v1_21_70)
        );
    }

    static Stream<Arguments> versionsAt471() {
        return Stream.of(
                Arguments.of(ProtocolInfo.v1_17_40)
        );
    }

    @ParameterizedTest(name = "MobEquipmentPacket v{0}")
    @MethodSource("versionsAll")
    void testMobEquipmentPacket(int protocolVersion) {
        var nukkitPacket = new MobEquipmentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.item = Item.AIR_ITEM;
        nukkitPacket.inventorySlot = 0;
        nukkitPacket.hotbarSlot = 0;
        nukkitPacket.windowId = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(0, cbPacket.getInventorySlot());
        assertEquals(0, cbPacket.getHotbarSlot());
        assertEquals(0, cbPacket.getContainerId());
    }

    @ParameterizedTest(name = "MobArmorEquipmentPacket v{0}")
    @MethodSource("versionsAll")
    void testMobArmorEquipmentPacket(int protocolVersion) {
        var nukkitPacket = new MobArmorEquipmentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.slots = new Item[]{Item.AIR_ITEM, Item.AIR_ITEM, Item.AIR_ITEM, Item.AIR_ITEM};
        if (protocolVersion >= ProtocolInfo.v1_21_20) {
            nukkitPacket.body = Item.AIR_ITEM;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
    }

    @ParameterizedTest(name = "InventoryContentPacket v{0}")
    @MethodSource("versionsAll")
    void testInventoryContentPacket(int protocolVersion) {
        var nukkitPacket = new InventoryContentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.inventoryId = 1;
        nukkitPacket.slots = new Item[]{Item.AIR_ITEM, Item.AIR_ITEM};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket.class);

        assertEquals(1, cbPacket.getContainerId());
        assertEquals(2, cbPacket.getContents().size());
    }

    @ParameterizedTest(name = "InventorySlotPacket v{0}")
    @MethodSource("versionsAll")
    void testInventorySlotPacket(int protocolVersion) {
        var nukkitPacket = new InventorySlotPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.inventoryId = 1;
        nukkitPacket.slot = 5;
        nukkitPacket.item = Item.AIR_ITEM;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket.class);

        assertEquals(1, cbPacket.getContainerId());
        assertEquals(5, cbPacket.getSlot());
    }

    @ParameterizedTest(name = "ContainerClosePacket v{0}")
    @MethodSource("versionsAll")
    void testContainerClosePacket(int protocolVersion) {
        var nukkitPacket = new ContainerClosePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.windowId = 5;
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            nukkitPacket.wasServerInitiated = true;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket.class);

        assertEquals(5, cbPacket.getId());
    }

    @ParameterizedTest(name = "InventoryTransactionPacket v{0}")
    @MethodSource("versionsAll")
    void testInventoryTransactionPacket(int protocolVersion) {
        var nukkitPacket = new InventoryTransactionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.transactionType = InventoryTransactionPacket.TYPE_RELEASE_ITEM;
        nukkitPacket.legacyRequestId = 0;
        nukkitPacket.hasNetworkIds = false;

        var action = new cn.nukkit.network.protocol.types.NetworkInventoryAction();
        action.sourceType = cn.nukkit.network.protocol.types.NetworkInventoryAction.SOURCE_CONTAINER;
        action.windowId = 0;
        action.inventorySlot = 4;
        action.oldItem = Item.AIR_ITEM;
        action.newItem = Item.AIR_ITEM;
        nukkitPacket.actions = new cn.nukkit.network.protocol.types.NetworkInventoryAction[]{action};

        var releaseItemData = new cn.nukkit.inventory.transaction.data.ReleaseItemData();
        releaseItemData.actionType = InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE;
        releaseItemData.hotbarSlot = 2;
        releaseItemData.itemInHand = Item.AIR_ITEM;
        releaseItemData.headRot = new cn.nukkit.math.Vector3(1.5f, 64.0f, -2.5f);
        nukkitPacket.transactionData = releaseItemData;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType.ITEM_RELEASE,
                cbPacket.getTransactionType());
        if (protocolVersion >= ProtocolInfo.v1_16_0) {
            assertEquals(0, cbPacket.getLegacyRequestId());
        }
        assertEquals(1, cbPacket.getActions().size());
        assertEquals(4, cbPacket.getActions().get(0).getSlot());
        assertEquals(InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE, cbPacket.getActionType());
        assertEquals(2, cbPacket.getHotbarSlot());
        assertEquals(1.5f, cbPacket.getHeadPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getHeadPosition().getY(), 0.001f);
        assertEquals(-2.5f, cbPacket.getHeadPosition().getZ(), 0.001f);
    }

    @ParameterizedTest(name = "InventoryTransactionPacket v{0} with network ids")
    @MethodSource("versions407To428")
    void testInventoryTransactionPacketWithNetworkIds(int protocolVersion) {
        var nukkitPacket = new InventoryTransactionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.transactionType = InventoryTransactionPacket.TYPE_RELEASE_ITEM;
        nukkitPacket.legacyRequestId = 0;
        nukkitPacket.hasNetworkIds = true;

        var action = new cn.nukkit.network.protocol.types.NetworkInventoryAction();
        action.sourceType = cn.nukkit.network.protocol.types.NetworkInventoryAction.SOURCE_CONTAINER;
        action.windowId = 0;
        action.inventorySlot = 4;
        action.oldItem = Item.AIR_ITEM;
        action.newItem = Item.AIR_ITEM;
        action.stackNetworkId = 321;
        nukkitPacket.actions = new cn.nukkit.network.protocol.types.NetworkInventoryAction[]{action};

        var releaseItemData = new cn.nukkit.inventory.transaction.data.ReleaseItemData();
        releaseItemData.actionType = InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE;
        releaseItemData.hotbarSlot = 2;
        releaseItemData.itemInHand = Item.AIR_ITEM;
        releaseItemData.headRot = new cn.nukkit.math.Vector3(1.5f, 64.0f, -2.5f);
        nukkitPacket.transactionData = releaseItemData;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket.class);

        assertTrue(cbPacket.isUsingNetIds());
        assertEquals(321, readStackNetworkId(cbPacket.getActions().get(0)));
    }

    @ParameterizedTest(name = "InventoryTransactionPacket v{0} should ignore network ids after v431")
    @MethodSource("versionsFrom431")
    void testInventoryTransactionPacketDoesNotWriteNetworkIdsAfterV431(int protocolVersion) {
        var nukkitPacket = new InventoryTransactionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.transactionType = InventoryTransactionPacket.TYPE_RELEASE_ITEM;
        nukkitPacket.legacyRequestId = 0;
        nukkitPacket.hasNetworkIds = true;

        var action = new cn.nukkit.network.protocol.types.NetworkInventoryAction();
        action.sourceType = cn.nukkit.network.protocol.types.NetworkInventoryAction.SOURCE_CONTAINER;
        action.windowId = 0;
        action.inventorySlot = 4;
        action.oldItem = Item.AIR_ITEM;
        action.newItem = Item.AIR_ITEM;
        action.stackNetworkId = 321;
        nukkitPacket.actions = new cn.nukkit.network.protocol.types.NetworkInventoryAction[]{action};

        var releaseItemData = new cn.nukkit.inventory.transaction.data.ReleaseItemData();
        releaseItemData.actionType = InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE;
        releaseItemData.hotbarSlot = 2;
        releaseItemData.itemInHand = Item.AIR_ITEM;
        releaseItemData.headRot = new cn.nukkit.math.Vector3(1.5f, 64.0f, -2.5f);
        nukkitPacket.transactionData = releaseItemData;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket.class);

        assertFalse(cbPacket.isUsingNetIds());
        assertEquals(0, readStackNetworkId(cbPacket.getActions().get(0)));
    }

    // ==================== CreativeContentPacket ====================

    @ParameterizedTest(name = "CreativeContentPacket v{0} (empty/spectator)")
    @MethodSource("versionsFrom407")
    void testCreativeContentPacketEmpty(int protocolVersion) {
        var nukkitPacket = new CreativeContentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        // null entries/creativeItems = spectator mode
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket.class);

        assertTrue(cbPacket.getContents().isEmpty());
        if (protocolVersion >= ProtocolInfo.v1_21_60) {
            assertTrue(cbPacket.getGroups().isEmpty());
        }
    }

    @ParameterizedTest(name = "CreativeContentPacket v{0} (with items)")
    @MethodSource("versionsFrom407")
    void testCreativeContentPacketWithItems(int protocolVersion) {
        var gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        var nukkitPacket = new CreativeContentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = gameVersion;
        nukkitPacket.entries = new Item[]{Item.AIR_ITEM};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket.class,
                withCreativeContentDefinitions(gameVersion));

        assertFalse(cbPacket.getContents().isEmpty());
        if (protocolVersion >= ProtocolInfo.v1_21_60) {
            assertEquals(1, cbPacket.getGroups().size());
            assertEquals(0, readCreativeItemGroupId(cbPacket.getContents().get(0)));
        }
    }

    @ParameterizedTest(name = "CreativeContentPacket v{0} should decode full legacy creative payload")
    @MethodSource("versionsAt471")
    void testCreativeContentPacketFullLegacyPayload(int protocolVersion) {
        var gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        var creativeItems = Item.getCreativeItemsAndGroups();

        var nukkitPacket = new CreativeContentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = gameVersion;
        nukkitPacket.creativeItems = creativeItems;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket.class,
                withCreativeContentDefinitions(gameVersion));

        assertFalse(cbPacket.getContents().isEmpty(), "legacy creative content should not be empty");
        assertEquals(creativeItems.getItems(gameVersion).size(), cbPacket.getContents().size(),
                "legacy creative item count mismatch for v" + protocolVersion);
    }

    @ParameterizedTest(name = "CreativeContentPacket v{0} should keep groups versioned and group ids remapped")
    @MethodSource("versionsFrom776")
    void testCreativeContentPacketUsesVersionedGroupsAndGroupIds(int protocolVersion) {
        var gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        var creativeItems = createCreativeGroupRemapSample(gameVersion);

        var nukkitPacket = new CreativeContentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = gameVersion;
        nukkitPacket.creativeItems = creativeItems;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket.class,
                withCreativeContentDefinitions(gameVersion));

        assertCreativePacketGroupsMatchExpected(protocolVersion, creativeItems, gameVersion, cbPacket);
    }

    @ParameterizedTest(name = "CreativeContentPacket v{0} should not leak future creative groups")
    @MethodSource("versionsAtV1_21_70")
    void testCreativeContentPacketDoesNotLeakFutureGroupsIntoV1_21_70(int protocolVersion) {
        var gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        var creativeItems = Item.getCreativeItemsAndGroups();

        Set<String> leakedGroups = Set.of(
                "itemGroup.name.spear",
                "itemGroup.name.harnesses",
                "itemGroup.name.nautilus_armor",
                "itemGroup.name.shelf",
                "itemGroup.name.copper_golem_statue"
        );

        assertFalse(creativeItems.getGroups(gameVersion).isEmpty(), "creative groups should be initialized in tests");
        assertTrue(creativeItems.getGroups(gameVersion).stream().map(cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup::getName)
                        .noneMatch(leakedGroups::contains),
                "1.21.70 creative groups should not contain categories introduced by later versions");
    }

    private static int readStackNetworkId(Object action) {
        try {
            return (int) action.getClass().getMethod("stackNetworkId").invoke(action);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (int) action.getClass().getMethod("getStackNetworkId").invoke(action);
            } catch (ReflectiveOperationException ignoredAgain) {
                try {
                    var field = action.getClass().getDeclaredField("stackNetworkId");
                    field.setAccessible(true);
                    return field.getInt(action);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Failed to read stackNetworkId from " + action.getClass().getName(), e);
                }
            }
        }
    }

    private static Consumer<BedrockCodecHelper> withCreativeContentDefinitions(cn.nukkit.GameVersion gameVersion) {
        return helper -> {
            var itemDefinitions = SimpleDefinitionRegistry
                    .<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition>builder();
            var runtimeIds = new java.util.HashSet<Integer>();
            var identifiers = new java.util.HashSet<String>();
            for (var entry : RuntimeItems.getMapping(gameVersion).getItemPaletteEntries()) {
                if (!runtimeIds.add(entry.getRuntimeId()) || !identifiers.add(entry.getIdentifier())) {
                    continue;
                }
                itemDefinitions.add(new org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition(
                        entry.getIdentifier(),
                        entry.getRuntimeId(),
                        false
                ));
            }

            helper.setItemDefinitions(itemDefinitions.build());
            helper.setBlockDefinitions(SimpleDefinitionRegistry
                    .<org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition>builder()
                    .build());
        };
    }

    private static Item.CreativeItems createCreativeGroupRemapSample(cn.nukkit.GameVersion gameVersion) {
        var creativeItems = Item.getCreativeItemsAndGroups();
        var filteredEntry = creativeItems.getContents().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> !entry.getKey().isSupportedOn(gameVersion))
                .findFirst()
                .orElse(null);
        var supportedEntries = creativeItems.getContents(gameVersion).entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .toList();
        if (supportedEntries.size() < 1) {
            throw new AssertionError("Expected at least one remaining creative group for " + gameVersion);
        }

        var sample = new Item.CreativeItems();
        if (filteredEntry != null) {
            var filteredGroup = filteredEntry.getValue();
            var supportedEntry = supportedEntries.stream()
                    .filter(entry -> entry.getValue() != filteredGroup)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected at least one remaining creative group for " + gameVersion));
            sample.addGroup(filteredGroup);
            sample.add(filteredEntry.getKey().clone(), filteredGroup);
            sample.addGroup(supportedEntry.getValue());
            sample.add(supportedEntry.getKey().clone(), supportedEntry.getValue());
            return sample;
        }

        var firstSupported = supportedEntries.get(0);
        var secondSupported = supportedEntries.stream()
                .filter(entry -> entry.getValue() != firstSupported.getValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected at least two creative groups for " + gameVersion));
        sample.addGroup(firstSupported.getValue());
        sample.add(firstSupported.getKey().clone(), firstSupported.getValue());
        sample.addGroup(secondSupported.getValue());
        sample.add(secondSupported.getKey().clone(), secondSupported.getValue());
        return sample;
    }

    private static void assertCreativePacketGroupsMatchExpected(
            int protocolVersion,
            Item.CreativeItems creativeItems,
            cn.nukkit.GameVersion gameVersion,
            org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket cbPacket) {
        List<cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup> expectedGroups =
                creativeItems.getGroups(gameVersion);
        Map<Item, cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup> expectedContents =
                creativeItems.getContents(gameVersion);

        assertEquals(expectedGroups.size(), cbPacket.getGroups().size(),
                "creative group count mismatch for v" + protocolVersion);

        IdentityHashMap<cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup, Integer> expectedGroupIds =
                new IdentityHashMap<>(expectedGroups.size());

        for (int i = 0; i < expectedGroups.size(); i++) {
            var expectedGroup = expectedGroups.get(i);
            var decodedGroup = cbPacket.getGroups().get(i);

            expectedGroupIds.put(expectedGroup, i);
            assertEquals(expectedGroup.getCategory().name(), readCreativeGroupCategoryName(decodedGroup),
                    "creative group category mismatch at index " + i + " for v" + protocolVersion);
            assertEquals(expectedGroup.getName(), readCreativeGroupName(decodedGroup),
                    "creative group name mismatch at index " + i + " for v" + protocolVersion);
        }

        assertEquals(expectedContents.size(), cbPacket.getContents().size(),
                "creative item count mismatch for v" + protocolVersion);

        int itemIndex = 0;
        for (var entry : expectedContents.entrySet()) {
            var expectedGroup = entry.getValue();
            int expectedGroupId = expectedGroup != null ? expectedGroupIds.get(expectedGroup) : 0;
            assertEquals(expectedGroupId, readCreativeItemGroupId(cbPacket.getContents().get(itemIndex)),
                    "creative item groupId mismatch at item index " + itemIndex + " for v" + protocolVersion);
            itemIndex++;
        }
    }

    private static String readCreativeGroupName(Object group) {
        try {
            return (String) group.getClass().getMethod("name").invoke(group);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (String) group.getClass().getMethod("getName").invoke(group);
            } catch (ReflectiveOperationException ignoredAgain) {
                try {
                    var field = group.getClass().getDeclaredField("name");
                    field.setAccessible(true);
                    return (String) field.get(group);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Failed to read creative group name from " + group.getClass().getName(), e);
                }
            }
        }
    }

    private static String readCreativeGroupCategoryName(Object group) {
        Object category;
        try {
            category = group.getClass().getMethod("category").invoke(group);
        } catch (ReflectiveOperationException ignored) {
            try {
                category = group.getClass().getMethod("getCategory").invoke(group);
            } catch (ReflectiveOperationException ignoredAgain) {
                try {
                    var field = group.getClass().getDeclaredField("category");
                    field.setAccessible(true);
                    category = field.get(group);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Failed to read creative group category from " + group.getClass().getName(), e);
                }
            }
        }
        return ((Enum<?>) category).name();
    }

    private static int readCreativeItemGroupId(Object item) {
        try {
            return (int) item.getClass().getMethod("groupId").invoke(item);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (int) item.getClass().getMethod("getGroupId").invoke(item);
            } catch (ReflectiveOperationException ignoredAgain) {
                try {
                    var field = item.getClass().getDeclaredField("groupId");
                    field.setAccessible(true);
                    return field.getInt(item);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Failed to read creative item groupId from " + item.getClass().getName(), e);
                }
            }
        }
    }
}
