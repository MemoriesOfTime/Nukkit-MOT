package cn.nukkit.block;

import cn.nukkit.item.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockSweetBerryBushBreakTimeTest {

    @Test
    void everyGrowthStageBreaksInstantly() {
        for (int growthStage = 0; growthStage <= 3; growthStage++) {
            BlockSweetBerryBush bush = new BlockSweetBerryBush(growthStage);

            assertEquals(0d, bush.getHardness(), "hardness at growth stage " + growthStage);
            assertEquals(0d, bush.calculateBreakTime(Item.AIR_ITEM),
                    "break time at growth stage " + growthStage);
        }
    }
}
