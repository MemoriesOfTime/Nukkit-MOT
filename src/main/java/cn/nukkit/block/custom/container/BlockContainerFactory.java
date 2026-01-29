package cn.nukkit.block.custom.container;

import cn.nukkit.block.custom.properties.BlockProperties;

public interface BlockContainerFactory {

    BlockContainer create(int meta);

    /**
     * 创建简单方块容器（手动指定ID）
     * Create simple block container with manually specified ID
     *
     * @param blockName 方块标识符 / block identifier
     * @param blockId 方块ID / block ID
     * @return 方块容器 / block container
     * @deprecated 使用 {@link #createSimple(String)} 代替，ID将自动分配
     */
    @Deprecated
    static BlockContainer createSimple(String blockName, int blockId) {
        return new CustomBlock(blockName, blockId);
    }

    /**
     * 创建简单方块容器（自动分配ID）
     * Create simple block container with automatic ID allocation
     *
     * @param blockName 方块标识符 / block identifier
     * @return 方块容器 / block container
     */
    static BlockContainer createSimple(String blockName) {
        return new CustomBlock(blockName);
    }

    /**
     * 创建带属性的方块容器（手动指定ID）
     * Create block container with properties and manually specified ID
     *
     * @param blockName 方块标识符 / block identifier
     * @param blockId 方块ID / block ID
     * @param meta 方块元数据值 / block metadata value
     * @param properties 方块属性 / block properties
     * @return 方块容器 / block container
     * @deprecated 使用 {@link #createMeta(String, int, BlockProperties)} 代替，ID将自动分配
     */
    @Deprecated
    static BlockContainer createMeta(String blockName, int blockId, int meta, BlockProperties properties) {
        return new CustomBlockMeta(blockName, blockId, properties, meta);
    }

    /**
     * 创建带属性的方块容器（自动分配ID）
     * Create block container with properties and automatic ID allocation
     *
     * @param blockName 方块标识符 / block identifier
     * @param meta 方块元数据值 / block metadata value
     * @param properties 方块属性 / block properties
     * @return 方块容器 / block container
     */
    static BlockContainer createMeta(String blockName, int meta, BlockProperties properties) {
        return new CustomBlockMeta(blockName, properties, meta);
    }

    /**
     * 创建带属性的方块容器（自动分配ID，默认meta=0）
     * Create block container with properties and automatic ID allocation (default meta=0)
     *
     * @param blockName 方块标识符 / block identifier
     * @param properties 方块属性 / block properties
     * @return 方块容器 / block container
     */
    static BlockContainer createMeta(String blockName, BlockProperties properties) {
        return new CustomBlockMeta(blockName, properties, 0);
    }
}
