package cn.nukkit.command.data;


import cn.nukkit.command.tree.node.IParamNode;
import com.google.common.collect.Lists;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ToString
public class CommandParameter {

    public final static String ARG_TYPE_STRING = "string";
    public final static String ARG_TYPE_STRING_ENUM = "stringenum";
    public final static String ARG_TYPE_BOOL = "bool";
    public final static String ARG_TYPE_TARGET = "target";
    public final static String ARG_TYPE_PLAYER = "target";
    public final static String ARG_TYPE_BLOCK_POS = "blockpos";
    public final static String ARG_TYPE_RAW_TEXT = "rawtext";
    public final static String ARG_TYPE_INT = "int";

    public static final String ENUM_TYPE_ITEM_LIST = "Item";
    public static final String ENUM_TYPE_BLOCK_LIST = "Block";
    public static final String ENUM_TYPE_COMMAND_LIST = "commandName";
    public static final String ENUM_TYPE_ENCHANTMENT_LIST = "enchantmentType";
    public static final String ENUM_TYPE_ENTITY_LIST = "entityType";
    public static final String ENUM_TYPE_EFFECT_LIST = "effectType";
    public static final String ENUM_TYPE_PARTICLE_LIST = "particleType";

    public String name;
    public CommandParamType type;
    public boolean optional;
    @Deprecated
    public byte options = 0;
    public List<CommandParamOption> paramOptions;

    public CommandEnum enumData;
    public String postFix;
    public final IParamNode<?> paramNode;

    public CommandParameter(String name, String type, boolean optional) {
        this(name, fromString(type), optional);
    }

    public CommandParameter(String name, CommandParamType type, boolean optional) {
        this.name = name;
        this.type = type;
        this.optional = optional;
        this.paramNode = null;
    }

    public CommandParameter(String name, boolean optional) {
        this(name, CommandParamType.RAWTEXT, optional);
    }

    public CommandParameter(String name) {
        this(name, false);
    }

    public CommandParameter(String name, boolean optional, String enumType) {
        this.name = name;
        this.type = CommandParamType.RAWTEXT;
        this.optional = optional;
        this.enumData = new CommandEnum(enumType, new ArrayList<>());
        this.paramNode = null;
    }

    public CommandParameter(String name, boolean optional, String[] enumValues) {
        this.name = name;
        this.type = CommandParamType.RAWTEXT;
        this.optional = optional;
        this.enumData = new CommandEnum(name + "Enums", Arrays.asList(enumValues));
        this.paramNode = null;
    }

    public CommandParameter(String name, String enumType) {
        this(name, false, enumType);
    }

    public CommandParameter(String name, String[] enumValues) {
        this(name, false, enumValues);
    }

    private CommandParameter(String name, boolean optional, CommandParamType type, CommandEnum enumData, String postFix, IParamNode<?> paramNode) {
        this.name = name;
        this.optional = optional;
        this.type = type;
        this.enumData = enumData;
        this.postFix = postFix;
        this.paramNode = paramNode;
    }

    /**
     * optional = false
     *
     * @see #newType(String name, boolean, CommandParamType type)
     */
    public static CommandParameter newType(String name, CommandParamType type) {
        return newType(name, false, type);
    }

    /**
     * optional = false,CommandParamOption=[]
     *
     * @see #newType(String, boolean, CommandParamType, IParamNode, CommandParamOption...)
     */
    public static CommandParameter newType(String name, CommandParamType type, IParamNode<?> paramNode) {
        return newType(name, false, type, paramNode);
    }

    /**
     * paramNode = null , CommandParamOption=[]
     *
     * @see #newType(String, boolean, CommandParamType, IParamNode, CommandParamOption...)
     */
    public static CommandParameter newType(String name, boolean optional, CommandParamType type) {
        return newType(name, optional, type, null, new CommandParamOption[]{});
    }

    /**
     * paramNode = null
     *
     * @see #newType(String, boolean, CommandParamType, IParamNode, CommandParamOption...)
     */
    public static CommandParameter newType(String name, boolean optional, CommandParamType type, CommandParamOption... options) {
        return newType(name, optional, type, null, options);
    }

    /**
     * 创建一个命令参数
     *
     * @param name      参数名
     * @param optional  该参数是否为可选参数
     * @param type      类型{@link CommandParamType}
     * @param paramNode 用于解析该参数的参数节点
     * @param options   the options
     * @return the command parameter
     */
    public static CommandParameter newType(String name, boolean optional, CommandParamType type, IParamNode<?> paramNode, CommandParamOption... options) {
        var result = new CommandParameter(name, optional, type, null, null, paramNode);
        if (options.length != 0) {
            result.paramOptions = Lists.newArrayList(options);
        }
        return result;
    }

    /**
     * optional = false
     *
     * @see #newEnum(String name, boolean optional, String[] values)
     */
    public static CommandParameter newEnum(String name, String[] values) {
        return newEnum(name, false, values);
    }

    /**
     * {@link CommandEnum#getName()}为 {@code name+"Enums"}<p>
     * isSoft = false
     *
     * @see #newEnum(String name, boolean optional, CommandEnum data)
     */
    public static CommandParameter newEnum(String name, boolean optional, String[] values) {
        return newEnum(name, optional, new CommandEnum(name + "Enums", values));
    }

    /**
     * @see #newEnum(String name, boolean optional, CommandEnum data)
     */
    public static CommandParameter newEnum(String name, boolean optional, String[] values, boolean soft) {
        return newEnum(name, optional, new CommandEnum(name + "Enums", Arrays.asList(values), soft));
    }

    /**
     * optional = false
     *
     * @see #newEnum(String, boolean, CommandEnum, IParamNode, CommandParamOption...)
     */
    public static CommandParameter newEnum(String name, String type) {
        return newEnum(name, false, type);
    }

    /**
     * optional = false
     *
     * @see #newEnum(String, boolean, CommandEnum, IParamNode, CommandParamOption...)
     */
    public static CommandParameter newEnum(String name, boolean optional, String type) {
        return newEnum(name, optional, new CommandEnum(type, new ArrayList<>()));
    }

    /**
     * optional = false
     *
     * @see #newEnum(String, boolean, CommandEnum)
     */
    public static CommandParameter newEnum(String name, CommandEnum data) {
        return newEnum(name, false, data);
    }

    /**
     * optional = false
     *
     * @see #newEnum(String, boolean, CommandEnum, IParamNode, CommandParamOption...)
     */
    public static CommandParameter newEnum(String name, boolean optional, CommandEnum data) {
        return new CommandParameter(name, optional, null, data, null, null);
    }

    /**
     * optional = false
     *
     * @see #newEnum(String, boolean, CommandEnum, IParamNode, CommandParamOption...)
     */
    public static CommandParameter newEnum(String name, boolean optional, CommandEnum data, CommandParamOption... options) {
        return newEnum(name, optional, data, null, options);
    }

    /**
     * optional = false
     *
     * @see #newEnum(String, boolean, CommandEnum, IParamNode, CommandParamOption...)
     */
    public static CommandParameter newEnum(String name, boolean optional, CommandEnum data, IParamNode<?> paramNode) {
        return newEnum(name, optional, data, paramNode, new CommandParamOption[]{});
    }

    /**
     * 创建一个枚举参数
     *
     * @param name      参数名称
     * @param optional  改参数是否可选
     * @param data      枚举数据{@link CommandEnum},其中的{@link CommandEnum#getName()}才是真正的枚举参数名
     * @param paramNode 该参数对应的{@link IParamNode}
     * @param options   the options
     * @return the command parameter
     */
    public static CommandParameter newEnum(String name, boolean optional, CommandEnum data, IParamNode<?> paramNode, CommandParamOption... options) {
        var result = new CommandParameter(name, optional, null, data, null, paramNode);
        if (options.length != 0) {
            result.paramOptions = Lists.newArrayList(options);
        }
        return result;
    }

    protected static CommandParamType fromString(String param) {
        switch (param) {
            case "string":
            case "stringenum":
                return CommandParamType.STRING;
            case "target":
                return CommandParamType.TARGET;
            case "blockpos":
                return CommandParamType.POSITION;
            case "rawtext":
                return CommandParamType.RAWTEXT;
            case "int":
                return CommandParamType.INT;
        }

        return CommandParamType.RAWTEXT;
    }
}
