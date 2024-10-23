package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.block.BlockFadeEvent;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.event.block.BlockSpreadEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;

import java.util.concurrent.ThreadLocalRandom;

public class BlockSeaPickle extends BlockFlowable {

    public BlockSeaPickle() {
        this(0);
    }

    protected BlockSeaPickle(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SEA_PICKLE;
    }

    @Override
    public String getName() {
        return "Sea Pickle";
    }

    public boolean isDead() {
        return (getDamage() & 0x4) == 0x4;
    }

    public void setDead(boolean dead) {
        if (dead) {
            this.setDamage(getDamage() | 0x4);
        } else {
            this.setDamage(getDamage() ^ 0x4);
        }
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL){
            Block down = this.down();
            if (!down.isSolid() || down.getId() == MAGMA) {
                this.getLevel().useBreakOn(this);
                return type;
            }

            Block layer1 = getLevelBlockAtLayer(1);
            if (layer1 instanceof BlockWater) {
                if (this.isDead() || layer1.getDamage() != 0 && layer1.getDamage() != 8) {
                    this.getLevel().useBreakOn(this);
                    return type;
                }
            } else if (!this.isDead()) {
                BlockFadeEvent event = new BlockFadeEvent(this, new BlockSeaPickle(this.getDamage() ^ 0x4));
                event.call();
                if (!event.isCancelled()) {
                    this.getLevel().setBlock(this, event.getNewState(), true, true);
                }
            }

            return type;
        }
        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (target.getId() == SEA_PICKLE && (target.getDamage() & 0b11) < 3) {
            target.setDamage(target.getDamage() + 1);
            this.getLevel().setBlock(target, target, true, true);
            return true;
        }

        if (target.isSolid() && target.getId() != MAGMA){
            Block layer1 = block.getLevelBlockAtLayer(1);
            if (layer1 instanceof BlockWater) {
                if (layer1.getDamage() != 0 && layer1.getDamage() != 8) {
                    return false;
                }

                if (layer1.getDamage() == 8) {
                    this.getLevel().setBlock(block, 1, new BlockWater(), true, false);
                }
            } else {
                this.setDead(true);
            }

            this.getLevel().setBlock(block, 0, this, true, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() != Item.DYE || item.getDamage() != 0x0f){ //Bone meal
            return super.onActivate(item, player);
        }

        BlockSeaPickle block = (BlockSeaPickle) this.clone();
        if (!block.isDead()){
            block.setDamage(3);
        }

        BlockGrowEvent blockGrowEvent = new BlockGrowEvent(this, block);
        Server.getInstance().getPluginManager().callEvent(blockGrowEvent);

        if (blockGrowEvent.isCancelled()){
            return false;
        }

        this.getLevel().setBlock(this, blockGrowEvent.getNewState(), false, true);
        this.level.addParticle(new BoneMealParticle(this));

        if (player != null && !player.isCreative()) {
            item.count--;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Block[] blocksAround = this.getLevel().getCollisionBlocks(new SimpleAxisAlignedBB(x - 2, y - 2, z - 2, x + 3, y, z + 3));
        for (Block blockNearby : blocksAround) {
            if (blockNearby.getId() == CORAL_BLOCK) {
                Block up = blockNearby.up();
                if (up instanceof BlockWater && (up.getDamage() == 0 || up.getDamage() == 8) && random.nextInt(6) == 0) {
                    BlockSpreadEvent blockSpreadEvent = new BlockSpreadEvent(up, this, new BlockSeaPickle(random.nextInt(3)));
                    if (!blockSpreadEvent.isCancelled()) {
                        this.getLevel().setBlock(up, 1, new BlockWater(), true, false);
                        this.getLevel().setBlock(up, blockSpreadEvent.getNewState(), true, true);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public int getLightLevel() {
        if (this.isDead()) {
            return 0;
        } else {
            return (this.getDamage() + 1) * 3;
        }
    }

    @Override
    public Item toItem() {
        return new ItemBlock(new BlockSeaPickle());
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{ new ItemBlock(new BlockSeaPickle(), 0, (this.getDamage() & 0x3) + 1) };
    }
}
