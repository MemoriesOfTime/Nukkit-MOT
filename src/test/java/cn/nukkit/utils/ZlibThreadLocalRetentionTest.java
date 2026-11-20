package cn.nukkit.utils;

import cn.nukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ZlibThreadLocal} 的复用行为：同一个 codec 实例连续处理不同大小的负载、以及处理被拒绝的
 * 解压之后，输出与异常都必须保持正确。
 * <p>
 * 这里只断言可观察结果（解压回来的字节、抛出的异常类型），不读取 {@code DEFLATER} /
 * {@code INFLATER} 等私有 {@code ThreadLocal} 的 {@code getBytesRead()}：那是在验证"实现选择了
 * 哪种复位方式"，而不是"复用是否安全"。字段改名或改为对象池都不应让本测试变红。
 * <p>
 * Reuse contract of the zlib codec: repeated use must keep output correct and failures recoverable.
 * Internal {@code getBytesRead()} counters are deliberately not inspected.
 */
class ZlibThreadLocalRetentionTest {

    private Field serverInstance;
    private Object previousServer;
    private ZlibThreadLocal codec;

    @BeforeEach
    void installCompressionThreshold() throws Exception {
        serverInstance = Server.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        previousServer = serverInstance.get(null);

        Server server = new ObjenesisStd().newInstance(Server.class);
        server.networkCompressionThreshold = 1;
        serverInstance.set(null, server);

        codec = new ZlibThreadLocal();
    }

    @AfterEach
    void restoreServer() throws Exception {
        // @BeforeEach 在取字段前失败时 serverInstance 仍为 null，不能让它掩盖原始异常。
        if (serverInstance != null) {
            serverInstance.set(null, previousServer);
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        return data;
    }

    @Test
    void reusesEveryCodecAcrossPayloadSizesWithoutCrossContamination() throws IOException {
        for (int size : new int[]{1, 8192, 2 * 1024 * 1024}) {
            byte[] data = randomBytes(size);
            byte[][] parts = {
                    Arrays.copyOfRange(data, 0, size / 2),
                    Arrays.copyOfRange(data, size / 2, size)};

            // 同一实例连续复用三轮：任何残留状态都会让第二轮起解压结果不一致。
            for (int round = 0; round < 3; round++) {
                assertArrayEquals(data, codec.inflate(codec.deflate(data, 1), size + 1),
                        "deflate 第 " + round + " 轮 size=" + size);
                assertArrayEquals(data, codec.inflate(codec.deflate(parts, 1), size + 1),
                        "分段 deflate 第 " + round + " 轮 size=" + size);
                assertArrayEquals(data, codec.inflateRaw(codec.deflateRaw(data, 1), size + 1),
                        "deflateRaw 第 " + round + " 轮 size=" + size);
                assertArrayEquals(data, codec.inflateRaw(codec.deflateRaw(parts, 1), size + 1),
                        "分段 deflateRaw 第 " + round + " 轮 size=" + size);
            }
        }
    }

    @Test
    void rejectedAndMalformedInflationKeepsTheCodecUsable() throws IOException {
        byte[] data = randomBytes(65536);
        byte[] wrapped = codec.deflate(data, 1);
        byte[] raw = codec.deflateRaw(new byte[][]{data}, 1);

        // 声明上限小于实际长度 → 必须拒绝，而不是返回截断数据
        assertThrows(IOException.class, () -> codec.inflate(wrapped, 8192));
        assertThrows(IOException.class, () -> codec.inflateRaw(raw, 8192));
        // 非法输入 → 必须抛出，而不是污染后续调用
        assertThrows(IOException.class, () -> codec.inflate(new byte[]{3, 4, 5}, 8192));

        // 失败之后同一实例仍能正确工作
        assertArrayEquals(data, codec.inflate(wrapped, data.length + 1));
        assertArrayEquals(data, codec.inflateRaw(raw, data.length + 1));
    }
}
