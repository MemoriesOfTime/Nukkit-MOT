import cn.nukkit.utils.BinaryStream;
import cn.nukkit.network.protocol.MovePlayerPacket;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Random;

/** Same scalar workloads on 256 actual DataPacket subclasses; class capability lookup is included. */
public final class PacketScalarReadBenchmark {
    private static volatile long sink;
    private static final BinaryStream[] INPUT = new BinaryStream[256];
    private static final BinaryStream[] CREATED = new BinaryStream[256];

    static {
        Random random = new Random(0x299144bL);
        for (int i = 0; i < INPUT.length; i++) {
            byte[] bytes = new byte[64];
            random.nextBytes(bytes);
            MovePlayerPacket packet = new MovePlayerPacket();
            packet.setBuffer(bytes);
            INPUT[i] = packet;
        }
    }

    private static long read(int iterations, String operation) {
        long sum = 0;
        for (int i = 0; i < iterations; i++) {
            BinaryStream stream = INPUT[i & 255];
            stream.offset = 0;
            switch (operation) {
                case "width2" -> sum += stream.getShort();
                case "width3" -> sum += stream.getTriad();
                case "width4" -> sum += stream.getInt();
                case "width8" -> sum += stream.getLong();
                case "little-control" -> {
                    sum += stream.getLInt();
                    sum += Float.floatToRawIntBits(stream.getLFloat());
                }
                case "constructor-control" -> {
                    BinaryStream created = new BinaryStream();
                    CREATED[i & 255] = created;
                    sum += created.getBufferUnsafe().length;
                }
                case "mixed" -> {
                    sum += stream.getLong();
                    sum += stream.getLLong();
                    sum += stream.getInt();
                    sum += stream.getShort();
                    sum += stream.getLShort();
                    sum += Float.floatToRawIntBits(stream.getFloat());
                    sum += stream.getTriad();
                    sum += stream.getLTriad();
                }
                default -> throw new IllegalArgumentException(operation);
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        String operation = args.length == 0 ? "mixed" : args[0];
        for (int warmup = 0; warmup < 8; warmup++) sink = read(1_000_000, operation);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        for (int sample = 0; sample < 7; sample++) {
            long allocated = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
            sink = read(1_000_000, operation);
            long elapsed = System.nanoTime() - start;
            allocated = bean.getThreadAllocatedBytes(thread) - allocated;
            System.out.printf(java.util.Locale.ROOT,
                    "{\"operation\":\"%s\",\"ns_per_op\":%.6f,\"bytes_per_op\":%.6f,\"checksum\":%d}%n",
                    operation, elapsed / 1_000_000d, allocated / 1_000_000d, sink);
        }
    }
}
