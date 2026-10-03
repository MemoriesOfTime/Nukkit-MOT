package cn.nukkit.command.tree.node;

import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.tree.ParamList;
import com.google.common.collect.Sets;

import java.util.Set;


/**
 * 解析为{@link String}值
 * <p>
 * 所有命令枚举类型如果没有手动指定{@link IParamNode},则会默认使用这个解析
 */
public class EnumNode extends ParamNode<String> {
    protected CommandEnum commandEnum;
    protected Set<String> enums;

    @Override
    public void fill(String arg) {
        if (commandEnum.isSoft()) {
            // 软枚举是自由文本（如 scoreboard objective 名），必须原样保留，不归一化
            this.value = arg;
            return;
        }
        // 硬枚举：大小写不敏感匹配，并归一化为枚举中定义的原始大小写形式。
        // 与原版 Bedrock 客户端行为一致，且下游解析（GameRule.parseString、Server.getGamemodeFromString 等）
        // 普遍已 toLowerCase 兜底，拿到归一化后的稳定键更安全。
        for (String v : enums) {
            if (v.equalsIgnoreCase(arg)) {
                this.value = v;
                return;
            }
        }
        this.error();
    }

    @Override
    public IParamNode<String> init(ParamList parent, String name, boolean optional, CommandParamType type, CommandEnum enumData, String postFix) {
        this.paramList = parent;
        this.commandEnum = enumData;
        this.enums = enumData == null ? Sets.newHashSet() : Sets.newHashSet(this.commandEnum.getValues());
        this.optional = optional;
        return this;
    }

}
