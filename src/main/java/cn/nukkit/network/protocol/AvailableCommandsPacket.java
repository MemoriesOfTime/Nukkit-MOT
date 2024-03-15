package cn.nukkit.network.protocol;

import cn.nukkit.command.data.*;
import cn.nukkit.network.protocol.types.CommandParam;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.SequencedHashSet;
import cn.nukkit.utils.TypeMap;
import lombok.ToString;

import java.util.*;
import java.util.function.ObjIntConsumer;

import static com.nukkitx.network.util.Preconditions.checkArgument;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class AvailableCommandsPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.AVAILABLE_COMMANDS_PACKET;

    private static final ObjIntConsumer<BinaryStream> WRITE_BYTE = (s, v) -> s.putByte((byte) v);
    private static final ObjIntConsumer<BinaryStream> WRITE_SHORT = BinaryStream::putLShort;
    private static final ObjIntConsumer<BinaryStream> WRITE_INT = BinaryStream::putLInt;
    //private static final ToIntFunction<BinaryStream> READ_BYTE = BinaryStream::getByte;
    //private static final ToIntFunction<BinaryStream> READ_SHORT = BinaryStream::getLShort;
    //private static final ToIntFunction<BinaryStream> READ_INT = BinaryStream::getLInt;

    public static final int ARG_FLAG_VALID = 0x100000;
    public static final int ARG_FLAG_ENUM = 0x200000;
    public static final int ARG_FLAG_POSTFIX = 0x1000000;
    public static final int ARG_FLAG_SOFT_ENUM = 0x4000000;


    //各个版本的参数
    private static final TypeMap<CommandParam> COMMAND_PARAMS_291 = TypeMap.builder(CommandParam.class)
            .insert(1, CommandParam.INT)
            .insert(2, CommandParam.FLOAT)
            .insert(3, CommandParam.VALUE)
            .insert(4, CommandParam.WILDCARD_INT)
            .insert(5, CommandParam.OPERATOR)
            .insert(6, CommandParam.TARGET)
            .insert(7, CommandParam.WILDCARD_TARGET)
            .insert(24, CommandParam.STRING)
            .insert(26, CommandParam.POSITION)
            .insert(29, CommandParam.MESSAGE)
            .insert(31, CommandParam.TEXT)
            .insert(34, CommandParam.JSON)
            .insert(41, CommandParam.COMMAND)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_313 = COMMAND_PARAMS_291.toBuilder()
            .shift(24, 2)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_332 = COMMAND_PARAMS_313.toBuilder()
            .insert(15, CommandParam.FILE_PATH)
            .shift(26, 2)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_340 = COMMAND_PARAMS_332.toBuilder()
            .shift(15, -1)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_388 = COMMAND_PARAMS_340.toBuilder()
            .shift(27, 2)
            .shift(31, 7)
            .insert(37, CommandParam.BLOCK_POSITION)
            .shift(46, 1)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_419 = COMMAND_PARAMS_388.toBuilder()
            .shift(7, 1)
            .shift(30, 1)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_428 = COMMAND_PARAMS_419.toBuilder()
            .shift(2, 1)
            .shift(57, 6)
            .insert(60, CommandParam.BLOCK_STATES)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_503 = COMMAND_PARAMS_428.toBuilder()
            .shift(32, 6)
            .insert(37, CommandParam.EQUIPMENT_SLOTS)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_527 = COMMAND_PARAMS_503.toBuilder()
            .shift(7, 1)
            .insert(7, CommandParam.COMPARE_OPERATOR)
            .insert(23, CommandParam.INT_RANGE)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_575 = TypeMap.builder(CommandParam.class)
            .insert(0, CommandParam.UNKNOWN)
            .insert(1, CommandParam.INT)
            //.insert(2, CommandParam.FLOAT)
            .insert(3, CommandParam.FLOAT) // FLOAT is actually VALUE
            .insert(4, CommandParam.VALUE) // and VALUE is actually R_VALUE
            .insert(5, CommandParam.WILDCARD_INT)
            .insert(6, CommandParam.OPERATOR)
            .insert(7, CommandParam.COMPARE_OPERATOR)
            .insert(8, CommandParam.TARGET)
            .insert(9, CommandParam.UNKNOWN_STANDALONE)
            .insert(10, CommandParam.WILDCARD_TARGET)
            .insert(11, CommandParam.UNKNOWN_NON_ID)
            .insert(12, CommandParam.SCORE_ARG)
            .insert(13, CommandParam.SCORE_ARGS)
            .insert(14, CommandParam.SCORE_SELECT_PARAM)
            .insert(15, CommandParam.SCORE_SELECTOR)
            .insert(16, CommandParam.TAG_SELECTOR)
            .insert(17, CommandParam.FILE_PATH)
            .insert(18, CommandParam.FILE_PATH_VAL)
            .insert(19, CommandParam.FILE_PATH_CONT)
            .insert(20, CommandParam.INT_RANGE_VAL)
            .insert(21, CommandParam.INT_RANGE_POST_VAL)
            .insert(22, CommandParam.INT_RANGE)
            .insert(23, CommandParam.INT_RANGE_FULL)
            .insert(24, CommandParam.SEL_ARGS)
            .insert(25, CommandParam.ARGS)
            .insert(26, CommandParam.ARG)
            .insert(27, CommandParam.MARG)
            .insert(28, CommandParam.MVALUE)
            .insert(29, CommandParam.NAME)
            .insert(30, CommandParam.TYPE)
            .insert(31, CommandParam.FAMILY)
            .insert(32, CommandParam.TAG)
            .insert(33, CommandParam.HAS_ITEM_ELEMENT)
            .insert(34, CommandParam.HAS_ITEM_ELEMENTS)
            .insert(35, CommandParam.HAS_ITEM)
            .insert(36, CommandParam.HAS_ITEMS)
            .insert(37, CommandParam.HAS_ITEM_SELECTOR)
            .insert(38, CommandParam.EQUIPMENT_SLOTS)
            .insert(39, CommandParam.STRING)
            .insert(40, CommandParam.ID_CONT)
            .insert(41, CommandParam.COORD_X_INT)
            .insert(42, CommandParam.COORD_Y_INT)
            .insert(43, CommandParam.COORD_Z_INT)
            .insert(44, CommandParam.COORD_X_FLOAT)
            .insert(45, CommandParam.COORD_Y_FLOAT)
            .insert(46, CommandParam.COORD_Z_FLOAT)
            .insert(47, CommandParam.BLOCK_POSITION)
            .insert(48, CommandParam.POSITION)
            .insert(49, CommandParam.MESSAGE_XP)
            .insert(50, CommandParam.MESSAGE)
            .insert(51, CommandParam.MESSAGE_ROOT)
            .insert(52, CommandParam.POST_SELECTOR)
            .insert(53, CommandParam.TEXT)
            .insert(54, CommandParam.TEXT_CONT)
            .insert(55, CommandParam.JSON_VALUE)
            .insert(56, CommandParam.JSON_FIELD)
            .insert(57, CommandParam.JSON)
            .insert(58, CommandParam.JSON_OBJECT_FIELDS)
            .insert(59, CommandParam.JSON_OBJECT_CONT)
            .insert(60, CommandParam.JSON_ARRAY)
            .insert(61, CommandParam.JSON_ARRAY_VALUES)
            .insert(62, CommandParam.JSON_ARRAY_CONT)
            .insert(63, CommandParam.BLOCK_STATE)
            .insert(64, CommandParam.BLOCK_STATE_KEY)
            .insert(65, CommandParam.BLOCK_STATE_VALUE)
            .insert(66, CommandParam.BLOCK_STATE_VALUES)
            .insert(67, CommandParam.BLOCK_STATES)
            .insert(68, CommandParam.BLOCK_STATES_CONT)
            .insert(69, CommandParam.COMMAND)
            .insert(70, CommandParam.SLASH_COMMAND)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_582 = COMMAND_PARAMS_575.toBuilder()
            .shift(32, 5)
            .insert(32, CommandParam.PERMISSION)
            .insert(33, CommandParam.PERMISSIONS)
            .insert(34, CommandParam.PERMISSION_SELECTOR)
            .insert(35, CommandParam.PERMISSION_ELEMENT)
            .insert(36, CommandParam.PERMISSION_ELEMENTS)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_594 = COMMAND_PARAMS_582.toBuilder()
            //TODO solve memory usage problem
            //.insert(134217728, CommandParam.CHAINED_COMMAND)
            .build();
    private static final TypeMap<CommandParam> COMMAND_PARAMS_662 = COMMAND_PARAMS_594.toBuilder()
            .shift(24, 4)
            .insert(24, CommandParam.RATIONAL_RANGE_VAL)
            .insert(25, CommandParam.RATIONAL_RANGE_POST_VAL)
            .insert(26, CommandParam.RATIONAL_RANGE)
            .insert(27, CommandParam.RATIONAL_RANGE_FULL)
            .shift(48, 8)
            .insert(48, CommandParam.PROPERTY_VALUE)
            .insert(49, CommandParam.HAS_PROPERTY_PARAM_VALUE)
            .insert(50, CommandParam.HAS_PROPERTY_PARAM_ENUM_VALUE)
            .insert(51, CommandParam.HAS_PROPERTY_ARG)
            .insert(52, CommandParam.HAS_PROPERTY_ARGS)
            .insert(53, CommandParam.HAS_PROPERTY_ELEMENT)
            .insert(54, CommandParam.HAS_PROPERTY_ELEMENTS)
            .insert(55, CommandParam.HAS_PROPERTY_SELECTOR)
            .build();

    //TODO Multiversion 保持最新版
    private static final TypeMap<CommandParam> COMMAND_PARAMS = COMMAND_PARAMS_662.toBuilder().build();

    //兼容nk插件
    public static final int ARG_TYPE_UNKNOWN = COMMAND_PARAMS.getId(CommandParam.UNKNOWN);
    public static final int ARG_TYPE_INT = COMMAND_PARAMS.getId(CommandParam.INT);
    public static final int ARG_TYPE_FLOAT = COMMAND_PARAMS.getId(CommandParam.FLOAT);
    public static final int ARG_TYPE_VALUE = COMMAND_PARAMS.getId(CommandParam.VALUE);
    public static final int ARG_TYPE_WILDCARD_INT = COMMAND_PARAMS.getId(CommandParam.WILDCARD_INT);
    public static final int ARG_TYPE_OPERATOR = COMMAND_PARAMS.getId(CommandParam.OPERATOR);
    public static final int ARG_TYPE_COMPARE_OPERATOR = COMMAND_PARAMS.getId(CommandParam.COMPARE_OPERATOR);
    public static final int ARG_TYPE_TARGET = COMMAND_PARAMS.getId(CommandParam.TARGET);
    public static final int ARG_TYPE_WILDCARD_TARGET = COMMAND_PARAMS.getId(CommandParam.WILDCARD_TARGET);
    public static final int ARG_TYPE_FILE_PATH = COMMAND_PARAMS.getId(CommandParam.FILE_PATH);
    public static final int ARG_TYPE_FULL_INTEGER_RANGE = COMMAND_PARAMS.getId(CommandParam.INT_RANGE_FULL);
    public static final int ARG_TYPE_EQUIPMENT_SLOT = COMMAND_PARAMS.getId(CommandParam.EQUIPMENT_SLOTS);
    public static final int ARG_TYPE_STRING = COMMAND_PARAMS.getId(CommandParam.STRING);
    public static final int ARG_TYPE_BLOCK_POSITION = COMMAND_PARAMS.getId(CommandParam.BLOCK_POSITION);
    public static final int ARG_TYPE_POSITION = COMMAND_PARAMS.getId(CommandParam.POSITION);
    public static final int ARG_TYPE_MESSAGE = COMMAND_PARAMS.getId(CommandParam.MESSAGE);
    public static final int ARG_TYPE_RAWTEXT = COMMAND_PARAMS.getId(CommandParam.TEXT);
    public static final int ARG_TYPE_JSON = COMMAND_PARAMS.getId(CommandParam.JSON);
    public static final int ARG_TYPE_BLOCK_STATES = COMMAND_PARAMS.getId(CommandParam.BLOCK_STATES);
    public static final int ARG_TYPE_COMMAND = COMMAND_PARAMS.getId(CommandParam.COMMAND);

    public Map<String, CommandDataVersions> commands;
    public final Map<String, List<String>> softEnums = new HashMap<>();

    public static TypeMap<CommandParam> getCommandParams(int protocol) {
        //TODO Multiversion
        if (protocol >= ProtocolInfo.v1_20_70) {
            return COMMAND_PARAMS_662;
        } else if (protocol >= ProtocolInfo.v1_20_10_21) {
            return COMMAND_PARAMS_594;
        } else if (protocol >= ProtocolInfo.v1_19_80) {
            return COMMAND_PARAMS_582;
        } else if (protocol >= ProtocolInfo.v1_19_70_24) {
            return COMMAND_PARAMS_575;
        } else if (protocol >= ProtocolInfo.v1_19_0_29) {
            return COMMAND_PARAMS_527;
        } else if (protocol >= ProtocolInfo.v1_18_30) {
            return COMMAND_PARAMS_503;
        } else if (protocol >= ProtocolInfo.v1_16_210) {
            return COMMAND_PARAMS_428;
        } else if (protocol >= ProtocolInfo.v1_16_100) {
            return COMMAND_PARAMS_419;
        } else if (protocol >= ProtocolInfo.v1_13_0) {
            return COMMAND_PARAMS_388;
        } else if (protocol >= ProtocolInfo.v1_10_0) {
            return COMMAND_PARAMS_340;
        } else if (protocol >= ProtocolInfo.v1_9_0) {
            return COMMAND_PARAMS_332;
        } else if (protocol >= ProtocolInfo.v1_8_0) {
            return COMMAND_PARAMS_313;
        } else {
            return COMMAND_PARAMS_291;
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        /*commands = new HashMap<>();

        List<String> enumValues = new ArrayList<>();
        List<String> postFixes = new ArrayList<>();
        List<CommandEnum> enums = new ArrayList<>();

        int len = (int) getUnsignedVarInt();
        while (len-- > 0) {
            enumValues.add(getString());
        }

        len = (int) getUnsignedVarInt();
        while (len-- > 0) {
            postFixes.add(getString());
        }

        ToIntFunction<BinaryStream> indexReader;
        if (enumValues.size() < 256) {
            indexReader = READ_BYTE;
        } else if (enumValues.size() < 65536) {
            indexReader = READ_SHORT;
        } else {
            indexReader = READ_INT;
        }

        len = (int) getUnsignedVarInt();
        while (len-- > 0) {
            String enumName = getString();
            int enumLength = (int) getUnsignedVarInt();

            List<String> values = new ArrayList<>();

            while (enumLength-- > 0) {
                int index = indexReader.applyAsInt(this);

                String enumValue;

                if (index < 0 || (enumValue = enumValues.get(index)) == null) {
                    throw new IllegalStateException("Enum value not found for index " + index);
                }

                values.add(enumValue);
            }

            enums.add(new CommandEnum(enumName, values));
        }

        len = (int) getUnsignedVarInt();

        while (len-- > 0) {
            String name = getString();
            String description = getString();
            int flags = getByte();
            int permission = getByte();
            CommandEnum alias = null;

            int aliasIndex = getLInt();
            if (aliasIndex >= 0) {
                alias = enums.get(aliasIndex);
            }

            Map<String, CommandOverload> overloads = new HashMap<>();

            int length = (int) getUnsignedVarInt();
            while (length-- > 0) {
                CommandOverload overload = new CommandOverload();

                int paramLen = (int) getUnsignedVarInt();

                overload.input.parameters = new CommandParameter[paramLen];
                for (int i = 0; i < paramLen; i++) {
                    String paramName = getString();
                    int type = getLInt();
                    boolean optional = getBoolean();

                    CommandParameter parameter = new CommandParameter(paramName, optional);

                    if ((type & ARG_FLAG_POSTFIX) != 0) {
                        parameter.postFix = postFixes.get(type & 0xffff);
                    } else if ((type & ARG_FLAG_VALID) == 0) {
                        throw new IllegalStateException("Invalid parameter type received");
                    } else {
                        int index = type & 0xffff;
                        if ((type & ARG_FLAG_ENUM) != 0) {
                            parameter.enumData = enums.get(index);
                        } else if ((type & ARG_FLAG_SOFT_ENUM) != 0) {
                            // TODO: soft enums
                        } else {
                            throw new IllegalStateException("Unknown parameter type!");
                        }
                    }

                    overload.input.parameters[i] = parameter;
                }

                overloads.put(Integer.toString(length), overload);
            }

            CommandData data = new CommandData();
            data.aliases = alias;
            data.overloads = overloads;
            data.description = description;
            data.flags = flags;
            data.permission = permission;

            CommandDataVersions versions = new CommandDataVersions();
            versions.versions.add(data);

            this.commands.put(name, versions);
        }*/
    }

    @Override
    public void encode() {
        this.reset();

        LinkedHashSet<String> enumValuesSet = new LinkedHashSet<>();
        SequencedHashSet<String> subCommandValues = new SequencedHashSet<>();
        LinkedHashSet<String> postFixesSet = new LinkedHashSet<>();
        SequencedHashSet<ChainedSubCommandData> subCommandData = new SequencedHashSet<>();
        LinkedHashSet<CommandEnum> enumsSet = new LinkedHashSet<>();

        commands.forEach((name, data) -> {
            CommandData cmdData = data.versions.get(0);

            if (cmdData.aliases != null) {
                enumsSet.add(cmdData.aliases);

                enumValuesSet.addAll(cmdData.aliases.getValues());
            }

            for (ChainedSubCommandData subcommand : cmdData.subcommands) {
                if (subCommandData.contains(subcommand)) {
                    continue;
                }

                subCommandData.add(subcommand);
                for (ChainedSubCommandData.Value value : subcommand.getValues()) {
                    if (subCommandValues.contains(value.getFirst())) {
                        subCommandValues.add(value.getFirst());
                    }

                    if (subCommandValues.contains(value.getSecond())) {
                        subCommandValues.add(value.getSecond());
                    }
                }
            }

            for (CommandOverload overload : cmdData.overloads.values()) {
                for (CommandParameter parameter : overload.input.parameters) {
                    if (parameter.enumData != null) {
                        enumsSet.add(parameter.enumData);

                        enumValuesSet.addAll(parameter.enumData.getValues());
                    }

                    if (parameter.postFix != null) {
                        postFixesSet.add(parameter.postFix);
                    }
                }
            }
        });

        List<String> enumValues = new ArrayList<>(enumValuesSet);
        List<CommandEnum> enums = new ArrayList<>(enumsSet);
        List<String> postFixes = new ArrayList<>(postFixesSet);

        this.putUnsignedVarInt(enumValues.size());
        enumValues.forEach(this::putString);

        if (this.protocol >= ProtocolInfo.v1_20_10_21) {
            this.putUnsignedVarInt(subCommandValues.size());
            subCommandValues.forEach(this::putString);
        }

        this.putUnsignedVarInt(postFixes.size());
        postFixes.forEach(this::putString);

        ObjIntConsumer<BinaryStream> indexWriter;
        if (enumValues.size() <= 256) {
            indexWriter = WRITE_BYTE;
        } else if (enumValues.size() <= 65536) {
            indexWriter = WRITE_SHORT;
        } else {
            indexWriter = WRITE_INT;
        }

        this.putUnsignedVarInt(enums.size());
        enums.forEach((cmdEnum) -> {
            putString(cmdEnum.getName());

            List<String> values = cmdEnum.getValues();
            putUnsignedVarInt(values.size());

            for (String val : values) {
                int i = enumValues.indexOf(val);

                if (i < 0) {
                    throw new IllegalStateException("Enum value '" + val + "' not found");
                }

                indexWriter.accept(this, i);
            }
        });

        if (this.protocol >= ProtocolInfo.v1_20_10_21) {
            this.putUnsignedVarInt(subCommandData.size());
            for (ChainedSubCommandData chainedSubCommandData : subCommandData) {
                this.putString(chainedSubCommandData.getName());
                this.putUnsignedVarInt(chainedSubCommandData.getValues().size());
                for (ChainedSubCommandData.Value value : chainedSubCommandData.getValues()) {
                    int first = subCommandValues.indexOf(value.getFirst());
                    checkArgument(first > -1, "Invalid enum value detected: " + value.getFirst());

                    int second = subCommandValues.indexOf(value.getSecond());
                    checkArgument(second > -1, "Invalid enum value detected: " + value.getSecond());

                    this.putLShort(first);
                    this.putLShort(second);
                }
            }
        }

        putUnsignedVarInt(commands.size());

        commands.forEach((name, cmdData) -> {
            CommandData data = cmdData.versions.get(0);

            putString(name);
            putString(data.description);
            if (protocol >= ProtocolInfo.v1_17_10) {
                putLShort(data.flags);
            } else {
                putByte((byte) data.flags);
            }
            putByte((byte) data.permission);

            putLInt(data.aliases == null ? -1 : enums.indexOf(data.aliases));

            if (this.protocol >= ProtocolInfo.v1_20_10_21) {
                this.putUnsignedVarInt(data.subcommands.size());
                for (ChainedSubCommandData subcommand : data.subcommands) {
                    int index = subCommandData.indexOf(subcommand);
                    checkArgument(index > -1, "Invalid subcommand index: " + subcommand);
                    this.putLShort(index);
                }
            }

            putUnsignedVarInt(data.overloads.size());
            for (CommandOverload overload : data.overloads.values()) {
                if (this.protocol >= ProtocolInfo.v1_20_10_21) {
                    this.putBoolean(overload.chaining);
                }
                putUnsignedVarInt(overload.input.parameters.length);

                for (CommandParameter parameter : overload.input.parameters) {
                    putString(parameter.name);

                    int type = 0;
                    if (parameter.postFix != null) {
                        int i = postFixes.indexOf(parameter.postFix);
                        if (i < 0) {
                            throw new IllegalStateException(
                                    "Postfix '" + parameter.postFix + "' isn't in postfix array");
                        }
                        type = ARG_FLAG_POSTFIX | i;
                    } else {
                        type |= ARG_FLAG_VALID;
                        if (parameter.enumData != null) {
                            type |= ARG_FLAG_ENUM | enums.indexOf(parameter.enumData);
                        } else {
                            CommandParam commandParam = COMMAND_PARAMS.getType(parameter.type.getId()); //正常来说应该传入最新版的数字id
                            int id = getCommandParams(protocol).getId(commandParam);
                            type |= id;
                        }
                    }

                    putLInt(type);
                    putBoolean(parameter.optional);
                    if (protocol >= 340) {
                        byte options = 0;
                        if (parameter.paramOptions != null) {
                            for (CommandParamOption option : parameter.paramOptions) {
                                options |= (byte) (1 << option.ordinal());
                            }
                        } else {
                            options = parameter.options;
                        }
                        this.putByte(options);
                    }
                }
            }
        });

        if (protocol > 274) {
            this.putUnsignedVarInt(softEnums.size());

            softEnums.forEach((name, values) -> {
                this.putString(name);
                this.putUnsignedVarInt(values.size());
                values.forEach(this::putString);
            });
        }

        if (protocol >= 407) {
            this.putUnsignedVarInt(0); //enumConstraints
        }
    }
}
