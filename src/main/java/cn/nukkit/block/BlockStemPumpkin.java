package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSeedsPumpkin;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockFace.Plane;
import cn.nukkit.utils.Faceable;
import cn.nukkit.utils.Utils;

import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;

/**
 * Created by Pub4Game on 15.01.2016.
 */
public class BlockStemPumpkin extends BlockCrops implements Faceable, BlockPropertiesHelper {

    protected static final BlockProperties PROPERTIES = new BlockProperties(GROWTH, VanillaProperties.FACING_DIRECTION);

    public BlockStemPumpkin() {
        this(0);
    }

    public BlockStemPumpkin(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PUMPKIN_STEM;
    }

    @Override
    public String getName() {
        return "Pumpkin Stem";
    }

    @Override
    public BlockFace getBlockFace() {
        return this.getPropertyValue(VanillaProperties.FACING_DIRECTION);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setPropertyValue(VanillaProperties.FACING_DIRECTION, face);
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:pumpkin_stem";
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.down().getId() != FARMLAND) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_RANDOM) {
            if (Utils.rand(1, 2) == 1) {
                if (this.getPropertyValue(GROWTH) < 7) {

                    this.setPropertyValue(GROWTH, this.getPropertyValue(GROWTH) + 1);

                    Block block = this.clone();
                    BlockGrowEvent ev = new BlockGrowEvent(this, block);
                    Server.getInstance().getPluginManager().callEvent(ev);

                    if (!ev.isCancelled()) {
                        this.getLevel().setBlock(this, ev.getNewState(), true, true);
                    }
                    return Level.BLOCK_UPDATE_RANDOM;
                } else {
                    for (BlockFace face : Plane.HORIZONTAL) {
                        Block b = this.getSide(face);
                        if (b.getId() == PUMPKIN) {
                            return Level.BLOCK_UPDATE_RANDOM;
                        }
                    }

                    BlockFace sideFace = Plane.HORIZONTAL.random(Utils.nukkitRandom);
                    Block side = this.getSide(sideFace);
                    Block d = side.down();
                    if (side.getId() == AIR && (d.getId() == FARMLAND || d.getId() == GRASS || d.getId() == DIRT)) {
                        BlockGrowEvent ev = new BlockGrowEvent(side, Block.get(PUMPKIN));
                        Server.getInstance().getPluginManager().callEvent(ev);
                        if (!ev.isCancelled()) {
                            this.getLevel().setBlock(side, ev.getNewState(), true, true);

                            this.setBlockFace(sideFace);
                            this.getLevel().setBlock(this, this, true, true);
                        }
                    }
                }
            }
            return Level.BLOCK_UPDATE_RANDOM;
        }
        return 0;
    }

    @Override
    public Item toItem() {
        return new ItemSeedsPumpkin();
    }

    @Override
    public Item[] getDrops(Item item) {
        if (this.getDamage() < 4) return Item.EMPTY_ARRAY;
        return new Item[]{
                new ItemSeedsPumpkin(0, Utils.rand(0, 48) >> 4)
        };
    }
}