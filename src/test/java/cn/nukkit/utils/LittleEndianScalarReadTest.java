package cn.nukkit.utils;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class LittleEndianScalarReadTest {
    @Test void floatBitsOffsetsAndRoundingMatchOldReader() {
        Random random=new Random(18271);
        for(int i=0;i<10000;i++) {
            byte[] bytes=new byte[9];random.nextBytes(bytes);
            BinaryStream old=new BinaryStream(bytes),actual=new BinaryStream(bytes);
            old.setOffset(3);actual.setOffset(3);
            float expected=Binary.readLFloat(old.get(4),-1),got=actual.getLFloat();
            assertEquals(Float.floatToRawIntBits(expected),Float.floatToRawIntBits(got));
            assertEquals(old.getOffset(),actual.getOffset());
            old.setOffset(3);actual.setOffset(3);
            assertEquals(Binary.readLInt(old.get(4)),actual.getLInt());
        }
        for(float f:new float[]{-0.0f,0.0f,-1.125f,3.1415926f,Float.POSITIVE_INFINITY,Float.NaN}) {
            for(int precision:new int[]{-1,0,2,5}) {
                byte[] bytes=Binary.writeLFloat(f);
                assertEquals(Binary.readLFloat(bytes,precision),new BinaryStream(bytes).getLFloat(precision));
            }
        }
    }
    @Test void truncatedReadsStillConsumeRemainingBytesAndThrowSameException() {
        for(int remaining=0;remaining<4;remaining++) {
            BinaryStream old=new BinaryStream(new byte[remaining]),actual=new BinaryStream(new byte[remaining]);
            Throwable expected=assertThrows(ArrayIndexOutOfBoundsException.class,()->Binary.readLFloat(old.get(4)));
            Throwable got=assertThrows(expected.getClass(),actual::getLFloat);
            assertEquals(old.getOffset(),actual.getOffset());
            assertEquals(expected.getClass(),got.getClass());
        }
    }
    @Test void readerDoesNotReadUnusedBufferCapacity() {
        BinaryStream stream=new BinaryStream();stream.putByte((byte)1);
        assertThrows(ArrayIndexOutOfBoundsException.class,stream::getLFloat);
        assertEquals(1,stream.getOffset());
    }
}
