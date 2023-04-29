package cn.nukkit.network.protocol;

import cn.nukkit.command.data.*;
import cn.nukkit.utils.BinaryStream;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import lombok.ToString;

import java.util.*;
import java.util.function.ObjIntConsumer;

import static cn.nukkit.utils.Utils.dynamic;

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

    /* From < 1.16.100 */
    public static final int ARG_TYPE_INT = dynamic(1);
    public static final int ARG_TYPE_FLOAT = dynamic(3);
    public static final int ARG_TYPE_VALUE = dynamic(4);
    public static final int ARG_TYPE_WILDCARD_INT = dynamic(5);
    public static final int ARG_TYPE_OPERATOR = dynamic(6);
    public static final int ARG_TYPE_COMPARE_OPERATOR = dynamic(7);
    public static final int ARG_TYPE_TARGET = dynamic(8);
    public static final int ARG_TYPE_WILDCARD_TARGET = dynamic(10);
    public static final int ARG_TYPE_FILE_PATH = dynamic(17);
    public static final int ARG_TYPE_FULL_INTEGER_RANGE = dynamic(23);
    public static final int ARG_TYPE_EQUIPMENT_SLOT = dynamic(43);
    public static final int ARG_TYPE_STRING = dynamic(44);
    public static final int ARG_TYPE_BLOCK_POSITION = dynamic(52);
    public static final int ARG_TYPE_POSITION = dynamic(53);
    public static final int ARG_TYPE_MESSAGE = dynamic(55);
    public static final int ARG_TYPE_RAWTEXT = dynamic(58);
    public static final int ARG_TYPE_JSON = dynamic(62);
    public static final int ARG_TYPE_BLOCK_STATES = dynamic(71);
    public static final int ARG_TYPE_COMMAND = dynamic(74);

    public Map<String, CommandDataVersions> commands;
    public final Map<String, List<String>> softEnums = new HashMap<>();

    private static final Int2IntOpenHashMap COMMAND_PARAMS = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_582 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_527 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_503 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_428 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_419 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_388 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_340 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_332 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_313 = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap COMMAND_PARAMS_291 = new Int2IntOpenHashMap();

    static {
        COMMAND_PARAMS.put(ARG_TYPE_INT, ARG_TYPE_INT);
        COMMAND_PARAMS.put(ARG_TYPE_FLOAT, ARG_TYPE_FLOAT);
        COMMAND_PARAMS.put(ARG_TYPE_VALUE, ARG_TYPE_VALUE);
        COMMAND_PARAMS.put(ARG_TYPE_WILDCARD_INT, ARG_TYPE_WILDCARD_INT);
        COMMAND_PARAMS.put(ARG_TYPE_OPERATOR, ARG_TYPE_OPERATOR);
        COMMAND_PARAMS.put(ARG_TYPE_COMPARE_OPERATOR, ARG_TYPE_COMPARE_OPERATOR);
        COMMAND_PARAMS.put(ARG_TYPE_TARGET, ARG_TYPE_TARGET);
        COMMAND_PARAMS.put(ARG_TYPE_WILDCARD_TARGET, ARG_TYPE_WILDCARD_TARGET);
        COMMAND_PARAMS.put(ARG_TYPE_FILE_PATH, ARG_TYPE_FILE_PATH);
        COMMAND_PARAMS.put(ARG_TYPE_FULL_INTEGER_RANGE, ARG_TYPE_FULL_INTEGER_RANGE);
        COMMAND_PARAMS.put(ARG_TYPE_EQUIPMENT_SLOT, ARG_TYPE_EQUIPMENT_SLOT);
        COMMAND_PARAMS.put(ARG_TYPE_STRING, ARG_TYPE_STRING);
        COMMAND_PARAMS.put(ARG_TYPE_BLOCK_POSITION, ARG_TYPE_BLOCK_POSITION);
        COMMAND_PARAMS.put(ARG_TYPE_POSITION, ARG_TYPE_POSITION);
        COMMAND_PARAMS.put(ARG_TYPE_MESSAGE, ARG_TYPE_MESSAGE);
        COMMAND_PARAMS.put(ARG_TYPE_RAWTEXT, ARG_TYPE_RAWTEXT);
        COMMAND_PARAMS.put(ARG_TYPE_JSON, ARG_TYPE_JSON);
        COMMAND_PARAMS.put(ARG_TYPE_BLOCK_STATES, ARG_TYPE_BLOCK_STATES);
        COMMAND_PARAMS.put(ARG_TYPE_COMMAND, ARG_TYPE_COMMAND);

        COMMAND_PARAMS_582.putAll(COMMAND_PARAMS);

        COMMAND_PARAMS_527.putAll(COMMAND_PARAMS_582);
        COMMAND_PARAMS_527.put(ARG_TYPE_EQUIPMENT_SLOT, 38);
        COMMAND_PARAMS_527.put(ARG_TYPE_STRING, 39);
        COMMAND_PARAMS_527.put(ARG_TYPE_BLOCK_POSITION, 47);
        COMMAND_PARAMS_527.put(ARG_TYPE_POSITION, 48);
        COMMAND_PARAMS_527.put(ARG_TYPE_MESSAGE, 51);
        COMMAND_PARAMS_527.put(ARG_TYPE_RAWTEXT, 53);
        COMMAND_PARAMS_527.put(ARG_TYPE_JSON, 57);
        COMMAND_PARAMS_527.put(ARG_TYPE_BLOCK_STATES, 67);
        COMMAND_PARAMS_527.put(ARG_TYPE_COMMAND, 70);

        COMMAND_PARAMS_503.putAll(COMMAND_PARAMS_527);
        COMMAND_PARAMS_503.put(ARG_TYPE_TARGET, 7);
        COMMAND_PARAMS_503.put(ARG_TYPE_WILDCARD_TARGET, 9);
        COMMAND_PARAMS_503.put(ARG_TYPE_FILE_PATH, 16);
        COMMAND_PARAMS_503.put(ARG_TYPE_EQUIPMENT_SLOT, 37);
        COMMAND_PARAMS_503.put(ARG_TYPE_STRING, 38);
        COMMAND_PARAMS_503.put(ARG_TYPE_BLOCK_POSITION, 46);
        COMMAND_PARAMS_503.put(ARG_TYPE_POSITION, 47);
        COMMAND_PARAMS_503.put(ARG_TYPE_MESSAGE, 50);
        COMMAND_PARAMS_503.put(ARG_TYPE_RAWTEXT, 52);
        COMMAND_PARAMS_503.put(ARG_TYPE_JSON, 56);
        COMMAND_PARAMS_503.put(ARG_TYPE_BLOCK_STATES, 66);
        COMMAND_PARAMS_503.put(ARG_TYPE_COMMAND, 69);

        COMMAND_PARAMS_428.putAll(COMMAND_PARAMS_503);
        COMMAND_PARAMS_428.put(ARG_TYPE_STRING, 32);
        COMMAND_PARAMS_428.put(ARG_TYPE_BLOCK_POSITION, 40);
        COMMAND_PARAMS_428.put(ARG_TYPE_POSITION, 41);
        COMMAND_PARAMS_428.put(ARG_TYPE_MESSAGE, 44);
        COMMAND_PARAMS_428.put(ARG_TYPE_RAWTEXT, 46);
        COMMAND_PARAMS_428.put(ARG_TYPE_JSON, 50);
        COMMAND_PARAMS_428.put(ARG_TYPE_BLOCK_STATES, 60);
        COMMAND_PARAMS_428.put(ARG_TYPE_COMMAND, 63);

        COMMAND_PARAMS_419.putAll(COMMAND_PARAMS_428);
        COMMAND_PARAMS_419.put(ARG_TYPE_FLOAT, 2);
        COMMAND_PARAMS_419.put(ARG_TYPE_VALUE, 3);
        COMMAND_PARAMS_419.put(ARG_TYPE_WILDCARD_INT, 4);
        COMMAND_PARAMS_419.put(ARG_TYPE_OPERATOR, 5);
        COMMAND_PARAMS_419.put(ARG_TYPE_TARGET, 6);
        COMMAND_PARAMS_419.put(ARG_TYPE_WILDCARD_TARGET, 8);
        COMMAND_PARAMS_419.put(ARG_TYPE_FILE_PATH, 15);
        COMMAND_PARAMS_419.put(ARG_TYPE_STRING, 31);
        COMMAND_PARAMS_419.put(ARG_TYPE_BLOCK_POSITION, 39);
        COMMAND_PARAMS_419.put(ARG_TYPE_POSITION, 40);
        COMMAND_PARAMS_419.put(ARG_TYPE_MESSAGE, 43);
        COMMAND_PARAMS_419.put(ARG_TYPE_RAWTEXT, 45);
        COMMAND_PARAMS_419.put(ARG_TYPE_JSON, 49);
        COMMAND_PARAMS_419.put(ARG_TYPE_COMMAND, 56);

        COMMAND_PARAMS_388.putAll(COMMAND_PARAMS_419);
        COMMAND_PARAMS_388.put(ARG_TYPE_WILDCARD_TARGET, 7);
        COMMAND_PARAMS_388.put(ARG_TYPE_FILE_PATH, 14);
        COMMAND_PARAMS_388.put(ARG_TYPE_STRING, 29);
        COMMAND_PARAMS_388.put(ARG_TYPE_BLOCK_POSITION, 37);
        COMMAND_PARAMS_388.put(ARG_TYPE_POSITION, 38);
        COMMAND_PARAMS_388.put(ARG_TYPE_MESSAGE, 41);
        COMMAND_PARAMS_388.put(ARG_TYPE_RAWTEXT, 43);
        COMMAND_PARAMS_388.put(ARG_TYPE_JSON, 47);
        COMMAND_PARAMS_388.put(ARG_TYPE_COMMAND, 54);

        COMMAND_PARAMS_340.putAll(COMMAND_PARAMS_388);
        COMMAND_PARAMS_340.put(ARG_TYPE_STRING, 27);
        COMMAND_PARAMS_340.put(ARG_TYPE_POSITION, 29);
        COMMAND_PARAMS_340.put(ARG_TYPE_MESSAGE, 32);
        COMMAND_PARAMS_340.put(ARG_TYPE_RAWTEXT, 34);
        COMMAND_PARAMS_340.put(ARG_TYPE_JSON, 37);
        COMMAND_PARAMS_340.put(ARG_TYPE_COMMAND, 44);

        COMMAND_PARAMS_332.putAll(COMMAND_PARAMS_340);
        COMMAND_PARAMS_332.put(ARG_TYPE_FILE_PATH, 15);
        COMMAND_PARAMS_332.put(ARG_TYPE_STRING, 28);
        COMMAND_PARAMS_332.put(ARG_TYPE_POSITION, 30);
        COMMAND_PARAMS_332.put(ARG_TYPE_MESSAGE, 33);
        COMMAND_PARAMS_332.put(ARG_TYPE_RAWTEXT, 35);
        COMMAND_PARAMS_332.put(ARG_TYPE_JSON, 38);
        COMMAND_PARAMS_332.put(ARG_TYPE_COMMAND, 45);

        COMMAND_PARAMS_313.putAll(COMMAND_PARAMS_332);
        COMMAND_PARAMS_313.put(ARG_TYPE_STRING, 26);
        COMMAND_PARAMS_313.put(ARG_TYPE_POSITION, 28);
        COMMAND_PARAMS_313.put(ARG_TYPE_MESSAGE, 31);
        COMMAND_PARAMS_313.put(ARG_TYPE_RAWTEXT, 33);
        COMMAND_PARAMS_313.put(ARG_TYPE_JSON, 36);
        COMMAND_PARAMS_313.put(ARG_TYPE_COMMAND, 43);

        COMMAND_PARAMS_291.putAll(COMMAND_PARAMS_313);
        COMMAND_PARAMS_291.put(ARG_TYPE_STRING, 24);
        COMMAND_PARAMS_291.put(ARG_TYPE_POSITION, 26);
        COMMAND_PARAMS_291.put(ARG_TYPE_MESSAGE, 29);
        COMMAND_PARAMS_291.put(ARG_TYPE_RAWTEXT, 31);
        COMMAND_PARAMS_291.put(ARG_TYPE_JSON, 34);
        COMMAND_PARAMS_291.put(ARG_TYPE_COMMAND, 41);
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
        LinkedHashSet<String> postFixesSet = new LinkedHashSet<>();
        LinkedHashSet<CommandEnum> enumsSet = new LinkedHashSet<>();

        commands.forEach((name, data) -> {
            CommandData cmdData = data.versions.get(0);

            if (cmdData.aliases != null) {
                enumsSet.add(cmdData.aliases);

                enumValuesSet.addAll(cmdData.aliases.getValues());
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

        this.putUnsignedVarInt(postFixes.size());
        postFixes.forEach(this::putString);

        ObjIntConsumer<BinaryStream> indexWriter;
        if (enumValues.size() < 256) {
            indexWriter = WRITE_BYTE;
        } else if (enumValues.size() < 65536) {
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

            putUnsignedVarInt(data.overloads.size());
            for (CommandOverload overload : data.overloads.values()) {
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
                            int id = parameter.type.getId();
                            //TODO Multiversion
                            if (protocol >= ProtocolInfo.v1_19_80) {
                                id = COMMAND_PARAMS_582.getOrDefault(id, id);
                            } else if (protocol >= ProtocolInfo.v1_19_0) {
                                id = COMMAND_PARAMS_527.getOrDefault(id, id);
                            } else if (protocol >= ProtocolInfo.v1_18_30) {
                                id = COMMAND_PARAMS_503.getOrDefault(id, id);
                            } else if (protocol >= ProtocolInfo.v1_16_210) {
                                id = COMMAND_PARAMS_428.getOrDefault(id, id);
                            } else if (protocol >= ProtocolInfo.v1_16_100) {
                                id = COMMAND_PARAMS_419.getOrDefault(id, id);
                            } else if (protocol >= ProtocolInfo.v1_13_0) {
                                id = COMMAND_PARAMS_388.getOrDefault(id, id);
                            } else if (protocol >= ProtocolInfo.v1_10_0) {
                                id = COMMAND_PARAMS_340.getOrDefault(id, id);
                            } else if (protocol >= ProtocolInfo.v1_9_0) {
                                id = COMMAND_PARAMS_332.getOrDefault(id, id);
                            } else if (protocol >= ProtocolInfo.v1_8_0) {
                                id = COMMAND_PARAMS_313.getOrDefault(id, id);
                            } else {
                                id = COMMAND_PARAMS_291.getOrDefault(id, id);
                            }

                            /*if (protocol < ProtocolInfo.v1_8_0) {
                                switch (parameter.type) {
                                    case STRING:
                                        id = 0x0f;
                                        break;
                                    case POSITION:
                                    case BLOCK_POSITION:
                                        id = 0x10;
                                        break;
                                    case MESSAGE:
                                        id = 0x13;
                                        break;
                                    case RAWTEXT:
                                        id = 0x15;
                                        break;
                                    case JSON:
                                        id = 0x18;
                                        break;
                                    case COMMAND:
                                        id = 0x1f;
                                        break;
                                }
                            } else if (protocol < ProtocolInfo.v1_13_0) {
                                switch (parameter.type) {
                                    case STRING:
                                        id = 27;
                                        break;
                                    case POSITION:
                                    case BLOCK_POSITION:
                                        id = 29;
                                        break;
                                    case MESSAGE:
                                        id = 32;
                                        break;
                                    case RAWTEXT:
                                        id = 34;
                                        break;
                                    case JSON:
                                        id = 37;
                                        break;
                                    case COMMAND:
                                        id = 44;
                                        break;
                                }
                            } else if (protocol >= ProtocolInfo.v1_19_0) {
                                if (id == ARG_TYPE_TARGET) {
                                    id = 8;
                                }else if (id == ARG_TYPE_WILDCARD_TARGET) {
                                    id = 10;
                                }else if (id == ARG_TYPE_FILE_PATH) {
                                    id = 17;
                                }else if (id == ARG_TYPE_EQUIPMENT_SLOT) {
                                    id = 38;
                                }else if (id >= ARG_TYPE_STRING && id <= ARG_TYPE_JSON) { //29-47
                                    id = id + 10;
                                }else if (id == ARG_TYPE_COMMAND) {
                                    id = 70;
                                }else if (id == ARG_TYPE_COMPARE_OPERATOR) {
                                    id = 7;
                                }
                            } else if (protocol >= ProtocolInfo.v1_18_30) {
                                if (id == ARG_TYPE_WILDCARD_TARGET ) {
                                    id = 9;
                                } else if (id == ARG_TYPE_STRING) {
                                    id = 38;
                                } else if (id == ARG_TYPE_BLOCK_POSITION) {
                                    id = 46;
                                } else if (id == ARG_TYPE_POSITION) {
                                    id = 47;
                                } else if (id == ARG_TYPE_MESSAGE) {
                                    id = 50;
                                } else if (id == ARG_TYPE_RAWTEXT) {
                                    id = 52;
                                } else if (id == ARG_TYPE_JSON) {
                                    id = 56;
                                } else if (id == ARG_TYPE_COMMAND) {
                                    id = 69;
                                }else if (id == ARG_TYPE_EQUIPMENT_SLOT) {
                                    id = 37;
                                }
                            } else if (protocol >= ProtocolInfo.v1_16_210) { //TODO: proper implementation for 1.16.210 command params
                                if (id == ARG_TYPE_COMMAND) {
                                    id = 63;
                                } else if (id == ARG_TYPE_FILE_PATH) {
                                    id = id + 2; // +1 from .100 and +1 from .210
                                } else if (id >= ARG_TYPE_STRING) {
                                    id = id + 3; // +2 from .100 and +1 from .210
                                } else if (id >= ARG_TYPE_FLOAT) {
                                    id++;
                                }
                            } else if (protocol >= ProtocolInfo.v1_16_100) { //TODO: proper implementation for 1.16.100 command params
                                if (id == ARG_TYPE_FILE_PATH) {
                                    id++;
                                } else if (id >= ARG_TYPE_STRING) {
                                    id = id + 2;
                                }
                            }*/

                            type |= id;
                        }
                    }

                    putLInt(type);
                    putBoolean(parameter.optional);
                    if (protocol >= 340) {
                        putByte(parameter.options);
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
            this.putUnsignedVarInt(0);
        }
    }
}
