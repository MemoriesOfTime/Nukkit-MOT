package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCommandBlock;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.GameRule;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Impulse command block. Executes once per redstone pulse.
 * <p>
 * Subclasses {@link BlockCommandBlockRepeating} and {@link BlockCommandBlockChain}
 * inherit all placement, redstone, UI, and comparator logic.
 * <p>
 * Adapted from Lumi (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 */
public class BlockCommandBlock extends BlockSolidMeta
        implements Faceable, BlockEntityHolder<BlockEntityCommandBlock>, BlockPropertiesHelper {

    private static final BlockProperties PROPERTIES = new BlockProperties(
            VanillaProperties.FACING_DIRECTION,
            VanillaProperties.CONDITIONAL_BIT
    );

    public BlockCommandBlock() {
        this(0);
    }

    public BlockCommandBlock(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return COMMAND_BLOCK;
    }

    @Override
    public double getHardness() {
        return -1;
    }

    @Override
    public double getResistance() {
        return 18000000;
    }

    @Override
    public String getName() {
        return "Command Block";
    }

    @Override
    public boolean isBreakable(Item item) {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:command_block";
    }

    @Override
    public BlockFace getBlockFace() {
        return getPropertyValue(VanillaProperties.FACING_DIRECTION);
    }

    public void setBlockFace(BlockFace face) {
        setPropertyValue(VanillaProperties.FACING_DIRECTION, face);
    }

    public boolean isConditionalBit() {
        return getBooleanValue(VanillaProperties.CONDITIONAL_BIT);
    }

    public void setConditionalBit(boolean conditional) {
        setBooleanValue(VanillaProperties.CONDITIONAL_BIT, conditional);
    }

    @Override
    public Class<? extends BlockEntityCommandBlock> getBlockEntityClass() {
        return BlockEntityCommandBlock.class;
    }

    @Override
    public String getBlockEntityType() {
        return BlockEntity.COMMAND_BLOCK;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face,
                         double fx, double fy, double fz, @Nullable Player player) {
        if (player == null || !player.isCreative()) {
            return false;
        }
        if (Math.abs(player.x - this.x) < 2 && Math.abs(player.z - this.z) < 2) {
            double y = player.y + player.getEyeHeight();
            if (y - this.y > 2) {
                this.setBlockFace(BlockFace.UP);
            } else if (this.y - y > 0) {
                this.setBlockFace(BlockFace.DOWN);
            } else {
                this.setBlockFace(player.getHorizontalFacing().getOpposite());
            }
        } else {
            this.setBlockFace(player.getHorizontalFacing().getOpposite());
        }
        this.setConditionalBit(false);

        CompoundTag nbt = new CompoundTag()
                .putString("id", BlockEntity.COMMAND_BLOCK)
                .putInt("x", this.getFloorX())
                .putInt("y", this.getFloorY())
                .putInt("z", this.getFloorZ());
        boolean success = BlockEntityHolder.setBlockAndCreateEntity(this, true, true, nbt) != null;
        if (success && this.level.isBlockPowered(this)) {
            BlockEntityCommandBlock tile = this.getBlockEntity();
            if (tile != null) {
                tile.setPowered(true);
            }
        }
        return success;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player) {
        if (player == null || !player.isCreative()) {
            return false;
        }
        if (!this.level.getGameRules().getBoolean(GameRule.COMMAND_BLOCKS_ENABLED)) {
            return false;
        }
        BlockEntityCommandBlock tile = this.getOrCreateBlockEntity();
        if (tile != null) {
            tile.spawnTo(player);
            player.addWindow(tile.getInventory());
            return true;
        }
        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL || type == Level.BLOCK_UPDATE_REDSTONE) {
            BlockEntityCommandBlock tile = this.getBlockEntity();
            if (tile == null) {
                return 0;
            }
            if (this.level.isBlockPowered(this)) {
                if (!tile.isPowered()) {
                    tile.setPowered(true);
                    // JE (CommandBlock.setPoweredAndUpdate): a redstone rising edge
                    // only schedules a tick for impulse blocks in redstone mode.
                    // Repeating blocks are polled by their own onUpdate; chain blocks
                    // are only triggered by their predecessor's executeChain; and
                    // impulse blocks in "Always Active" mode are triggered once via
                    // the setAutomatic side effect when set in the GUI.
                    if (this.getId() != REPEATING_COMMAND_BLOCK
                            && this.getId() != CHAIN_COMMAND_BLOCK
                            && !tile.isAuto()) {
                        tile.trigger();
                    }
                }
            } else {
                tile.setPowered(false);
            }
        }
        return 0;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntityCommandBlock tile = this.getBlockEntity();
        return tile != null ? Math.min(tile.getSuccessCount(), 15) : 0;
    }
}
