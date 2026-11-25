package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;

import java.lang.reflect.Field;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual native item construction/close; the world registry/provider boundary is controlled. */
public final class NativePublishedPickupSourceFixture {
    private NativePublishedPickupSourceFixture() { }

    public static EntityItem create(Player player, Item item) {
        Level level = player.getLevel();
        if (level == null) { level = mock(Level.class); player.level = level; }
        Map<Long, Entity> entities = new Long2ObjectNonBlockingMap<>(), chunkEntities = new LinkedHashMap<>();
        try {
            Field registry = Level.class.getDeclaredField("entities");
            registry.setAccessible(true); registry.set(level, entities);
            Field updates = Level.class.getDeclaredField("updateEntities");
            updates.setAccessible(true); updates.set(level, new Long2ObjectNonBlockingMap<Entity>());
        } catch (ReflectiveOperationException error) { throw new AssertionError("Native Level registries unavailable", error); }
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(level);
        when(chunk.getProvider()).thenReturn(provider);
        when(chunk.getEntities()).thenReturn(chunkEntities);
        when(level.getServer()).thenReturn(Server.getInstance());
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
        when(level.getChunk(0, 0, true)).thenReturn(chunk);
        when(level.getChunk(0, 0)).thenReturn(chunk);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        when(level.getEntity(anyLong())).thenAnswer(call -> entities.get(call.getArgument(0)));
        when(level.getEntities()).thenAnswer(call -> entities.values().toArray(Entity[]::new));
        doAnswer(call -> { Entity value = call.getArgument(0); entities.put(value.getId(), value); return null; })
                .when(level).addEntity(any(Entity.class));
        doAnswer(call -> { Entity value = call.getArgument(0); entities.remove(value.getId(), value); return null; })
                .when(level).removeEntity(any(Entity.class));
        doAnswer(call -> { Entity value = call.getArgument(0); chunkEntities.put(value.getId(), value); return null; })
                .when(chunk).addEntity(any(Entity.class));
        doAnswer(call -> { Entity value = call.getArgument(0); chunkEntities.remove(value.getId(), value); return null; })
                .when(chunk).removeEntity(any(Entity.class));
        EntityItem source = new EntityItem(chunk, Entity.getDefaultNBT(new Vector3(.25, 64, .25))
                .putShort("Health", 5).putShort("PickupDelay", 0).putCompound("Item", NBTIO.putItemHelper(item)));
        assertFalse(source.isClosed()); assertSame(level, source.getLevel()); assertSame(chunk, source.chunk);
        assertSame(source, level.getEntity(source.getId())); assertSame(source, chunkEntities.get(source.getId()));
        return source;
    }
}
