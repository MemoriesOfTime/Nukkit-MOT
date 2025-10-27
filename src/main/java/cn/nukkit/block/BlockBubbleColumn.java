package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.block.BlockFadeEvent;
import cn.nukkit.event.block.BlockFromToEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.level.particle.SplashParticle;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class BlockBubbleColumn extends BlockTransparentMeta {

    public static final int DIRECTION_UP = 0;
    public static final int DIRECTION_DOWN = 1;

    public BlockBubbleColumn() {
        this(0);
    }

    public BlockBubbleColumn(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BUBBLE_COLUMN;
    }

    @Override
    public String getName() {
        return "Bubble Column";
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.FLOW_INTO_BLOCK;
    }

    @Override
    public boolean canPassThrough() {
        return true;
    }

    @Override
    public boolean canBeFlowedInto() {
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        return Item.EMPTY_ARRAY;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(new BlockAir());
    }

    @Override
    protected AxisAlignedBB recalculateCollisionBoundingBox() {
        return this;
    }

    @Override
    public boolean isBreakable(Item item) {
        return false;
    }

    @Override
    public boolean canBePlaced() {
        return false;
    }

    @Override
    public boolean canBeReplaced() {
        return true;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return null;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        return null;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        if (this.getLevel().setBlock(this, this, true, true)) {
            this.getLevel().setBlock(this, 1, Block.get(Block.STILL_WATER), true, true);
            return true;
        }
        return false;
    }

    @Override
    public double getHardness() {
        return 100;
    }

    @Override
    public double getResistance() {
        return 500;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            Block water = getLevelBlockAtLayer(1);
            if (!(water instanceof BlockWater) || water.getDamage() != 0 && water.getDamage() != 8) {
                fadeOut(water);
                return type;
            }

            Block down = down();
            if (down.getId() == BUBBLE_COLUMN) {
                if (down.getDamage() != this.getDamage()) {
                    this.getLevel().setBlock(this, down, true, true);
                }
            } else if (down.getId() == SOUL_SAND) {
                if (this.getDamage() != DIRECTION_UP) {
                    setDamage(DIRECTION_UP);
                    this.getLevel().setBlock(this, this, true, true);
                }
            } else if (down.getId() == MAGMA) {
                if (this.getDamage() != DIRECTION_DOWN) {
                    setDamage(DIRECTION_DOWN);
                    this.getLevel().setBlock(this, this, true, true);
                }
            } else {
                fadeOut(water);
                return type;
            }

            Block up = up();
            if (up instanceof BlockWater && (up.getDamage() == 0 || up.getDamage() == 8)) {
                BlockFromToEvent event = new BlockFromToEvent(this, up);
                event.call();
                if (!event.isCancelled()) {
                    this.getLevel().setBlock(up, 1, Block.get(WATER), true, false);
                    this.getLevel().setBlock(up, 0, Block.get(BUBBLE_COLUMN, this.getDamage()), true, true);
                }
            }

            return type;
        }

        return 0;
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public void onEntityCollide(Entity entity) {
        if (entity.canBeMovedByCurrents()) {
            if (up().getId() == AIR) {
                double motY = entity.motionY;

                if (this.getDamage() == DIRECTION_DOWN) {
                    motY = Math.max(-0.9, motY - 0.03);
                } else {
                    if ((entity instanceof EntityCreature entityCreature) && motY < -entityCreature.getGravity() * 8) {
                        motY = -entityCreature.getGravity() * 2;
                    }
                    motY = Math.min(1.8, motY + 0.1);
                }

                ThreadLocalRandom random = ThreadLocalRandom.current();
                for(int i = 0; i < 2; ++i) {
                    level.addParticle(new SplashParticle(add(random.nextFloat(), random.nextFloat() + 1, random.nextFloat())));
                    level.addParticle(new BubbleParticle(add(random.nextFloat(), random.nextFloat() + 1, random.nextFloat())));
                }

                if (entity instanceof Player player) {
                    player.setMotionLocally(entity.getMotion().setY(motY));
                } else {
                    entity.motionY = motY;
                }
            } else {
                double motY = entity.motionY;

                if (getDamage() == DIRECTION_DOWN) {
                    motY = Math.max(-0.3, motY - 0.3);
                } else {
                    motY = Math.min(0.7, motY + 0.06);
                }

                if (entity instanceof Player) {
                    ((Player) entity).setMotionLocally(entity.getMotion().setY(motY));
                } else {
                    entity.motionY = motY;
                }
            }
            if (entity instanceof EntityItem) {
                entity.collisionBlocks = null;
            }
            entity.resetFallDistance();
        }
    }

    private void fadeOut(Block water) {
        BlockFadeEvent event = new BlockFadeEvent(this, water.clone());
        if (!event.isCancelled()) {
            this.getLevel().setBlock(this, 1, Block.get(AIR), true, false);
            this.getLevel().setBlock(this, 0, event.getNewState(), true, true);
        }
    }

}