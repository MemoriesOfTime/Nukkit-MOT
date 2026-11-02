package cn.nukkit.level.explosion;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteOrder;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Calls the unchanged public1125 palette test loader with an owned synthetic resource.
 * Production/parser/resource bytes and all typed-state assertions remain unchanged. */
@org.junit.jupiter.api.Timeout(15)
class NativePaletteLoaderStreamOwnershipTest {
    private static final String TARGET = NativeExplosionPaletteTypedStateTest.class.getName();
    private static final String RESOURCE = "runtime_block_states_419.dat";

    private static byte[] root() throws IOException {
        ListTag<CompoundTag> rows = new ListTag<>("");
        // Reuse the existing public419 row-count contract; no new palette state/projection data.
        for (int i = 0; i < 8324; i++) rows.add(new CompoundTag());
        return NBTIO.write((Tag) rows, ByteOrder.BIG_ENDIAN, false);
    }

    private static byte[] gzip(byte[] decoded) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(decoded); }
        return bytes.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(first); bytes.write(second); return bytes.toByteArray();
    }

    @Test void theActualLoaderRetainsPhysicalOwnershipThroughDecodedRootEof() throws Exception {
        TrackingInput input = new TrackingInput(gzip(root()));
        ListTag<?> rows = load(input);
        assertEquals(8324, rows.size()); assertTrue(input.closed);
        assertFalse(input.readAfterClose);
    }

    @Test void trailingDecodedBytesAreRejectedAfterCompressedReadAheadReachesRawEof() throws Exception {
        TrackingInput input = new TrackingInput(gzip(concat(root(), new byte[]{0x7f, 1, 2})));
        AssertionError failure = assertThrows(AssertionError.class, () -> load(input));
        assertTrue(failure.getMessage().contains("Exact root EOF " + RESOURCE));
        assertTrue(input.exhaustedWhileOpen, "Compressed read-ahead cannot substitute for decoded EOF");
        assertTrue(input.closed); assertFalse(input.readAfterClose);
    }

    @Test void anotherGzipMemberCannotHideTrailingDecodedPayload() throws Exception {
        byte[] extra = "trailing decoded member bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TrackingInput input = new TrackingInput(concat(gzip(root()), gzip(extra)));
        AssertionError failure = assertThrows(AssertionError.class, () -> load(input));
        assertTrue(failure.getMessage().contains("Exact root EOF " + RESOURCE));
        assertTrue(input.exhaustedWhileOpen); assertTrue(input.closed); assertFalse(input.readAfterClose);
    }

    @Test void aNativeTruncatedRootStillClosesTheOuterPhysicalResource() throws Exception {
        TrackingInput input = new TrackingInput(gzip(new byte[]{Tag.TAG_List}));
        assertThrows(IOException.class, () -> load(input));
        assertTrue(input.closed); assertFalse(input.readAfterClose);
    }

    private static ListTag<?> load(TrackingInput input) throws Exception {
        Class<?> target = new SyntheticResourceLoader(input).loadClass(TARGET);
        var method = target.getDeclaredMethod("palette", int.class);
        method.setAccessible(true);
        try { return (ListTag<?>) method.invoke(null, 419); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        }
    }

    /** Only this test loader and its nest members are reloaded; NBTIO and assertions are native parent classes. */
    private static final class SyntheticResourceLoader extends ClassLoader {
        private final TrackingInput resource;
        SyntheticResourceLoader(TrackingInput resource) {
            super(NativePaletteLoaderStreamOwnershipTest.class.getClassLoader()); this.resource = resource;
        }
        @Override public InputStream getResourceAsStream(String name) {
            return RESOURCE.equals(name) ? resource : super.getResourceAsStream(name);
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(TARGET) && !name.startsWith(TARGET + "$")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream bytes = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (bytes == null) throw new ClassNotFoundException(name);
                        byte[] code = bytes.readAllBytes(); loaded = defineClass(name, code, 0, code.length);
                    } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    private static final class TrackingInput extends FilterInputStream {
        boolean closed, readAfterClose, exhaustedWhileOpen;
        TrackingInput(byte[] bytes) { super(new ByteArrayInputStream(bytes)); }
        private void checkOpen() throws IOException {
            if (closed) { readAfterClose = true; throw new IOException("Physical resource already closed"); }
        }
        @Override public int read() throws IOException {
            checkOpen(); int value = super.read(); if (super.available() == 0) exhaustedWhileOpen = true; return value;
        }
        @Override public int read(byte[] bytes, int off, int len) throws IOException {
            checkOpen(); int count = super.read(bytes, off, len);
            if (super.available() == 0) exhaustedWhileOpen = true;
            return count;
        }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
}
