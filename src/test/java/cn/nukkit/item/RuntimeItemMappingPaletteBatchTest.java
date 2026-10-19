package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.customitem.CustomItem;
import cn.nukkit.item.customitem.CustomItemDefinition;
import cn.nukkit.item.customitem.ItemCustom;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeItemMappingPaletteBatchTest {
    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void startupBatchEncodesOnceForEveryMappingAndPreservesEagerBytes() throws Exception {
        List<CustomItem> items = new ArrayList<>();
        for (int i = 0; i < 121; i++) items.add(custom("palette_batch:item_" + i));
        Map<String, CustomItemDefinition> definitions = definitions();
        CustomItemDefinition component = mock(CustomItemDefinition.class);
        when(component.isComponentBased()).thenReturn(true);
        definitions.put(items.get(0).getNamespaceId(), component);
        try {
            int mappings = 0;
            for (RuntimeItemMapping source : RuntimeItems.values()) {
                CountingMapping mapping = copyOf(source);
                assertArrayEquals(source.getItemPalette(), mapping.getItemPalette());
                for (CustomItem item : items) assertTrue(mapping.registerCustomItem(item));
                assertEquals(0, mapping.encodes, "Intermediate palette encodes: " + mapping.getProtocolId());
                mapping.generatePalette(); // Keep Server's unconditional final rebuild contract.
                assertEquals(1, mapping.encodes);
                byte[] completed = mapping.getItemPalette();
                assertArrayEquals(eagerPalette(mapping), completed);
                assertEquals(1, mapping.encodes, "Clean getter must reuse palette");
                mapping.deleteCustomItem(items.get(0));
                assertArrayEquals(eagerPalette(mapping), mapping.getItemPalette());
                assertEquals(2, mapping.encodes);
                mapping.registerCustomItem(items.get(0));
                assertArrayEquals(eagerPalette(mapping), mapping.getItemPalette());
                assertEquals(3, mapping.encodes);
                mappings++;
            }
            assertEquals(46, mappings, "Cover every international and NetEase mapping");
        } finally {
            definitions.remove(items.get(0).getNamespaceId());
        }
    }

    @Test
    void earlyGetterTracksAddDeleteReaddWithoutDuplicateOrMissingItemRebuilds() throws Exception {
        CountingMapping mapping = copyOf(RuntimeItems.getMapping(GameVersion.getLastVersion()));
        CustomItem item = custom("palette_batch:early");
        mapping.registerCustomItem(item);
        byte[] added = mapping.getItemPalette();
        assertEquals(1, mapping.encodes);
        assertArrayEquals(eagerPalette(mapping), added);
        assertSame(added, mapping.getItemPalette());
        mapping.registerCustomItem(item);
        assertSame(added, mapping.getItemPalette());
        assertEquals(1, mapping.encodes);
        mapping.deleteCustomItem(item);
        byte[] deleted = mapping.getItemPalette();
        assertArrayEquals(eagerPalette(mapping), deleted);
        assertEquals(2, mapping.encodes);
        assertTrue(mapping.getNetworkIdByNamespaceId(item.getNamespaceId()).isEmpty());
        mapping.deleteCustomItem(item);
        assertSame(deleted, mapping.getItemPalette());
        mapping.registerCustomItem(item);
        assertArrayEquals(added, mapping.getItemPalette());
        assertEquals(3, mapping.encodes);
    }

    @Test
    void explicitGenerateAlwaysRefreshesDefinitionsEvenWithoutMappingMutation() throws Exception {
        CountingMapping mapping = copyOf(RuntimeItems.getMapping(GameVersion.getLastVersion()));
        CustomItem item = custom("palette_batch:component");
        Map<String, CustomItemDefinition> definitions = definitions();
        CustomItemDefinition component = mock(CustomItemDefinition.class);
        when(component.isComponentBased()).thenReturn(true);
        try {
            mapping.registerCustomItem(item);
            byte[] early = mapping.getItemPalette(); // Definition is not yet present.
            definitions.put(item.getNamespaceId(), component);
            mapping.generatePalette();
            byte[] completed = mapping.getItemPalette();
            assertFalse(java.util.Arrays.equals(early, completed));
            assertArrayEquals(eagerPalette(mapping), completed);
            mapping.generatePalette();
            assertEquals(3, mapping.encodes, "Public generatePalette remains unconditional");
            assertArrayEquals(completed, mapping.getItemPalette());
            when(component.isComponentBased()).thenReturn(false);
            mapping.generatePalette();
            assertArrayEquals(early, mapping.getItemPalette());
        } finally {
            definitions.remove(item.getNamespaceId());
        }
    }

    @Test
    void customBlockAndRegularItemMutationsInvalidateEarlyPalette() throws Exception {
        CountingMapping mapping = copyOf(RuntimeItems.getMapping(GameVersion.getLastVersion()));
        mapping.registerCustomBlockItem("palette_batch:block", 30000, 12345);
        assertArrayEquals(eagerPalette(mapping), mapping.getItemPalette());
        assertEquals(1, mapping.encodes);
        mapping.registerItem("palette_batch:regular", 30001, 30001, 12345);
        assertArrayEquals(eagerPalette(mapping), mapping.getItemPalette());
        assertEquals(2, mapping.encodes);
    }

    @Test
    void getterWaitsForMutationMonitorBeforePublishingCompleteBytes() throws Exception {
        CountingMapping mapping = copyOf(RuntimeItems.getMapping(GameVersion.getLastVersion()));
        var executor = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        java.util.concurrent.Future<byte[]> result;
        try {
            synchronized (mapping) {
                result = executor.submit(() -> {
                    entered.countDown();
                    return mapping.getItemPalette();
                });
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
                mapping.registerCustomItem(custom("palette_batch:published"));
            }
            assertArrayEquals(eagerPalette(mapping), result.get(2, TimeUnit.SECONDS));
            assertEquals(1, mapping.encodes);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    private static CustomItem custom(String identifier) {
        var item = new PaletteItem(identifier);
        item.getDefinition(); // Allocate the same stable runtime ID used by real registration.
        return item;
    }

    @SuppressWarnings("unchecked")
    private static CountingMapping copyOf(RuntimeItemMapping source) throws Exception {
        Field entries = RuntimeItems.class.getDeclaredField("mappingEntries");
        entries.setAccessible(true);
        Field version = RuntimeItemMapping.class.getDeclaredField("gameVersion");
        version.setAccessible(true);
        var mapping = new CountingMapping((Map<String, RuntimeItems.MappingEntry>) entries.get(null),
                (GameVersion) version.get(source));
        // Item.init may add vanilla string entries after the mapping constructor.
        mapping.getItemPaletteEntries().clear();
        mapping.getItemPaletteEntries().addAll(source.getItemPaletteEntries());
        mapping.generatePalette();
        mapping.encodes = 0;
        return mapping;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CustomItemDefinition> definitions() throws Exception {
        Field definitions = Item.class.getDeclaredField("CUSTOM_ITEM_DEFINITIONS");
        definitions.setAccessible(true);
        return (Map<String, CustomItemDefinition>) definitions.get(null);
    }

    // Eager encoder contract before batching: entry order, IDs and component bits stay exact.
    private static byte[] eagerPalette(RuntimeItemMapping mapping) {
        BinaryStream stream = new BinaryStream();
        int size = 0;
        for (RuntimeItemMapping.RuntimeEntry entry : mapping.getItemPaletteEntries()) {
            if (entry.isCustomItem() && mapping.getProtocolId() < ProtocolInfo.v1_16_100) break;
            size++;
        }
        stream.putUnsignedVarInt(size);
        for (RuntimeItemMapping.RuntimeEntry entry : mapping.getItemPaletteEntries()) {
            if (entry.isCustomItem() && mapping.getProtocolId() < ProtocolInfo.v1_16_100) continue;
            stream.putString(entry.getIdentifier());
            stream.putLShort(entry.getRuntimeId());
            if (mapping.getProtocolId() >= ProtocolInfo.v1_16_100) {
                var definition = Item.getCustomItemDefinition(entry.getIdentifier());
                stream.putBoolean(entry.isCustomItem() && definition != null && definition.isComponentBased());
            }
        }
        return stream.getBuffer();
    }

    private static final class PaletteItem extends ItemCustom {
        PaletteItem(String identifier) {
            super(identifier, "Palette test");
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition.legacyBuilder(this).build();
        }
    }

    private static final class CountingMapping extends RuntimeItemMapping {
        int encodes;

        CountingMapping(Map<String, RuntimeItems.MappingEntry> entries, GameVersion version) {
            super(entries, version);
            encodes = 0;
        }

        @Override
        public synchronized void generatePalette() {
            encodes++;
            super.generatePalette();
        }
    }
}
