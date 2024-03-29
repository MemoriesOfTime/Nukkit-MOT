package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.event.block.DoorToggleEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.level.sound.DoorSound;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.Faceable;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class BlockDoor extends BlockTransparentMeta implements Faceable {

    public static final int DOOR_OPEN_BIT = 0x04;
    public static final int DOOR_TOP_BIT = 0x08;
    public static final int DOOR_HINGE_BIT = 0x01;
    public static final int DOOR_POWERED_BIT = 0x02;

    private static final int[] faces = {1, 2, 3, 0};

    protected BlockDoor(int meta) {
        super(meta);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean breaksWhenMoved() {
        return true;
    }

    @Override
    public boolean sticksToPiston() {
        return false;
    }

    private int getFullDamage() {
        int up;
        int down;
        if (isTop()) {
            down = this.down().getDamage();
            up = this.getDamage();
        } else {
            down = this.getDamage();
            up = this.up().getDamage();
        }

        boolean isRight = (up & DOOR_HINGE_BIT) > 0;

        return down & 0x07 | (isTop() ? 0x08 : 0) | (isRight ? 0x10 : 0);
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {

        double f = 0.1875;
        int damage = this.getFullDamage();

        AxisAlignedBB bb = new SimpleAxisAlignedBB(
                this.x,
                this.y,
                this.z,
                this.x + 1,
                this.y + 2,
                this.z + 1
        );

        int j = damage & 0x03;
        boolean isOpen = ((damage & 0x04) > 0);
        boolean isRight = ((damage & 0x10) > 0);

        if (j == 0) {
            if (isOpen) {
                if (!isRight) {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z,
                            this.x + 1,
                            this.y + 1,
                            this.z + f
                    );
                } else {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z + 1 - f,
                            this.x + 1,
                            this.y + 1,
                            this.z + 1
                    );
                }
            } else {
                bb.setBounds(
                        this.x,
                        this.y,
                        this.z,
                        this.x + f,
                        this.y + 1,
                        this.z + 1
                );
            }
        } else if (j == 1) {
            if (isOpen) {
                if (!isRight) {
                    bb.setBounds(
                            this.x + 1 - f,
                            this.y,
                            this.z,
                            this.x + 1,
                            this.y + 1,
                            this.z + 1
                    );
                } else {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z,
                            this.x + f,
                            this.y + 1,
                            this.z + 1
                    );
                }
            } else {
                bb.setBounds(
                        this.x,
                        this.y,
                        this.z,
                        this.x + 1,
                        this.y + 1,
                        this.z + f
                );
            }
        } else if (j == 2) {
            if (isOpen) {
                if (!isRight) {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z + 1 - f,
                            this.x + 1,
                            this.y + 1,
                            this.z + 1
                    );
                } else {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z,
                            this.x + 1,
                            this.y + 1,
                            this.z + f
                    );
                }
            } else {
                bb.setBounds(
                        this.x + 1 - f,
                        this.y,
                        this.z,
                        this.x + 1,
                        this.y + 1,
                        this.z + 1
                );
            }
        } else if (j == 3) {
            if (isOpen) {
                if (!isRight) {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z,
                            this.x + f,
                            this.y + 1,
                            this.z + 1
                    );
                } else {
                    bb.setBounds(
                            this.x + 1 - f,
                            this.y,
                            this.z,
                            this.x + 1,
                            this.y + 1,
                            this.z + 1
                    );
                }
            } else {
                bb.setBounds(
                        this.x,
                        this.y,
                        this.z + 1 - f,
                        this.x + 1,
                        this.y + 1,
                        this.z + 1
                );
            }
        }

        return bb;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.down().getId() == AIR) {
                Block up = this.up();

                if (up instanceof BlockDoor) {
                    this.getLevel().setBlock(up, Block.get(BlockID.AIR), false);
                    this.getLevel().useBreakOn(this, getToolType() == ItemTool.TYPE_PICKAXE ? Item.get(ItemID.DIAMOND_PICKAXE) : Item.get(Item.WOODEN_PICKAXE)); // Drop iron doors
                }

                return Level.BLOCK_UPDATE_NORMAL;
            }
        }

        if (type == Level.BLOCK_UPDATE_REDSTONE) {
            if ((!isOpen() && this.level.isBlockPowered(this.getLocation())) || (isOpen() && !this.level.isBlockPowered(this.getLocation()))) {
                this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, isOpen() ? 15 : 0, isOpen() ? 0 : 15));

                this.toggle(null);
            }
        }

        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (this.y > 254) return false;
        if (face == BlockFace.UP) {
            Block blockUp = this.up();
            Block blockDown = this.down();
            if (!blockUp.canBeReplaced() || blockDown.isTransparent()) {
                return false;
            }

            int direction = faces[player != null ? player.getDirection().getHorizontalIndex() : 0];

            Block left = this.getSide(player.getDirection().rotateYCCW());
            Block right = this.getSide(player.getDirection().rotateY());
            int metaUp = DOOR_TOP_BIT;
            if (left.getId() == this.getId() || (!right.isTransparent() && left.isTransparent())) { //Door hinge
                metaUp |= DOOR_HINGE_BIT;
            }

            this.setDamage(direction);
            this.getLevel().setBlock(block, this, true, true); //Bottom
            this.getLevel().setBlock(blockUp, Block.get(this.getId(), metaUp), true); //Top

            if (!this.isOpen() && this.level.isBlockPowered(this.getLocation())) {
                this.toggle(null);
                metaUp |= DOOR_POWERED_BIT;
                this.getLevel().setBlockDataAt(blockUp.getFloorX(), blockUp.getFloorY(), blockUp.getFloorZ(), metaUp);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean onBreak(Item item) {
        if (isTop(this.getDamage())) {
            Block down = this.down();
            if (down.getId() == this.getId()) {
                this.getLevel().setBlock(down, Block.get(BlockID.AIR), true);
            }
        } else {
            Block up = this.up();
            if (up.getId() == this.getId()) {
                this.getLevel().setBlock(up, Block.get(BlockID.AIR), true);
            }
        }
        this.getLevel().setBlock(this, Block.get(BlockID.AIR), true);

        return true;
    }

    @Override
    public boolean onActivate(Item item) {
        return this.onActivate(item, null);
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (!this.toggle(player)) {
            return false;
        }

        this.level.addSound(new DoorSound(this));
        return true;
    }

    public boolean toggle(Player player) {
        DoorToggleEvent event = new DoorToggleEvent(this, player);
        this.getLevel().getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        Block down;
        Block up;
        if (isTop(this.getDamage())) {
            down = this.down();
            up = this;
        } else {
            down = this;
            up = this.up();
        }

        if (up.getId() != down.getId()) {
            return false;
        }

        this.level.setBlockDataAt(down.getFloorX(), down.getFloorY(), down.getFloorZ(), down.getDamage() ^ 0x04);
        this.level.addSound(new DoorSound(this));
        return true;
    }

    public boolean isOpen() {
        if (isTop(this.getDamage())) {
            return (this.down().getDamage() & DOOR_OPEN_BIT) > 0;
        } else {
            return (this.getDamage() & DOOR_OPEN_BIT) > 0;
        }
    }
    public boolean isTop() {
        return isTop(this.getDamage());
    }

    public boolean isTop(int meta) {
        return (meta & DOOR_TOP_BIT) != 0;
    }

    public boolean isRightHinged() {
        if (isTop()) {
            return (this.getDamage() & DOOR_HINGE_BIT ) > 0;
        }
        return (this.up().getDamage() & DOOR_HINGE_BIT) > 0;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }
}
