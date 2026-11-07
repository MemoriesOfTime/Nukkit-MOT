package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encodes every packet twice and requires identical bytes. encode() implementations that
 * miss the leading reset() append on the second call, so reused packet instances corrupt
 * the stream — a bug class invisible to single-shot tests.
 */
public class DoubleEncodeIdempotenceTest {

    private static final int LATEST_PROTOCOL = 2193;

    /**
     * 已知无法用默认字段编码的包数量。这个数字是回归哨兵：如果某个原本可编码的包
     * 退化为抛异常，exercised 会减少、skipped 会增加，超过基线即失败。
     */
    private static final int BASELINE_SKIPPED = 75;

    @Test
    void encodeTwiceProducesIdenticalBytes() {
        MockServer.init();

        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int exercised = 0;

        for (Class<?> cls : scanDataPacketSubclasses()) {
            String name = cls.getSimpleName();
            if (name.equals("BatchPacket") || name.endsWith("_v113")) {
                continue;
            }
            try {
                DataPacket packet = (DataPacket) cls.getDeclaredConstructor().newInstance();
                packet.protocol = LATEST_PROTOCOL;
                packet.gameVersion = cn.nukkit.GameVersion.byProtocol(LATEST_PROTOCOL, false);
                try {
                    packet.encode();
                } catch (Throwable ignored) {
                    skipped.add(name); // needs populated fields — not exercisable with defaults
                    continue;
                }
                byte[] first = packet.getBuffer();
                packet.encode();
                byte[] second = packet.getBuffer();
                if (!Arrays.equals(first, second)) {
                    failures.add(name);
                }
                exercised++;
            } catch (Throwable ignored) {
                skipped.add(name); // not instantiable — record instead of silently dropping
            }
        }

        System.out.println("double-encode skipped: " + skipped.size() + " (baseline " + BASELINE_SKIPPED + ")");
        assertTrue(exercised >= 50,
                "only " + exercised + " packets exercised; scan is broken or protocol moved");
        assertTrue(skipped.size() <= BASELINE_SKIPPED,
                "more packets became non-exercisable (" + skipped.size() + " > " + BASELINE_SKIPPED
                        + "): " + skipped);
        assertTrue(failures.isEmpty(),
                "encode() not idempotent — missing reset() at encode start: " + failures
                        + " (exercised " + exercised + " packets)");
    }

    private static List<Class<?>> scanDataPacketSubclasses() {
        List<Class<?>> result = new ArrayList<>();
        String path = "cn/nukkit/network/protocol".replace('.', '/');
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if (!"file".equals(resource.getProtocol())) {
                    continue;
                }
                scanDirectory(new File(resource.toURI()), "cn.nukkit.network.protocol", result);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan packet classes", e);
        }
        return result;
    }

    private static void scanDirectory(File dir, String packageName, List<Class<?>> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> cls = Class.forName(className, false,
                            Thread.currentThread().getContextClassLoader());
                    if (DataPacket.class.isAssignableFrom(cls)
                            && !Modifier.isAbstract(cls.getModifiers())) {
                        result.add(cls);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    // skip
                }
            }
        }
    }
}
