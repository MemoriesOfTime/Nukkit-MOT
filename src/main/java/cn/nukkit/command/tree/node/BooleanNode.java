package cn.nukkit.command.tree.node;

import cn.nukkit.command.data.CommandEnum;
import com.google.common.collect.Sets;

import java.util.Locale;
import java.util.Set;

/**
 * 解析对应参数为{@link Boolean}值
 * <p>
 * 所有命令枚举{@link CommandEnum#ENUM_BOOLEAN ENUM_BOOLEAN}如果没有手动指定{@link IParamNode},则会默认使用这个解析
 */
public class BooleanNode extends ParamNode<Boolean> {
    private final static Set<String> ENUM_BOOLEAN = Sets.newHashSet(CommandEnum.ENUM_BOOLEAN.getValues());

    @Override
    public void fill(String arg) {
        // 大小写不敏感匹配 "true"/"false"，与原版 Bedrock 行为一致。
        // Boolean.parseBoolean 本身就忽略大小写，这里放宽枚举校验即可。
        String lower = arg.toLowerCase(Locale.ROOT);
        if (ENUM_BOOLEAN.contains(lower)) this.value = Boolean.parseBoolean(arg);
        else this.error();
    }
}
