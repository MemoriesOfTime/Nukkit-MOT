package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCalibratedSculkSensor;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Like {@link BlockSculkSensor}, with an input face (low 2 damage bits) used as a frequency filter.
 * Adapted from PowerNukkitX.
 */
public class BlockCalibratedSculkSensor extends BlockSculkSensor {

    private static final int FACING_MASK = 0x03;
    private static final int PHASE_SHIFT = 2;

    public BlockCalibratedSculkSensor() {
        this(0);
    }

    public BlockCalibratedSculkSensor(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CALIBRATED_SCULK_SENSOR;
    }

    @Override
    public String getName() {
        return "Calibrated Sculk Sensor";
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityCalibratedSculkSensor> getBlockEntityClass() {
        return BlockEntityCalibratedSculkSensor.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.CALIBRATED_SCULK_SENSOR;
    }

    @Override
    public int getActiveTicks() {
        return 10;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        setFacing(player != null ? player.getDirection().getOpposite() : BlockFace.SOUTH);
        this.getLevel().setBlock(block, this, true, true);
        this.createBlockEntity();
        return true;
    }

    /** Input face (the side the arrow points toward). */
    public BlockFace getFacing() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & FACING_MASK);
    }

    /** Sets the input face (low 2 bits of the damage value). */
    public void setFacing(BlockFace face) {
        int horizontalIndex = face.getHorizontalIndex();
        if (horizontalIndex > -1) {
            this.setDamage((this.getDamage() & ~FACING_MASK) | horizontalIndex);
        }
    }

    @Override
    public int getPhase() {
        return (this.getDamage() >> PHASE_SHIFT) & 0x03;
    }

    @Override
    public void setPhase(int phase) {
        playPhaseSound(phase);
        this.setDamage((this.getDamage() & FACING_MASK) | ((phase & 0x03) << PHASE_SHIFT));
        this.level.setBlock(this, this, true, false);
    }
}
