package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.level.Position;

import java.util.List;
import java.util.Set;

public class BlockExplodeEvent extends BlockEvent implements Cancellable {
    private final cn.nukkit.event.block.ExplosionBlockProcessing blockProcessing = new cn.nukkit.event.block.ExplosionBlockProcessing();
    private final java.util.List<Block> originalBlocks;
    public java.util.List<Block> getOriginalBlocks(){return originalBlocks.stream().map(Block::clone).toList();}
    public void addBlockProcessor(cn.nukkit.event.block.NativeExplosionBlockProcessor processor) { blockProcessing.addBlockProcessor(processor); }
    public cn.nukkit.event.block.NativeExplosionBlockProcessor.Result processBlock(cn.nukkit.block.Block block) { return blockProcessing.processBlock(block); }
    public void blockRemoved(cn.nukkit.block.Block block) { blockProcessing.blockRemoved(block); }
    public java.util.List<cn.nukkit.block.Block> getRemovedBlocks() { return blockProcessing.getRemovedBlocks(); }
    public cn.nukkit.event.block.ExplosionBlockProcessing getBlockProcessing() { return blockProcessing; }


    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    protected final Position position;
    protected final double fireChance;

    protected double yield;
    protected Set<Block> blocks;
    protected Set<Block> ignitions;

    /**
     * Block explode event is called when a block explodes (For example a bed in nether)
     * @param block Block that exploded
     * @param position Position
     * @param blocks Blocks affected by the explosion
     * @param yield Explosion yield
     */
    public BlockExplodeEvent(Block block, Position position, Set<Block> blocks, Set<Block> ignitions, double yield, double fireChance) {
        super(block);
        this.position = position;
        this.blocks = blocks;
        this.originalBlocks=blocks.stream().map(Block::clone).toList();
        this.yield = yield;
        this.ignitions = ignitions;
        this.fireChance = fireChance;
    }

    public Position getPosition() {
        return this.position;
    }

    public Set<Block> getAffectedBlocks() {
        return this.blocks;
    }

    public void setAffectedBlocks(Set<Block> blocks) {
        this.blocks = blocks;
    }

    public double getYield() {
        return this.yield;
    }

    public void setYield(double yield) {
        this.yield = yield;
    }

    public Set<Block> getIgnitions() {
        return ignitions;
    }

    public void setIgnitions(Set<Block> ignitions) {
        this.ignitions = ignitions;
    }

    public double getFireChance() {
        return fireChance;
    }
}