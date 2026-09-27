package cn.nukkit;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 编解码名称查找表条目（UUID + 认证来源）。
 * <p>
 * Encodes and decodes name lookup entries (UUID + auth provenance).
 */
class ServerNameLookupEntryTest {

    private static final UUID UUID_VALUE = UUID.fromString("6a1b7ac6-2f0f-4a6a-9f43-2b6c4a1d0e11");

    @Test
    void roundTripsAuthenticatedProvenance() {
        byte[] encoded = Server.encodeNameEntry(UUID_VALUE, Server.NameProvenance.XBOX_AUTHED);
        assertEquals(17, encoded.length);

        Server.NameEntry entry = Server.decodeNameEntry(encoded);
        assertEquals(UUID_VALUE, entry.uuid());
        assertEquals(Server.NameProvenance.XBOX_AUTHED, entry.provenance());
    }

    @Test
    void roundTripsOfflineProvenance() {
        byte[] encoded = Server.encodeNameEntry(UUID_VALUE, Server.NameProvenance.OFFLINE);
        assertEquals(17, encoded.length);

        Server.NameEntry entry = Server.decodeNameEntry(encoded);
        assertEquals(UUID_VALUE, entry.uuid());
        assertEquals(Server.NameProvenance.OFFLINE, entry.provenance());
    }

    @Test
    void decodesLegacyEntriesAsUnknownProvenance() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(UUID_VALUE.getMostSignificantBits());
        buffer.putLong(UUID_VALUE.getLeastSignificantBits());

        Server.NameEntry entry = Server.decodeNameEntry(buffer.array());

        assertEquals(UUID_VALUE, entry.uuid());
        assertEquals(Server.NameProvenance.LEGACY_UNKNOWN, entry.provenance());
    }

    @Test
    void rejectsInvalidLengthsAndProvenanceBytes() {
        assertNull(Server.decodeNameEntry(null));
        assertNull(Server.decodeNameEntry(new byte[0]));
        assertNull(Server.decodeNameEntry(new byte[15]));
        assertNull(Server.decodeNameEntry(new byte[18]));

        byte[] unknownProvenance = Server.encodeNameEntry(UUID_VALUE, Server.NameProvenance.OFFLINE);
        unknownProvenance[16] = 0x7f;
        assertNull(Server.decodeNameEntry(unknownProvenance));
    }

    @Test
    void legacyProvenanceIsNeverWritten() {
        assertThrows(IllegalArgumentException.class,
                () -> Server.encodeNameEntry(UUID_VALUE, Server.NameProvenance.LEGACY_UNKNOWN));
    }
}
