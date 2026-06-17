package cn.nukkit.blockentity;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockCalibratedSculkSensor;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.vibration.VibrationEvent;
import cn.nukkit.level.vibration.VibrationType;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * Calibrated sculk sensor: like {@link BlockEntitySculkSensor}, but a redstone signal on the input
 * face selects a single frequency to listen to (radius 16). Adapted from PowerNukkitX.
 */
public class BlockEntityCalibratedSculkSensor extends BlockEntitySculkSensor {

    protected static final int LISTENER_RADIUS = 16;

    public BlockEntityCalibratedSculkSensor(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public boolean isBlockEntityValid() {
        return getLevelBlock().getId() == BlockID.CALIBRATED_SCULK_SENSOR;
    }

    @Override
    public double getListenRange() {
        return LISTENER_RADIUS;
    }

    @Override
    protected int getRedstoneStrengthForDistance(float distance) {
        return Math.max(1, 15 - (int) Math.floor((15.0 / LISTENER_RADIUS) * distance));
    }

    @Override
    public boolean onVibrationOccur(VibrationEvent event) {
        if (!this.isBlockEntityValid() || this.currentVibration != null) {
            return false;
        }
        if (this.level.getBlock(event.source()) instanceof BlockCalibratedSculkSensor) {
            return false;
        }
        if (event.source().getFloorX() == this.getFloorX()
                && event.source().getFloorY() == this.getFloorY()
                && event.source().getFloorZ() == this.getFloorZ()
                && (event.type() == VibrationType.BLOCK_DESTROY || event.type() == VibrationType.BLOCK_PLACE)) {
            return false;
        }
        if (getPhase() != BlockCalibratedSculkSensor.PHASE_INACTIVE) {
            return false;
        }
        // JE: if back signal != 0, only accept vibrations whose frequency equals the back signal
        int backSignal = getBackSignal();
        if (backSignal != 0 && event.type().frequency != backSignal) {
            return false;
        }
        // Accept: add to the same-tick candidate pool (selection on next tick, like the parent).
        double distance = event.source().distance(getListenerVector());
        this.selector.addCandidate(event, distance, this.level.getCurrentTick());
        scheduleUpdate();
        return true;
    }

    /**
     * JE getBackSignal: redstone signal on the input face (opposite of FACING). Uses the level's
     * full redstone propagation ({@code Level.getRedstonePower}), so a redstone dust line or a
     * charged solid block feeding the input face is read correctly — not just directly-adjacent
     * power sources.
     */
    protected int getBackSignal() {
        Block block = this.getLevelBlock();
        if (!(block instanceof BlockCalibratedSculkSensor sensor)) {
            return 0;
        }
        BlockFace inputFace = sensor.getFacing().getOpposite();
        return this.level.getRedstonePower(sensor.getSideVec(inputFace), inputFace);
    }
}
