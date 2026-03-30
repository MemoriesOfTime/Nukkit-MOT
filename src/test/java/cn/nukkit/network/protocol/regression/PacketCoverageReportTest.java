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
 * have encode and decode regression tests.
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
     * Some Nukkit packet names intentionally keep the legacy name even when CB renamed the counterpart.
     */
    private static final Map<String, String> CB_NAME_ALIASES = Map.of();

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

        // 3. Auto-detect tested packets from @ParameterizedTest annotations (split by direction)
        Set<String> encodeTestedPackets = scanTestedPacketsFromAnnotations(
                standardPacketNames, "cn.nukkit.network.protocol.regression.encode");
        Set<String> decodeTestedPackets = scanTestedPacketsFromAnnotations(
                standardPacketNames, "cn.nukkit.network.protocol.regression.decode");

        // 4. Detect not-applicable packets per direction
        Map<String, Class<?>> classLookup = new HashMap<>();
        for (Class<?> cls : allPacketClasses) {
            classLookup.put(cls.getSimpleName(), cls);
        }
        Set<String> encodeNotApplicable = detectEncodeNotApplicable(testablePackets, classLookup);
        Set<String> decodeNotApplicable = detectDecodeNotApplicable(testablePackets, classLookup);

        // Packets with encodeUnsupported = pure client-to-server: decode tests are highest priority.
        // Packets with decodeUnsupported = pure server-to-client: decode tests are not needed.
        // encodeNotApplicable ∩ testablePackets = client-to-server subset (priority for decode).
        Set<String> clientToServerPackets = new TreeSet<>(testablePackets);
        clientToServerPackets.retainAll(encodeNotApplicable);

        // 5. Build report
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Packet Regression Test Coverage Report ===\n");
        sb.append(String.format("Total standard packets: %d  |  CB counterpart: %d\n",
                standardPacketNames.size(), testablePackets.size()));

        // --- Encode section ---
        buildDirectionReport(sb, "ENCODE", "NK encode → CB decode",
                standardPacketNames, testablePackets,
                encodeTestedPackets, encodeNotApplicable);

        // --- Decode section ---
        buildDirectionReport(sb, "DECODE", "CB encode → NK decode",
                standardPacketNames, testablePackets,
                decodeTestedPackets, decodeNotApplicable);

        // --- Decode priority focus: client-to-server packets ---
        buildDecodeClientToServerReport(sb, clientToServerPackets, decodeTestedPackets, decodeNotApplicable);

        // --- Combined per-packet matrix ---
        sb.append("\n--- Per-Packet Status Matrix ---\n");
        sb.append(String.format("  %-50s  %-10s  %-12s  %-5s  %s\n",
                "Packet", "Encode", "Decode", "CB?", "Direction"));
        sb.append(String.format("  %-50s  %-10s  %-12s  %-5s  %s\n",
                "-".repeat(50), "-".repeat(10), "-".repeat(12), "-----", "---------"));

        for (String name : standardPacketNames) {
            boolean hasCb = testablePackets.contains(name);
            String encStatus = getStatus(name, hasCb, encodeTestedPackets, encodeNotApplicable);
            String decStatus = getStatus(name, hasCb, decodeTestedPackets, decodeNotApplicable);
            String direction = getDirection(name, encodeNotApplicable, decodeNotApplicable, hasCb);
            sb.append(String.format("  %-50s  %-10s  %-12s  %-5s  %s\n",
                    name, encStatus, decStatus, hasCb ? "Y" : "N", direction));
        }

        // --- Legacy v113 ---
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
     * Builds a coverage section for one direction (encode or decode).
     */
    private static void buildDirectionReport(StringBuilder sb, String direction, String description,
                                             Set<String> allPackets, Set<String> testablePackets,
                                             Set<String> testedPackets, Set<String> notApplicable) {
        Set<String> effectiveTestable = new TreeSet<>(testablePackets);
        effectiveTestable.removeAll(notApplicable);

        int testedCount = testedPackets.size();
        int effectiveTestableCount = effectiveTestable.size();
        double overallPct = allPackets.isEmpty() ? 0 : (testedCount * 100.0 / allPackets.size());
        double testablePct = effectiveTestableCount == 0 ? 0 : (testedCount * 100.0 / effectiveTestableCount);

        sb.append(String.format("\n--- %s (%s) ---\n", direction, description));
        sb.append(String.format("Overall:  %d / %d (%.1f%%)\n", testedCount, allPackets.size(), overallPct));
        sb.append(String.format("Testable: %d / %d (%.1f%%)  [CB counterpart + functional %s()]\n",
                testedCount, effectiveTestableCount, testablePct, direction.toLowerCase()));
        sb.append(String.format("  (CB counterpart: %d, %s-not-applicable: %d)\n",
                testablePackets.size(), direction.toLowerCase(), notApplicable.size()));

        List<String> tested = new ArrayList<>();
        List<String> testableUntested = new ArrayList<>();
        List<String> naList = new ArrayList<>(notApplicable);
        Collections.sort(naList);

        for (String name : allPackets) {
            if (testedPackets.contains(name)) {
                tested.add(name);
            } else if (effectiveTestable.contains(name)) {
                testableUntested.add(name);
            }
        }

        sb.append(String.format("\n  TESTED (%d):\n", tested.size()));
        for (String name : tested) {
            sb.append("    ").append(name).append('\n');
        }

        sb.append(String.format("\n  TESTABLE BUT UNTESTED (%d):\n", testableUntested.size()));
        for (String name : testableUntested) {
            sb.append("    ").append(name).append('\n');
        }

        if (!naList.isEmpty()) {
            sb.append(String.format("\n  NOT APPLICABLE (%d):\n", naList.size()));
            for (String name : naList) {
                sb.append("    ").append(name).append('\n');
            }
        }
    }

    /**
     * Builds a focused report on client-to-server decode coverage.
     * These packets (where encode is N/A) are the highest priority for decode tests.
     */
    private static void buildDecodeClientToServerReport(StringBuilder sb,
                                                        Set<String> clientToServerPackets,
                                                        Set<String> decodeTestedPackets,
                                                        Set<String> decodeNotApplicable) {
        // Among client-to-server packets, those with working decode() are priority
        Set<String> priorityTestable = new TreeSet<>(clientToServerPackets);
        priorityTestable.removeAll(decodeNotApplicable);

        List<String> tested = new ArrayList<>();
        List<String> untested = new ArrayList<>();
        for (String name : priorityTestable) {
            if (decodeTestedPackets.contains(name)) {
                tested.add(name);
            } else {
                untested.add(name);
            }
        }

        double pct = priorityTestable.isEmpty() ? 0 : (tested.size() * 100.0 / priorityTestable.size());
        sb.append("\n--- DECODE PRIORITY: Client→Server packets (encode=N/A) ---\n");
        sb.append(String.format("Coverage: %d / %d (%.1f%%)  [highest priority for decode tests]\n",
                tested.size(), priorityTestable.size(), pct));

        if (!untested.isEmpty()) {
            sb.append(String.format("\n  UNTESTED CLIENT→SERVER (%d):\n", untested.size()));
            for (String name : untested) {
                sb.append("    ").append(name).append('\n');
            }
        }
    }

    /**
     * Returns the traffic direction for the per-packet matrix.
     */
    private static String getDirection(String name, Set<String> encodeNA, Set<String> decodeNA, boolean hasCb) {
        if (!hasCb) {
            return "";
        }
        boolean encNA = encodeNA.contains(name);
        boolean decNA = decodeNA.contains(name);
        if (encNA && !decNA) {
            return "C→S";       // client-to-server: encode N/A, decode real
        }
        if (!encNA && decNA) {
            return "S→C";       // server-to-client: decode N/A, encode real
        }
        if (encNA) {
            return "C→S(?)";    // both N/A, unusual
        }
        return "BIDIR";         // both implemented
    }

    /**
     * Returns a short status string for the per-packet matrix.
     */
    private static String getStatus(String name, boolean hasCb,
                                    Set<String> tested, Set<String> notApplicable) {
        if (tested.contains(name)) {
            return "TESTED";
        }
        if (notApplicable.contains(name)) {
            return "N/A";
        }
        if (!hasCb) {
            return "NO_CB";
        }
        return "UNTESTED";
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
     * Detects packets whose decode() consumes no data (empty, calls decodeUnsupported(), or throws).
     * These are typically server-to-client packets that the server never needs to decode.
     */
    private static Set<String> detectDecodeNotApplicable(Set<String> testableNames, Map<String, Class<?>> classLookup) {
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
                // Provide a buffer with enough dummy data for decode() to read from
                byte[] dummyBuffer = new byte[1024];
                packet.setBuffer(dummyBuffer);
                int offsetBefore = packet.getOffset();
                try {
                    packet.decode();
                } catch (UnsupportedOperationException e) {
                    result.add(name);
                    continue;
                } catch (Exception ignored) {
                    // decode() crashed on dummy data — it has a real implementation
                    continue;
                }
                int offsetAfter = packet.getOffset();
                if (offsetAfter == offsetBefore) {
                    // No bytes consumed — decode is effectively a no-op
                    result.add(name);
                }
            } catch (Exception ignored) {
                // Can't instantiate or test — skip
            }
        }
        return result;
    }

    /**
     * Scans test classes in the specified package for {@code @ParameterizedTest} annotations
     * and extracts tested packet names from their {@code name} attribute.
     */
    private static Set<String> scanTestedPacketsFromAnnotations(Set<String> knownPacketNames, String testPackage) {
        Set<String> tested = new HashSet<>();
        List<Class<?>> testClasses = scanClassesInPackage(testPackage);

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
                    String packetName = def.getFactory().get().getClass().getSimpleName();
                    names.add(packetName);
                    String alias = CB_NAME_ALIASES.get(packetName);
                    if (alias != null) {
                        names.add(alias);
                    }
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
