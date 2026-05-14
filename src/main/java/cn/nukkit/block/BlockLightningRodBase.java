package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared implementation for lightning rod copper variants.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public abstract class BlockLightningRodBase extends BlockTransparentMeta implements Faceable, BlockPropertiesHelper, Oxidizable, Waxable {

    public static final int FACING_DIRECTION_MASK = 0x07;
    public static final int POWERED_BIT = 0x08;
    private static final int ACTIVATION_TICKS = 8;

    private static final BlockProperties PROPERTIES = new BlockProperties(VanillaProperties.FACING_DIRECTION, VanillaProperties.POWERED);

    protected BlockLightningRodBase() {
        this(0);
    }

    protected BlockLightningRodBase(int meta) {
        super(isValidMeta(meta) ? meta : 0);
    }

    private static boolean isValidMeta(int meta) {
        int face = meta & FACING_DIRECTION_MASK;
        return meta >= 0 && (meta & ~(FACING_DIRECTION_MASK | POWERED_BIT)) == 0 && face <= BlockFace.EAST.getIndex();
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public String getIdentifier() {
        return switch (this.getId()) {
            case LIGHTNING_ROD -> "minecraft:lightning_rod";
            case EXPOSED_LIGHTNING_ROD -> "minecraft:exposed_lightning_rod";
            case WEATHERED_LIGHTNING_ROD -> "minecraft:weathered_lightning_rod";
            case OXIDIZED_LIGHTNING_ROD -> "minecraft:oxidized_lightning_rod";
            case WAXED_LIGHTNING_ROD -> "minecraft:waxed_lightning_rod";
            case WAXED_EXPOSED_LIGHTNING_ROD -> "minecraft:waxed_exposed_lightning_rod";
            case WAXED_WEATHERED_LIGHTNING_ROD -> "minecraft:waxed_weathered_lightning_rod";
            case WAXED_OXIDIZED_LIGHTNING_ROD -> "minecraft:waxed_oxidized_lightning_rod";
            default -> throw new IllegalStateException("Unexpected lightning rod block id: " + this.getId());
        };
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean isSolid(BlockFace side) {
        return false;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public double getHardness() {
        return 3;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getMinX() {
        return this.getBlockFace().getAxis() == BlockFace.Axis.X ? this.x : this.x + 0.4;
    }

    @Override
    public double getMinY() {
        return this.getBlockFace().getAxis() == BlockFace.Axis.Y ? this.y : this.y + 0.4;
    }

    @Override
    public double getMinZ() {
        return this.getBlockFace().getAxis() == BlockFace.Axis.Z ? this.z : this.z + 0.4;
    }

    @Override
    public double getMaxX() {
        return this.getBlockFace().getAxis() == BlockFace.Axis.X ? this.x + 1 : this.x + 0.6;
    }

    @Override
    public double getMaxY() {
        return this.getBlockFace().getAxis() == BlockFace.Axis.Y ? this.y + 1 : this.y + 0.6;
    }

    @Override
    public double getMaxZ() {
        return this.getBlockFace().getAxis() == BlockFace.Axis.Z ? this.z + 1 : this.z + 0.6;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face,
                         double fx, double fy, double fz, Player player) {
        this.setBlockFace(face);
        this.setPowered(false);
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_STONE) {
            return new Item[]{this.toItem()};
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0, 1);
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public BlockFace getBlockFace() {
        return this.getPropertyValue(VanillaProperties.FACING_DIRECTION);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setPropertyValue(VanillaProperties.FACING_DIRECTION, face == null ? BlockFace.UP : face);
    }

    public boolean isPowered() {
        return this.getPropertyValue(VanillaProperties.POWERED);
    }

    public void setPowered(boolean powered) {
        this.setPropertyValue(VanillaProperties.POWERED, powered);
    }

    public boolean activatePower() {
        return this.activatePower(ACTIVATION_TICKS);
    }

    public boolean activatePower(int ticks) {
        if (ticks <= 0) {
            return this.deactivatePower();
        }

        boolean wasPowered = this.isPowered();
        this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, wasPowered ? 15 : 0, 15));
        this.setPowered(true);
        this.level.setBlock(this, this, true, true);
        this.level.scheduleUpdate(this, ticks);
        this.updateRedstoneNeighbours();
        return true;
    }

    public boolean deactivatePower() {
        if (!this.isPowered()) {
            return false;
        }

        this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 15, 0));
        this.setPowered(false);
        this.level.setBlock(this, this, true, true);
        this.updateRedstoneNeighbours();
        return true;
    }

    private void updateRedstoneNeighbours() {
        this.level.updateAroundRedstone(this, null);
        this.level.updateAroundRedstone(this.getSideVec(this.getBlockFace().getOpposite()), null);
    }

    @Override
    public boolean isPowerSource() {
        return true;
    }

    @Override
    public int getWeakPower(BlockFace face) {
        return this.isPowered() ? 15 : 0;
    }

    @Override
    public int getStrongPower(BlockFace side) {
        return this.isPowered() && this.getBlockFace() == side ? 15 : 0;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            if (this.isPowered()) {
                this.deactivatePower();
            }
            return type;
        }
        return Oxidizable.super.onUpdate(type);
    }

    @Override
    public boolean onBreak(Item item) {
        boolean powered = this.isPowered();
        if (!super.onBreak(item)) {
            return false;
        }
        if (powered) {
            this.updateRedstoneNeighbours();
        }
        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item, Player player) {
        return Waxable.super.onActivate(item, player)
                || Oxidizable.super.onActivate(item, player);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public Block getStateWithOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        return Block.get(this.getCopperId(this.isWaxed(), oxidizationLevel), this.getDamage());
    }

    @Override
    public boolean setOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        if (this.getOxidizationLevel().equals(oxidizationLevel)) {
            return true;
        }
        return this.getValidLevel().setBlock(this, Block.get(this.getCopperId(this.isWaxed(), oxidizationLevel), this.getDamage()));
    }

    @Override
    public boolean setWaxed(boolean waxed) {
        if (this.isWaxed() == waxed) {
            return true;
        }
        return this.getValidLevel().setBlock(this, Block.get(this.getCopperId(waxed, this.getOxidizationLevel()), this.getDamage()));
    }

    @Override
    public boolean isWaxed() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ADOBE_BLOCK_COLOR;
    }

    protected int getCopperId(boolean waxed, @Nullable OxidizationLevel oxidizationLevel) {
        if (oxidizationLevel == null) {
            return this.getId();
        }
        return switch (oxidizationLevel) {
            case UNAFFECTED -> waxed ? WAXED_LIGHTNING_ROD : LIGHTNING_ROD;
            case EXPOSED -> waxed ? WAXED_EXPOSED_LIGHTNING_ROD : EXPOSED_LIGHTNING_ROD;
            case WEATHERED -> waxed ? WAXED_WEATHERED_LIGHTNING_ROD : WEATHERED_LIGHTNING_ROD;
            case OXIDIZED -> waxed ? WAXED_OXIDIZED_LIGHTNING_ROD : OXIDIZED_LIGHTNING_ROD;
        };
    }
}
