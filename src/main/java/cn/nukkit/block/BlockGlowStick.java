package cn.nukkit.block;

import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.CommonBlockProperties;
import cn.nukkit.item.Item;
import org.jetbrains.annotations.NotNull;

/**
 * Created by PetteriM1
 */
public class BlockGlowStick extends BlockTransparentMeta {

    public static final BlockProperties PROPERTIES = CommonBlockProperties.EMPTY_PROPERTIES;

    public BlockGlowStick() {
        this(0);
    }

    public BlockGlowStick(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GLOW_STICK;
    }

    @NotNull
    @Override
    public BlockProperties getProperties() {
        return PROPERTIES;
    }

    @Override
    public String getName() {
        return "Glow Stick";
    }

    @Override
    public Item toItem() {
        return Item.get(AIR);
    }
}
