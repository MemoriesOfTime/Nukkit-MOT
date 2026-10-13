package cn.nukkit.block.custom;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.custom.container.BlockContainer;
import cn.nukkit.block.custom.container.CustomBlock;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItemMapping;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;
import cn.nukkit.utils.serverconfig.ServerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Registration/NBT ownership tests; item/creative global publication is isolated, not simulated as client proof. */
class CustomBlockManagerDefinitionIdentityTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        MockServer.init();
    }

    private CustomBlockManager manager() throws ReflectiveOperationException {
        ServerConfig config = new ServerConfig();
        config.customBlockSettings().autoDownloadVanillaPalette(false);
        Server server = mock(Server.class);
        when(server.getServerConfig()).thenReturn(config);
        when(server.getDataPath()).thenReturn(directory.toString());
        var constructor = CustomBlockManager.class.getDeclaredConstructor(Server.class);
        constructor.setAccessible(true);
        return constructor.newInstance(server);
    }

    private static Supplier<BlockContainer> factory(CustomBlockManager manager, String identifier) {
        // Same before-allocation fallback as the native lazy CustomBlock, bound to this isolated registry.
        return () -> {
            int id = manager.getBlockId(identifier);
            return new CustomBlock(identifier, id == -1 ? BlockID.INFO_UPDATE : id);
        };
    }

    private static CustomBlockDefinition configured(Supplier<BlockContainer> factory, boolean creative) {
        CustomBlockDefinition base = CustomBlockDefinition.builder(factory.get()).build();
        base.nbt().setName("test:caller_root");
        base.nbt().getCompound("vanilla_block_data").putString("material", "stone");
        base.nbt().getCompound("components").putCompound("test:ownership_witness", new CompoundTag()
                .putByte("flag", (byte) 1).putString("text", "caller-owned")
                .putList("values", new ListTag<CompoundTag>()
                        .add(new CompoundTag().putInt("number", 7))));
        base.nbt().putList("permutations", new ListTag<CompoundTag>()
                .add(new CompoundTag().putString("condition", "query.block_state('km:variant') == 0")
                        .putCompound("components", new CompoundTag().putString("km:marker", "kept"))));
        return new CustomBlockDefinition(base.identifier(), base.nbt(), base.nukkitId(), base.typeOf(), creative);
    }

    private static CustomBlockDefinition onlyDefinition(CustomBlockManager manager) {
        assertEquals(1, manager.getBlockDefinitions().size());
        return manager.getBlockDefinitions().iterator().next();
    }

    private static void assertOwnedRebinding(CustomBlockDefinition source, CompoundTag before,
                                             CustomBlockDefinition stored, int id) throws IOException {
        assertNotSame(source, stored);
        assertNotSame(source.nbt(), stored.nbt());
        assertNotSame(source.nbt().getCompound("components"), stored.nbt().getCompound("components"));
        assertNamedWitnesses(source.nbt());
        assertNamedWitnesses(stored.nbt());
        assertEquals(before, source.nbt(), "Registration must not mutate caller-owned typed NBT");
        assertArrayEquals(NBTIO.write(before.copy()), NBTIO.write(source.nbt().copy()));
        CompoundTag expected = before.copy();
        expected.getCompound("vanilla_block_data").putInt("block_id", id);
        assertEquals(expected, stored.nbt(), "Only the registration-owned numeric ID changes");
        assertArrayEquals(NBTIO.write(expected.copy()), NBTIO.write(stored.nbt().copy()),
                "The complete named NBT wire image must retain every unrelated field");
        assertEquals(source.identifier(), stored.identifier());
        assertEquals(source.typeOf(), stored.typeOf());
        assertEquals(source.registerCreativeItem(), stored.registerCreativeItem());
        assertEquals(id, stored.nukkitId());
        assertTrue(stored.nbt().getCompound("vanilla_block_data").containsInt("block_id"));
        assertEquals(id, stored.nbt().getCompound("vanilla_block_data").getInt("block_id"));
    }

    private static void assertNamedWitnesses(CompoundTag nbt) {
        assertEquals("test:caller_root", nbt.getName());
        assertEquals("vanilla_block_data", nbt.getCompound("vanilla_block_data").getName());
        CompoundTag components = nbt.getCompound("components");
        assertEquals("components", components.getName());
        CompoundTag witness = components.getCompound("test:ownership_witness");
        assertEquals("test:ownership_witness", witness.getName());
        ListTag<CompoundTag> values = witness.getList("values", CompoundTag.class);
        assertEquals("values", values.getName());
        assertEquals("", values.get(0).getName(), "Native list entries remain unnamed");
    }

    @Test
    void preallocatedDefinitionRebindsBothIdsAndPreservesCallerOwnedSettings() throws ReflectiveOperationException, IOException {
        CustomBlockManager manager = manager();
        String name = "test:definition_before_allocation";
        Supplier<BlockContainer> factory = factory(manager, name);
        CustomBlockDefinition source = configured(factory, false);
        assertEquals(BlockID.INFO_UPDATE, source.nukkitId());
        assertEquals(BlockID.INFO_UPDATE, source.nbt().getCompound("vanilla_block_data").getInt("block_id"));
        CompoundTag before = source.nbt().copy();
        try (MockedStatic<RuntimeItems> items = mockStatic(RuntimeItems.class)) {
            items.when(RuntimeItems::values).thenReturn(new RuntimeItemMapping[0]);
            int id = manager.registerCustomBlock(name, source, factory);
            assertTrue(id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID);
            assertEquals(id, manager.getBlockId(name));
            assertEquals(id, manager.getBlock(id, 0).getId());
            assertOwnedRebinding(source, before, onlyDefinition(manager), id);
            items.verify(() -> RuntimeItems.registerCustomBlockLegacyId(name, 255 - id));
            onlyDefinition(manager).nbt().getCompound("components").getCompound("test:ownership_witness")
                    .getList("values", CompoundTag.class).get(0).putInt("number", 99);
            assertEquals(before, source.nbt(), "Nested registration-owned state must not alias caller NBT");
        }
    }

    @Test
    void alreadyAllocatedRecordStillRepairsAStaleNetworkId() throws ReflectiveOperationException, IOException {
        CustomBlockManager manager = manager();
        String name = "test:network_id_before_allocation";
        CustomBlockDefinition source = configured(() -> new CustomBlock(name, CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID), false);
        source.nbt().getCompound("vanilla_block_data").putInt("block_id", BlockID.INFO_UPDATE);
        CompoundTag before = source.nbt().copy();
        try (MockedStatic<RuntimeItems> items = mockStatic(RuntimeItems.class)) {
            items.when(RuntimeItems::values).thenReturn(new RuntimeItemMapping[0]);
            int id = manager.registerCustomBlock(name, source, factory(manager, name));
            assertEquals(source.nukkitId(), id);
            assertOwnedRebinding(source, before, onlyDefinition(manager), id);
            items.verify(() -> RuntimeItems.registerCustomBlockLegacyId(name, 255 - id));
        }
    }

    @Test
    void alreadyConsistentSuppliedDefinitionKeepsItsIdentityAndSettings() throws ReflectiveOperationException {
        CustomBlockManager manager = manager();
        String name = "test:consistent_definition";
        CustomBlockDefinition source = configured(() -> new CustomBlock(name, CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID), false);
        CompoundTag before = source.nbt().copy();
        try (MockedStatic<RuntimeItems> items = mockStatic(RuntimeItems.class)) {
            items.when(RuntimeItems::values).thenReturn(new RuntimeItemMapping[0]);
            int id = manager.registerCustomBlock(name, source, factory(manager, name));
            assertEquals(source.nukkitId(), id);
            assertSame(source, onlyDefinition(manager));
            assertNamedWitnesses(onlyDefinition(manager).nbt());
            assertEquals(before, source.nbt());
            assertEquals(id, manager.getBlock(id, 0).getId());
            items.verify(() -> RuntimeItems.registerCustomBlockLegacyId(name, 255 - id));
        }
    }

    @Test
    void defaultDefinitionIsBuiltAfterAllocationAndRetainsCreativePublication() throws ReflectiveOperationException {
        CustomBlockManager manager = manager();
        String name = "test:definition_after_allocation";
        try (MockedStatic<RuntimeItems> items = mockStatic(RuntimeItems.class);
             MockedStatic<Item> creative = mockStatic(Item.class)) {
            items.when(RuntimeItems::values).thenReturn(new RuntimeItemMapping[0]);
            int id = manager.registerCustomBlock(name, factory(manager, name));
            CustomBlockDefinition stored = onlyDefinition(manager);
            assertEquals(id, stored.nukkitId());
            assertTrue(stored.nbt().getCompound("vanilla_block_data").containsInt("block_id"));
            assertEquals(id, stored.nbt().getCompound("vanilla_block_data").getInt("block_id"));
            assertTrue(stored.registerCreativeItem());
            assertEquals("", stored.nbt().getName(), "Default post-allocation construction retains its native root name");
            assertEquals("vanilla_block_data", stored.nbt().getCompound("vanilla_block_data").getName());
            assertEquals(CustomBlock.class, stored.typeOf());
            assertEquals(id, manager.getBlock(id, 0).getId());
            items.verify(() -> RuntimeItems.registerCustomBlockLegacyId(name, 255 - id));
            creative.verify(() -> Item.addCreativeItem(any(Item.class), any(CreativeItemCategory.class), anyString()));
        }
    }
}
