package cn.nukkit.entity.passive;

import cn.nukkit.entity.NativeWalkingFullAiAllocationProbe;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeWalkingAnimalAllocationTest {
    @Test
    void fullNativeWalkingTickStaysWithinMeasuredAllocationBudget(@TempDir Path temporaryDirectory)
            throws Exception {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof ThreadMXBean mx && mx.isThreadAllocatedMemorySupported());

        // The JUnit extension instruments native classes. Run the exact cow fixture in a
        // bounded plain JVM so the allocation count excludes test-agent bookkeeping.
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path outputFile = temporaryDirectory.resolve("walking-allocation.log");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-Xms256m", "-Xmx512m", "-cp", classpath,
                NativeWalkingFullAiAllocationProbe.class.getName(), "junit-child", "50000", "20000", "7", "900")
                .redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Native walking allocation probe timed out");
            String output = Files.readString(outputFile);
            System.out.print(output);
            assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Native walking allocation probe must be reaped");
            }
        }
    }
}
