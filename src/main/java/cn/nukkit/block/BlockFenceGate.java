package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.block.DoorToggleEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.level.sound.DoorSound;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;

/**
 * Created on 2015/11/23 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockFenceGate extends BlockTransparentMeta implements Faceable {

    public static final int DIRECTIO_BIT = 0x03;
    public static final int OPEN_BIT = 0x04;
    public static final int IN_WALL_BIT = 0x08;

    public BlockFenceGate() {
        this(0);
    }

    public BlockFenceGate(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return FENCE_GATE_OAK;
    }

    @Override
    public String getName() {
        return "Oak Fence Gate";
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 15;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        if (this.isOpen()) {
            return null;
        }
        int i = this.getDamage() & DIRECTIO_BIT;
        if (i == 2 || i == 0) {
            return new SimpleAxisAlignedBB(x, y, z + 0.375, x + 1, y + 1.5, z + 0.625);
        } else {
            return new SimpleAxisAlignedBB( x + 0.375, y, z, x + 0.625, y + 1.5, z + 1);
        }
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(player != null ? player.getDirection().getHorizontalIndex() : 0);
        this.getLevel().setBlock(block, this, true, true);

        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (player == null) {
            return false;
        }

        if (!this.toggle(player)) {
            return false;
        }

        this.getLevel().setBlock(this, this, true);
        this.getLevel().addSound(new DoorSound(this));

        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WOOD_BLOCK_COLOR;
    }

    public boolean toggle(Player player) {
        DoorToggleEvent event = new DoorToggleEvent(this, player);
        this.getLevel().getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        player = event.getPlayer();

        int direction;

        if (player != null) {
            double yaw = player.yaw;
            double rotation = (yaw - 90) % 360;

            if (rotation < 0) {
                rotation += 360.0;
            }

            int originDirection = this.getDamage() & 0x01;

            if (originDirection == 0) {
                if (rotation >= 0 && rotation < 180) {
                    direction = 2;
                } else {
                    direction = 0;
                }
            } else {
                if (rotation >= 90 && rotation < 270) {
                    direction = 3;
                } else {
                    direction = 1;
                }
            }
        } else {
            int originDirection = this.getDamage() & 0x01;

            if (originDirection == 0) {
                direction = 0;
            } else {
                direction = 1;
            }
        }

        this.setDamage(direction | ((~this.getDamage()) & OPEN_BIT));
        this.level.addSound(new DoorSound(this));
        this.level.setBlock(this, this, false, false);
        return true;
    }

    public boolean isOpen() {
        return (this.getDamage() & OPEN_BIT) > 0;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_REDSTONE) {
            if ((!isOpen() && this.level.isBlockPowered(this.getLocation())) || (isOpen() && !this.level.isBlockPowered(this.getLocation()))) {
                this.toggle(null);
                return type;
            }
        }

        return 0;
    }
    
    @Override
    public Item toItem() {
        return Item.get(Item.FENCE_GATE, 0, 1);
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & DIRECTIO_BIT);
    }

    @Override
    public boolean canPassThrough() {
        return this.isOpen();
    }
}
