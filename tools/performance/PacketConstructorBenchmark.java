import cn.nukkit.network.protocol.MovePlayerPacket;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;

/** Retains actual DataPacket subclasses to expose inherited construction cost. */
public final class PacketConstructorBenchmark {
    private static final MovePlayerPacket[] OUTPUT = new MovePlayerPacket[256];
    private static volatile long sink;
    private static long run(int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            MovePlayerPacket packet = new MovePlayerPacket();
            OUTPUT[i & 255] = packet;
            sum += packet.getBufferUnsafe().length + packet.protocol;
        }
        return sum;
    }
    public static void main(String[] args) {
        for (int i = 0; i < 8; i++) sink = run(1_000_000);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        for (int sample = 0; sample < 7; sample++) {
            long bytes = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
            sink = run(1_000_000);
            long nanos = System.nanoTime() - start;
            bytes = bean.getThreadAllocatedBytes(thread) - bytes;
            System.out.printf(Locale.ROOT,
                    "{\"ns_per_op\":%.6f,\"bytes_per_op\":%.6f,\"checksum\":%d}%n",
                    nanos / 1_000_000d, bytes / 1_000_000d, sink);
        }
    }
}
