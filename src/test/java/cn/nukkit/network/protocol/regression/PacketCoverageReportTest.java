package cn.nukkit.network.protocol.regression;

import cn.nukkit.MockServer;
import cn.nukkit.network.protocol.DataPacket;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Generates a coverage report showing which DataPacket subclasses
 * have cross-decode regression tests and which do not.
 * <p>
 * Automatically detects tested packets from {@code @ParameterizedTest} annotations
 * and testable packets from CB Protocol codec registrations. No manual maintenance required.
 * <p>
 * This test always passes — it is purely informational.
 */
public class PacketCoverageReportTest {

    /** Regex to extract packet class name (word ending with "Packet") from annotation name. */
    private static final Pattern PACKET_NAME_PATTERN = Pattern.compile("(\\w+Packet)");

    /**
     * Packet classes excluded from coverage tracking.
     * These are internal/structural packets that don't need cross-decode tests.
     */
    private static final Set<String> EXCLUDED_PACKETS = Set.of(
            "BatchPacket",       // Internal compression wrapper, not a game packet
            "DataPacket_v113"    // Abstract base for legacy v113 packets
    );

    @Test
    void generateCoverageReport() {
        MockServer.init();

        // 1. Scan all Nukkit DataPacket subclasses
        List<Class<?>> allPacketClasses = scanDataPacketSubclasses();
        assertFalse(allPacketClasses.isEmpty(), "Failed to scan DataPacket subclasses");

        Set<String> standardPacketNames = new TreeSet<>();
        List<String> legacyV113Packets = new ArrayList<>();

        for (Class<?> cls : allPacketClasses) {
            String name = cls.getSimpleName();
            if (EXCLUDED_PACKETS.contains(name)) {
                continue;
            }
            if (name.endsWith("V113")) {
                legacyV113Packets.add(name);
            } else {
                standardPacketNames.add(name);
            }
        }
        Collections.sort(legacyV113Packets);

        // 2. Scan CB codec for testable packets (those with a CB counterpart)
        Set<String> cbPacketNames = scanCbCodecPacketNames();
        Set<String> testablePackets = new TreeSet<>();
        for (String nukkitName : standardPacketNames) {
            if (cbPacketNames.contains(nukkitName)) {
                testablePackets.add(nukkitName);
            }
        }

        // 3. Auto-detect tested packets from @ParameterizedTest annotations
        Set<String> testedPackets = scanTestedPacketsFromAnnotations(standardPacketNames);

        // 4. Detect encode-not-applicable packets (empty encode or throws exception)
        Map<String, Class<?>> classLookup = new HashMap<>();
        for (Class<?> cls : allPacketClasses) {
            classLookup.put(cls.getSimpleName(), cls);
        }
        Set<String> encodeNotApplicable = detectEncodeNotApplicable(testablePackets, classLookup);

        // 5. Classify
        List<String> tested = new ArrayList<>();
        List<String> testableUntested = new ArrayList<>();
        List<String> notTestable = new ArrayList<>();
        List<String> encodeNA = new ArrayList<>(encodeNotApplicable);
        Collections.sort(encodeNA);

        // Effective testable = testable minus encode-not-applicable
        Set<String> effectiveTestable = new TreeSet<>(testablePackets);
        effectiveTestable.removeAll(encodeNotApplicable);

        for (String name : standardPacketNames) {
            if (testedPackets.contains(name)) {
                tested.add(name);
            } else if (encodeNotApplicable.contains(name)) {
                // already in encodeNA list
            } else if (testablePackets.contains(name)) {
                testableUntested.add(name);
            } else {
                notTestable.add(name);
            }
        }

        // 6. Build report
        int total = standardPacketNames.size();
        int testedCount = tested.size();
        int effectiveTestableCount = effectiveTestable.size();
        int rawTestableCount = testablePackets.size();
        double overallPct = total == 0 ? 0 : (testedCount * 100.0 / total);
        double testablePct = effectiveTestableCount == 0 ? 0 : (testedCount * 100.0 / effectiveTestableCount);

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Packet Regression Test Coverage Report ===\n");
        sb.append(String.format("Overall:  %d / %d (%.1f%%)\n", testedCount, total, overallPct));
        sb.append(String.format("Testable: %d / %d (%.1f%%)  [packets with CB counterpart and functional encode()]\n",
                testedCount, effectiveTestableCount, testablePct));
        sb.append(String.format("  (Raw CB counterpart count: %d, encode-not-applicable: %d)\n",
                rawTestableCount, encodeNotApplicable.size()));

        sb.append(String.format("\nTESTED (%d):\n", testedCount));
        for (String name : tested) {
            sb.append("  ").append(name).append('\n');
        }

        sb.append(String.format("\nTESTABLE BUT UNTESTED (%d):\n", testableUntested.size()));
        for (String name : testableUntested) {
            sb.append("  ").append(name).append('\n');
        }

        if (!encodeNA.isEmpty()) {
            sb.append(String.format("\nENCODE NOT APPLICABLE (%d):\n", encodeNA.size()));
            for (String name : encodeNA) {
                sb.append("  ").append(name).append('\n');
            }
        }

        sb.append(String.format("\nNO CB COUNTERPART (%d):\n", notTestable.size()));
        for (String name : notTestable) {
            sb.append("  ").append(name).append('\n');
        }

        if (!legacyV113Packets.isEmpty()) {
            sb.append(String.format("\nLEGACY v113 PACKETS (excluded, %d):\n", legacyV113Packets.size()));
            for (String name : legacyV113Packets) {
                sb.append("  ").append(name).append('\n');
            }
        }

        sb.append("=== End of Report ===\n");

        System.out.println(sb);
    }

    /**
     * Detects packets whose encode() produces no body (empty or throws exception).
     * These are typically client-to-server packets that the server never encodes.
     */
    private static Set<String> detectEncodeNotApplicable(Set<String> testableNames, Map<String, Class<?>> classLookup) {
        Set<String> result = new HashSet<>();
        int latestProtocol = ProtocolCodecMapping.getSupportedVersions().stream()
                .mapToInt(Integer::intValue).max().orElse(898);

        for (String name : testableNames) {
            Class<?> cls = classLookup.get(name);
            if (cls == null || Modifier.isAbstract(cls.getModifiers())) {
                continue;
            }
            try {
                DataPacket packet = (DataPacket) cls.getDeclaredConstructor().newInstance();
                packet.protocol = latestProtocol;
                try {
                    packet.encode();
                } catch (UnsupportedOperationException e) {
                    result.add(name);
                    continue;
                } catch (Exception ignored) {
                    // encode() may fail due to uninitialized fields — this is NOT the same as "empty encode"
                    continue;
                }
                // Check if encode() produced any body bytes beyond the packet header
                byte[] buffer = packet.getBuffer();
                if (buffer == null || buffer.length == 0) {
                    result.add(name);
                    continue;
                }
                // Measure header size: reset() writes packet ID as unsigned VarInt (for protocol > 274)
                DataPacket headerOnly = (DataPacket) cls.getDeclaredConstructor().newInstance();
                headerOnly.protocol = latestProtocol;
                headerOnly.reset();
                byte[] headerBuf = headerOnly.getBuffer();
                if (headerBuf != null && buffer.length <= headerBuf.length) {
                    result.add(name);
                }
            } catch (Exception ignored) {
                // Can't instantiate or test — skip
            }
        }
        return result;
    }

    /**
     * Scans all regression test classes for {@code @ParameterizedTest} annotations
     * and extracts tested packet names from their {@code name} attribute.
     */
    private static Set<String> scanTestedPacketsFromAnnotations(Set<String> knownPacketNames) {
        Set<String> tested = new HashSet<>();
        List<Class<?>> testClasses = scanClassesInPackage("cn.nukkit.network.protocol.regression");

        for (Class<?> testClass : testClasses) {
            if (testClass == PacketCoverageReportTest.class) {
                continue;
            }
            for (Method method : testClass.getDeclaredMethods()) {
                ParameterizedTest pt = method.getAnnotation(ParameterizedTest.class);
                if (pt == null) {
                    continue;
                }
                Matcher m = PACKET_NAME_PATTERN.matcher(pt.name());
                if (m.find()) {
                    String packetName = m.group(1);
                    if (knownPacketNames.contains(packetName)) {
                        tested.add(packetName);
                    }
                }
            }
        }
        return tested;
    }

    /**
     * Scans all CB Protocol codecs to collect the union of registered BedrockPacket class names.
     * Uses all codecs (not just the latest) because some packets exist only in certain versions.
     */
    private static Set<String> scanCbCodecPacketNames() {
        Set<String> names = new HashSet<>();
        for (int protocolVersion : ProtocolCodecMapping.getSupportedVersions()) {
            BedrockCodec codec = ProtocolCodecMapping.getCodec(protocolVersion);
            // Iterate packet IDs; CB codecs typically use IDs 0~300
            for (int id = 0; id < 500; id++) {
                BedrockPacketDefinition<?> def = codec.getPacketDefinition(id);
                if (def != null) {
                    names.add(def.getFactory().get().getClass().getSimpleName());
                }
            }
        }
        return names;
    }

    // --- Classpath scanning utilities ---

    /**
     * Scans the classpath for all non-abstract DataPacket subclasses
     * in cn.nukkit.network.protocol and its subpackages.
     */
    private static List<Class<?>> scanDataPacketSubclasses() {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> cls : scanClassesInPackage("cn.nukkit.network.protocol")) {
            if (DataPacket.class.isAssignableFrom(cls)
                    && !java.lang.reflect.Modifier.isAbstract(cls.getModifiers())) {
                result.add(cls);
            }
        }
        return result;
    }

    private static List<Class<?>> scanClassesInPackage(String basePackage) {
        List<Class<?>> result = new ArrayList<>();
        String path = basePackage.replace('.', '/');

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if (!"file".equals(resource.getProtocol())) {
                    continue;
                }
                File dir = new File(resource.toURI());
                if (dir.isDirectory()) {
                    scanDirectory(dir, basePackage, result);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan classes in " + basePackage, e);
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
                    result.add(cls);
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    // Skip classes that can't be loaded
                }
            }
        }
    }
}
