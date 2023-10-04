package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.blockproperty.ArrayBlockProperty;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import static cn.nukkit.block.blockproperty.CommonBlockProperties.DEPRECATED;

/**
 * @author CreeperFace
 */
public class BlockBone extends BlockSolid implements Faceable {

    private static final ArrayBlockProperty<String> SPECIAL_PILLAR_AXIS = new ArrayBlockProperty<>("pillar_axis", false,
            new String[] {
                    "y",
                    "unused1",
                    "unused2",
                    "unused3",
                    "x",
                    "unused5",
                    "unused6",
                    "unused7",
                    "z",
            }
    );

    public static final BlockProperties PROPERTIES = new BlockProperties(SPECIAL_PILLAR_AXIS, DEPRECATED);

    private static final int[] faces = {
            0,
            0,
            0b1000,
            0b1000,
            0b0100,
            0b0100
    };

    @Override
    public int getId() {
        return BONE_BLOCK;
    }

    @NotNull
    @Override
    public BlockProperties getProperties() {
        return PROPERTIES;
    }

    @Override
    public String getName() {
        return "Bone Block";
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 10;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            return new Item[]{new ItemBlock(this)};
        }

        return Item.EMPTY_ARRAY;
    }

    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(((this.getDamage() & 0x3) | faces[face.getIndex()]));
        this.getLevel().setBlock(block, this, true);
        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SAND_BLOCK_COLOR;
    }
}
