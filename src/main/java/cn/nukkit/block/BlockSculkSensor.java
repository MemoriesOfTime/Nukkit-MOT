package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySculkSensor;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

/**
 * Emits a redstone signal proportional to the vibration source distance. Damage value stores the
 * sculk_sensor_phase (0=inactive, 1=active, 2=cooldown). Adapted from PowerNukkitX.
 */
public class BlockSculkSensor extends BlockTransparentMeta implements BlockEntityHolder<BlockEntitySculkSensor> {

    public static final int PHASE_INACTIVE = 0;
    public static final int PHASE_ACTIVE = 1;
    public static final int PHASE_COOLDOWN = 2;

    public BlockSculkSensor() {
        this(0);
    }

    public BlockSculkSensor(int meta) {
        super(meta);
    }

    protected static final int ACTIVE_TICKS = 30;
    protected static final int COOLDOWN_TICKS = 10;

    @Override
    public int getId() {
        return SCULK_SENSOR;
    }

    @Override
    public String getName() {
        return "Sculk Sensor";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_HOE;
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public double getResistance() {
        return 1.5;
    }

    @Override
    public int getLightLevel() {
        return 1;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BLACK_BLOCK_COLOR;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
            return new Item[]{this.toItem()};
        }
        return Item.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntitySculkSensor> getBlockEntityClass() {
        return BlockEntitySculkSensor.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.SCULK_SENSOR;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        this.getLevel().setBlock(block, this, true, true);
        this.createBlockEntity();
        return true;
    }

    @Override
    public boolean isPowerSource() {
        return true;
    }

    @Override
    public int getWeakPower(BlockFace face) {
        BlockEntitySculkSensor blockEntity = this.getOrCreateBlockEntity();
        if (this.getSide(face.getOpposite()) instanceof BlockRedstoneComparator) {
            return getPhase() == PHASE_ACTIVE ? blockEntity.getLastVibrationFrequency() : 0;
        }
        return blockEntity.getPower();
    }

    @Override
    public int onUpdate(int type) {
        BlockEntitySculkSensor be = getOrCreateBlockEntity();
        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            int phase = getPhase();
            if (phase == PHASE_ACTIVE) {
                be.resetPower();
                setPhase(PHASE_COOLDOWN);
                this.level.scheduleUpdate(this, COOLDOWN_TICKS);
            } else if (phase == PHASE_COOLDOWN) {
                setPhase(PHASE_INACTIVE);
            }
            this.level.updateAroundRedstone(this, null);
            return type;
        }
        return 0;
    }

    /** JE SculkSensorBlock.activate: set ACTIVE, schedule deactivation, update redstone. */
    public void activate(int calculatedPower) {
        getOrCreateBlockEntity().setPower(calculatedPower);
        setPhase(PHASE_ACTIVE);
        this.level.scheduleUpdate(this, getActiveTicks());
        this.level.updateAroundRedstone(this, null);
    }

    /** Active phase duration in ticks (30 for regular, 10 for calibrated). */
    public int getActiveTicks() {
        return ACTIVE_TICKS;
    }

    public int getPhase() {
        return this.getDamage();
    }

    public void setPhase(int phase) {
        playPhaseSound(phase);
        this.setDamage(phase);
        this.level.setBlock(this, this, true, false);
    }

    /**
     * Plays the activate/deactivate click sound, suppressed when the sensor is waterlogged.
     */
    protected void playPhaseSound(int phase) {
        if (isWaterlogged()) {
            return;
        }
        if (phase == PHASE_ACTIVE) {
            this.level.addSound(this.add(0.5, 0.5, 0.5), Sound.POWER_ON_SCULK_SENSOR);
        } else {
            this.level.addSound(this.add(0.5, 0.5, 0.5), Sound.POWER_OFF_SCULK_SENSOR);
        }
    }

    /** Whether this sensor currently has water in its secondary layer (waterlogged). */
    protected boolean isWaterlogged() {
        return this.getLevelBlockAtLayer(1) instanceof BlockWater;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }
}
