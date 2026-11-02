package cn.nukkit.event.block;
import cn.nukkit.block.Block;
import java.util.*;
/** Shared accepted-source accounting for block and entity explosions. */
public final class ExplosionBlockProcessing {
    private final List<NativeExplosionBlockProcessor> processors = new ArrayList<>();
    private final List<Block> removed = new ArrayList<>();
    private final Set<Key> handled = new HashSet<>();
    private Set<Key> surviving;
    private final Set<Key> vetoed=new HashSet<>();
    private final List<RuntimeException> failures = new ArrayList<>();
    public void addBlockProcessor(NativeExplosionBlockProcessor processor) {
        if (surviving != null) throw new IllegalStateException("Explosion plans are already finalized");
        processors.add(Objects.requireNonNull(processor));
    }
    public void begin(Collection<Block> finalBlocks) { begin(finalBlocks,finalBlocks); }
    public void begin(Collection<Block> finalBlocks,Collection<Block> selectedBlocks) {
        if (surviving != null) throw new IllegalStateException("Explosion processing already started");
        surviving = new HashSet<>();
        for (Block block : finalBlocks) surviving.add(Key.of(block));
        for(Block block:selectedBlocks)if(!surviving.contains(Key.of(block)))vetoed.add(Key.of(block));
    }
    public NativeExplosionBlockProcessor.Result processBlock(Block block) {
        if (surviving == null || !surviving.contains(Key.of(block)) || handled.contains(Key.of(block)))
            return NativeExplosionBlockProcessor.Result.REFUSED;
        for (NativeExplosionBlockProcessor processor : processors) {
            NativeExplosionBlockProcessor.Result result;
            try { result = Objects.requireNonNull(processor.process(block)); }
            catch (RuntimeException failure) { failures.add(failure); return NativeExplosionBlockProcessor.Result.REFUSED; }
            if (result == NativeExplosionBlockProcessor.Result.HANDLED) {
                if (!block.level.getBlock(block, block.layer).isAir()) return NativeExplosionBlockProcessor.Result.REFUSED;
                markRemoved(block); return result;
            }
            if (result == NativeExplosionBlockProcessor.Result.REFUSED) return result;
        }
        return NativeExplosionBlockProcessor.Result.NATIVE;
    }
    /** Check every processor before committing any external metadata. */
    public boolean canRemove(List<Block> originals) {
        if(originals.stream().anyMatch(block->vetoed.contains(Key.of(block))))return false;
        for (NativeExplosionBlockProcessor processor:processors) if(!processor.canRemove(List.copyOf(originals)))return false;
        return true;
    }
    public void blockRemoved(Block original) { blocksRemoved(List.of(original)); }
    /** Every source was accepted before any processor receives the indivisible batch. */
    public void blocksRemoved(List<Block> originals) {
        List<Block> pending=originals.stream().filter(block->!handled.contains(Key.of(block))).map(Block::clone).toList();
        if(surviving==null || pending.isEmpty())return;
        for(Block block:pending)markRemoved(block);
        Throwable failure=null;
        for(NativeExplosionBlockProcessor processor:processors) try{processor.removed(pending);}
        catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
        if(failure instanceof Error error)throw error;if(failure instanceof RuntimeException error)throw error;
    }
    private void markRemoved(Block block) {
        if (handled.add(Key.of(block))) removed.add(block.clone());
    }
    public List<Block> getRemovedBlocks() { return removed.stream().map(Block::clone).toList(); }
    public List<RuntimeException> getProcessingFailures() { return List.copyOf(failures); }
    private record Key(Object level, int x,int y,int z,int layer) {
        static Key of(Block block) { return new Key(block.level,block.getFloorX(),block.getFloorY(),block.getFloorZ(),block.layer); }
    }
}
