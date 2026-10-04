package cn.nukkit.utils;

import cn.nukkit.Server;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import static org.junit.jupiter.api.Assertions.*;

class ZlibThreadLocalRetentionTest {
    private static Object cached(String name) throws Exception {
        Field field = ZlibThreadLocal.class.getDeclaredField(name);
        field.setAccessible(true);
        return ((ThreadLocal<?>)field.get(null)).get();
    }

    @Test
    void resetsEveryCodecAfterReturningIdenticalPayloads() throws Exception {
        ZlibThreadLocal codec = new ZlibThreadLocal();
        Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        Server server = new ObjenesisStd().newInstance(Server.class);
        server.networkCompressionThreshold = 1;
        instance.set(null, server);
        try {
            for (int size : new int[]{1, 8192, 2 * 1024 * 1024}) {
                byte[] data = new byte[size];
                new Random(size).nextBytes(data);
                byte[][] parts = {Arrays.copyOfRange(data, 0, size / 2), Arrays.copyOfRange(data, size / 2, size)};
                for (byte[] compressed : new byte[][]{codec.deflate(data, 1), codec.deflate(parts, 1)}) {
                    assertEquals(0, ((Deflater)cached("DEFLATER")).getBytesRead());
                    assertArrayEquals(data, codec.inflate(compressed, size + 1));
                    assertEquals(0, ((Inflater)cached("INFLATER")).getBytesRead());
                }
                for (byte[] compressed : new byte[][]{codec.deflateRaw(data, 1), codec.deflateRaw(parts, 1)}) {
                    assertEquals(0, ((Deflater)cached("DEFLATER_RAW")).getBytesRead());
                    assertArrayEquals(data, codec.inflateRaw(compressed, size + 1));
                    assertEquals(0, ((Inflater)cached("INFLATER_RAW")).getBytesRead());
                }
            }
        } finally { instance.set(null, previous); }
    }

    @Test
    void resetsRejectedAndMalformedInflation() throws Exception {
        ZlibThreadLocal codec = new ZlibThreadLocal();
        byte[] data = new byte[65536];
        byte[] wrapped = codec.deflate(data, 1);
        byte[] raw = codec.deflateRaw(new byte[][]{data}, 1);
        assertThrows(IOException.class, () -> codec.inflate(wrapped, 8192));
        assertEquals(0, ((Inflater)cached("INFLATER")).getBytesRead());
        assertEquals(0, ((Inflater)cached("INFLATER")).getRemaining());
        assertThrows(IOException.class, () -> codec.inflateRaw(raw, 8192));
        assertEquals(0, ((Inflater)cached("INFLATER_RAW")).getBytesRead());
        assertEquals(0, ((Inflater)cached("INFLATER_RAW")).getRemaining());
        assertThrows(IOException.class, () -> codec.inflate(new byte[]{3,4,5}, 8192));
        assertEquals(0, ((Inflater)cached("INFLATER")).getBytesRead());
        assertArrayEquals(data, codec.inflate(wrapped, data.length + 1));
        assertArrayEquals(data, codec.inflateRaw(raw, data.length + 1));
    }
}
