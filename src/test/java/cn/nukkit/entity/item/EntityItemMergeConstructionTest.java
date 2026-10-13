package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityItemMergeConstructionTest {
    private Server server;
    private PluginManager originalPlugins;
    private PluginManager plugins;
    private FullChunk chunk;

    @BeforeEach
    void setUp() {
        MockServer.init();
        server = MockServer.get();
        originalPlugins = server.getPluginManager();
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        Level level = mock(Level.class);
        level.isBeingConverted = true;
        when(level.getServer()).thenReturn(server);
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(level);
        chunk = mock(FullChunk.class);
        when(chunk.getProvider()).thenReturn(provider);
    }

    @AfterEach
    void restorePlugins() {
        when(server.getPluginManager()).thenReturn(originalPlugins);
    }

    private CompoundTag itemNbt() {
        return Entity.getDefaultNBT(new Vector3(1, 64, 1))
                .putShort("Health", 5)
                .putCompound("Item", NBTIO.putItemHelper(Item.get(Item.DIAMOND)));
    }

    @Test
    void savedOptOutSurvivesTheRealConstructorAndSave() {
        EntityItem entity = new EntityItem(chunk, itemNbt().putBoolean("Mergeable", false));
        assertFalse(entity.isClosed());
        assertFalse(entity.isMergeable());
        entity.saveNBT();
        assertTrue(entity.namedTag.contains("Mergeable"));
        assertFalse(entity.namedTag.getBoolean("Mergeable"));
    }

    @Test
    void missingOrExplicitTrueFlagKeepsOrdinaryDropsMergeable() {
        assertTrue(new EntityItem(chunk, itemNbt()).isMergeable());
        assertTrue(new EntityItem(chunk, itemNbt().putBoolean("Mergeable", true)).isMergeable());
    }

    @Test
    void itemSpawnListenerOptOutSurvivesConstruction() {
        assertSpawnListenerOptOut(ItemSpawnEvent.class);
    }

    @Test
    void entitySpawnListenerOptOutSurvivesConstruction() {
        assertSpawnListenerOptOut(EntitySpawnEvent.class);
    }

    private void assertSpawnListenerOptOut(Class<? extends EntityEvent> eventType) {
        AtomicBoolean called = new AtomicBoolean();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event.getClass() == eventType) {
                EntityItem entity = (EntityItem) ((EntityEvent) event).getEntity();
                assertTrue(entity.isMergeable(), "initEntity must establish the default before events");
                entity.setMergeable(false);
                called.set(true);
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
        EntityItem entity = new EntityItem(chunk, itemNbt());
        assertTrue(called.get());
        assertFalse(entity.isMergeable());
        assertFalse(entity.namedTag.getBoolean("Mergeable"));
    }
}
