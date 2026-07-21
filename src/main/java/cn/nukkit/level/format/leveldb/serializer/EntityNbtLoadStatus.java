package cn.nukkit.level.format.leveldb.serializer;

/**
 * Result of normalizing a raw entity NBT record for live Nukkit entity loading.
 * <p>
 * 归一化原始实体 NBT 以便加载为 Nukkit 活动实体的结果。
 *
 * @see EntityNbtAdapter#normalizeForNukkitLoad(cn.nukkit.nbt.tag.CompoundTag)
 */
public enum EntityNbtLoadStatus {
    /**
     * The NBT can be turned into a live entity via {@code Entity.createEntity}.
     * NBT 可通过 {@code Entity.createEntity} 转换为活动实体。
     */
    LOADABLE,
    /**
     * The entity id is recognized but cannot be constructed on this server
     * (e.g. residual custom entity from a removed plugin). The raw NBT should be
     * preserved verbatim so it survives a load/save round-trip.
     * <p>
     * 实体 id 已识别但当前服务端无法构造（例如被移除插件残留的自定义实体）。
     * 原始 NBT 应原样保留，使其在加载/保存周期中不丢失。
     */
    PRESERVE_ONLY,
    /**
     * The NBT is structurally invalid (missing/illegal Pos, id, etc.) and must be dropped.
     * NBT 结构非法（缺 Pos、id 等），必须丢弃。
     */
    INVALID
}
