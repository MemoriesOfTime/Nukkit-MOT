package cn.nukkit.level.format.anvil;

import cn.nukkit.MockServer;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.BaseLevelProvider;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Anvil 异步区块加载支持测试:putChunkIfAbsent 语义与能力开关
 * <p>
 * Anvil async chunk loading support tests: putChunkIfAbsent semantics and capability flag
 *
 * @author LT_Name
 */
public class AnvilAsyncChunkSupportTest {

    private Anvil anvil;
    private ConcurrentMap<Long, BaseFullChunk> chunks;

    @BeforeEach
    public void setUp() throws Exception {
        MockServer.init();

        this.anvil = Mockito.mock(Anvil.class, Mockito.CALLS_REAL_METHODS);
        // CALLS_REAL_METHODS 不执行构造器,手动初始化依赖字段
        // CALLS_REAL_METHODS skips constructors; init the fields under test
        this.chunks = new ConcurrentHashMap<>();
        setField(this.anvil, "chunks", this.chunks);
        setField(this.anvil, "level", Mockito.mock(Level.class));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = BaseLevelProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void putChunkIfAbsentInsertsWhenSlotEmpty() {
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);

        BaseFullChunk existing = this.anvil.putChunkIfAbsent(3, 5, chunk);

        Assertions.assertNull(existing);
        Assertions.assertSame(chunk, this.chunks.get(Level.chunkHash(3, 5)));
        Mockito.verify(chunk).setProvider(this.anvil);
        Mockito.verify(chunk).setPosition(3, 5);
    }

    @Test
    public void putChunkIfAbsentKeepsExistingChunk() {
        BaseFullChunk existing = Mockito.mock(BaseFullChunk.class);
        this.chunks.put(Level.chunkHash(3, 5), existing);
        BaseFullChunk decoded = Mockito.mock(BaseFullChunk.class);

        BaseFullChunk result = this.anvil.putChunkIfAbsent(3, 5, decoded);

        Assertions.assertSame(existing, result);
        Assertions.assertSame(existing, this.chunks.get(Level.chunkHash(3, 5)));
    }

    @Test
    public void offThreadReadSupportedFollowsProviderLifecycle() throws Exception {
        Assertions.assertTrue(this.anvil.isOffThreadChunkReadSupported());

        // close() 会把 level 置 null,此后不再受理异步读取 / close() nulls the level, disabling off-thread reads
        setField(this.anvil, "level", null);
        Assertions.assertFalse(this.anvil.isOffThreadChunkReadSupported());
    }

    @Test
    public void tileTicksAreDeferredToPendingBlockUpdates() throws Exception {
        // 构造带 TileTicks 的 nbt:指向 cn.nukkit.block.Stone
        // Build nbt with a TileTicks entry pointing at cn.nukkit.block.Stone
        CompoundTag tick = new CompoundTag()
                .putString("i", "BlockStone")
                .putInt("x", 16)
                .putInt("y", 64)
                .putInt("z", 32)
                .putInt("t", 5)
                .putInt("p", 0);
        ListTag<CompoundTag> tileTicks = new ListTag<>("TileTicks");
        tileTicks.add(tick);

        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<>("Sections"))
                .putList(tileTicks)
                .putInt("xPos", 1)
                .putInt("zPos", 2);

        Chunk chunk = new Chunk(this.anvil, nbt);

        // 解码阶段不应触碰 Level:anvil 的 level(mock) 上 scheduleUpdate 系列方法从未被调用
        // Decode must not touch Level: scheduleUpdate overloads never invoked on the anvil's mocked level
        Level mockedLevel = (Level) getField(this.anvil, "level");
        Mockito.verify(mockedLevel, Mockito.never())
                .scheduleUpdate(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());

        // 延迟条目应暂存到 pendingBlockUpdates
        // The deferred entry must be stashed in pendingBlockUpdates
        @SuppressWarnings("unchecked")
        List<BaseFullChunk.PendingBlockUpdate> pending = (List<BaseFullChunk.PendingBlockUpdate>)
                getField(chunk, "pendingBlockUpdates");
        Assertions.assertNotNull(pending, "TileTicks must be captured as pending updates");
        Assertions.assertEquals(1, pending.size());
    }

    @Test
    public void deferredTileTicksSurviveSaveBeforeChunkInitialization() throws Exception {
        CompoundTag tick = new CompoundTag()
                .putString("i", "BlockStone")
                .putInt("x", 16)
                .putInt("y", 64)
                .putInt("z", 32)
                .putInt("t", 5)
                .putInt("p", 2);
        ListTag<CompoundTag> tileTicks = new ListTag<>("TileTicks");
        tileTicks.add(tick);
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<>("Sections"))
                .putList(tileTicks)
                .putInt("xPos", 1)
                .putInt("zPos", 2);
        Chunk chunk = new Chunk(this.anvil, nbt);

        Mockito.when(this.anvil.getLevel().getPendingBlockUpdates(chunk)).thenReturn(null);
        byte[] saved = chunk.toBinary();
        CompoundTag root = NBTIO.read(new ByteArrayInputStream(Zlib.inflate(saved)), ByteOrder.BIG_ENDIAN);
        ListTag<CompoundTag> savedTicks = root.getCompound("Level").getList("TileTicks", CompoundTag.class);

        Assertions.assertEquals(1, savedTicks.size());
        CompoundTag savedTick = savedTicks.get(0);
        Assertions.assertEquals("BlockStone", savedTick.getString("i"));
        Assertions.assertEquals(5, savedTick.getInt("t"));
        Assertions.assertEquals(2, savedTick.getInt("p"));
    }

    private static Object getField(Object target, String name) throws Exception {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
