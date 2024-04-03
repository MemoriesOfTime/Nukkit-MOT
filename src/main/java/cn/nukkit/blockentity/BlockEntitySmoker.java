package cn.nukkit.blockentity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class BlockEntitySmoker extends BlockEntityFurnace {

    public BlockEntitySmoker(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected InventoryType getInventoryType() {
        return InventoryType.SMOKER;
    }

    @Override
    protected String getFurnaceName() {
        return "Smoker";
    }

    @Override
    protected String getClientName() {
        return SMOKER;
    }

    @Override
    public boolean isBlockEntityValid() {
        int blockID = level.getBlockIdAt(chunk, (int) x, (int) y, (int) z, 0);
        return blockID == Block.SMOKER || blockID == Block.LIT_SMOKER;
    }

    private static final IntSet CAN_SMELT = new IntOpenHashSet(new int[]{
            Item.RAW_PORKCHOP, Item.RAW_BEEF, Item.RAW_RABBIT, Item.RAW_FISH, Item.RAW_CHICKEN, Item.RAW_MUTTON, Item.RAW_SALMON, Item.POTATO
    });

    @Override
    public boolean onUpdate() {
        if (this.closed) {
            return false;
        }

        Item raw = this.inventory.getSmelting();
        // TODO: smoker recipes
        if (!CAN_SMELT.contains(raw.getId())) {
            if (burnTime > 0) {
                burnTime--;
                burnDuration = (int) Math.ceil((float) burnTime / maxTime * 100);

                if (burnTime == 0) {
                    Block block = this.level.getBlock(this.chunk, (int) x, (int) y, (int) z, 0, true);
                    if (block.getId() == BlockID.LIT_SMOKER) {
                        this.level.setBlock(this, Block.get(BlockID.SMOKER, block.getDamage()), true);
                    }
                    return false;
                }
            }

            cookTime = 0;
            if (Server.getInstance().getTick() % 4 == 0) {
                this.sendPacket();
            }
            return true;
        }

        return super.onUpdate();
    }

    @Override
    protected int getIdleBlockId() {
        return Block.SMOKER;
    }

    @Override
    protected int getBurningBlockId() {
        return Block.LIT_SMOKER;
    }

    @Override
    protected int getSpeedMultiplier() {
        return 2;
    }
}
