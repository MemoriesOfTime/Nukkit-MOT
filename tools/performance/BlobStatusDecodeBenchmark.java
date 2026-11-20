import cn.nukkit.network.protocol.ClientCacheBlobStatusPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.Random;

/** Whole production packet decoding, including owned output arrays and inherited readers. */
public final class BlobStatusDecodeBenchmark {
    private static final ClientCacheBlobStatusPacket[] INPUT = new ClientCacheBlobStatusPacket[256];
    private static volatile long sink;

    private static long decode(int iterations) {
        long sum = 0;
        for (int i = 0; i < iterations; i++) {
            ClientCacheBlobStatusPacket packet = INPUT[i & 255];
            packet.offset = 0;
            packet.decode();
            sum += packet.offset;
            for (long value : packet.missHashes) sum = sum * 31 + value;
            for (long value : packet.hitHashes) sum = sum * 31 + value;
        }
        return sum;
    }

    public static void main(String[] args) {
        String[] mode = args[0].split("-");
        boolean modern = mode[0].equals("modern");
        int count = Integer.parseInt(mode[1]), missCount = count / 2, hitCount = count - missCount;
        int iterations = count >= 256 ? 8192 : 131072;
        Random random = new Random(0x1771faaeL);
        for (int i = 0; i < INPUT.length; i++) {
            long[] miss = new long[missCount], hit = new long[hitCount];
            for (int j = 0; j < miss.length; j++) miss[j] = random.nextLong();
            for (int j = 0; j < hit.length; j++) hit[j] = random.nextLong();
            BinaryStream writer = new BinaryStream();
            writer.putUnsignedVarInt(missCount);
            if (!modern) writer.putUnsignedVarInt(hitCount);
            for (long value : miss) writer.putLLong(value);
            if (modern) writer.putUnsignedVarInt(hitCount);
            for (long value : hit) writer.putLLong(value);
            ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
            packet.protocol = modern ? ProtocolInfo.v1_26_30 : ProtocolInfo.v1_20_0;
            packet.setBuffer(writer.getBuffer());
            packet.decode();
            if (!java.util.Arrays.equals(miss, packet.missHashes)
                    || !java.util.Arrays.equals(hit, packet.hitHashes)
                    || packet.offset != writer.getCount()) throw new AssertionError("decode mismatch");
            INPUT[i] = packet;
        }
        for (int warmup = 0; warmup < 8; warmup++) sink = decode(iterations);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        for (int sample = 0; sample < 7; sample++) {
            long allocated = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
            sink = decode(iterations);
            long elapsed = System.nanoTime() - start;
            allocated = bean.getThreadAllocatedBytes(thread) - allocated;
            System.out.printf(Locale.ROOT,
                    "{\"operation\":\"%s\",\"ns_per_op\":%.6f,\"bytes_per_op\":%.6f,\"checksum\":%d}%n",
                    args[0], elapsed / (double) iterations, allocated / (double) iterations, sink);
        }
    }
}
