package cn.nukkit.event.block;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

import javax.annotation.Nullable;

/**
 * @author joserobjr
 * @since 2020-10-06
 */
public class BlockExplosionPrimeEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private double force;
    private boolean blockBreaking;
    private double fireChance;
    private final Player player;

    @Deprecated
    public BlockExplosionPrimeEvent(Block block, double force) {
        this(block, force, 0);
    }

    public BlockExplosionPrimeEvent(Block block, double force, double fireChance) {
        this(block, null, force, fireChance);
    }

    public BlockExplosionPrimeEvent(Block block, @Nullable Player player, double force) {
        this(block, player, force, 0);
    }

    public BlockExplosionPrimeEvent(Block block, @Nullable Player player, double force, double fireChance) {
        super(block);
        this.force = force;
        this.blockBreaking = true;
        this.fireChance = fireChance;
        this.player = player;
    }

    public double getForce() {
        return force;
    }

    public void setForce(double force) {
        this.force = force;
    }

    public boolean isBlockBreaking() {
        return blockBreaking;
    }

    public void setBlockBreaking(boolean blockBreaking) {
        this.blockBreaking = blockBreaking;
    }

    public boolean isIncendiary() {
        return fireChance > 0;
    }

    public void setIncendiary(boolean incendiary) {
        if (!incendiary) {
            fireChance = 0;
        } else if (fireChance <= 0) {
            fireChance = 1.0 / 3.0;
        }
    }

    public double getFireChance() {
        return fireChance;
    }

    public void setFireChance(double fireChance) {
        this.fireChance = fireChance;
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }
}
