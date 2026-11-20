import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Measures complete native skin decoding; decoded objects escape into a retained ring. */
public final class SkinTintBenchmark {
    private static final Skin[] RETAINED = new Skin[64];
    private static volatile long sink;
    private static void decode(byte[] bytes, int count, int protocol) {
        BinaryStream stream = new BinaryStream(bytes);
        for (int i = 0; i < count; i++) {
            stream.offset = 0;
            RETAINED[i & 63] = stream.getSkin(protocol);
        }
        long sum = 0;
        for (Skin skin : RETAINED) if (skin != null) sum += skin.getSkinColor().hashCode() + skin.getTintColors().size();
        sink = sum;
    }
    public static void main(String[] args) {
        MockServer.init();
        int tints = args.length == 0 ? 8 : Integer.parseInt(args[0]);
        int protocol = args.length > 1 ? Integer.parseInt(args[1]) : ProtocolInfo.v1_26_60;
        Skin skin = new Skin();skin.setSkinId("benchmark");skin.setSkinColor("#FE12AB34");
        for (int i = 0; i < tints; i++) skin.getTintColors().add(new PersonaPieceTint(PersonaPieceType.BODY,
                List.of("#0123ABCD", "#FF123456", "#00000000", "#80000000")));
        BinaryStream encoded = new BinaryStream();encoded.putSkin(GameVersion.byProtocol(protocol, false), skin);
        byte[] bytes = encoded.getBuffer();
        for (int i = 0; i < 8; i++) decode(bytes, 2000, protocol);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
        long tid = Thread.currentThread().getId();
        for (int sample = 0; sample < 7; sample++) {
            long allocated = bean.getThreadAllocatedBytes(tid), start = System.nanoTime();
            decode(bytes, 4000, protocol);
            long elapsed = System.nanoTime() - start;allocated = bean.getThreadAllocatedBytes(tid) - allocated;
            System.out.printf(Locale.ROOT, "%d/%d %.3f ns/decode %.3f B/decode checksum=%d%n", protocol, tints,
                    elapsed / 4000d, allocated / 4000d, sink);
        }
    }
}
