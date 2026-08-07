package cn.nukkit.level.format;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.serializer.EntityNbtAdapter;
import cn.nukkit.level.format.leveldb.serializer.EntityNbtLoadStatus;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 验证 {@link BaseFullChunk#initChunk()} 对无法识别的实体 NBT 走 PRESERVE_ONLY 路径，
 * 将原始 NBT 保留在 {@code preservedEntityNbt} 中以便保存周期回写（issue #800）。
 * <p>
 * Verifies {@link BaseFullChunk#initChunk()} routes unrecognized entity NBT through the
 * PRESERVE_ONLY path, retaining the raw NBT in {@code preservedEntityNbt} for the save cycle.
 */
@ExtendWith(MockitoExtension.class)
public class AnvilUnrecognizedEntityPreservationTest {

    private BaseFullChunk chunk;
    private LevelProvider provider;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() throws Exception {
        chunk = mock(BaseFullChunk.class, CALLS_REAL_METHODS);
        // Mockito mock 不执行字段初始化器，需手动初始化
        Field changesField = BaseFullChunk.class.getDeclaredField("changes");
        changesField.setAccessible(true);
        changesField.set(chunk, new AtomicLong());

        Field preservedField = BaseFullChunk.class.getDeclaredField("preservedEntityNbt");
        preservedField.setAccessible(true);
        preservedField.set(chunk, new java.util.ArrayList<CompoundTag>());

        provider = mock(LevelProvider.class);
        // 先获取 defaultLevel，避免在 when() 内部调用引起 Mockito 状态混乱
        cn.nukkit.level.Level defaultLevel = Server.getInstance().getDefaultLevel();
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(defaultLevel);
    }

    /**
     * Regression for issue #800: a chunk containing a residual custom-entity NBT
     * (id="10086", from a removed plugin) must NOT silently drop the record. It must be
     * retained in {@link BaseFullChunk#getPreservedEntityNbt()} so the next save writes
     * it back unchanged.
     */
    @Test
    void unrecognizedEntityNbtIsPreservedOnLoad() {
        setNbtEntities(chunk, Collections.singletonList(
                new CompoundTag()
                        .putString("id", "10086")
                        .putList(new ListTag<DoubleTag>("Pos")
                                .add(new DoubleTag("", 0.5))
                                .add(new DoubleTag("", 64.0))
                                .add(new DoubleTag("", 0.5)))
        ));

        chunk.initChunk();

        List<CompoundTag> preserved = chunk.getPreservedEntityNbt();
        assertEquals(1, preserved.size(),
                "Unrecognized entity NBT must be preserved exactly once");
        assertEquals("10086", preserved.get(0).getString("id"),
                "Preserved NBT must retain its original id");
        assertEquals(0, chunk.getEntities().size(),
                "No live entity should be constructed for an unrecognized id");
    }

    /**
     * All three ids reported in issue #800 must be preserved.
     */
    @Test
    void issue800ReportedIdsAreAllPreserved() {
        List<CompoundTag> entities = new ArrayList<>();
        for (int id : new int[]{10086, 10089, 10090}) {
            entities.add(new CompoundTag()
                    .putString("id", String.valueOf(id))
                    .putList(new ListTag<DoubleTag>("Pos")
                            .add(new DoubleTag("", 0.5))
                            .add(new DoubleTag("", 64.0))
                            .add(new DoubleTag("", 0.5))));
        }
        setNbtEntities(chunk, entities);

        chunk.initChunk();

        assertEquals(3, chunk.getPreservedEntityNbt().size(),
                "All three issue-#800 ids must be preserved");
    }

    /**
     * Sanity: invalid NBT (no id, no identifier) is dropped, not preserved.
     */
    @Test
    void invalidNbtIsDroppedNotPreserved() {
        setNbtEntities(chunk, Collections.singletonList(
                new CompoundTag()
                        .putList(new ListTag<DoubleTag>("Pos")
                                .add(new DoubleTag("", 0.5))
                                .add(new DoubleTag("", 64.0))
                                .add(new DoubleTag("", 0.5)))
        ));

        chunk.initChunk();

        assertTrue(chunk.getPreservedEntityNbt().isEmpty(),
                "Invalid NBT (no id) must not be preserved");
    }

    /**
     * Sanity: the PRESERVE_ONLY classification is the precondition for the preservation
     * behavior. Directly verifies the routing decision for the issue #800 ids.
     */
    @Test
    void normalizeClassifiesUnknownIdAsPreserveOnly() {
        CompoundTag nbt = new CompoundTag()
                .putString("id", "10086")
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)));
        assertEquals(EntityNbtLoadStatus.PRESERVE_ONLY, EntityNbtAdapter.normalizeForNukkitLoad(nbt),
                "Residual custom-entity NBT must be PRESERVE_ONLY");
    }

    /**
     * Sanity: an identifier-based record (Bedrock actor format) with an unknown namespace
     * is also PRESERVE_ONLY.
     */
    @Test
    void unknownIdentifierIsPreserveOnly() {
        CompoundTag nbt = new CompoundTag()
                .putString("identifier", "myplugin:removed_entity")
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)));
        assertEquals(EntityNbtLoadStatus.PRESERVE_ONLY, EntityNbtAdapter.normalizeForNukkitLoad(nbt),
                "Unknown identifier-based NBT must be PRESERVE_ONLY");
    }

    /**
     * Helper: set the NBTentities field via reflection (package-private in BaseFullChunk).
     */
    private static void setNbtEntities(BaseFullChunk chunk, List<CompoundTag> entities) {
        try {
            Field field = BaseFullChunk.class.getDeclaredField("NBTentities");
            field.setAccessible(true);
            field.set(chunk, new ArrayList<>(entities));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
