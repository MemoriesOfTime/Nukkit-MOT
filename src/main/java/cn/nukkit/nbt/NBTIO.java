package cn.nukkit.nbt;

import cn.nukkit.block.Block;
import cn.nukkit.block.blockproperty.PropertyTypes;
import cn.nukkit.block.blockstate.BlockStateRegistry;
import cn.nukkit.block.blockstate.BlockStateRegistryMapping;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.nbt.stream.FastByteArrayOutputStream;
import cn.nukkit.nbt.stream.NBTInputStream;
import cn.nukkit.nbt.stream.NBTOutputStream;
import cn.nukkit.nbt.stream.PGZIPOutputStream;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.ThreadCache;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;

/**
 * A Named Binary Tag library for Nukkit Project
 */
public class NBTIO {

    public static CompoundTag putItemHelper(Item item) {
        return putItemHelper(item, null);
    }

    public static CompoundTag putItemHelper(Item item, Integer slot) {
        CompoundTag tag = new CompoundTag((String) null)
                .putByte("Count", item.getCount())
                .putShort("Damage", item.getDamage());

        int id = item.getId();
        if (id == ItemID.STRING_IDENTIFIED_ITEM) {
            tag.putString("Name", item.getNamespaceId());
        } else {
            tag.putShort("id", item.getId() & 0xFFFF);
        }

        if (slot != null) {
            tag.putByte("Slot", slot);
        }

        if (item.hasCompoundTag()) {
            tag.putCompound("tag", item.getNamedTag());
        }

        return tag;
    }

    public static Item getItemHelper(CompoundTag tag) {
        if ((!tag.contains("id") && !tag.contains("Name"))
                || !tag.contains("Count")) {
            return Item.get(0);
        }

        int damage = !tag.contains("Damage") ? 0 : tag.getShort("Damage");
        int count = tag.getByte("Count");
        Item item;

        if (tag.containsShort("id")) {
            try {
                item = Item.get((short) tag.getShort("id"), damage, count);
            } catch (Exception e) {
                item = Item.fromString(tag.getString("id"));
                item.setDamage(damage);
                item.setCount(count);
            }
        } else {
            item = Item.fromString(tag.getString("Name"));
            if (item.getDamage() == 0) {
                item.setDamage(damage);
            }
            item.setCount(count);
        }

        if (item.count > item.getMaxStackSize()) {
            item.count = item.getMaxStackSize();
            tag.putByte("Count", item.getMaxStackSize());
        }

        Tag tagTag = tag.get("tag");
        if (tagTag instanceof CompoundTag) {
            item.setNamedTag((CompoundTag) tagTag);
        }

        return item;
    }

    public static CompoundTag putBlockHelper(Block block) {
        return putBlockHelper(block, "Block");
    }

    public static CompoundTag putBlockHelper(Block block, String nbtName) {
        BlockStateRegistryMapping mapping = BlockStateRegistry.getMapping(ProtocolInfo.CURRENT_PROTOCOL);
        String[] states = mapping.getKnownBlockStateIdByRuntimeId(block.getRuntimeId()).split(";");
        CompoundTag result = new CompoundTag(nbtName).putString("name", states[0]);
        var nbt = new CompoundTag("", new TreeMap<>());
        /*if (block instanceof CustomBlock) {
            for (var str : block.getProperties().getNames()) {
                BlockProperty<?> property = block.getCurrentState().getProperty(str);
                if (property instanceof BooleanBlockProperty) {
                    nbt.putBoolean(str, block.getCurrentState().getBooleanValue(str));
                } else if (property instanceof IntBlockProperty) {
                    nbt.putInt(str, block.getCurrentState().getIntValue(str));
                } else if (property instanceof UnsignedIntBlockProperty) {
                    nbt.putInt(str, block.getCurrentState().getIntValue(str));
                } else if (property instanceof ArrayBlockProperty<?> arrayBlockProperty) {
                    if (arrayBlockProperty.isOrdinal()) {
                        if (property.getBitSize() > 1) {
                            nbt.putInt(str, Integer.parseInt(block.getCurrentState().getPersistenceValue(str)));
                        } else {
                            nbt.putBoolean(str, !block.getCurrentState().getPersistenceValue(str).equals("0"));
                        }
                    } else {
                        nbt.putString(str, block.getCurrentState().getPersistenceValue(str));
                    }
                }
            }
        } else {*/
            for (int i = 1, len = states.length; i < len; ++i) {
                String[] split = states[i].split("=");
                String propertyTypeString = PropertyTypes.getPropertyTypeString(split[0]);
                if (propertyTypeString != null) {
                    switch (propertyTypeString) {
                        case "BOOLEAN" -> nbt.putBoolean(split[0], Integer.parseInt(split[1]) == 1);
                        case "ENUM" -> nbt.putString(split[0], split[1]);
                        case "INTEGER" -> nbt.putInt(split[0], Integer.parseInt(split[1]));
                    }
                }
            }
        //}
        result.putCompound("states", nbt);
        return result.putInt("version", mapping.blockPaletteVersion.get());
    }

    public static CompoundTag read(File file) throws IOException {
        return read(file, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag read(File file, ByteOrder endianness) throws IOException {
        if (!file.exists()) return null;
        return read(new FileInputStream(file), endianness);
    }

    public static CompoundTag read(InputStream inputStream) throws IOException {
        return read(inputStream, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag read(InputStream inputStream, ByteOrder endianness) throws IOException {
        return read(inputStream, endianness, false);
    }

    public static CompoundTag read(InputStream inputStream, ByteOrder endianness, boolean network) throws IOException {
        try (NBTInputStream stream = new NBTInputStream(inputStream, endianness, network)) {
            Tag tag = Tag.readNamedTag(stream);
            if (tag instanceof CompoundTag) {
                return (CompoundTag) tag;
            }
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    /**
     * 和read方法相同，但不使用try自动关闭流
     */
    public static CompoundTag readNoClose(InputStream inputStream, ByteOrder endianness, boolean network) throws IOException {
        Tag tag = Tag.readNamedTag(new NBTInputStream(inputStream, endianness, network));
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag;
        }
        throw new IOException("Root tag must be a named compound tag");
    }

    public static Tag readNetwork(InputStream inputStream) throws IOException {
        try (NBTInputStream stream = new NBTInputStream(inputStream, ByteOrder.LITTLE_ENDIAN, true)) {
            return Tag.readNamedTag(stream);
        }
    }

    public static Tag readTag(InputStream inputStream, ByteOrder endianness, boolean network) throws IOException {
        try (NBTInputStream stream = new NBTInputStream(inputStream, endianness, network)) {
            return Tag.readNamedTag(stream);
        }
    }

    public static CompoundTag read(byte[] data) throws IOException {
        return read(data, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag read(byte[] data, ByteOrder endianness) throws IOException {
        return read(new ByteArrayInputStream(data), endianness);
    }

    public static CompoundTag read(byte[] data, ByteOrder endianness, boolean network) throws IOException {
        return read(new ByteArrayInputStream(data), endianness, network);
    }

    public static CompoundTag readCompressed(InputStream inputStream) throws IOException {
        return readCompressed(inputStream, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag readCompressed(InputStream inputStream, ByteOrder endianness) throws IOException {
        return read(new BufferedInputStream(new GZIPInputStream(inputStream)), endianness);
    }

    public static CompoundTag readCompressed(byte[] data) throws IOException {
        return readCompressed(data, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag readCompressed(byte[] data, ByteOrder endianness) throws IOException {
        return read(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(data))), endianness, true);
    }

    public static CompoundTag readNetworkCompressed(InputStream inputStream) throws IOException {
        return readNetworkCompressed(inputStream, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag readNetworkCompressed(InputStream inputStream, ByteOrder endianness) throws IOException {
        return read(new BufferedInputStream(new GZIPInputStream(inputStream)), endianness);
    }

    public static CompoundTag readNetworkCompressed(byte[] data) throws IOException {
        return readNetworkCompressed(data, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag readNetworkCompressed(byte[] data, ByteOrder endianness) throws IOException {
        return read(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(data))), endianness, true);
    }

    public static byte[] write(CompoundTag tag) throws IOException {
        return write(tag, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] write(CompoundTag tag, ByteOrder endianness) throws IOException {
        return write(tag, endianness, false);
    }

    public static byte[] write(CompoundTag tag, ByteOrder endianness, boolean network) throws IOException {
        return write((Tag) tag, endianness, network);
    }

    public static byte[] write(Tag tag, ByteOrder endianness, boolean network) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        try (NBTOutputStream stream = new NBTOutputStream(baos, endianness, network)) {
            Tag.writeNamedTag(tag, stream);
            return baos.toByteArray();
        }
    }

    public static byte[] write(Collection<CompoundTag> tags) throws IOException {
        return write(tags, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] write(Collection<CompoundTag> tags, ByteOrder endianness) throws IOException {
        return write(tags, endianness, false);
    }

    public static byte[] write(Collection<CompoundTag> tags, ByteOrder endianness, boolean network) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        try (NBTOutputStream stream = new NBTOutputStream(baos, endianness, network)) {
            for (CompoundTag tag : tags) {
                Tag.writeNamedTag(tag, stream);
            }
            return baos.toByteArray();
        }
    }

    public static void write(CompoundTag tag, File file) throws IOException {
        write(tag, file, ByteOrder.BIG_ENDIAN);
    }

    public static void write(CompoundTag tag, File file, ByteOrder endianness) throws IOException {
        write(tag, new FileOutputStream(file), endianness);
    }

    public static void write(CompoundTag tag, OutputStream outputStream) throws IOException {
        write(tag, outputStream, ByteOrder.BIG_ENDIAN);
    }

    public static void write(CompoundTag tag, OutputStream outputStream, ByteOrder endianness) throws IOException {
        write(tag, outputStream, endianness, false);
    }

    public static void write(CompoundTag tag, OutputStream outputStream, ByteOrder endianness, boolean network) throws IOException {
        try (NBTOutputStream stream = new NBTOutputStream(outputStream, endianness, network)) {
            Tag.writeNamedTag(tag, stream);
        }
    }

    public static byte[] writeNetwork(Tag tag) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        try (NBTOutputStream stream = new NBTOutputStream(baos, ByteOrder.LITTLE_ENDIAN, true)) {
            Tag.writeNamedTag(tag, stream);
        }
        return baos.toByteArray();
    }

    public static byte[] writeGZIPCompressed(CompoundTag tag) throws IOException {
        return writeGZIPCompressed(tag, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] writeGZIPCompressed(CompoundTag tag, ByteOrder endianness) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        writeGZIPCompressed(tag, baos, endianness);
        return baos.toByteArray();
    }

    public static void writeGZIPCompressed(CompoundTag tag, OutputStream outputStream) throws IOException {
        writeGZIPCompressed(tag, outputStream, ByteOrder.BIG_ENDIAN);
    }

    public static void writeGZIPCompressed(CompoundTag tag, OutputStream outputStream, ByteOrder endianness) throws IOException {
        write(tag, new PGZIPOutputStream(outputStream), endianness);
    }

    public static byte[] writeNetworkGZIPCompressed(CompoundTag tag) throws IOException {
        return writeNetworkGZIPCompressed(tag, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] writeNetworkGZIPCompressed(CompoundTag tag, ByteOrder endianness) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        writeNetworkGZIPCompressed(tag, baos, endianness);
        return baos.toByteArray();
    }

    public static void writeNetworkGZIPCompressed(CompoundTag tag, OutputStream outputStream) throws IOException {
        writeNetworkGZIPCompressed(tag, outputStream, ByteOrder.BIG_ENDIAN);
    }

    public static void writeNetworkGZIPCompressed(CompoundTag tag, OutputStream outputStream, ByteOrder endianness) throws IOException {
        write(tag, new PGZIPOutputStream(outputStream), endianness, true);
    }

    public static void writeZLIBCompressed(CompoundTag tag, OutputStream outputStream) throws IOException {
        writeZLIBCompressed(tag, outputStream, ByteOrder.BIG_ENDIAN);
    }

    public static void writeZLIBCompressed(CompoundTag tag, OutputStream outputStream, ByteOrder endianness) throws IOException {
        writeZLIBCompressed(tag, outputStream, Deflater.DEFAULT_COMPRESSION, endianness);
    }

    public static void writeZLIBCompressed(CompoundTag tag, OutputStream outputStream, int level) throws IOException {
        writeZLIBCompressed(tag, outputStream, level, ByteOrder.BIG_ENDIAN);
    }

    public static void writeZLIBCompressed(CompoundTag tag, OutputStream outputStream, int level, ByteOrder endianness) throws IOException {
        write(tag, new DeflaterOutputStream(outputStream, new Deflater(level)), endianness);
    }

    public static void safeWrite(CompoundTag tag, File file) throws IOException {
        File tmpFile = new File(file.getAbsolutePath() + "_tmp");
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        write(tag, tmpFile);
        Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
