package cn.nukkit.block;

import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.UnsignedIntBlockProperty;
import org.jetbrains.annotations.NotNull;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BlockUnknown extends BlockMeta {

    public static final UnsignedIntBlockProperty UNKNOWN = new UnsignedIntBlockProperty("nukkit-unknown", true, 0xFFFFFFFF);

    public static final BlockProperties PROPERTIES = new BlockProperties(UNKNOWN);

    private final int id;

    public BlockUnknown(int id) {
        this(id, 0);
    }

    public BlockUnknown(int id, Integer meta) {
        super(meta);
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }

    @NotNull
    @Override
    public BlockProperties getProperties() {
        return PROPERTIES;
    }

    @Override
    public String getName() {
        return "Unknown";
    }
}
