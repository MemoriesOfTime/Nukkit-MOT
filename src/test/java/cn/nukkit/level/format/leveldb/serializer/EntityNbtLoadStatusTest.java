package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.MockServer;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 {@link EntityNbtAdapter#normalizeForNukkitLoad(CompoundTag)} 对 saveId 空且 identifier 空的
 * 实体 NBT 判定为 {@link EntityNbtLoadStatus#DROPPABLE}，从而在加载时被丢弃而非永久保留。
 * <p>
 * 同时验证保守策略：只要存在 identifier 字段（即便无法解析）或有非空 id，仍走 PRESERVE_ONLY 而非丢弃。
 * <p>
 * 注意：MockServer 不初始化实体注册表，因此 {@code canCreateEntity} 对所有 id 返回 false，
 * LOADABLE 路径无法在此测试，本测试聚焦 DROPPABLE 与 PRESERVE_ONLY 的区分。
 */
public class EntityNbtLoadStatusTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    private static CompoundTag withPos(CompoundTag nbt) {
        return nbt.putList(new ListTag<DoubleTag>("Pos")
                .add(new DoubleTag("", 0.5))
                .add(new DoubleTag("", 64.0))
                .add(new DoubleTag("", 0.5)));
    }

    /**
     * saveId 空 + 无 identifier → DROPPABLE（纯垃圾，丢弃）。
     */
    @Test
    void emptyIdAndNoIdentifierIsDroppable() {
        CompoundTag nbt = withPos(new CompoundTag().putString("id", ""));
        assertEquals(EntityNbtLoadStatus.DROPPABLE, EntityNbtAdapter.normalizeForNukkitLoad(nbt),
                "Empty id with no identifier must be DROPPABLE");
    }

    /**
     * 既无 id 也无 identifier → DROPPABLE。
     */
    @Test
    void noIdAndNoIdentifierIsDroppable() {
        CompoundTag nbt = withPos(new CompoundTag());
        assertEquals(EntityNbtLoadStatus.DROPPABLE, EntityNbtAdapter.normalizeForNukkitLoad(nbt),
                "No id and no identifier must be DROPPABLE");
    }

    /**
     * 空 id 但携带 identifier 字段（即便无法解析）→ PRESERVE_ONLY（保守保留，插件可能复活）。
     */
    @Test
    void emptyIdWithIdentifierIsPreserveOnly() {
        CompoundTag nbt = withPos(new CompoundTag()
                .putString("id", "")
                .putString("identifier", "myplugin:removed_entity"));
        assertEquals(EntityNbtLoadStatus.PRESERVE_ONLY, EntityNbtAdapter.normalizeForNukkitLoad(nbt),
                "Empty id with an identifier field must stay PRESERVE_ONLY (plugin may revive)");
    }

    /**
     * 非空但不可识别的 id（残留自定义实体）→ PRESERVE_ONLY。
     */
    @Test
    void unrecognizedNonEmptyIdIsPreserveOnly() {
        CompoundTag nbt = withPos(new CompoundTag().putString("id", "10086"));
        assertEquals(EntityNbtLoadStatus.PRESERVE_ONLY, EntityNbtAdapter.normalizeForNukkitLoad(nbt),
                "Non-empty unrecognized id must stay PRESERVE_ONLY");
    }

    /**
     * 仅有不可解析的 identifier（无 id 字段）→ PRESERVE_ONLY。
     */
    @Test
    void unknownIdentifierAloneIsPreserveOnly() {
        CompoundTag nbt = withPos(new CompoundTag()
                .putString("identifier", "myplugin:removed_entity"));
        assertEquals(EntityNbtLoadStatus.PRESERVE_ONLY, EntityNbtAdapter.normalizeForNukkitLoad(nbt),
                "Unknown identifier without id must stay PRESERVE_ONLY");
    }
}
