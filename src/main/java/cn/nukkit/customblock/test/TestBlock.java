package cn.nukkit.customblock.test;

import cn.nukkit.customblock.CustomBlockDefinition;
import cn.nukkit.customblock.CustomBlockManager;
import cn.nukkit.customblock.container.CustomBlock;
import cn.nukkit.customblock.container.data.Materials;
import cn.nukkit.customblock.container.data.Transformation;
import cn.nukkit.math.Vector3;

/**
 * @author LT_Name
 */
public class TestBlock extends CustomBlock {

    public TestBlock() {
        super("farmersdelight:bamboo_cabinet", 6000);
    }

    public static void register() {
        CustomBlockDefinition blockDefinition = CustomBlockDefinition
                .builder(new TestBlock())
                .materials(Materials.builder()
                        .any(Materials.RenderMethod.OPAQUE, "bamboo_cabinet_side")
                        .up(Materials.RenderMethod.OPAQUE, "bamboo_cabinet_top")
                        .down(Materials.RenderMethod.OPAQUE, "bamboo_cabinet_top")
                        .south(Materials.RenderMethod.OPAQUE, "bamboo_cabinet_front")
                )
                .transformation(new Transformation(new Vector3(0, 0, 0), new Vector3(1, 1, 1), new Vector3(90, 180, 90)))
                .breakTime(3)
                .build();
        CustomBlockManager.get().registerCustomBlock("farmersdelight:bamboo_cabinet", 6000, blockDefinition, TestBlock::new);
    }

}
