package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityFallingBlock;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.BlockColor;

public class BlockScaffolding extends BlockFallable {

    private int meta = 0;

    public BlockScaffolding() {
        this(0);
    }

    public BlockScaffolding(int meta) {
        this.meta = meta;
    }

    @Override
    public int getId() {
        return SCAFFOLDING;
    }

    @Override
    public int getFullId() {
        return (this.getId() << DATA_BITS) + this.getDamage();
    }

    @Override
    public String getName() {
        return "Scaffolding";
    }

    @Override
    public int getDamage() {
        return this.meta;
    }

    @Override
    public void setDamage(int meta) {
        this.meta = meta;
    }

    public int getStability() {
        return this.getDamage() & 0x7;
    }

    public void setStability(int stability) {
        this.setDamage(stability & 0x7 | (this.getDamage() & 0x8));
    }

    public boolean getStabilityCheck() {
        return (this.getDamage() & 0x8) > 0;
    }

    public void setStabilityCheck(boolean check) {
        if (check) {
            this.setDamage(this.getDamage() | 0x8);
        } else {
            this.setDamage(this.getDamage() & 0x7);
        }
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public double getResistance() {
        return 0;
    }

    @Override
    public int getBurnChance() {
        return 60;
    }

    @Override
    public int getBurnAbility() {
        return 60;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean canBeClimbed() {
        return true;
    }

    @Override
    public boolean canBeFlowedInto() {
        return false;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        return new SimpleAxisAlignedBB(x, y + (2.0/16), z, x + 1, y + 1, z + 1);
    }

    @Override
    public void onEntityCollide(Entity entity) {
        entity.resetFallDistance();
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public double getMinY() {
        return this.y + (14.0/16);
    }

    @Override
    public boolean canPassThrough() {
        return false;
    }

    @Override
    public boolean isTransparent() {
        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.TRANSPARENT_BLOCK_COLOR;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (block instanceof BlockLava) {
            return false;
        }

        Block down = this.down();
        if (target.getId() != SCAFFOLDING && down.getId() != SCAFFOLDING && down.getId() != AIR && !down.isSolid()) {
            boolean scaffoldOnSide = false;
            for (int i = 0; i < 4; i++) {
                BlockFace sideFace = BlockFace.fromHorizontalIndex(i);
                if (sideFace != face) {
                    Block side = this.getSide(sideFace);
                    if (side.getId() == SCAFFOLDING) {
                        scaffoldOnSide = true;
                        break;
                    }
                }
            }
            if (!scaffoldOnSide) {
                return false;
            }
        }

        this.setDamage(0x8);
        this.getLevel().setBlock(this, this, true, true);
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            Block down = this.down();
            if (down.isSolid()) {
                if (this.getDamage() != 0) {
                    this.setDamage(0);
                    this.getLevel().setBlock(this, this, true, true);
                }
                return type;
            }

            int stability = 7;
            for (BlockFace face : BlockFace.values()) {
                if (face == BlockFace.UP) {
                    continue;
                }

                Block otherBlock = this.getSide(face);
                if (otherBlock.getId() == SCAFFOLDING) {
                    BlockScaffolding other = (BlockScaffolding) otherBlock;
                    int otherStability = other.getStability();
                    if (otherStability < stability) {
                        if (face == BlockFace.DOWN) {
                            stability = otherStability;
                        } else {
                            stability = otherStability + 1;
                        }
                    }
                }
            }

            if (stability >= 7) {
                if (this.getStabilityCheck()) {
                    super.onUpdate(type);
                } else {
                    this.getLevel().scheduleUpdate(this, 0);
                }
                return type;
            }

            this.setStabilityCheck(false);
            this.setStability(stability);
            this.getLevel().setBlock(this, this, true, true);
            return type;
        } else if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            this.getLevel().useBreakOn(this);
            return type;
        }

        return 0;
    }

    @Override
    protected EntityFallingBlock createFallingEntity(CompoundTag customNbt) {
        this.setDamage(0);
        customNbt.putBoolean("BreakOnLava", true);
        return super.createFallingEntity(customNbt);
    }

    @Override
    public Item toItem() {
        return new ItemBlock(new BlockScaffolding(0));
    }
}
