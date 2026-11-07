package cn.nukkit.nbt.tag;

import cn.nukkit.nbt.stream.NBTInputStream;
import cn.nukkit.nbt.stream.NBTOutputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class ListTagUnknownTypeTest {
    @ParameterizedTest
    @ValueSource(ints = {-128, -1, 12, 13, 99, 127})
    void unknownNonEmptyElementTypeIsRejectedBeforeZeroWidthElements(int type) throws Exception {
        // Three elements prove the no-progress path without stressing the JVM on an unfixed build.
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            for (boolean network : new boolean[]{false, true}) {
                try (NBTInputStream input = input(type, 3, order, network)) {
                    assertThrows(IOException.class, () -> new ListTag<>().load(input, 0));
                }
            }
        }
    }

    @Test
    void everyUnsupportedByteRejectsEmptyAndHugeLengths() throws Exception {
        for (int type = Tag.TAG_Int_Array + 1; type <= 255; type++) {
            for (int size : new int[]{0, Integer.MAX_VALUE}) {
                for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
                    for (boolean network : new boolean[]{false, true}) {
                        try (NBTInputStream input = input(type, size, order, network)) {
                            assertThrows(IOException.class, () -> new ListTag<>().load(input, 0),
                                    "Unsupported element type " + type + " with size " + size);
                        }
                    }
                }
            }
        }
    }

    @Test
    void legalEmptyAndEndListsRetainExistingBehavior() throws Exception {
        for (int type = Tag.TAG_End; type <= Tag.TAG_Int_Array; type++) {
            try (NBTInputStream input = input(type, 0, ByteOrder.BIG_ENDIAN, false)) {
                ListTag<?> list = new ListTag<>();
                list.load(input, 0);
                assertEquals(0, list.size());
            }
        }
        try (NBTInputStream input = input(Tag.TAG_End, Integer.MAX_VALUE, ByteOrder.BIG_ENDIAN, false)) {
            ListTag<?> list = new ListTag<>();
            list.load(input, 0);
            assertEquals(0, list.size());
        }
    }

    @Test
    void knownNonEmptyTypeStillRequiresItsPayload() throws Exception {
        try (NBTInputStream input = input(Tag.TAG_Int, 3, ByteOrder.BIG_ENDIAN, false)) {
            assertThrows(IOException.class, () -> new ListTag<>().load(input, 0));
        }
    }

    private static NBTInputStream input(int type, int size, ByteOrder order, boolean network) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NBTOutputStream output = new NBTOutputStream(bytes, order, network)) {
            output.writeByte(type);
            output.writeInt(size);
        }
        return new NBTInputStream(new ByteArrayInputStream(bytes.toByteArray()), order, network);
    }
}
