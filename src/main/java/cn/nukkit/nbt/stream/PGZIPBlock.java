package cn.nukkit.nbt.stream;

import java.util.concurrent.Callable;

public class PGZIPBlock implements Callable<byte[]> {

    public static final int SIZE = 65536; // 64 * 1024
    /**
     * This ThreadLocal avoids the recycling of a lot of memory, causing lumpy performance.
     */
    protected final ThreadLocal<PGZIPState> STATE;
    protected final byte[] in = new byte[SIZE];
    protected int in_length = 0;
    public PGZIPBlock(final PGZIPOutputStream parent) {
        STATE = new PGZIPThreadLocal(parent);
    }

    @Override
    public byte[] call() throws Exception {

        PGZIPState state = STATE.get();
        state.def.reset();
        state.buf.reset();
        state.str.write(in, 0, in_length);
        state.str.flush();

        return state.buf.toByteArray();
    }

    @Override
    public String toString() {
        return "Block" + '(' + in_length + '/' + in.length + " bytes)";
    }
}
