package cn.nukkit.blockentity;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockSculkSensor;
import cn.nukkit.event.level.VibrationArriveEvent;
import cn.nukkit.math.BlockFace;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.vibration.VibrationEvent;
import cn.nukkit.level.vibration.VibrationListener;
import cn.nukkit.level.vibration.VibrationSelector;
import cn.nukkit.level.vibration.VibrationType;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * Sculk sensor listener: schedules its own vibration arrival via a per-tick countdown, emits a
 * redstone signal scaled by distance, and re-emits SCULK_SENSOR_TENDRILS_CLICKING (chaining to
 * shriekers). Adapted from PowerNukkitX.
 */
public class BlockEntitySculkSensor extends BlockEntity implements VibrationListener {

    protected static final int LISTENER_RADIUS = 8;

    protected VibrationEvent lastVibrationEvent;
    protected int lastVibrationFrequency = 0;
    protected long lastActiveTime = 0;
    protected int power = 0;

    /**
     * In-flight vibration handled by this listener's own tick (single-flight).
     * Set only after the selector has committed the winning candidate.
     */
    protected VibrationEvent currentVibration;
    protected long arrivalTick;

    /**
     * Same-tick candidate buffer. Mirrors vanilla {@code VibrationSelector}: among all vibrations
     * arriving within the same tick, the closest one (ties broken by frequency) is committed to
     * {@link #currentVibration} on the next tick.
     */
    protected final VibrationSelector selector = new VibrationSelector();

    public BlockEntitySculkSensor(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        this.lastActiveTime = this.level.getCurrentTick();
        // Restore persisted state
        this.lastVibrationFrequency = this.namedTag.getInt("lastVibrationFrequency");
        this.power = this.namedTag.getInt("power");
        if (this.namedTag.contains("pendingVibration")) {
            CompoundTag pending = this.namedTag.getCompound("pendingVibration");
            try {
                VibrationType type = VibrationType.valueOf(pending.getString("type"));
                Vector3 source = new Vector3(
                        pending.getFloat("sx"), pending.getFloat("sy"), pending.getFloat("sz"));
                this.currentVibration = new VibrationEvent(this, source, type);
                this.arrivalTick = pending.getLong("arrivalTick");
            } catch (Exception ignored) {
                this.currentVibration = null;
                this.arrivalTick = 0;
            }
        }
        this.level.getVibrationManager().addListener(this);
        // Resume an in-flight vibration saved to NBT, if any
        if (this.currentVibration != null) {
            if (this.arrivalTick > this.level.getCurrentTick()) {
                scheduleUpdate();
            } else {
                // already past arrival time on reload — deliver immediately
                deliverVibration();
            }
        }
        // Reload: the phase-transition tick isn't persisted, so re-arm it for an ACTIVE/COOLDOWN
        // sensor that would otherwise stay stuck.
        if (getPhase() != BlockSculkSensor.PHASE_INACTIVE && this.level != null) {
            Block block = this.getLevelBlock();
            if (block instanceof BlockSculkSensor sensor && !this.level.isUpdateScheduled(this, sensor)) {
                this.level.scheduleUpdate(sensor, 0);
            }
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putInt("lastVibrationFrequency", this.lastVibrationFrequency);
        this.namedTag.putInt("power", this.power);
        if (this.currentVibration != null) {
            CompoundTag pending = new CompoundTag();
            pending.putString("type", this.currentVibration.type().name());
            Vector3 s = this.currentVibration.source();
            pending.putFloat("sx", (float) s.x);
            pending.putFloat("sy", (float) s.y);
            pending.putFloat("sz", (float) s.z);
            pending.putLong("arrivalTick", this.arrivalTick);
            this.namedTag.putCompound("pendingVibration", pending);
        } else if (this.namedTag.contains("pendingVibration")) {
            this.namedTag.remove("pendingVibration");
        }
    }

    @Override
    public void onBreak() {
        if (this.level != null) {
            this.level.getVibrationManager().removeListener(this);
        }
    }

    @Override
    public void close() {
        if (this.level != null) {
            this.level.getVibrationManager().removeListener(this);
        }
        super.close();
    }

    @Override
    public boolean isBlockEntityValid() {
        return getLevelBlock().getId() == BlockID.SCULK_SENSOR;
    }

    @Override
    public Vector3 getListenerVector() {
        return new Vector3(this.getFloorX() + 0.5, this.getFloorY() + 0.5, this.getFloorZ() + 0.5);
    }

    @Override
    public boolean handleArrivalSelf() {
        return true;
    }

    @Override
    public boolean onVibrationOccur(VibrationEvent event) {
        if (!this.isBlockEntityValid()) {
            return false;
        }
        if (this.currentVibration != null) {
            // single-flight: a vibration is already traveling to this sensor
            return false;
        }
        if (this.level.getBlock(event.source()) instanceof BlockSculkSensor) {
            return false;
        }
        // sensors ignore BLOCK_DESTROY/BLOCK_PLACE at their own position
        if (event.source().getFloorX() == this.getFloorX()
                && event.source().getFloorY() == this.getFloorY()
                && event.source().getFloorZ() == this.getFloorZ()
                && (event.type() == VibrationType.BLOCK_DESTROY || event.type() == VibrationType.BLOCK_PLACE)) {
            return false;
        }
        int phase = getPhase();
        if (phase != BlockSculkSensor.PHASE_INACTIVE) {
            return false;
        }
        // Accept: add to the same-tick candidate pool. Selection happens on the next tick
        // (see onUpdate), so the closest/highest-frequency candidate wins per vanilla.
        double distance = event.source().distance(getListenerVector());
        this.selector.addCandidate(event, distance, this.level.getCurrentTick());
        scheduleUpdate();
        return true;
    }

    @Override
    public void onVibrationArrive(VibrationEvent event) {
        // not used: arrival handled by onUpdate (handleArrivalSelf == true)
    }

    @Override
    public boolean onUpdate() {
        if (!this.isBlockEntityValid()) {
            close();
            return false;
        }
        // Commit the winning candidate once the selection tick has passed (one tick later),
        // ensuring all same-tick vibrations have been collected.
        if (this.currentVibration == null) {
            var chosen = this.selector.chosenCandidate(this.level.getCurrentTick());
            if (chosen.isPresent()) {
                VibrationSelector.Candidate candidate = chosen.get();
                this.selector.startOver();
                this.currentVibration = candidate.vibration;
                // JE VibrationSystem.getTravelTimeInTicks: signal moves at 0.5 blocks/tick, so
                // travel time is round(distance * 2). Matches the particle animation and the
                // manager-scheduled arrival path for non-self-scheduling listeners.
                this.arrivalTick = this.level.getCurrentTick()
                        + Math.max(1, (int) Math.round(candidate.distance * 2.0));
            } else if (!this.selector.isEmpty()) {
                // selection still pending within the same tick; keep ticking
                return true;
            } else {
                return false;
            }
        }
        if (this.currentVibration == null) {
            return false;
        }
        if (this.level.getCurrentTick() < this.arrivalTick) {
            return true;
        }
        deliverVibration();
        return false;
    }

    /** Process the in-flight vibration: compute power, activate block, chain to shriekers. */
    protected void deliverVibration() {
        VibrationEvent event = this.currentVibration;
        this.currentVibration = null;
        this.arrivalTick = 0;
        if (event == null || this.level == null || !this.isBlockEntityValid()) {
            return;
        }
        if (getPhase() != BlockSculkSensor.PHASE_INACTIVE) {
            return;
        }
        // handleArrivalSelf() == true means the manager skips the delayed arrival path, so fire
        // VibrationArriveEvent here to keep plugins able to cancel activation at arrival time.
        VibrationArriveEvent arriveEvent = new VibrationArriveEvent(event, this);
        this.level.getServer().getPluginManager().callEvent(arriveEvent);
        if (arriveEvent.isCancelled()) {
            return;
        }
        this.lastVibrationEvent = event;
        this.lastVibrationFrequency = event.type().frequency;
        this.lastActiveTime = this.level.getCurrentTick();
        this.power = getRedstoneStrengthForDistance((float) event.source().distance(getListenerVector()));

        BlockSculkSensor sensor = (BlockSculkSensor) this.getLevelBlock();
        sensor.activate(this.power);
        // Chain to shriekers: re-emit a tendrils-clicking vibration from the sensor position
        this.level.getVibrationManager().callVibrationEvent(
                new VibrationEvent(event.initiator(), getListenerVector(), VibrationType.SCULK_SENSOR_TENDRILS_CLICKING));
        // JE tryResonateVibration: amethyst blocks adjacent to the sensor re-emit a RESONATE event
        // at the received frequency, propagating the signal further.
        tryResonate(event.initiator(), this.lastVibrationFrequency);
    }

    /**
     * JE SculkSensorBlock.tryResonateVibration: for each of the 6 adjacent blocks, if it is a
     * vibration resonator (amethyst block), emit the matching RESONATE_<freq> event from that
     * block's position and play the amethyst resonate chime with a frequency-dependent pitch.
     */
    protected void tryResonate(Object initiator, int frequency) {
        VibrationType resonateType = getResonateType(frequency);
        if (resonateType == null) {
            return;
        }
        for (BlockFace face : BlockFace.values()) {
            Block adjacent = this.level.getBlock(this.getSideVec(face));
            if (adjacent.getId() == BlockID.AMETHYST_BLOCK) {
                Vector3 resonatePos = new Vector3(adjacent.getFloorX() + 0.5, adjacent.getFloorY() + 0.5, adjacent.getFloorZ() + 0.5);
                this.level.getVibrationManager().callVibrationEvent(
                        new VibrationEvent(initiator, resonatePos, resonateType));
                float pitch = RESONANCE_PITCH_BEND[frequency];
                this.level.addSound(resonatePos, cn.nukkit.level.Sound.CHIME_AMETHYST_BLOCK, 1.0f, pitch);
            }
        }
    }

    /** Maps a frequency (1-15) to its RESONATE_N VibrationType, or null if out of range. */
    protected static VibrationType getResonateType(int frequency) {
        if (frequency < 1 || frequency > 15) {
            return null;
        }
        return RESONATE_TYPES[frequency - 1];
    }

    /** JE RESONANCE_PITCH_BEND: note offsets per frequency used to bend the amethyst chime pitch. */
    protected static final float[] RESONANCE_PITCH_BEND = new float[16];

    /** RESONATE_N VibrationType lookup by frequency (index 0 = frequency 1). */
    protected static final VibrationType[] RESONATE_TYPES = new VibrationType[]{
            VibrationType.RESONATE_1, VibrationType.RESONATE_2, VibrationType.RESONATE_3,
            VibrationType.RESONATE_4, VibrationType.RESONATE_5, VibrationType.RESONATE_6,
            VibrationType.RESONATE_7, VibrationType.RESONATE_8, VibrationType.RESONATE_9,
            VibrationType.RESONATE_10, VibrationType.RESONATE_11, VibrationType.RESONATE_12,
            VibrationType.RESONATE_13, VibrationType.RESONATE_14, VibrationType.RESONATE_15
    };

    static {
        // JE toneMap -> note pitch. Index 0 unused.
        int[] toneMap = {0, 0, 2, 4, 6, 7, 9, 10, 12, 14, 15, 18, 19, 21, 22, 24};
        for (int i = 0; i < 16; i++) {
            RESONANCE_PITCH_BEND[i] = (float) Math.pow(2.0, (toneMap[i] - 12) / 12.0);
        }
    }

    protected int getRedstoneStrengthForDistance(float distance) {
        return Math.max(1, 15 - (int) Math.floor((15.0 / getListenRange()) * distance));
    }

    public void setPower(int power) {
        this.power = power;
    }

    public void resetPower() {
        this.power = 0;
    }

    public VibrationEvent getLastVibrationEvent() {
        return this.lastVibrationEvent;
    }

    public int getLastVibrationFrequency() {
        return this.lastVibrationFrequency;
    }

    public int getPower() {
        return power;
    }

    @Override
    public double getListenRange() {
        return LISTENER_RADIUS;
    }

    protected int getPhase() {
        Block block = this.level != null ? this.getLevelBlock() : null;
        return block instanceof BlockSculkSensor sensor ? sensor.getPhase() : BlockSculkSensor.PHASE_INACTIVE;
    }
}
