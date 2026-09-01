package cn.nukkit.entity.data.property;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.EntityClimateVariant;
import cn.nukkit.entity.passive.EntityChicken;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 验证内置实体属性在首次访问时已经可用。
 * <p>
 * Verifies built-in entity properties are available on first access.
 */
class EntityPropertyInitializationTest {

    private static Server server;

    @BeforeAll
    static void initServer() {
        MockServer.init();
        server = MockServer.get();
    }

    @Test
    void chickenClimateVariantIsAvailableDuringConstruction() {
        Level level = newMockLevel();
        EntityChicken chicken = new EntityChicken(newMockChunk(level), baseNbt());

        assertEquals(EntityClimateVariant.Variant.TEMPERATE, chicken.getVariant(),
                "Chicken climate variant must be initialized before the entity is loaded");
    }

    @Test
    void enumPropertyRegisteredAfterEntityConstructionUsesDefinitionDefault() {
        Level level = newMockLevel();
        EntityChicken chicken = new EntityChicken(newMockChunk(level), baseNbt());
        String identifier = "test:late_chicken_variant";

        assertTrue(EntityProperty.register("minecraft:chicken",
                new EnumEntityProperty(identifier, new String[]{"default", "alternate"}, "default")));
        assertEquals("default", chicken.getEnumEntityProperty(identifier),
                "A missing per-entity value must fall back to the registered enum default");
    }

    private static Level newMockLevel() {
        Level level = mock(Level.class);
        lenient().when(level.getServer()).thenReturn(server);
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(server.getTick()).thenReturn(0);
        lenient().doNothing().when(level).addEntity(any());
        try {
            Field field = Level.class.getDeclaredField("isBeingConverted");
            field.setAccessible(true);
            field.setBoolean(level, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return level;
    }

    private static FullChunk newMockChunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(level);
        lenient().doNothing().when(chunk).addEntity(any());
        return chunk;
    }

    private static CompoundTag baseNbt() {
        return new CompoundTag()
                .putList(new ListTag<>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64))
                        .add(new DoubleTag("", 0.5)))
                .putList(new ListTag<>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)));
    }
}
