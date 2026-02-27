package cn.nukkit.block.custom.container;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.custom.CustomBlockManager;

public class CustomBlock extends Block implements BlockContainer {

    private final String blockName;
    private final int blockId;
    private final boolean useLazyLookup;

    /**
     * 构造函数（兼容旧API）
     * Constructor for backward compatibility
     *
     * @param blockName 方块标识符 / block identifier
     * @param blockId 方块ID / block ID
     */
    public CustomBlock(String blockName, int blockId) {
        this.blockName = blockName;
        this.blockId = blockId;
        this.useLazyLookup = false;
    }

    /**
     * 构造函数（自动ID分配）
     * Constructor for automatic ID allocation
     *
     * @param blockName 方块标识符 / block identifier
     */
    public CustomBlock(String blockName) {
        this.blockName = blockName;
        this.blockId = -1;
        this.useLazyLookup = true;
    }

    @Override
    public String getIdentifier() {
        return this.blockName;
    }

    @Override
    public int getId() {
        return getNukkitId();
    }

    @Override
    public int getNukkitId() {
        if (useLazyLookup && blockId == -1) {
            int id = CustomBlockManager.get().getBlockId(blockName);
            return id != -1 ? id : BlockID.INFO_UPDATE;
        }
        return this.blockId;
    }

    @Override
    public String getName() {
        return this.blockName;
    }
}
