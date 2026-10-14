package cn.nukkit.event.entity;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.level.Position;

import java.util.List;
import java.util.Set;

/**
 * @author Angelic47
 * Nukkit Project
 */
public class EntityExplodeEvent extends EntityEvent implements Cancellable {
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
    protected List<Block> blocks;

    protected Set<Block> ignitions;
    protected double yield;

    public EntityExplodeEvent(Entity entity, Position position, List<Block> blocks, double yield) {
        this.entity = entity;
        this.position = position;
        this.blocks = blocks;
        this.originalBlocks=blocks.stream().map(Block::clone).toList();
        this.yield = yield;
    }

    public Position getPosition() {
        return this.position;
    }

    public List<Block> getBlockList() {
        return this.blocks;
    }

    public void setBlockList(List<Block> blocks) {
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
}
