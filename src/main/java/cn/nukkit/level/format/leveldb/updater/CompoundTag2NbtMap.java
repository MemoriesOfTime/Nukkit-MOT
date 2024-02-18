package cn.nukkit.level.format.leveldb.updater;

import cn.nukkit.nbt.tag.*;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;

public class CompoundTag2NbtMap {

    public static NbtMap compoundTag2NbtMap(CompoundTag compoundTag) {
        NbtMapBuilder builder = NbtMap.builder();
        for (Tag tag : compoundTag.getAllTags()) {
            switch (tag.getId()) {
                case 1:
                    builder.putByte(tag.getName(), (byte) ((Integer) tag.parseValue()).intValue());
                    break;
                case 2:
                    builder.putShort(tag.getName(), (short) ((Integer) tag.parseValue()).intValue());
                    break;
                case 3:
                    builder.putInt(tag.getName(), (int) tag.parseValue());
                    break;
                case 4:
                    builder.putLong(tag.getName(), (long) tag.parseValue());
                    break;
                case 5:
                    builder.putFloat(tag.getName(), (float) tag.parseValue());
                    break;
                case 6:
                    builder.putDouble(tag.getName(), (double) tag.parseValue());
                    break;
                case 7:
                    builder.putByteArray(tag.getName(), (byte[]) tag.parseValue());
                    break;
                case 8:
                    builder.putString(tag.getName(), (String) tag.parseValue());
                    break;
                case 9:
                    ListTag listTag = (ListTag) tag;
                    builder.putList(tag.getName(), tagType2NbtType(listTag.type), listTag2NbtList(listTag));
                    break;
                case 10:
                    builder.putCompound(tag.getName(), compoundTag2NbtMap((CompoundTag) tag));
                    break;
                case 11:
                    builder.putIntArray(tag.getName(), (int[]) tag.parseValue());
                    break;
            }
        }
        return builder.build();
    }

    private static CompoundTag nbtMap2CompoundTag(NbtMap nbtMap) {
        CompoundTag compoundTag = new CompoundTag();
        for (String key : nbtMap.keySet()) {
            Object value = nbtMap.get(key);
            if (value instanceof Byte) {
                compoundTag.putByte(key, (byte) value);
            } else if (value instanceof Short) {
                compoundTag.putShort(key, (short) value);
            } else if (value instanceof Integer) {
                compoundTag.putInt(key, (int) value);
            } else if (value instanceof Long) {
                compoundTag.putLong(key, (long) value);
            } else if (value instanceof Float) {
                compoundTag.putFloat(key, (float) value);
            } else if (value instanceof Double) {
                compoundTag.putDouble(key, (double) value);
            } else if (value instanceof byte[]) {
                compoundTag.putByteArray(key, (byte[]) value);
            } else if (value instanceof String) {
                compoundTag.putString(key, (String) value);
            } else if (value instanceof NbtList nbtList) {
                compoundTag.put(key, nbtList2ListTag(nbtList));
            } else if (value instanceof NbtMap) {
                compoundTag.putCompound(key, nbtMap2CompoundTag((NbtMap) value));
            } else if (value instanceof int[]) {
                compoundTag.putIntArray(key, (int[]) value);
            }
        }
        return compoundTag;
    }

    private static NbtList listTag2NbtList(ListTag<Tag> listTag) {
        NbtList nbtList = new NbtList(tagType2NbtType(listTag.type));
        for (Tag tag : listTag.getAll()) {
            if (tag instanceof ByteTag) {
                nbtList.add(((ByteTag) tag).parseValue());
            } else if (tag instanceof ShortTag) {
                nbtList.add(((ShortTag) tag).parseValue());
            } else if (tag instanceof IntTag) {
                nbtList.add(((IntTag) tag).parseValue());
            } else if (tag instanceof LongTag) {
                nbtList.add(((LongTag) tag).parseValue());
            } else if (tag instanceof FloatTag) {
                nbtList.add(((FloatTag) tag).parseValue());
            } else if (tag instanceof DoubleTag) {
                nbtList.add(((DoubleTag) tag).parseValue());
            } else if (tag instanceof ByteArrayTag) {
                nbtList.add(((ByteArrayTag) tag).parseValue());
            } else if (tag instanceof StringTag) {
                nbtList.add(((StringTag) tag).parseValue());
            } else if (tag instanceof ListTag listTag1) {
                nbtList.add(listTag2NbtList(listTag1));
            } else if (tag instanceof CompoundTag compoundTag) {
                nbtList.add(compoundTag2NbtMap(compoundTag));
            } else if (tag instanceof IntArrayTag) {
                nbtList.add(((IntArrayTag) tag).parseValue());
            }
        }
        return nbtList;
    }

    private static ListTag<Tag> nbtList2ListTag(NbtList nbtList) {
        ListTag<Tag> listTag = new ListTag<>();
        for (Object value : nbtList) {
            if (value instanceof Byte) {
                listTag.add(new ByteTag("", (byte) value));
            } else if (value instanceof Short) {
                listTag.add(new ShortTag("", (short) value));
            } else if (value instanceof Integer) {
                listTag.add(new IntTag("", (int) value));
            } else if (value instanceof Long) {
                listTag.add(new LongTag("", (long) value));
            } else if (value instanceof Float) {
                listTag.add(new FloatTag("", (float) value));
            } else if (value instanceof Double) {
                listTag.add(new DoubleTag("", (double) value));
            } else if (value instanceof byte[]) {
                listTag.add(new ByteArrayTag("", (byte[]) value));
            } else if (value instanceof String) {
                listTag.add(new StringTag("", (String) value));
            } else if (value instanceof NbtList nbtList1) {
                listTag.add(nbtList2ListTag(nbtList1));
            } else if (value instanceof NbtMap) {
                listTag.add(nbtMap2CompoundTag((NbtMap) value));
            } else if (value instanceof int[]) {
                listTag.add(new IntArrayTag("", (int[]) value));
            }
        }
        return listTag;
    }

    private static NbtType<?> tagType2NbtType(int tagType) {
        return switch (tagType) {
            case 0 -> NbtType.END;
            case 1 -> NbtType.BYTE;
            case 2 -> NbtType.SHORT;
            case 3 -> NbtType.INT;
            case 4 -> NbtType.LONG;
            case 5 -> NbtType.FLOAT;
            case 6 -> NbtType.DOUBLE;
            case 7 -> NbtType.BYTE_ARRAY;
            case 8 -> NbtType.STRING;
            case 9 -> NbtType.LIST;
            case 10 -> NbtType.COMPOUND;
            case 11 -> NbtType.INT_ARRAY;
            default -> throw new IllegalArgumentException("Unknown tag type " + tagType);
        };
    }

    private static int nbtType2TagType(NbtType<?> nbtType) {
        return switch (nbtType.getEnum()) {
            case END -> 0;
            case BYTE -> 1;
            case SHORT -> 2;
            case INT -> 3;
            case LONG -> 4;
            case FLOAT -> 5;
            case DOUBLE -> 6;
            case BYTE_ARRAY -> 7;
            case STRING -> 8;
            case LIST -> 9;
            case COMPOUND -> 10;
            case INT_ARRAY -> 11;
            default -> throw new IllegalArgumentException("Unknown tag type " + nbtType);
        };
    }

}
