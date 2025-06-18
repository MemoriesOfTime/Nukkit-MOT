package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.block.properties.enums.CrackedState;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.math.BlockFace;

public class BlockSnifferEgg extends BlockTransparentMeta implements BlockPropertiesHelper {
    private static final BlockProperties PROPERTIES = new BlockProperties(VanillaProperties.CRACKED_STATE);

    public BlockSnifferEgg() {
        this(0);
    }

    public BlockSnifferEgg(int meta) {
        super(meta);
    }

    @Override
    public String getIdentifier() {
        return "minecraft:sniffer_egg";
    }

    @Override
    public int getId() {
        return SNIFFER_EGG;
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public String getName() {
        return "Sniffer Egg";
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setCrackedState(CrackedState.NO_CRACKS);
        return this.getLevel().setBlock(this, this, true, true);
    }

    public void setCrackedState(CrackedState state) {
        setPropertyValue(VanillaProperties.CRACKED_STATE, state);
    }

    public CrackedState getCrackedState() {
        return getPropertyValue(VanillaProperties.CRACKED_STATE);
    }

    @Override
    public double getResistance() {
        return 2.5;
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0, 1);
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }
}
