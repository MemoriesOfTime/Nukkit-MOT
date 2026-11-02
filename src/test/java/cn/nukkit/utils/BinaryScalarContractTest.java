package cn.nukkit.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BinaryScalarContractTest {
    @Test void falseRoundTrips() { assertFalse(Binary.readBool(Binary.writeBool(false))); }
    @Test void trueRoundTrips() { assertTrue(Binary.readBool(Binary.writeBool(true))); }
    @Test void onlyZeroIsFalseForEveryWireByte() {
        for (int i = 0; i < 256; i++) assertEquals(i != 0, Binary.readBool((byte) i));
    }
    @Test void signedByteRetainsSignForEveryWireByte() {
        for (int i = -128; i <= 127; i++) assertEquals(i, Binary.readSignedByte(Binary.writeByte((byte) i)));
    }
    @Test void existingTriadEncodingIsAlreadyCorrect() {
        for (int i : new int[]{0, 1, 255, 256, 65535, 65536, 0xffffff})
            assertEquals(i, Binary.readLTriad(Binary.writeLTriad(i)));
    }
}
