package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SkinTintHexTest {
    @BeforeAll
    static void initializeServer() { MockServer.init(); }
    @Test
    void formatterKeepsEveryBytePositionRandomValuesAndLocales() throws Exception {
        Method formatter = BinaryStream.class.getDeclaredMethod("intToSkinColor", int.class);
        formatter.setAccessible(true);
        Locale saved = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.ROOT, Locale.US, Locale.forLanguageTag("tr-TR"),
                    Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("th-TH"))) {
                Locale.setDefault(locale);
                for (int shift = 0; shift < 32; shift += 8) {
                    for (int value = 0; value < 256; value++) {
                        verify(formatter, value << shift);
                        verify(formatter, ~(value << shift));
                    }
                }
                Random random = new Random(0x5c01);
                for (int i = 0; i < 1024; i++) verify(formatter, random.nextInt());
            }
        } finally {
            Locale.setDefault(saved);
        }
    }
    private static void verify(Method formatter, int value) throws Exception {
        String expected = String.format("#%08X", value);
        assertEquals(expected, formatter.invoke(null, value));
    }
    @Test
    void everySupportedClientProtocolKeepsSkinTintBytesAndReadOffset() {
        List<String> colors = List.of("#00000000", "#FFFFFFFF", "#80000000", "#0123ABCD");
        for (int protocol : ProtocolInfo.SUPPORTED_PROTOCOLS) {
            if (protocol < ProtocolInfo.v1_20_0) continue;
            Skin source = new Skin();
            source.setSkinId("tint-parity");
            source.setSkinColor("#FEDCBA98");
            source.getTintColors().add(new PersonaPieceTint(PersonaPieceType.BODY, colors));
            BinaryStream stream = new BinaryStream();
            stream.putSkin(GameVersion.byProtocol(protocol, false), source);
            byte[] expected = stream.getBuffer();
            stream.offset = 0;
            Skin decoded = stream.getSkin(protocol);
            assertEquals(expected.length, stream.offset, "read offset protocol " + protocol);
            assertEquals("#FEDCBA98", decoded.getSkinColor());
            assertEquals(1, decoded.getTintColors().size());
            assertEquals(colors, decoded.getTintColors().get(0).colors);
            BinaryStream encoded = new BinaryStream();
            encoded.putSkin(GameVersion.byProtocol(protocol, false), decoded);
            assertArrayEquals(expected, encoded.getBuffer(), "wire bytes protocol " + protocol);
        }
    }
}
