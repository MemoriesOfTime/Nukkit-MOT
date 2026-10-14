package cn.nukkit.event.block;
import cn.nukkit.block.Block;
/** Deferred block plan; called only after final explosion-event filtering and cancellation. */
public interface NativeExplosionBlockProcessor {
    enum Result { NATIVE, HANDLED, REFUSED }
    Result process(Block original);
    /** Pure policy/generation validation for every cell before any source write. */
    default boolean canRemove(java.util.List<Block> originals) { return true; }
    default void removed(Block original) { }
    /** Assignment-only settlement for an already accepted complete source batch. */
    default void removed(java.util.List<Block> originals) { for (Block original : originals) removed(original); }
}
