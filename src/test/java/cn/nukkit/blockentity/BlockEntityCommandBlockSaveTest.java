package cn.nukkit.blockentity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the command block entity persists its {@code command} (and name)
 * across a {@code saveNBT()} -> reconstruct -> {@code initBlockEntity()} round-trip.
 * <p>
 * Regression guard for the same field-initialization-order trap that affected
 * {@link cn.nukkit.entity.item.EntityMinecartCommandBlock}: {@link BlockEntity}'s
 * constructor calls {@code initBlockEntity()} up the {@code super()} chain, which
 * runs <em>before</em> the subclass's field initializers (e.g. {@code command = ""}).
 * Those initializers therefore executed <em>after</em> {@code initBlockEntity()} had
 * loaded the persisted values from NBT, silently clobbering them with defaults.
 */
public class BlockEntityCommandBlockSaveTest {

    private static Server serverMock;
    private static PluginManager pluginManagerMock;

    @BeforeAll
    static void init() throws Exception {
        Block.init();
        // Required so BlockEntity.saveNBT() can resolve the save id via getSaveId().
        BlockEntity.registerBlockEntity(BlockEntity.COMMAND_BLOCK, BlockEntityCommandBlock.class);
        serverMock = mock(Server.class);
        pluginManagerMock = mock(PluginManager.class);
        lenient().when(serverMock.getPluginManager()).thenReturn(pluginManagerMock);
        Field instanceField = Server.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, serverMock);
    }

    @AfterAll
    static void cleanup() throws Exception {
        Field instanceField = Server.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private static Level newMockLevel() {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(level.getServer()).thenReturn(serverMock);
        GameRules gameRules = mock(GameRules.class);
        lenient().when(level.getGameRules()).thenReturn(gameRules);
        lenient().when(gameRules.getBoolean(GameRule.COMMAND_BLOCKS_ENABLED)).thenReturn(true);
        // saveNBT()/getLevelBlock() resolve the backing block; return a command block
        // so isBlockEntityValid() and mode lookups don't NPE.
        lenient().when(level.getBlock(anyVector())).thenReturn(Block.get(Block.COMMAND_BLOCK));
        lenient().when(level.getBlockDataAt(0, 64, 0)).thenReturn(Block.COMMAND_BLOCK);
        return level;
    }

    private static cn.nukkit.math.Vector3 anyVector() {
        return org.mockito.ArgumentMatchers.any(cn.nukkit.math.Vector3.class);
    }

    private static FullChunk newMockChunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().doNothing().when(chunk).addBlockEntity(org.mockito.ArgumentMatchers.any());
        lenient().when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private static CompoundTag baseNbt() {
        return new CompoundTag()
                .putString("id", BlockEntity.COMMAND_BLOCK)
                .putInt("x", 0)
                .putInt("y", 64)
                .putInt("z", 0)
                .putBoolean("isMovable", true);
    }

    private static CompoundTag cloneCompound(CompoundTag tag) {
        return tag.clone();
    }

    @Test
    void commandSurvivesSaveLoadRoundTrip() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        BlockEntityCommandBlock original = new BlockEntityCommandBlock(chunk, baseNbt());
        original.setCommand("say hello");

        // Simulate a true disk round-trip via a fresh NBT tree (no shared state).
        // The constructor triggers initBlockEntity(), the same path createBlockEntity()
        // takes on load.
        original.saveNBT();
        BlockEntityCommandBlock reloaded = new BlockEntityCommandBlock(chunk, cloneCompound(original.namedTag));

        assertEquals("say hello", reloaded.getCommand(),
                "Command must survive save/load round-trip");
    }

    @Test
    void nameSurvivesSaveLoadRoundTrip() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        BlockEntityCommandBlock original = new BlockEntityCommandBlock(chunk, baseNbt());
        original.setName("MyName");

        original.saveNBT();
        BlockEntityCommandBlock reloaded = new BlockEntityCommandBlock(chunk, cloneCompound(original.namedTag));

        assertEquals("MyName", reloaded.getName(),
                "CommandSender name must survive save/load round-trip");
    }

    @Test
    void spawnCompoundCarriesCommandAfterLoad() {
        // Regression for "command executes but GUI shows it blank": the editor reads
        // the Command field from getSpawnCompound()'s BlockEntityDataPacket. The loaded
        // command must reach it, otherwise the input box is empty.
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        BlockEntityCommandBlock original = new BlockEntityCommandBlock(chunk, baseNbt());
        original.setCommand("say hello");
        original.saveNBT();

        BlockEntityCommandBlock reloaded = new BlockEntityCommandBlock(chunk, cloneCompound(original.namedTag));
        CompoundTag spawnNbt = reloaded.getSpawnCompound();

        assertEquals("say hello", spawnNbt.getString(ICommandBlock.TAG_COMMAND),
                "getSpawnCompound must carry the loaded command so the client GUI can show it");
    }
}
