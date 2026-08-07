package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.block.properties.enums.CauldronLiquid;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * Called when a hanging pointed dripstone tip drips a liquid (water or lava)
 * into a cauldron below it during a random tick.
 * <p>
 * The block returned by {@link #getBlock()} is the cauldron being filled.
 *
 * <p>
 * Adapted from PowerNukkitX's {@code CauldronFilledByDrippingLiquidEvent}
 * (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public class CauldronFilledByDrippingLiquidEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Block dripstone;
    private CauldronLiquid liquid;
    private int liquidLevelIncrement;

    public CauldronFilledByDrippingLiquidEvent(Block cauldron, Block dripstone, CauldronLiquid liquid, int liquidLevelIncrement) {
        super(cauldron);
        this.dripstone = dripstone;
        this.liquid = liquid;
        this.liquidLevelIncrement = liquidLevelIncrement;
    }

    /**
     * @return the pointed dripstone that is dripping the liquid
     */
    public Block getDripstone() {
        return this.dripstone;
    }

    public CauldronLiquid getLiquid() {
        return this.liquid;
    }

    public void setLiquid(CauldronLiquid liquid) {
        this.liquid = liquid;
    }

    /**
     * @return how many fill levels the cauldron will gain
     */
    public int getLiquidLevelIncrement() {
        return this.liquidLevelIncrement;
    }

    public void setLiquidLevelIncrement(int liquidLevelIncrement) {
        this.liquidLevelIncrement = liquidLevelIncrement;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
