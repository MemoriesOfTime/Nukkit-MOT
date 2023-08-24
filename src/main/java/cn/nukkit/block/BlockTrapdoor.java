package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.event.block.DoorToggleEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.utils.Faceable;

public class BlockTrapdoor extends BlockTransparentMeta implements Faceable {
    public static final int DIRECTION_MASK = 0b11;
    public static final int TRAPDOOR_TOP_BIT = 0x04;
    public static final int TRAPDOOR_OPEN_BIT = 0x08;
    private static final AxisAlignedBB[] boundingBoxDamage = new AxisAlignedBB[16];

    static {
        for (int damage = 0; damage < 16; damage++) {
            AxisAlignedBB bb;
            double f = 0.1875;
            if ((damage & TRAPDOOR_TOP_BIT) > 0) {
                bb = new SimpleAxisAlignedBB(0, 1 - f, 0, 1, 1, 1);
            } else {
                bb = new SimpleAxisAlignedBB(0, 0, 0, 1, 0 + f, 1);
            }
            if ((damage & TRAPDOOR_OPEN_BIT) > 0) {
                if ((damage & DIRECTION_MASK) == 0) {
                    bb.setBounds(0, 0, 1 - f, 1, 1, 1);
                } else if ((damage & DIRECTION_MASK) == 1) {
                    bb.setBounds(0, 0, 0, 1, 1, 0 + f);
                }
                if ((damage & DIRECTION_MASK) == 2) {
                    bb.setBounds(1 - f, 0, 0, 1, 1, 1);
                }
                if ((damage & DIRECTION_MASK) == 3) {
                    bb.setBounds(0, 0, 0, 0 + f, 1, 1);
                }
            }
            boundingBoxDamage[damage] = bb;
        }
    }

    public BlockTrapdoor() {
        this(0);
    }

    public BlockTrapdoor(int meta) {
        super(meta);
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_REDSTONE && (
                !this.isOpen() && level.isBlockPowered(this) || this.isOpen() && !level.isBlockPowered(this)
        )) {
            level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, this.isOpen() ? 15 : 0, this.isOpen() ? 0 : 15));
            this.setDamage(this.getDamage() ^ TRAPDOOR_OPEN_BIT);
            level.setBlock(this, this, true);
            level.addLevelEvent(this.add(0.5, 0.5, 0.5), LevelEventPacket.EVENT_SOUND_DOOR);
            return type;
        }

        return 0;
    }

    @Override
    public double getHardness() {
        return 3;
    }

    @Override
    public double getResistance() {
        return 15;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public String getName() {
        return "Oak Trapdoor";
    }

    @Override
    public int getId() {
        return TRAPDOOR;
    }

    @Override
    public double getMinX() {
        return x + this.getRelativeBoundingBox().getMinX();
    }

    @Override
    public double getMinY() {
        return y + this.getRelativeBoundingBox().getMinY();
    }

    @Override
    public double getMinZ() {
        return z + this.getRelativeBoundingBox().getMinZ();
    }

    @Override
    public double getMaxX() {
        return x + this.getRelativeBoundingBox().getMaxX();
    }

    @Override
    public double getMaxY() {
        return y + this.getRelativeBoundingBox().getMaxY();
    }

    @Override
    public double getMaxZ() {
        return z + this.getRelativeBoundingBox().getMaxZ();
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        BlockFace facing;
        boolean top;
        int meta = 0;

        if (face.getAxis().isHorizontal() || player == null) {
            facing = face;
            top = fy > 0.5;
        } else {
            facing = player.getDirection().getOpposite();
            top = face != BlockFace.UP;
        }

        int faceBit = facing.getReversedHorizontalIndex();
        meta |= faceBit;

        if (top) {
            meta |= TRAPDOOR_TOP_BIT;
        }
        this.setDamage(meta);
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (this.toggle(player)) {
            level.addLevelEvent(this.add(0.5, 0.5, 0.5), LevelEventPacket.EVENT_SOUND_DOOR);
            return true;
        }
        return false;
    }

    public boolean toggle(Player player) {
        DoorToggleEvent ev = new DoorToggleEvent(this, player);
        this.getLevel().getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return false;
        }
        this.setDamage(this.getDamage() ^ TRAPDOOR_OPEN_BIT);
        this.getLevel().setBlock(this, this, true);
        return true;
    }

    public boolean isOpen() {
        return (this.getDamage() & TRAPDOOR_OPEN_BIT) == TRAPDOOR_OPEN_BIT;
    }

    public boolean isTop() {
        return (this.getDamage() & TRAPDOOR_TOP_BIT) == TRAPDOOR_TOP_BIT;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromReversedHorizontalIndex(this.getDamage() & DIRECTION_MASK);
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    private AxisAlignedBB getRelativeBoundingBox() {
        return boundingBoxDamage[this.getDamage()];
    }
}
