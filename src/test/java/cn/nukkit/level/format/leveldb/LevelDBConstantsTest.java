package cn.nukkit.level.format.leveldb;

import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.utils.Binary;
import org.cloudburstmc.nbt.NbtMap;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author LT_Name
 */
public class LevelDBConstantsTest {

    @Test
    public void testStateVersion() {
        List<NbtMap> list = NukkitLegacyMapper.loadBlockPalette();
        int version = 0;
        for (int i = 0; i < list.size(); ++i) {
            NbtMap nbtMap = list.get(i);
            version = nbtMap.getInt("version");
            break;
        }
        assertEquals(version, LevelDBConstants.STATE_VERSION, "LevelDBConstants.STATE_VERSION mismatch");
    }

    @Test
    public void testCurrentStorageMetadataVersions() {
        assertEquals(9, LevelDBConstants.CURRENT_STORAGE_VERSION);
        assertEquals(41, LevelDBConstants.CURRENT_LEVEL_CHUNK_VERSION);
        assertEquals(9, LevelDBConstants.CURRENT_LEVEL_SUBCHUNK_VERSION);
        assertEquals(List.of(1, 26, 10, 0, 0), LevelDBConstants.CURRENT_LEVEL_VERSION.stream().map(IntTag::getData).toList());
        assertArrayEquals(new byte[]{7}, LevelDBConstants.LEGACY_CHUNK_VERSION_SAVE_DATA);
        assertArrayEquals(new byte[]{0}, LevelDBConstants.GENERATED_PRE_CAVES_AND_CLIFFS_BLENDING_SAVE_DATA);
        assertArrayEquals(new byte[]{0, 8}, LevelDBConstants.BLENDING_DATA_SAVE_DATA);
    }

    @Test
    public void testChunkerStyleSubChunkKeyLayout() {
        byte[] overworldKey = LevelDBKey.SUB_CHUNK_PREFIX.getKey(1, -2, -4, 0);
        assertArrayEquals(new byte[]{
                1, 0, 0, 0,
                (byte) 0xfe, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                '/',
                (byte) 0xfc
        }, overworldKey);

        byte[] netherKey = LevelDBKey.SUB_CHUNK_PREFIX.getKey(1, -2, -1, 1);
        assertArrayEquals(new byte[]{
                1, 0, 0, 0,
                (byte) 0xfe, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                1, 0, 0, 0,
                '/',
                (byte) 0xff
        }, netherKey);
    }

    @Test
    public void testPrefixedChunkKeyLayout() {
        byte[] key = LevelDBKey.getKey(LevelDBKey.DIGP_PREFIX, 1, -2, 0);
        assertArrayEquals(new byte[]{
                'd', 'i', 'g', 'p',
                1, 0, 0, 0,
                (byte) 0xfe, (byte) 0xff, (byte) 0xff, (byte) 0xff
        }, key);
    }

    @Test
    public void testPrefixedKeyHelpersMatchChunkerStyleApi() {
        byte[] key = LevelDBKey.getKey(LevelDBKey.MAP_PREFIX, "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(true, LevelDBKey.startsWith(key, LevelDBKey.MAP_PREFIX));
        assertEquals("abc", LevelDBKey.extractSuffix(key, LevelDBKey.MAP_PREFIX));
    }

    @Test
    public void testChunkVersionKeyDimensionFilterTreatsDimensionlessKeysAsOverworldOnly() {
        byte[] overworldVersionKey = LevelDBKey.VERSION.getKey(1, -2, 0);
        byte[] netherVersionKey = LevelDBKey.VERSION.getKey(1, -2, 1);
        byte[] netherSubChunkKey = LevelDBKey.SUB_CHUNK_PREFIX.getKey(1, -2, -1, 1);

        assertTrue(LevelDBProvider.isChunkVersionKeyForDimension(overworldVersionKey, 0));
        assertFalse(LevelDBProvider.isChunkVersionKeyForDimension(overworldVersionKey, 1));
        assertFalse(LevelDBProvider.isChunkVersionKeyForDimension(overworldVersionKey, 2));

        assertTrue(LevelDBProvider.isChunkVersionKeyForDimension(netherVersionKey, 1));
        assertFalse(LevelDBProvider.isChunkVersionKeyForDimension(netherVersionKey, 0));
        assertFalse(LevelDBProvider.isChunkVersionKeyForDimension(netherSubChunkKey, 1));
    }

    @Test
    public void testFinalizedStateMappingUsesBedrockValues() {
        assertEquals(ChunkState.GENERATED, LevelDBConstants.deserializeFinalizationState(Binary.writeLInt(0)));
        assertEquals(ChunkState.POPULATED, LevelDBConstants.deserializeFinalizationState(Binary.writeLInt(1)));
        assertEquals(ChunkState.FINISHED, LevelDBConstants.deserializeFinalizationState(Binary.writeLInt(2)));
        assertEquals(ChunkState.FINISHED, LevelDBConstants.deserializeFinalizationState(new byte[]{2}));
        assertEquals(ChunkState.FINISHED, LevelDBConstants.deserializeFinalizationState(Binary.writeLInt(3)));

        assertArrayEquals(Binary.writeLInt(0), LevelDBConstants.serializeFinalizationState(ChunkState.GENERATED));
        assertArrayEquals(Binary.writeLInt(1), LevelDBConstants.serializeFinalizationState(ChunkState.POPULATED));
        assertArrayEquals(Binary.writeLInt(2), LevelDBConstants.serializeFinalizationState(ChunkState.FINISHED));
    }

    @Test
    public void testBedrockDoneIsNotTreatedAsLegacy() {
        DB bedrockDb = dbWithEntries(entry(LevelDBKey.STATE_FINALIZATION.getKey(0, 0, 0), Binary.writeLInt(2)));
        assertFalse(LevelDBProvider.hasLegacyNukkitFinalizationState(bedrockDb, "bedrock-test"));
    }

    @Test
    public void testLegacyFinalizationDetectionUsesFinishedOrdinalByDefault() {
        DB bedrockDb = dbWithEntries(entry(LevelDBKey.STATE_FINALIZATION.getKey(0, 0, 0), Binary.writeLInt(2)));
        DB legacyDb = dbWithEntries(entry(LevelDBKey.STATE_FINALIZATION.getKey(0, 0, 0), Binary.writeLInt(3)));

        assertFalse(LevelDBProvider.hasLegacyNukkitFinalizationState(bedrockDb, "bedrock-test"));
        assertTrue(LevelDBProvider.hasLegacyNukkitFinalizationState(legacyDb, "legacy-test"));
    }

    @Test
    public void testLegacyFinalizationDetectionUsesNukkitChunkMarkerForAmbiguousOrdinals() {
        Entry<byte[], byte[]> generatedEntry = entry(LevelDBKey.STATE_FINALIZATION.getKey(0, 0, 0), Binary.writeLInt(ChunkState.GENERATED.ordinal()));
        Entry<byte[], byte[]> populatedEntry = entry(LevelDBKey.STATE_FINALIZATION.getKey(0, 0, 0), Binary.writeLInt(ChunkState.POPULATED.ordinal()));
        Entry<byte[], byte[]> nukkitMarker = entry(LevelDBKey.NUKKIT_BLOCK_LIGHT.getKey(0, 0, 0, 0), new byte[]{1});
        Entry<byte[], byte[]> otherChunkNukkitMarker = entry(LevelDBKey.NUKKIT_BLOCK_LIGHT.getKey(1, 0, 0, 0), new byte[]{1});

        assertFalse(LevelDBProvider.hasLegacyNukkitFinalizationState(dbWithEntries(generatedEntry), "legacy-generated-test"));
        assertFalse(LevelDBProvider.hasLegacyNukkitFinalizationState(dbWithEntries(populatedEntry), "legacy-populated-test"));
        assertFalse(LevelDBProvider.hasLegacyNukkitFinalizationState(dbWithEntries(populatedEntry, otherChunkNukkitMarker), "legacy-populated-test"));
        assertTrue(LevelDBProvider.hasLegacyNukkitFinalizationState(dbWithEntries(generatedEntry, nukkitMarker), "legacy-generated-test"));
        assertTrue(LevelDBProvider.hasLegacyNukkitFinalizationState(dbWithEntries(populatedEntry, nukkitMarker), "legacy-populated-test"));
    }

    @Test
    public void testLegacyFinalizationDecisionKeepsUnmarkedBedrockDoneInMixedWorld() {
        byte[] markedFinalization = LevelDBKey.STATE_FINALIZATION.getKey(0, 0, 0);
        byte[] unmarkedFinalization = LevelDBKey.STATE_FINALIZATION.getKey(1, 0, 0);
        Set<ByteBuffer> nukkitChunks = Set.of(ByteBuffer.wrap(markedFinalization));

        assertTrue(LevelDBProvider.isLegacyNukkitFinalizationState(
                markedFinalization, ChunkState.POPULATED.ordinal(), nukkitChunks));
        assertFalse(LevelDBProvider.isLegacyNukkitFinalizationState(
                unmarkedFinalization, ChunkState.POPULATED.ordinal(), nukkitChunks));
    }

    @SafeVarargs
    private static DB dbWithEntries(Entry<byte[], byte[]>... entries) {
        DB db = mock(DB.class);
        when(db.iterator()).thenAnswer(invocation -> new TestDBIterator(List.of(entries).iterator()));
        return db;
    }

    private static Entry<byte[], byte[]> entry(byte[] key, byte[] value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static final class TestDBIterator implements DBIterator {
        private final Iterator<Entry<byte[], byte[]>> delegate;

        private TestDBIterator(Iterator<Entry<byte[], byte[]>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return this.delegate.hasNext();
        }

        @Override
        public Entry<byte[], byte[]> next() {
            return this.delegate.next();
        }

        @Override
        public void close() {
        }

        @Override
        public void seek(byte[] key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void seekToFirst() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entry<byte[], byte[]> peekNext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasPrev() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entry<byte[], byte[]> prev() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entry<byte[], byte[]> peekPrev() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void seekToLast() {
            throw new UnsupportedOperationException();
        }
    }

}
