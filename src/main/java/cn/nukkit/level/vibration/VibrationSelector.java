package cn.nukkit.level.vibration;

import java.util.Optional;

/**
 * Selects a single "winning" vibration among candidates emitted during the same tick.
 * <p>
 * Mirrors vanilla's {@code VibrationSelector}: among all candidates added at the same tick time,
 * the one with the <b>shortest distance</b> wins; on a distance tie, the one with the
 * <b>higher frequency</b> wins. The chosen candidate is only made available once the tick has
 * advanced past {@code selectionTick}, so listeners can batch up same-tick events before picking.
 * <p>
 * Adapted from Minecraft Java Edition ({@code net.minecraft.world.level.gameevent.vibrations.VibrationSelector}).
 */
public class VibrationSelector {

    /** The selected candidate plus the tick at which it was chosen. Empty until chosen. */
    protected Candidate current;

    public void addCandidate(VibrationEvent vibration, double distance, long tickTime) {
        if (shouldReplace(vibration, distance, tickTime)) {
            this.current = new Candidate(vibration, distance, tickTime);
        }
    }

    protected boolean shouldReplace(VibrationEvent vibration, double distance, long tickTime) {
        if (this.current == null) {
            return true;
        }
        // Only consider replacements within the same tick (the batch window).
        if (tickTime != this.current.tick) {
            return false;
        }
        if (distance < this.current.distance) {
            return true;
        }
        if (distance > this.current.distance) {
            return false;
        }
        // Distance tie: higher frequency wins.
        return vibration.type().frequency > this.current.vibration.type().frequency;
    }

    /**
     * Returns the chosen candidate once the selection tick has passed (i.e. on a later tick),
     * so the listener can be sure no more same-tick candidates will arrive.
     */
    public Optional<Candidate> chosenCandidate(long currentTick) {
        if (this.current == null) {
            return Optional.empty();
        }
        return this.current.tick < currentTick ? Optional.of(this.current) : Optional.empty();
    }

    public void startOver() {
        this.current = null;
    }

    public boolean isEmpty() {
        return this.current == null;
    }

    /** A selected vibration plus its distance to the listener and the tick it was chosen at. */
    public static final class Candidate {
        public final VibrationEvent vibration;
        public final double distance;
        public final long tick;

        public Candidate(VibrationEvent vibration, double distance, long tick) {
            this.vibration = vibration;
            this.distance = distance;
            this.tick = tick;
        }
    }
}
