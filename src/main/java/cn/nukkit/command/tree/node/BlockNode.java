package cn.nukkit.command.tree.node;

import cn.nukkit.block.Block;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.item.Item;

/**
 * 解析对应参数为{@link Block}值
 * <p>
 * 所有命令枚举{@link CommandEnum#ENUM_BLOCK ENUM_BLOCK}如果没有手动指定{@link IParamNode},则会默认使用这个解析
 */
public class BlockNode extends ParamNode<Block> {
    @Override
    public void fill(String arg) {
        Block block = Item.fromString(arg).getBlockUnsafe();
        if (block == null) {
            this.error();
            return;
        }
        this.value = block;
    }
}
