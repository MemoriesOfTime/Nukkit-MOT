package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import com.nukkitx.network.util.Preconditions;
import io.netty.buffer.ByteBuf;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.security.Key;
import java.util.Map;

public class ChunkSectionSerializers {
    private static final ChunkSectionSerializer[] serializers;
    private static final long b;
    private static final String[] c;
    private static final String[] d;
    private static final Map e;

    static {
        serializers = new ChunkSectionSerializer[10];
        serializers[0] = ChunkSectionSerializerV7.INSTANCE;

        serializers[1] = ChunkSectionSerializerV1.INSTANCE;
        serializers[2] = ChunkSectionSerializerV7.INSTANCE;
        serializers[3] = ChunkSectionSerializerV7.INSTANCE;
        serializers[4] = ChunkSectionSerializerV7.INSTANCE;
        serializers[5] = ChunkSectionSerializerV7.INSTANCE;
        serializers[6] = ChunkSectionSerializerV7.INSTANCE;
        serializers[7] = ChunkSectionSerializerV7.INSTANCE;
        serializers[8] = ChunkSectionSerializerV8.INSTANCE;
        serializers[9] = ChunkSectionSerializerV9.INSTANCE;
    }

    public static void serializer(ByteBuf byteBuf, StateBlockStorage[] stateBlockStorageArray, int ySection, int version) {
        ChunkSectionSerializers.getChunkSectionSerializer(version).serializer(byteBuf, stateBlockStorageArray, ySection);
    }

    public static StateBlockStorage[] deserialize(ByteBuf byteBuf, ChunkBuilder chunkBuilder, int version) {
        return ChunkSectionSerializers.getChunkSectionSerializer(version).deserialize(byteBuf, chunkBuilder);
    }

    public static ChunkSectionSerializer getChunkSectionSerializer(int version) {
        Preconditions.checkElementIndex(version, serializers.length, "ChunkSectionSerializers invalid version: " + version);
        ChunkSectionSerializer serializer = serializers[version];
        Preconditions.checkNotNull(serializer, "ChunkSectionSerializers invalid version: " + version);
        return serializer;
    }

    private static String a(byte[] byArray) {
        int n2 = 0;
        int n3 = byArray.length;
        char[] cArray = new char[n3];
        for (int i2 = 0; i2 < n3; ++i2) {
            char c2;
            int n4 = 0xFF & byArray[i2];
            if (n4 < 192) {
                cArray[n2++] = (char)n4;
                continue;
            }
            if (n4 < 224) {
                c2 = (char)((char)(n4 & 0x1F) << 6);
                n4 = byArray[++i2];
                c2 = (char)(c2 | (char)(n4 & 0x3F));
                cArray[n2++] = c2;
                continue;
            }
            if (i2 >= n3 - 2) continue;
            c2 = (char)((char)(n4 & 0xF) << 12);
            n4 = byArray[++i2];
            c2 = (char)(c2 | (char)(n4 & 0x3F) << 6);
            n4 = byArray[++i2];
            c2 = (char)(c2 | (char)(n4 & 0x3F));
            cArray[n2++] = c2;
        }
        return new String(cArray, 0, n2);
    }

    private static String b(int n2, long l2) {
        int n3 = n2 ^ (int)(l2 & 0x7FFFL) ^ 0x1FD9;
        if (d[n3] == null) {
            Object[] objectArray;
            try {
                Long l3 = Thread.currentThread().getId();
                objectArray = (Object[])e.get(l3);
                if (objectArray == null) {
                    objectArray = new Object[]{Cipher.getInstance("DES/CBC/PKCS5Padding"), SecretKeyFactory.getInstance("DES"), new IvParameterSpec(new byte[8])};
                    e.put(l3, objectArray);
                }
            }
            catch (Exception exception) {
                throw new RuntimeException("cn/nukkit/level/format/leveldb/serializer/ChunkSectionSerializers", exception);
            }
            byte[] byArray = new byte[8];
            byArray[0] = (byte)(l2 >>> 56);
            for (int i2 = 1; i2 < 8; ++i2) {
                byArray[i2] = (byte)(l2 << i2 * 8 >>> 56);
            }
            DESKeySpec dESKeySpec = new DESKeySpec(byArray);
            SecretKey secretKey = ((SecretKeyFactory)objectArray[1]).generateSecret(dESKeySpec);
            ((Cipher)objectArray[0]).init(2, (Key)secretKey, (IvParameterSpec)objectArray[2]);
            byte[] byArray2 = c[n3].getBytes("ISO-8859-1");
            ChunkSectionSerializers.d[n3] = ChunkSectionSerializers.a(((Cipher)objectArray[0]).doFinal(byArray2));
        }
        return d[n3];
    }
}

