package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.math.BlockFace;

public class BlockWoodBark extends BlockWood {

    private static final String[] NAMES = {
            "Oak Wood",
            "Spruce Wood",
            "Birch Wood",
            "Jungle Wood",
            "Acacia Wood",
            "Dark Oak Wood",
    };

    public static final int STRIPPED_BIT = 0b1000;

    public BlockWoodBark() {
        this(0);
    }
    
    public BlockWoodBark(int meta) {
        super(meta);
    }
    
    @Override
    public void setDamage(int meta) {
        super.setDamage(meta);
    }
    
    @Override
    public int getId() {
        return WOOD_BARK;
    }
    
    @Override
    public String getName() {
        int variant = (this.getDamage() & 0x7);
        String name = variant >= NAMES.length ? NAMES[0] : NAMES[variant];
        if ((this.getDamage() & STRIPPED_BIT) != 0) {
            name = "Stripped " + name;
        }
        return name;
    }
    
    @Override
    protected int getStrippedId() {
        return getId();
    }
    
    @Override
    protected int getStrippedDamage() {
        return getDamage() | STRIPPED_BIT;
    }
    
    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (face.getAxis().isHorizontal()) {
            if (face.getAxis() == BlockFace.Axis.X) {
                setDamage(getDamage() | 0x10);
            } else {
                setDamage(getDamage() | 0x20);
            }
        }
        this.getLevel().setBlock(block, this, true, true);
        
        return true;
    }
    
    @Override
    public Item toItem() {
        int meta = getDamage() & 0xF;
        return new ItemBlock(Block.get(WOOD_BARK, meta), meta);
    }
}
