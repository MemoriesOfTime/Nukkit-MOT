package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

@Log4j2
public class GlobalBlockPalette {

    private static final Gson GSON = new Gson();
    private static boolean initialized;

    private static final AtomicInteger runtimeIdAllocator282 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator291 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator313 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator332 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator340 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator354 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator361 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator388 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator389 = new AtomicInteger(0);
    private static final AtomicInteger runtimeIdAllocator407 = new AtomicInteger(0);

    private static final Int2IntMap legacyToRuntimeId223 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId261 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId274 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId282 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId291 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId313 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId332 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId340 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId354 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId361 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId388 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId389 = new Int2IntOpenHashMap();
    private static final Int2IntMap legacyToRuntimeId407 = new Int2IntOpenHashMap();

    private static BlockPalette blockPalette419;
    private static BlockPalette blockPalette428;
    private static BlockPalette blockPalette440;
    private static BlockPalette blockPalette448;
    private static BlockPalette blockPalette465;
    private static BlockPalette blockPalette471;
    private static BlockPalette blockPalette486;
    private static BlockPalette blockPalette503;
    private static BlockPalette blockPalette527;
    private static BlockPalette blockPalette544;
    private static BlockPalette blockPalette560;
    private static BlockPalette blockPalette567;
    private static BlockPalette blockPalette575;
    private static BlockPalette blockPalette582;
    private static BlockPalette blockPalette589;
    private static BlockPalette blockPalette594;
    private static BlockPalette blockPalette618;
    private static BlockPalette blockPalette622;
    private static BlockPalette blockPalette630;
    private static BlockPalette blockPalette649;
    private static BlockPalette blockPalette662;
    private static BlockPalette blockPalette671;
    private static BlockPalette blockPalette685;
    private static BlockPalette blockPalette712;
    private static BlockPalette blockPalette729;
    private static BlockPalette blockPalette748;
    private static BlockPalette blockPalette766;
    private static BlockPalette blockPalette776;
    private static BlockPalette blockPalette786;
    private static BlockPalette blockPalette800;
    private static BlockPalette blockPalette818;

    private static BlockPalette blockPalette_netease_630;
    private static BlockPalette blockPalette_netease_686;

    private static byte[] compiledTable282;
    private static byte[] compiledTable291;
    private static byte[] compiledTable313;
    private static byte[] compiledTable332;
    private static byte[] compiledTable340;
    private static byte[] compiledTable354;
    private static byte[] compiledTable361;
    private static byte[] compiledTable388;
    private static byte[] compiledTable389;
    private static byte[] compiledTable407;

    static {
        legacyToRuntimeId223.defaultReturnValue(-1);
        legacyToRuntimeId261.defaultReturnValue(-1);
        legacyToRuntimeId274.defaultReturnValue(-1);
        legacyToRuntimeId282.defaultReturnValue(-1);
        legacyToRuntimeId291.defaultReturnValue(-1);
        legacyToRuntimeId313.defaultReturnValue(-1);
        legacyToRuntimeId332.defaultReturnValue(-1);
        legacyToRuntimeId340.defaultReturnValue(-1);
        legacyToRuntimeId354.defaultReturnValue(-1);
        legacyToRuntimeId361.defaultReturnValue(-1);
        legacyToRuntimeId388.defaultReturnValue(-1);
        legacyToRuntimeId389.defaultReturnValue(-1);
        legacyToRuntimeId407.defaultReturnValue(-1);

        // cache current block palette
        getPaletteByProtocol(GameVersion.getLastVersion());
        if (Server.getInstance().netEaseMode) {
            getPaletteByProtocol(GameVersion.V1_21_2_NETEASE);
        }
    }

    public static void init() {
        if (initialized) {
            throw new IllegalStateException("BlockPalette was already generated!");
        }
        initialized = true;
        log.debug("Loading block palette...");

        // 223
        InputStream stream223 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_223.json");
        if (stream223 == null) throw new AssertionError("Unable to locate RuntimeID table 223");
        Collection<TableEntryOld> entries223 = GSON.fromJson(new InputStreamReader(stream223, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntryOld>>(){}.getType());
        for (TableEntryOld entry : entries223) {
            legacyToRuntimeId223.put((entry.id << 4) | entry.data, entry.runtimeID);
        }
        // Compiled table not needed for 223
        // 261
        InputStream stream261 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_261.json");
        if (stream261 == null) throw new AssertionError("Unable to locate RuntimeID table 261");
        Collection<TableEntryOld> entries261 = GSON.fromJson(new InputStreamReader(stream261, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntryOld>>(){}.getType());
        for (TableEntryOld entry : entries261) {
            legacyToRuntimeId261.put((entry.id << 4) | entry.data, entry.runtimeID);
        }
        // Compiled table not needed 261
        // 274
        InputStream stream274 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_274.json");
        if (stream274 == null) throw new AssertionError("Unable to locate RuntimeID table 274");
        Collection<TableEntryOld> entries274 = GSON.fromJson(new InputStreamReader(stream274, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntryOld>>(){}.getType());
        for (TableEntryOld entry : entries274) {
            legacyToRuntimeId274.put((entry.id << 4) | entry.data, entry.runtimeID);
        }
        // Compiled table not needed 274
        // 282
        InputStream stream282 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_282.json");
        if (stream282 == null) throw new AssertionError("Unable to locate RuntimeID table 282");
        Collection<TableEntry> entries282 = GSON.fromJson(new InputStreamReader(stream282, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntry>>(){}.getType());
        BinaryStream table282 = new BinaryStream();
        table282.putUnsignedVarInt(entries282.size());
        for (TableEntry entry : entries282) {
            legacyToRuntimeId282.put((entry.id << 4) | entry.data, runtimeIdAllocator282.getAndIncrement());
            table282.putString(entry.name);
            table282.putLShort(entry.data);
        }
        compiledTable282 = table282.getBuffer();
        // 291
        InputStream stream291 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_291.json");
        if (stream291 == null) throw new AssertionError("Unable to locate RuntimeID table 291");
        Collection<TableEntry> entries291 = GSON.fromJson(new InputStreamReader(stream291, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntry>>(){}.getType());
        BinaryStream table291 = new BinaryStream();
        table291.putUnsignedVarInt(entries291.size());
        for (TableEntry entry : entries291) {
            legacyToRuntimeId291.put((entry.id << 4) | entry.data, runtimeIdAllocator291.getAndIncrement());
            table291.putString(entry.name);
            table291.putLShort(entry.data);
        }
        compiledTable291 = table291.getBuffer();
        // 313
        InputStream stream313 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_313.json");
        if (stream313 == null) throw new AssertionError("Unable to locate RuntimeID table 313");
        Collection<TableEntry> entries313 = GSON.fromJson(new InputStreamReader(stream313, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntry>>(){}.getType());
        BinaryStream table313 = new BinaryStream();
        table313.putUnsignedVarInt(entries313.size());
        for (TableEntry entry : entries313) {
            legacyToRuntimeId313.put((entry.id << 4) | entry.data, runtimeIdAllocator313.getAndIncrement());
            table313.putString(entry.name);
            table313.putLShort(entry.data);
        }
        compiledTable313 = table313.getBuffer();
        // 332
        InputStream stream332 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_332.json");
        if (stream332 == null) throw new AssertionError("Unable to locate RuntimeID table 332");
        Collection<TableEntry> entries332 = GSON.fromJson(new InputStreamReader(stream332, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntry>>(){}.getType());
        BinaryStream table332 = new BinaryStream();
        table332.putUnsignedVarInt(entries332.size());
        for (TableEntry entry : entries332) {
            legacyToRuntimeId332.put((entry.id << 4) | entry.data, runtimeIdAllocator332.getAndIncrement());
            table332.putString(entry.name);
            table332.putLShort(entry.data);
        }
        compiledTable332 = table332.getBuffer();
        // 340
        InputStream stream340 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_340.json");
        if (stream340 == null) throw new AssertionError("Unable to locate RuntimeID table 340");
        Collection<TableEntry> entries340 = GSON.fromJson(new InputStreamReader(stream340, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntry>>(){}.getType());
        BinaryStream table340 = new BinaryStream();
        table340.putUnsignedVarInt(entries340.size());
        for (TableEntry entry : entries340) {
            legacyToRuntimeId340.put((entry.id << 4) | entry.data, runtimeIdAllocator340.getAndIncrement());
            table340.putString(entry.name);
            table340.putLShort(entry.data);
        }
        compiledTable340 = table340.getBuffer();
        // 354
        InputStream stream354 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_354.json");
        if (stream354 == null) throw new AssertionError("Unable to locate RuntimeID table 354");
        Collection<TableEntry> entries354 = GSON.fromJson(new InputStreamReader(stream354, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntry>>(){}.getType());
        BinaryStream table354 = new BinaryStream();
        table354.putUnsignedVarInt(entries354.size());
        for (TableEntry entry : entries354) {
            legacyToRuntimeId354.put((entry.id << 4) | entry.data, runtimeIdAllocator354.getAndIncrement());
            table354.putString(entry.name);
            table354.putLShort(entry.data);
        }
        compiledTable354 = table354.getBuffer();
        // 361
        InputStream stream361 = Server.class.getClassLoader().getResourceAsStream("runtimeid_table_361.json");
        if (stream361 == null) throw new AssertionError("Unable to locate RuntimeID table 361");
        Collection<TableEntry> entries361 = GSON.fromJson(new InputStreamReader(stream361, StandardCharsets.UTF_8), new TypeToken<Collection<TableEntry>>(){}.getType());
        BinaryStream table361 = new BinaryStream();
        table361.putUnsignedVarInt(entries361.size());
        for (TableEntry entry : entries361) {
            legacyToRuntimeId361.put((entry.id << 4) | entry.data, runtimeIdAllocator361.getAndIncrement());
            table361.putString(entry.name);
            table361.putLShort(entry.data);
            table361.putLShort(entry.id);
        }
        compiledTable361 = table361.getBuffer();
        // 388
        InputStream stream388 = Server.class.getClassLoader().getResourceAsStream("runtime_block_states_388.dat");
        if (stream388 == null) throw new AssertionError("Unable to locate block state nbt 388");
        ListTag<CompoundTag> tag388;
        try {
            compiledTable388 = ByteStreams.toByteArray(stream388);
            //noinspection unchecked
            tag388 = (ListTag<CompoundTag>) NBTIO.readNetwork(new ByteArrayInputStream(compiledTable388));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        for (CompoundTag state : tag388.getAll()) {
            int runtimeId = runtimeIdAllocator388.getAndIncrement();
            if (!state.contains("meta")) continue;
            for (int val : state.getIntArray("meta")) {
                legacyToRuntimeId388.put(state.getShort("id") << 6 | val, runtimeId);
            }
            state.remove("meta");
        }
        // 389
        InputStream stream389 = Server.class.getClassLoader().getResourceAsStream("runtime_block_states_389.dat");
        if (stream389 == null) throw new AssertionError("Unable to locate block state nbt 389");
        ListTag<CompoundTag> tag389;
        try {
            //noinspection unchecked
            tag389 = (ListTag<CompoundTag>) NBTIO.readTag(new BufferedInputStream(new GZIPInputStream(stream389)), ByteOrder.BIG_ENDIAN, false);
        } catch (IOException e) {
            throw new AssertionError("Unable to load block palette 389", e);
        }
        for (CompoundTag state : tag389.getAll()) {
            int runtimeId = runtimeIdAllocator389.getAndIncrement();
            if (!state.contains("meta")) continue;
            for (int val : state.getIntArray("meta")) {
                legacyToRuntimeId389.put(state.getShort("id") << 6 | val, runtimeId);
            }
            state.remove("meta");
        }
        try {
            compiledTable389 = NBTIO.write(tag389, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new AssertionError("Unable to write block palette 389", e);
        }
        // 407
        ListTag<CompoundTag> tag407;
        try (InputStream stream407 = Server.class.getClassLoader().getResourceAsStream("runtime_block_states_407.dat")) {
            if (stream407 == null) {
                throw new AssertionError("Unable to locate block state nbt 407");
            }
            //noinspection unchecked
            tag407 = (ListTag<CompoundTag>) NBTIO.readTag(new BufferedInputStream(new GZIPInputStream(stream407)), ByteOrder.BIG_ENDIAN, false);
        } catch (IOException e) {
            throw new AssertionError("Unable to load block palette 407", e);
        }
        for (CompoundTag state : tag407.getAll()) {
            int id = state.getInt("id");
            int data = state.getShort("data");
            int runtimeId = runtimeIdAllocator407.getAndIncrement();
            int legacyId = id << 6 | data;
            legacyToRuntimeId407.put(legacyId, runtimeId);
            state.remove("data");
        }
        try {
            compiledTable407 = NBTIO.write(tag407, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new AssertionError("Unable to write block palette 407", e);
        }
    }

    @Deprecated
    public static BlockPalette getPaletteByProtocol(int protocol) {
        return getPaletteByProtocol(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode));
    }

    public static BlockPalette getPaletteByProtocol(GameVersion gameVersion) {
        int protocol = gameVersion.getProtocol();

        if (gameVersion.isNetEase()) {
            return getPaletteByProtocolNetEase(protocol);
        }

        if (protocol >= ProtocolInfo.v1_21_90) {
            if (blockPalette818 == null) {
                blockPalette818 = new BlockPalette(GameVersion.V1_21_90);
            }
            return blockPalette818;
        } else if (protocol >= ProtocolInfo.v1_21_80) {
            if (blockPalette800 == null) {
                blockPalette800 = new BlockPalette(GameVersion.V1_21_80);
            }
            return blockPalette800;
        } else if (protocol >= ProtocolInfo.v1_21_70_24) {
            if (blockPalette786 == null) {
                blockPalette786 = new BlockPalette(GameVersion.V1_21_70);
            }
            return blockPalette786;
        } else if (protocol >= ProtocolInfo.v1_21_60) {
            if (blockPalette776 == null) {
                blockPalette776 = new BlockPalette(GameVersion.V1_21_60);
            }
            return blockPalette776;
        } else if (protocol >= ProtocolInfo.v1_21_50_26) {
            if (blockPalette766 == null) {
                blockPalette766 = new BlockPalette(GameVersion.V1_21_50);
            }
            return blockPalette766;
        } else if (protocol >= ProtocolInfo.v1_21_40) {
            if (blockPalette748 == null) {
                blockPalette748 = new BlockPalette(GameVersion.V1_21_40);
            }
            return blockPalette748;
        } else if (protocol >= ProtocolInfo.v1_21_30) {
            if (blockPalette729 == null) {
                blockPalette729 = new BlockPalette(GameVersion.V1_21_30);
            }
            return blockPalette729;
        } else if (protocol >= ProtocolInfo.v1_21_20) {
            if (blockPalette712 == null) {
                blockPalette712 = new BlockPalette(GameVersion.V1_21_20);
            }
            return blockPalette712;
        } else if (protocol >= ProtocolInfo.v1_21_0) {
            if (blockPalette685 == null) {
                blockPalette685 = new BlockPalette(GameVersion.V1_21_0);
            }
            return blockPalette685;
        } else if (protocol >= ProtocolInfo.v1_20_80) {
            if (blockPalette671 == null) {
                blockPalette671 = new BlockPalette(GameVersion.V1_20_80);
            }
            return blockPalette671;
        } else if (protocol >= ProtocolInfo.v1_20_70) {
            if (blockPalette662 == null) {
                blockPalette662 = new BlockPalette(GameVersion.V1_20_70);
            }
            return blockPalette662;
        } else if (protocol >= ProtocolInfo.v1_20_60) {
            if (blockPalette649 == null) {
                blockPalette649 = new BlockPalette(GameVersion.V1_20_60);
            }
            return blockPalette649;
        } else if (protocol >= ProtocolInfo.v1_20_50) {
            if (blockPalette630 == null) {
                blockPalette630 = new BlockPalette(GameVersion.V1_20_50);
            }
            return blockPalette630;
        } else if (protocol >= ProtocolInfo.v1_20_40) {
            if (blockPalette622 == null) {
                blockPalette622 = new BlockPalette(GameVersion.V1_20_40);
            }
            return blockPalette622;
        } else if (protocol >= ProtocolInfo.v1_20_30_24) {
            if (blockPalette618 == null) {
                blockPalette618 = new BlockPalette(GameVersion.V1_20_30);
            }
            return blockPalette618;
        } else if (protocol >= ProtocolInfo.v1_20_10_21) {
            if (blockPalette594 == null) {
                blockPalette594 = new BlockPalette(GameVersion.V1_20_10);
            }
            return blockPalette594;
        } else if (protocol >= ProtocolInfo.v1_20_0_23) {
            if (blockPalette589 == null) {
                blockPalette589 = new BlockPalette(GameVersion.V1_20_0);
            }
            return blockPalette589;
        } else if (protocol >= ProtocolInfo.v1_19_80) {
            if (blockPalette582 == null) {
                blockPalette582 = new BlockPalette(GameVersion.V1_19_80);
            }
            return blockPalette582;
        } else if (protocol >= ProtocolInfo.v1_19_70_24) {
            if (blockPalette575 == null) {
                blockPalette575 = new BlockPalette(GameVersion.V1_19_70);
            }
            return blockPalette575;
        } else if (protocol >= ProtocolInfo.v1_19_60) {
            if (blockPalette567 == null) {
                blockPalette567 = new BlockPalette(GameVersion.V1_19_60);
            }
            return blockPalette567;
        } else if (protocol >= ProtocolInfo.v1_19_50_20) {
            if (blockPalette560 == null) {
                blockPalette560 = new BlockPalette(GameVersion.V1_19_50);
            }
            return blockPalette560;
        } else if (protocol >= ProtocolInfo.v1_19_20) {
            if (blockPalette544 == null) {
                blockPalette544 = new BlockPalette(GameVersion.V1_19_20);
            }
            return blockPalette544;
        } else if (protocol >= ProtocolInfo.v1_19_0_29) {
            if (blockPalette527 == null) {
                blockPalette527 = new BlockPalette(GameVersion.V1_19_0);
            }
            return blockPalette527;
        } else if (protocol >= ProtocolInfo.v1_18_30) {
            if (blockPalette503 == null) {
                blockPalette503 = new BlockPalette(GameVersion.V1_18_30);
            }
            return blockPalette503;
        } else if (protocol >= ProtocolInfo.v1_18_10_26) {
            if (blockPalette486 == null) {
                blockPalette486 = new BlockPalette(GameVersion.V1_18_10);
            }
            return blockPalette486;
        } else if (protocol >= ProtocolInfo.v1_17_40) {
            if (blockPalette471 == null) {
                blockPalette471 = new BlockPalette(GameVersion.V1_17_40);
            }
            return blockPalette471;
        } else if (protocol >= ProtocolInfo.v1_17_30) {
            if (blockPalette465 == null) {
                blockPalette465 = new BlockPalette(GameVersion.V1_17_30);
            }
            return blockPalette465;
        } else if (protocol >= ProtocolInfo.v1_17_10) {
            if (blockPalette448 == null) {
                blockPalette448 = new BlockPalette(GameVersion.V1_17_10);
            }
            return blockPalette448;
        } else if (protocol >= ProtocolInfo.v1_17_0) {
            if (blockPalette440 == null) {
                blockPalette440 = new BlockPalette(GameVersion.V1_17_0);
            }
            return blockPalette440;
        } else if (protocol >= ProtocolInfo.v1_16_210) {
            if (blockPalette428 == null) {
                blockPalette428 = new BlockPalette(GameVersion.V1_16_210);
            }
            return blockPalette428;
        } else if (protocol >= ProtocolInfo.v1_16_100) {
            if (blockPalette419 == null) {
                blockPalette419 = new BlockPalette(GameVersion.V1_16_100);
            }
            return blockPalette419;
        }

        throw new IllegalArgumentException("Tried to get BlockPalette for unsupported protocol version: " + protocol);
    }

    private static BlockPalette getPaletteByProtocolNetEase(int protocol) {
        if (protocol >= GameVersion.V1_21_2_NETEASE.getProtocol()) {
            if (blockPalette_netease_686 == null) {
                blockPalette_netease_686 = new BlockPalette(GameVersion.V1_21_2_NETEASE);
            }
            return blockPalette_netease_686;
        }
        if (protocol >= GameVersion.V1_20_50_NETEASE.getProtocol()) {
            if (blockPalette_netease_630 == null) {
                blockPalette_netease_630 = new BlockPalette(GameVersion.V1_20_50_NETEASE);
            }
            return blockPalette_netease_630;
        }
        throw new IllegalArgumentException("Tried to get BlockPalette for unsupported protocol version: " + protocol + " (NetEase)");
    }

    @Deprecated
    public static int getOrCreateRuntimeId(int protocol, int id, int meta) {
        return getOrCreateRuntimeId(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), id, meta);
    }

    public static int getOrCreateRuntimeId(GameVersion gameVersion, int id, int meta) {
        int protocol = gameVersion.getProtocol();
        if (protocol >= ProtocolInfo.v1_16_100) {
            return getPaletteByProtocol(gameVersion).getRuntimeId(id, meta);
        }

        if (protocol < 223) throw new IllegalArgumentException("Tried to get block runtime id for unsupported protocol version: " + protocol);
        int legacyId = protocol >= 388 ? ((id << 6) | meta) : ((id << 4) | meta);
        int runtimeId;
        switch (protocol) {
            // Versions before this doesn't use runtime IDs
            case 223:
            case 224:
                runtimeId = legacyToRuntimeId223.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId223.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 261:
                runtimeId = legacyToRuntimeId261.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId261.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 274:
                runtimeId = legacyToRuntimeId274.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId274.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 281:
            case 282:
                runtimeId = legacyToRuntimeId282.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId282.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 291:
                runtimeId = legacyToRuntimeId291.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId291.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 313:
                runtimeId = legacyToRuntimeId313.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId313.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 332:
                runtimeId = legacyToRuntimeId332.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId332.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 340:
                runtimeId = legacyToRuntimeId340.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId340.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 354:
                runtimeId = legacyToRuntimeId354.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId354.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 361:
                runtimeId = legacyToRuntimeId361.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId361.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 388:
                runtimeId = legacyToRuntimeId388.get(legacyId);
                if (runtimeId == -1) {
                    runtimeId = legacyToRuntimeId388.get(id << 6);
                    if (runtimeId == -1) runtimeId = legacyToRuntimeId388.get(BlockID.INFO_UPDATE << 6);
                }
                return runtimeId;
            case 389:
            case 390:
                runtimeId = legacyToRuntimeId389.get(legacyId);
                if (runtimeId == -1) {
                    runtimeId = legacyToRuntimeId389.get(id << 6);
                    if (runtimeId == -1) runtimeId = legacyToRuntimeId389.get(BlockID.INFO_UPDATE << 6);
                }
                return runtimeId;
            case 407:
            case 408:
            case 409:
            case 410:
            case 411:
                runtimeId = legacyToRuntimeId407.get(legacyId);
                if (runtimeId == -1) {
                    runtimeId = legacyToRuntimeId407.get(id << 6);
                    if (runtimeId == -1) runtimeId = legacyToRuntimeId407.get(BlockID.INFO_UPDATE << 6);
                }
                return runtimeId;
            default:
                throw new IllegalArgumentException("Tried to get legacyToRuntimeIdMap for unsupported protocol version: " + protocol);
        }
    }

    public static byte[] getCompiledTable(int protocol) {
        switch (protocol) {
            // Versions before this doesn't send compiled table in StartGamePacket
            case 281:
            case 282:
                return compiledTable282;
            case 291:
                return compiledTable291;
            case 313:
                return compiledTable313;
            case 332:
                return compiledTable332;
            case 340:
                return compiledTable340;
            case 354:
                return compiledTable354;
            case 361:
                return compiledTable361;
            case 388:
                return compiledTable388;
            case 389:
            case 390:
                return compiledTable389;
            case 407:
            case 408:
            case 409:
            case 410:
            case 411:
                return compiledTable407;
            default: // Unused since 1.16.100 (419)
                throw new IllegalArgumentException("Tried to get compiled block runtime id table for unsupported protocol version: " + protocol);
        }
    }

    @Deprecated
    public static int getOrCreateRuntimeId(int protocol, int legacyId) throws NoSuchElementException {
        return getOrCreateRuntimeId(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), legacyId);
    }

    public static int getOrCreateRuntimeId(GameVersion gameVersion, int legacyId) throws NoSuchElementException {
        int protocol = gameVersion.getProtocol();
        if (protocol < 223) throw new IllegalArgumentException("Tried to get block runtime id for unsupported protocol version: " + protocol);
        int runtimeId;
        switch (protocol) {
            case 223:
            case 224:
                runtimeId = legacyToRuntimeId223.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId223.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 261:
                runtimeId = legacyToRuntimeId261.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId261.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 274:
                runtimeId = legacyToRuntimeId274.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId274.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 281:
            case 282:
                runtimeId = legacyToRuntimeId282.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId282.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 291:
                runtimeId = legacyToRuntimeId291.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId291.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 313:
                runtimeId = legacyToRuntimeId313.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId313.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 332:
                runtimeId = legacyToRuntimeId332.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId332.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 340:
                runtimeId = legacyToRuntimeId340.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId340.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 354:
                runtimeId = legacyToRuntimeId354.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId354.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            case 361:
                runtimeId = legacyToRuntimeId361.get(legacyId);
                if (runtimeId == -1) runtimeId = legacyToRuntimeId361.get(BlockID.INFO_UPDATE << 4);
                return runtimeId;
            default: // 388+
                return getOrCreateRuntimeId(gameVersion, legacyId >> Block.DATA_BITS, legacyId & Block.DATA_MASK);
        }
    }

    @Deprecated
    public static int getLegacyFullId(int protocolId, int runtimeId) {
        return getLegacyFullId(GameVersion.byProtocol(protocolId, Server.getInstance().onlyNetEaseMode), runtimeId);
    }

    public static int getLegacyFullId(GameVersion protocolId, int runtimeId) {
        BlockPalette blockPalette = getPaletteByProtocol(protocolId);
        if (blockPalette != null) {
            return blockPalette.getLegacyFullId(runtimeId);
        }
        throw new IllegalArgumentException("Tried to get legacyFullId for unsupported protocol version: " + protocolId);
    }

    @Deprecated
    public static int getLegacyFullId(int protocolId, CompoundTag compoundTag) {
        BlockPalette blockPalette = getPaletteByProtocol(protocolId);
        if (blockPalette != null) {
            return blockPalette.getLegacyFullId(compoundTag);
        }
        throw new IllegalArgumentException("Tried to get legacyFullId for unsupported protocol version: " + protocolId);
    }

    public static int getLegacyFullId(GameVersion protocolId, CompoundTag compoundTag) {
        BlockPalette blockPalette = getPaletteByProtocol(protocolId);
        if (blockPalette != null) {
            return blockPalette.getLegacyFullId(compoundTag);
        }
        throw new IllegalArgumentException("Tried to get legacyFullId for unsupported protocol version: " + protocolId);
    }

    public static int getOrCreateRuntimeId(int legacyId) throws NoSuchElementException {
        Server.mvw("GlobalBlockPalette#getOrCreateRuntimeId(int)");
        return getOrCreateRuntimeId(GameVersion.getLastVersion(), legacyId >> 4, legacyId & 0xf);
    }

    public static int getLegacyFullId(int runtimeId) {
        Server.mvw("GlobalBlockPalette#getLegacyFullId(int)");
        return getLegacyFullId(GameVersion.getLastVersion(), runtimeId);
    }

    @SuppressWarnings("unused")
    private static class TableEntry {
        private int id;
        private int data;
        private String name;
    }

    @SuppressWarnings("unused")
    private static class TableEntryOld {
        private int id;
        private int data;
        private int runtimeID;
        private String name;
    }
}
