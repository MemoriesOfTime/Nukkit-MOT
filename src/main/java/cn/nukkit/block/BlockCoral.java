package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.block.BlockFadeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class BlockCoral extends BlockFlowable {

    public static final int TYPE_TUBE = 0;
    public static final int TYPE_BRAIN = 1;
    public static final int TYPE_BUBBLE = 2;
    public static final int TYPE_FIRE = 3;
    public static final int TYPE_HORN = 4;

    public static final int TYPE_BIT = 0x7;
    public static final int DEAD_BIT = 0x8;

    private static final String[] NAMES = {
            "Tube Coral",
            "Brain Coral",
            "Bubble Coral",
            "Fire Coral",
            "Horn Coral",
            "",
            "",
            "",
            "Dead Tube Coral",
            "Dead Brain Coral",
            "Dead Bubble Coral",
            "Dead Fire Coral",
            "Dead Horn Coral"
    };

    private static final BlockColor[] COLORS = new BlockColor[] {
            BlockColor.BLUE_BLOCK_COLOR,
            BlockColor.PINK_BLOCK_COLOR,
            BlockColor.PURPLE_BLOCK_COLOR,
            BlockColor.RED_BLOCK_COLOR,
            BlockColor.YELLOW_BLOCK_COLOR,
            // Invalid
            BlockColor.BLUE_BLOCK_COLOR,
            BlockColor.BLUE_BLOCK_COLOR,
            BlockColor.BLUE_BLOCK_COLOR
    };
    
    public BlockCoral() {
        this(0);
    }
    
    public BlockCoral(int meta) {
        super(meta);
    }
    
    @Override
    public int getId() {
        return CORAL;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.FLOW_INTO_BLOCK;
    }
    
    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            Block down = this.down();
            if (!down.isSolid()) {
                this.getLevel().useBreakOn(this);
            } else if (!this.isDead()) {
                this.getLevel().scheduleUpdate(this, 60 + ThreadLocalRandom.current().nextInt(40));
            }
            return type;
        } else if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            if (!this.isDead() && !(this.getLevelBlockAtLayer(1) instanceof BlockWater)  && !(this.getLevelBlockAtLayer(1) instanceof BlockIceFrosted)) {
                BlockFadeEvent event = new BlockFadeEvent(this, new BlockCoral(this.getDamage() | 0x8));
                if (!event.isCancelled()) {
                    this.setDead(true);
                    this.getLevel().setBlock(this, event.getNewState(), true, true);
                }
            }
            return type;
        }
        return 0;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @NotNull Player player) {
        if (this.down().isTransparent()) {
            return false;
        }
        if (this.getDamage() < 8 && !(block instanceof BlockWater || block.level.isBlockWaterloggedAt(block.getChunk(), (int) block.x, (int) block.y, (int) block.z))) {
            this.setDamage(this.getDamage() | DEAD_BIT); // Dead
        }
        if (this.getLevel().setBlock(this, this, true, true)) {
            if (block instanceof BlockWater) {
                this.getLevel().setBlock((int) this.x, (int) this.y, (int) this.z, BlockLayer.WATERLOGGED.ordinal(), Block.get(Block.STILL_WATER), true, true);
            }
            return true;
        }
        return false;
    }

    @Override
    public String getName() {
        int variant = this.getDamage();
        if (variant >= NAMES.length) {
            return NAMES[0];
        }
        return NAMES[variant];
    }

    @Override
    public BlockColor getColor() {
        if (this.isDead()) {
            return BlockColor.GRAY_BLOCK_COLOR;
        }

        return COLORS[this.getDamage() & TYPE_BIT];
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.getEnchantment(Enchantment.ID_SILK_TOUCH) != null) {
            return super.getDrops(item);
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    public boolean isDead() {
        return (this.getDamage() & DEAD_BIT) != 0;
    }

    public void setDead(boolean dead) {
        if (dead) {
            this.setDamage(this.getDamage() | DEAD_BIT);
        } else {
            this.setDamage(this.getDamage() ^ DEAD_BIT);
        }
    }
}
