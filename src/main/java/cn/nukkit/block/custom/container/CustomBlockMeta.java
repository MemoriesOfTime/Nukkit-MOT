package cn.nukkit.block.custom.container;

import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockMeta;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.block.custom.properties.BlockProperties;

public class CustomBlockMeta extends BlockMeta implements BlockStorageContainer {

    private final String blockName;
    private final int blockId;
    private final BlockProperties properties;
    private final boolean useLazyLookup;

    /**
     * 构造函数（兼容旧API）
     * Constructor for backward compatibility
     */
    public CustomBlockMeta(String blockName, int blockId, BlockProperties properties) {
        this(blockName, blockId, properties, 0);
    }

    /**
     * 构造函数（兼容旧API）
     * Constructor for backward compatibility
     */
    public CustomBlockMeta(String blockName, int blockId, BlockProperties properties, int meta) {
        super(meta);
        this.blockName = blockName;
        this.blockId = blockId;
        this.properties = properties;
        this.useLazyLookup = false;
    }

    /**
     * 构造函数（自动ID分配）
     * Constructor for automatic ID allocation
     */
    public CustomBlockMeta(String blockName, BlockProperties properties) {
        this(blockName, properties, 0);
    }

    /**
     * 构造函数（自动ID分配）
     * Constructor for automatic ID allocation
     */
    public CustomBlockMeta(String blockName, BlockProperties properties, int meta) {
        super(meta);
        this.blockName = blockName;
        this.blockId = -1;
        this.properties = properties;
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

    @Override
    public int getStorage() {
        return this.getDamage();
    }

    @Override
    public void setStorage(int damage) {
        this.setDamage(damage);
    }

    @Override
    public BlockProperties getBlockProperties() {
        return this.properties;
    }
}
