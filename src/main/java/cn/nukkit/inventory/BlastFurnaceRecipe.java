package cn.nukkit.inventory;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;

public class BlastFurnaceRecipe extends FurnaceRecipe {

    public BlastFurnaceRecipe(Item result, Item ingredient) {
        super(result, ingredient);
    }

    @Override
    public void registerToCraftingManager(CraftingManager manager) {
        manager.registerBlastFurnaceRecipe(ProtocolInfo.v1_11_0, this);
    }

    @Override
    public RecipeType getType() {
        return this.ingredient.hasMeta() ? RecipeType.BLAST_FURNACE_DATA : RecipeType.BLAST_FURNACE;
    }
}
