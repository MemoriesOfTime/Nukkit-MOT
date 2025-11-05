package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.entity.mob.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class contains miscellaneous stuff used in other parts of the program.
 *
 * @author MagicDroidX
 * Nukkit Project
 */
public class Utils {

    /**
     * A SplittableRandom you can use without having to create a new object every time.
     */
    public static final SplittableRandom random = new SplittableRandom();
    /**
     * A NukkitRandom you can use without having to create a new object every time.
     */
    public static final NukkitRandom nukkitRandom = new NukkitRandom();
    /**
     * An empty damage array used when mobs have no attack damage.
     */
    @Deprecated
    public static final int[] emptyDamageArray = new int[] { 0, 0, 0, 0 };
    /**
     * List of network ids of monsters. Currently used for example to check which entities will make players unable to sleep when nearby the bed.
     */
    public static final IntSet monstersList = new IntOpenHashSet(Arrays.asList(EntityBlaze.NETWORK_ID, EntityCaveSpider.NETWORK_ID, EntityCreeper.NETWORK_ID, EntityDrowned.NETWORK_ID, EntityElderGuardian.NETWORK_ID, EntityEnderman.NETWORK_ID, EntityEndermite.NETWORK_ID, EntityEvoker.NETWORK_ID, EntityGhast.NETWORK_ID, EntityGuardian.NETWORK_ID, EntityHoglin.NETWORK_ID, EntityHusk.NETWORK_ID, EntityPiglinBrute.NETWORK_ID, EntityPillager.NETWORK_ID, EntityRavager.NETWORK_ID, EntityShulker.NETWORK_ID, EntitySilverfish.NETWORK_ID, EntitySkeleton.NETWORK_ID, EntitySlime.NETWORK_ID, EntitySpider.NETWORK_ID, EntityStray.NETWORK_ID, EntityVex.NETWORK_ID, EntityVindicator.NETWORK_ID, EntityWitch.NETWORK_ID, EntityWither.NETWORK_ID, EntityWitherSkeleton.NETWORK_ID, EntityZoglin.NETWORK_ID, EntityZombie.NETWORK_ID, EntityZombiePigman.NETWORK_ID, EntityZombieVillager.NETWORK_ID, EntityZombieVillagerV2.NETWORK_ID));
    /**
     * List of biomes where water can freeze
     */
    public static final IntSet freezingBiomes = new IntOpenHashSet(Arrays.asList(10, 11, 12, 26, 30, 31, 140, 158));

    /**
     * 检查物品或方块是否已在nk中实现
     * Check if the item or block id is implemented in Nukkit.
     *
     * @param id 物品或方块id
     * @return 是否已实现
     */
    public static boolean hasItemOrBlock(Object id) {
        if (id instanceof Number number) {
            return hasItemOrBlock(number.intValue());
        } else if (id instanceof String string) {
            return hasItemOrBlock(string);
        }
        throw new IllegalArgumentException("id must be a number or a string");
    }

    public static boolean hasItemOrBlock(String id) {
        return Item.NAMESPACED_ID_ITEM.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public static boolean hasItemOrBlock(int id) {
        if (id < 0) {
            int blockId = 255 - id;
            if (blockId > CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
                return CustomBlockManager.get().getBlock(blockId) != null;
            }
            return blockId < Block.MAX_BLOCK_ID && Block.list[blockId] != null;
        } else {
            return id < Item.list.length && Item.list[id] != null;
        }
    }

    public static int[] getEmptyDamageArray() {
        return new int[] { 0, 0, 0, 0 };
    }
    
    public static void writeFile(String fileName, String content) throws IOException {
        writeFile(fileName, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    public static void writeFile(String fileName, InputStream content) throws IOException {
        writeFile(new File(fileName), content);
    }

    public static void writeFile(File file, String content) throws IOException {
        writeFile(file, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    public static void writeFile(File file, InputStream content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (!file.exists()) {
            file.createNewFile();
        }
        try (FileOutputStream stream = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = content.read(buffer)) != -1) {
                stream.write(buffer, 0, length);
            }
        }
        content.close();
    }

    public static String readFile(File file) throws IOException {
        if (!file.exists() || file.isDirectory()) {
            throw new FileNotFoundException();
        }
        return readFile(new FileInputStream(file));
    }

    public static String readFile(String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists() || file.isDirectory()) {
            throw new FileNotFoundException();
        }
        return readFile(new FileInputStream(file));
    }

    public static String readFile(InputStream inputStream) throws IOException {
        return readFile(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    private static String readFile(Reader reader) throws IOException {
        try (BufferedReader br = new BufferedReader(reader)) {
            String temp;
            StringBuilder stringBuilder = new StringBuilder();
            temp = br.readLine();
            while (temp != null) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append('\n');
                }
                stringBuilder.append(temp);
                temp = br.readLine();
            }
            return stringBuilder.toString();
        }
    }

    public static void copyFile(File from, File to) throws IOException {
        if (!from.exists()) {
            throw new FileNotFoundException();
        }
        if (from.isDirectory() || to.isDirectory()) {
            throw new FileNotFoundException();
        }
        FileInputStream fi = null;
        FileChannel in = null;
        FileOutputStream fo = null;
        FileChannel out = null;
        try {
            if (!to.exists()) {
                to.createNewFile();
            }
            fi = new FileInputStream(from);
            in = fi.getChannel();
            fo = new FileOutputStream(to);
            out = fo.getChannel();
            in.transferTo(0, in.size(), out);
        } finally {
            if (fi != null) fi.close();
            if (in != null) in.close();
            if (fo != null) fo.close();
            if (out != null) out.close();
        }
    }

    public static String getAllThreadDumps() {
        ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
        StringBuilder builder = new StringBuilder();
        for (ThreadInfo info : threads) {
            builder.append('\n').append(info);
        }
        return builder.toString();
    }


    public static String getExceptionMessage(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
            e.printStackTrace(printWriter);
            printWriter.flush();
        }
        return stringWriter.toString();
    }

    public static UUID dataToUUID(String... params) {
        StringBuilder builder = new StringBuilder();
        for (String param : params) {
            builder.append(param);
        }
        return UUID.nameUUIDFromBytes(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static UUID dataToUUID(byte[]... params) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (byte[] param : params) {
            try {
                stream.write(param);
            } catch (IOException e) {
                break;
            }
        }
        return UUID.nameUUIDFromBytes(stream.toByteArray());
    }

    public static String rtrim(String s, char character) {
        int i = s.length() - 1;
        while (i >= 0 && (s.charAt(i)) == character) {
            i--;
        }
        return s.substring(0, i + 1);
    }

    public static boolean isByteArrayEmpty(final byte[] array) {
        for (byte b : array) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public static long toRGB(byte r, byte g, byte b, byte a) {
        long result = (int) r & 0xff;
        result |= ((int) g & 0xff) << 8;
        result |= ((int) b & 0xff) << 16;
        result |= ((int) a & 0xff) << 24;
        return result & 0xFFFFFFFFL;
    }

    public static long toABGR(int argb) {
        long result = argb & 0xFF00FF00L;
        result |= (argb << 16) & 0x00FF0000L; // B to R
        result |= (argb >>> 16) & 0xFFL; // R to B
        return result & 0xFFFFFFFFL;
    }

    @Nullable
    public static Object[][] splitArray(Object[] arrayToSplit, @Nonnegative int chunkSize) {
        if (chunkSize <= 0) {
            return null;
        }

        if (arrayToSplit.length <= chunkSize) {
            return new Object[][] { arrayToSplit };
        }

        int rest = arrayToSplit.length % chunkSize;
        int chunks = arrayToSplit.length / chunkSize + (rest > 0 ? 1 : 0);

        Object[][] arrays = new Object[chunks][];
        for (int i = 0; i < (rest > 0 ? chunks - 1 : chunks); i++) {
            arrays[i] = Arrays.copyOfRange(arrayToSplit, i * chunkSize, i * chunkSize + chunkSize);
        }
        if (rest > 0) {
            arrays[chunks - 1] = Arrays.copyOfRange(arrayToSplit, (chunks - 1) * chunkSize, (chunks - 1) * chunkSize + rest);
        }
        return arrays;
    }

    public static <T> void reverseArray(T[] data) {
        reverseArray(data, false);
    }

    public static <T> T[] reverseArray(T[] array, boolean copy) {
        T[] data = array;

        if (copy) {
            data = Arrays.copyOf(array, array.length);
        }

        for (int left = 0, right = data.length - 1; left < right; left++, right--) {
            T temp = data[left];
            data[left] = data[right];
            data[right] = temp;
        }

        return data;
    }

    public static <T> T[][] clone2dArray(T[][] array) {
        T[][] newArray = Arrays.copyOf(array, array.length);

        for (int i = 0; i < array.length; i++) {
            newArray[i] = Arrays.copyOf(array[i], array[i].length);
        }

        return newArray;
    }

    public static <T,U,V> Map<U,V> getOrCreate(Map<T, Map<U, V>> map, T key) {
        Map<U, V> existing = map.get(key);
        if (existing == null) {
            ConcurrentHashMap<U, V> toPut = new ConcurrentHashMap<>();
            existing = map.putIfAbsent(key, toPut);
            if (existing == null) {
                existing = toPut;
            }
        }
        return existing;
    }

    public static <T, U, V extends U> U getOrCreate(Map<T, U> map, Class<V> clazz, T key) {
        U existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        try {
            U toPut = clazz.getDeclaredConstructor().newInstance();
            existing = map.putIfAbsent(key, toPut);
            return Objects.requireNonNullElse(existing, toPut);
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static int toInt(Object number) {
        if (number instanceof Integer) {
            return (Integer) number;
        } else if (number instanceof String) {
            return new BigDecimal(number.toString()).intValue();
        }

        return (int) Math.round((double) number);
    }

    public static double toDouble(Object number) {
        if (number instanceof Double doubleNumber) {
            return doubleNumber;
        } else if (number instanceof String) {
            return new BigDecimal(number.toString()).doubleValue();
        }
        return (double) number;
    }

    public static byte[] parseHexBinary(String s) {
        final int len = s.length();

        if (len % 2 != 0)
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);

        byte[] out = new byte[(len >> 1)];

        for (int i = 0; i < len; i += 2) {
            int h = hexToBin(s.charAt(i));
            int l = hexToBin(s.charAt(i + 1));
            if (h == -1 || l == -1)
                throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);

            out[(i >> 1)] = (byte)((h << 4) + l);
        }

        return out;
    }

    private static int hexToBin( char ch ) {
        if ('0' <= ch && ch <= '9')    return ch - '0';
        if ('A' <= ch && ch <= 'F')    return ch - 'A' + 10;
        if ('a' <= ch && ch <= 'f')    return ch - 'a' + 10;
        return -1;
    }

    /**
     * Get a random int
     *
     * @param min minimum value
     * @param max maximum value
     * @return random int between min and max
     */
    public static int rand(int min, int max) {
        if (min == max) {
            return max;
        }
        return random.nextInt(max + 1 - min) + min;
    }

    /**
     * Get a random double
     *
     * @param min minimum value
     * @param max maximum value
     * @return random double between min and max
     */
    public static double rand(double min, double max) {
        if (min == max) {
            return max;
        }
        return min + random.nextDouble() * (max-min);
    }

    public static float rand(float min, float max) {
        if (min == max) {
            return max;
        }
        return min + (float) Math.random() * (max-min);
    }

    /**
     * Get a random boolean
     *
     * @return random boolean
     */
    public static boolean rand() {
        return random.nextBoolean();
    }

    public static int dynamic(int value) {
        return value;
    }

    public static <T> T dynamic(T value) {
        return value;
    }

    /**
     * Get game version string by protocol version.
     * For internal usage!
     *
     * @param protocol protocol version
     * @return game version string
     */
    public static String getVersionByProtocol(int protocol) {
        return switch (protocol) {
            case ProtocolInfo.v1_1_0 -> "1.1.0";
            case ProtocolInfo.v1_2_0 -> "1.2.0";
            case ProtocolInfo.v1_2_5_11, ProtocolInfo.v1_2_5 -> "1.2.5";
            case ProtocolInfo.v1_2_6 -> "1.2.6";
            case ProtocolInfo.v1_2_7 -> "1.2.7";
            case ProtocolInfo.v1_2_10 -> "1.2.10";
            case ProtocolInfo.v1_2_13, ProtocolInfo.v1_2_13_11 -> "1.2.13";
            case ProtocolInfo.v1_4_0 -> "1.4.0";
            case ProtocolInfo.v1_5_0 -> "1.5.0";
            case ProtocolInfo.v1_6_0_5, ProtocolInfo.v1_6_0 -> "1.6.0";
            case ProtocolInfo.v1_7_0 -> "1.7.0";
            case ProtocolInfo.v1_8_0 -> "1.8.0";
            case ProtocolInfo.v1_9_0 -> "1.9.0";
            case ProtocolInfo.v1_10_0 -> "1.10.0";
            case ProtocolInfo.v1_11_0 -> "1.11.0";
            case ProtocolInfo.v1_12_0 -> "1.12.0";
            case ProtocolInfo.v1_13_0 -> "1.13.0";
            case ProtocolInfo.v1_14_0 -> "1.14.0";
            case ProtocolInfo.v1_14_60 -> "1.14.60";
            case ProtocolInfo.v1_16_0 -> "1.16.0";
            case ProtocolInfo.v1_16_20 -> "1.16.20";
            case ProtocolInfo.v1_16_100_0, ProtocolInfo.v1_16_100_51, ProtocolInfo.v1_16_100_52, ProtocolInfo.v1_16_100 ->
                    "1.16.100";
            case ProtocolInfo.v1_16_200_51, ProtocolInfo.v1_16_200 -> "1.16.200";
            case ProtocolInfo.v1_16_210_50, ProtocolInfo.v1_16_210_53, ProtocolInfo.v1_16_210 -> "1.16.210";
            case ProtocolInfo.v1_16_220 -> "1.16.220";
            case ProtocolInfo.v1_16_230_50, ProtocolInfo.v1_16_230, ProtocolInfo.v1_16_230_54 -> "1.16.230";
            case ProtocolInfo.v1_17_0 -> "1.17.0";
            case ProtocolInfo.v1_17_10 -> "1.17.10";
            case ProtocolInfo.v1_17_20_20 -> "1.17.20";
            case ProtocolInfo.v1_17_30 -> "1.17.30";
            case ProtocolInfo.v1_17_40 -> "1.17.40";
            case ProtocolInfo.v1_18_0 -> "1.18.0";
            case ProtocolInfo.v1_18_10_26, ProtocolInfo.v1_18_10 -> "1.18.10";
            case ProtocolInfo.v1_18_30 -> "1.18.30";
            case ProtocolInfo.v1_19_0_29, ProtocolInfo.v1_19_0_31, ProtocolInfo.v1_19_0 -> "1.19.0";
            case ProtocolInfo.v1_19_10 -> "1.19.10";
            case ProtocolInfo.v1_19_20 -> "1.19.20";
            case ProtocolInfo.v1_19_21 -> "1.19.21";
            case ProtocolInfo.v1_19_30_23, ProtocolInfo.v1_19_30 -> "1.19.30";
            case ProtocolInfo.v1_19_40 -> "1.19.40";
            case ProtocolInfo.v1_19_50_20, ProtocolInfo.v1_19_50 -> "1.19.50";
            case ProtocolInfo.v1_19_60 -> "1.19.60";
            case ProtocolInfo.v1_19_63 -> "1.19.63";
            case ProtocolInfo.v1_19_70_24, ProtocolInfo.v1_19_70 -> "1.19.70";
            case ProtocolInfo.v1_19_80 -> "1.19.80";
            case ProtocolInfo.v1_20_0_23, ProtocolInfo.v1_20_0 -> "1.20.0";
            case ProtocolInfo.v1_20_10_21, ProtocolInfo.v1_20_10 -> "1.20.10";
            case ProtocolInfo.v1_20_30_24, ProtocolInfo.v1_20_30 -> "1.20.30";
            case ProtocolInfo.v1_20_40 -> "1.20.40";
            case ProtocolInfo.v1_20_50 -> "1.20.50";
            case ProtocolInfo.v1_20_60 -> "1.20.60";
            case ProtocolInfo.v1_20_70 -> "1.20.70";
            case ProtocolInfo.v1_20_80 -> "1.20.80";
            case ProtocolInfo.v1_21_0 -> "1.21.0";
            case ProtocolInfo.v1_21_2 -> "1.21.2";
            case ProtocolInfo.v1_21_20 -> "1.21.20";
            case ProtocolInfo.v1_21_30 -> "1.21.30";
            case ProtocolInfo.v1_21_40 -> "1.21.40";
            case ProtocolInfo.v1_21_50_26, ProtocolInfo.v1_21_50 -> "1.21.50";
            case ProtocolInfo.v1_21_60 -> "1.21.60";
            case ProtocolInfo.v1_21_70_24, ProtocolInfo.v1_21_70 -> "1.21.70";
            case ProtocolInfo.v1_21_80 -> "1.21.80";
            case ProtocolInfo.v1_21_90 -> "1.21.90";
            case ProtocolInfo.v1_21_93 -> "1.21.93";
            case ProtocolInfo.v1_21_100 -> "1.21.100";
            case ProtocolInfo.v1_21_110_26, ProtocolInfo.v1_21_110 -> "1.21.110";
            case ProtocolInfo.v1_21_120 -> "1.21.120";
            //TODO Multiversion 添加新版本支持时修改这里
            default -> throw new IllegalStateException("Invalid protocol: " + protocol);
        };
    }

    /**
     * Get player's operating system/device name from login chain data.
     * NOTICE: It's possible to spoof this.
     *
     * @param player player
     * @return operating system/device name
     */
    public static String getOS(Player player) {
        switch(player.getLoginChainData().getDeviceOS()) {
            case 1:
                return "Android";
            case 2:
                return "iOS";
            case 3:
                return "macOS";
            case 4:
                return "Fire";
            case 5:
                return "Gear VR";
            case 6:
                return "HoloLens";
            case 7:
                return "Windows";
            case 8:
                return "Windows x86";
            case 9:
                return "Dedicated";
            case 10:
                return "tvOS";
            case 11:
                return "PlayStation";
            case 12:
                return "Switch";
            case 13:
                return "Xbox";
            case 14:
                return "Windows Phone";
            case 15:
                return "Linux";
            default:
                return "Unknown";
        }
    }

    public static <T> T sumObjectsAndGet(Class<? extends T> clazz1, Class<? extends T> clazz2) {
        try {
            for (Field field : clazz1.getDeclaredFields()) {
                field.setAccessible(true);
                Object value1 = field.get(clazz1);
                Object value2 = field.get(clazz2);
                if (value1 instanceof Integer v1 && value2 instanceof Integer v2) {
                    var sum = v1 + v2;
                    field.set(clazz1, sum);
                }
                if (value1 instanceof Long v1 && value2 instanceof Long v2) {
                    var sum = v1 + v2;
                    field.set(clazz1, sum);
                }
                if (value1 instanceof Double v1 && value2 instanceof Double v2) {
                    var sum = BigDecimal.valueOf(v1).add(BigDecimal.valueOf(v2));
                    field.set(clazz1, sum.doubleValue());
                }
                if (value1 instanceof Float v1 && value2 instanceof Float v2) {
                    var sum = BigDecimal.valueOf(v1).add(BigDecimal.valueOf(v2));
                    field.set(clazz1, sum.floatValue());
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return (T) clazz1;
    }

    public static byte[] convertByteBuf2Array(ByteBuf buf) {
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);
        return payload;
    }

    /**
     * @see #fastSplit(String, String, int)
     */

    public static List<String> fastSplit(String delimiter, String str) {
        return fastSplit(delimiter, str, Integer.MAX_VALUE);
    }

    /**
     * 在短字符串上(通常只有一个分割)处理比{@link String#split(String)}快
     * <p>
     * Processing on short strings(There is usually only one split) is faster than {@link String#split(String)}
     *
     * @param delimiter the delimiter
     * @param str       the str
     * @param limit     the limit
     * @return the list
     */

    public static List<String> fastSplit(String delimiter, String str, int limit) {
        var tmp = str;
        var results = new ArrayList<String>();
        var count = 1;
        while (true) {
            int j = tmp.indexOf(delimiter);
            if (j < 0) {
                results.add(tmp);
                break;
            }
            results.add(tmp.substring(0, j));
            count++;
            tmp = tmp.substring(j + 1);
            if (count == limit || tmp.isEmpty()) {
                results.add(tmp);
                break;
            }
        }
        return results;
    }

    public static Block[] getLevelBlocks(Level level, AxisAlignedBB bb) {
        int minX = NukkitMath.floorDouble(Math.min(bb.getMinX(), bb.getMaxX()));
        int minY = NukkitMath.floorDouble(Math.min(bb.getMinY(), bb.getMaxY()));
        int minZ = NukkitMath.floorDouble(Math.min(bb.getMinZ(), bb.getMaxZ()));
        int maxX = NukkitMath.floorDouble(Math.max(bb.getMinX(), bb.getMaxX()));
        int maxY = NukkitMath.floorDouble(Math.max(bb.getMinY(), bb.getMaxY()));
        int maxZ = NukkitMath.floorDouble(Math.max(bb.getMinZ(), bb.getMaxZ()));

        List<Block> blocks = new ArrayList<>();
        Vector3 vec = new Vector3();

        for (int z = minZ; z <= maxZ; ++z) {
            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    blocks.add(level.getBlock(vec.setComponents(x, y, z), false));
                }
            }
        }

        return blocks.toArray(Block.EMPTY_ARRAY);
    }

    public static JsonElement loadJsonResource(String file) {
        try {
            InputStream stream = Server.class.getClassLoader().getResourceAsStream(file);
            if (stream == null) {
                throw new AssertionError("Unable to load " + file);
            }

            JsonElement element = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            stream.close();
            return element;
        } catch (Exception e) {
            throw new RuntimeException("Unable to load " + file, e);
        }
    }

    public static ObjectSet<GameVersion> intSet2GameVersionSet(IntSet protocols, boolean isNetEase) {
        ObjectSet<GameVersion> versions = new ObjectOpenHashSet<>();
        for (int protocol : protocols) {
            versions.add(GameVersion.byProtocol(protocol, isNetEase));
        }
        return versions;
    }
}
