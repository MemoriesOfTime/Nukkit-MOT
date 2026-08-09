package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Regression test for the command block minecart (and other minecart variants)
 * showing the wrong display block on spawn.
 * <p>
 * Root cause: the subclass constructor called
 * {@code setDisplayBlock(block, false)} <em>after</em> {@code super(chunk, nbt)},
 * but {@code initEntity()} (which computes {@link Entity#DATA_DISPLAY_ITEM}) runs
 * <em>inside</em> that super call — before {@code blockInside} is assigned. So the
 * spawn metadata carried {@code DATA_HAS_DISPLAY=0} and the client fell back to the
 * entity's own texture (the orange pulse block, which looks like a repeating
 * command block). The fix introduces a {@code getDefaultDisplayBlock()} hook that
 * {@code prepareDataProperty()} consults during {@code initEntity()}.
 */
public class EntityMinecartDisplayBlockTest {

    private static Server serverMock;

    @BeforeAll
    static void init() throws Exception {
        MockServer.init();
        serverMock = MockServer.get();

        // Entity.init() dispatches EntitySpawnEvent and schedules an update; both go
        // through the mock server/plugin manager. Default lenient mocks suffice since
        // we only assert that construction completes and metadata is set.
    }

    private static Level newMockLevel() {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(level.getServer()).thenReturn(serverMock);
        lenient().when(serverMock.getTick()).thenReturn(0);
        lenient().doNothing().when(level).addEntity(org.mockito.ArgumentMatchers.any());
        // scheduleUpdate() (a final Entity method) reads this field and short-circuits when
        // true, so it never touches the uninitialized updateEntities map on the mock.
        try {
            Field f = Level.class.getDeclaredField("isBeingConverted");
            f.setAccessible(true);
            f.setBoolean(level, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return level;
    }

    private static FullChunk newMockChunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().doNothing().when(chunk).addEntity(org.mockito.ArgumentMatchers.any());
        lenient().when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private static EntityMinecartAbstract spawnMinecart(java.util.function.Function<FullChunk, CompoundTag> nbtBuilder) {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);
        CompoundTag nbt = nbtBuilder.apply(chunk);
        return new EntityMinecartCommandBlock(chunk, nbt);
    }

    private static CompoundTag baseNbt() {
        return new CompoundTag()
                .putList(new ListTag<>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)))
                .putList(new ListTag<>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)));
    }

    @Test
    void freshlySpawnedCommandBlockMinecartShowsCommandBlock() {
        EntityMinecartAbstract minecart = spawnMinecart(chunk -> baseNbt());

        // DATA_HAS_DISPLAY must be 1 (otherwise the client uses the entity's default
        // texture, which for NETWORK_ID 100 is the orange pulse block).
        assertEquals(1, minecart.getDataPropertyByte(Entity.DATA_HAS_DISPLAY),
                "Command block minecart must report a display block on spawn");

        // DATA_DISPLAY_ITEM is packed as legacyId | (meta << 16); for a normal
        // command block that is exactly Block.COMMAND_BLOCK (137).
        int display = minecart.getDataPropertyInt(Entity.DATA_DISPLAY_ITEM);
        int expected = Block.COMMAND_BLOCK;
        assertEquals(expected, display,
                "Display item should be command_block (137), was " + display);
    }

    @Test
    void loadedFromSaveKeepsPersistedDisplayTile() {
        // Simulate a minecart restored from NBT where a different display tile was
        // persisted. The CustomDisplayTile branch must win over the default hook.
        CompoundTag nbt = baseNbt()
                .putBoolean("CustomDisplayTile", true)
                .putInt("DisplayTile", Block.COMMAND_BLOCK)
                .putInt("DisplayOffset", 8);

        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        EntityMinecartAbstract minecart = new EntityMinecartCommandBlock(chunk, nbt);

        assertEquals(1, minecart.getDataPropertyByte(Entity.DATA_HAS_DISPLAY));
        assertEquals(Block.COMMAND_BLOCK, minecart.getDataPropertyInt(Entity.DATA_DISPLAY_ITEM));
        // Persisted offset must be honored, not the default 6.
        assertEquals(8, minecart.getDataPropertyInt(Entity.DATA_DISPLAY_OFFSET));
    }
}
