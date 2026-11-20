package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.types.SubChunkRequestResult;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SubChunkPacketAllocationTest {
    private static final int HEIGHT_MAP_LENGTH = 256;
    private static final byte[] NO_DATA = new byte[0];

    @Test
    void everyProtocolBranchMatchesFormerEncoderByteForByte() {
        for (int protocol : ProtocolInfo.SUPPORTED_PROTOCOLS) {
            if (protocol < ProtocolInfo.v1_17_30 || protocol > ProtocolInfo.v1_26_50) continue;
            for (boolean cache : new boolean[]{false, true}) {
                for (int seed = 0; seed < 8; seed++) {
                    SubChunkPacket expected = fixture(new FormerSubChunkPacket(), protocol, cache, seed);
                    SubChunkPacket actual = fixture(new SubChunkPacket(), protocol, cache, seed);
                    expected.encode();
                    actual.encode();
                    assertArrayEquals(expected.getBuffer(), actual.getBuffer(),
                            protocol + " cache=" + cache + " seed=" + seed);
                    actual.encode();
                    assertArrayEquals(expected.getBuffer(), actual.getBuffer(), "re-encoding");
                }
                SubChunkPacket empty = fixture(new SubChunkPacket(), protocol, cache, 0);
                SubChunkPacket former = fixture(new FormerSubChunkPacket(), protocol, cache, 0);
                empty.subChunks.clear();
                former.subChunks.clear();
                empty.encode();
                former.encode();
                assertArrayEquals(former.getBuffer(), empty.getBuffer());
            }
        }
    }

    @Tag("perf")
    @Test
    void presizingCutsLargePayloadAllocationsInEveryEncoderBranch() {
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(allocations.isThreadAllocatedMemorySupported());
        if (!allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        for (int protocol : new int[]{ProtocolInfo.v1_17_30, ProtocolInfo.v1_18_0,
                ProtocolInfo.v1_18_10, ProtocolInfo.v1_20_0, ProtocolInfo.v1_21_90,
                ProtocolInfo.v1_26_40, ProtocolInfo.v1_26_50_27, ProtocolInfo.v1_26_50}) {
            SubChunkPacket fixture = fixture(new SubChunkPacket(), protocol, true, 0);
            fixture.subChunks.clear();
            for (int i = 0; i < 16; i++) {
                SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
                entry.position = fixture.centerPosition;
                entry.offset = new BlockVector3(i, -i, i);
                entry.result = SubChunkRequestResult.SUCCESS;
                entry.data = new byte[65536];
                fixture.subChunks.add(entry);
            }
            Runnable former = () -> encodeFresh(new FormerSubChunkPacket(), fixture);
            Runnable candidate = () -> encodeFresh(new SubChunkPacket(), fixture);
            for (int i = 0; i < 16; i++) {
                former.run();
                candidate.run();
            }
            long before = allocated(allocations, former);
            long after = allocated(allocations, candidate);
            System.out.println("SubChunk protocol=" + protocol + " allocated bytes/encode: "
                    + before + " -> " + after);
            assertTrue(after * 5 < before * 3,
                    protocol + ": expected at least 40% fewer allocated bytes, before=" + before + " after=" + after);
        }
    }

    @Test
    void smallPayloadKeepsInitialBufferAndLegacyIgnoresLaterEntries() {
        for (int protocol : new int[]{ProtocolInfo.v1_18_0, ProtocolInfo.v1_18_10,
                ProtocolInfo.v1_20_0, ProtocolInfo.v1_21_90, ProtocolInfo.v1_26_40, ProtocolInfo.v1_26_50}) {
            SubChunkPacket packet = new SubChunkPacket();
            packet.protocol = protocol;
            packet.centerPosition = new BlockVector3();
            SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
            entry.position = packet.centerPosition;
            entry.offset = packet.centerPosition;
            entry.result = SubChunkRequestResult.SUCCESS;
            packet.subChunks.add(entry);
            if (protocol < ProtocolInfo.v1_18_10) packet.subChunks.add(null);
            byte[] initial = packet.getBufferUnsafe();
            packet.encode();
            assertSame(initial, packet.getBufferUnsafe(), "small packet protocol=" + protocol);
        }
    }

    private static long allocated(ThreadMXBean allocations, Runnable action) {
        long start = allocations.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 8; i++) action.run();
        return (allocations.getCurrentThreadAllocatedBytes() - start) / 8;
    }

    private static void encodeFresh(SubChunkPacket packet, SubChunkPacket fixture) {
        packet.protocol = fixture.protocol;
        packet.dimension = fixture.dimension;
        packet.cacheEnabled = fixture.cacheEnabled;
        packet.centerPosition = fixture.centerPosition;
        packet.subChunks = fixture.subChunks;
        packet.encode();
        assertTrue(packet.getCount() >= 65536);
    }

    private static <T extends SubChunkPacket> T fixture(T packet, int protocol, boolean cache, int seed) {
        packet.protocol = protocol;
        packet.cacheEnabled = cache;
        packet.dimension = new int[]{0, -1, 1, 127, 128, -128, Integer.MIN_VALUE, Integer.MAX_VALUE}[seed];
        packet.centerPosition = new BlockVector3(Integer.MIN_VALUE, -64, Integer.MAX_VALUE);
        if (seed % 2 != 0) packet.subChunks = new LinkedList<>();
        Random random = new Random(486 + seed);
        int[] sizes = {-1, 0, 1, 127, 128, 16383, 16384, 65536};
        for (int i = 0; i < 16; i++) {
            SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
            entry.position = new BlockVector3(-i, Integer.MIN_VALUE, Integer.MAX_VALUE);
            entry.offset = new BlockVector3(-i, i, 127 - i);
            entry.result = SubChunkRequestResult.values()[(i + seed) % SubChunkRequestResult.values().length];
            int length = sizes[(i + seed) % sizes.length];
            if (length >= 0) {
                entry.data = new byte[length];
                random.nextBytes(entry.data);
            }
            entry.heightMapType = SubChunkPacket.HeightMapDataType.values()[(i + seed) % SubChunkPacket.HeightMapDataType.values().length];
            entry.renderHeightMapType = SubChunkPacket.HeightMapDataType.values()[(i + seed + 1) % SubChunkPacket.HeightMapDataType.values().length];
            entry.heightMapData = new byte[256 + seed];
            entry.renderHeightMapData = new byte[272 + seed];
            random.nextBytes(entry.heightMapData);
            random.nextBytes(entry.renderHeightMapData);
            if (protocol < ProtocolInfo.v1_26_50_27 && seed == 0) {
                entry.heightMapData = null;
                entry.renderHeightMapData = null;
            }
            entry.blobId = random.nextLong();
            entry.hasBlobId = (i + seed) % 2 == 0;
            packet.subChunks.add(entry);
        }
        return packet;
    }

    private static class FormerSubChunkPacket extends SubChunkPacket {
        @Override
        public void encode() {
            this.reset();

            if (this.protocol >= ProtocolInfo.v1_26_40) {
                this.encodeV2168();
            } else if (this.protocol >= ProtocolInfo.v1_18_10) {
                this.encodeV486();
            } else {
                this.encodeV471();
            }
        }

        private void encodeV2168() {
            this.putBoolean(this.cacheEnabled);
            this.putVarInt(this.dimension);
            this.putLInt(this.centerPosition.x);
            this.putLInt(this.centerPosition.y);
            this.putLInt(this.centerPosition.z);

            this.putUnsignedVarInt(this.subChunks.size());
            for (SubChunkData subChunk : this.subChunks) {
                this.putByte((byte) subChunk.offset.x);
                this.putByte((byte) subChunk.offset.y);
                this.putByte((byte) subChunk.offset.z);

                this.putByte((byte) subChunk.result.ordinal());

                boolean hasData = subChunk.data != null;
                this.putBoolean(hasData);
                if (hasData) {
                    this.putByteArray(subChunk.data);
                }

                this.putByte((byte) subChunk.heightMapType.ordinal());
                boolean hasHeightMap = subChunk.heightMapType == HeightMapDataType.HAS_DATA;
                this.putBoolean(hasHeightMap);
                if (hasHeightMap) {
                    this.putHeightMapData(subChunk.heightMapData);
                }

                this.putByte((byte) subChunk.renderHeightMapType.ordinal());
                boolean hasRenderHeightMap = subChunk.renderHeightMapType == HeightMapDataType.HAS_DATA;
                this.putBoolean(hasRenderHeightMap);
                if (hasRenderHeightMap) {
                    this.putHeightMapData(subChunk.renderHeightMapData);
                }

                this.putBoolean(subChunk.hasBlobId);
                if (subChunk.hasBlobId) {
                    this.putLLong(subChunk.blobId);
                }
            }
        }

        
        private void putHeightMapData(byte[] heightMapData) {
            if (this.protocol < ProtocolInfo.v1_26_50_27) {
                this.put(heightMapData);
                return;
            }
            for (int offset = 0; offset < HEIGHT_MAP_LENGTH; offset += 16) {
                this.putUnsignedVarInt(16);
                this.put(Arrays.copyOfRange(heightMapData, offset, offset + 16));
            }
        }

        private void encodeV486() {
            this.putBoolean(this.cacheEnabled);
            this.putVarInt(this.dimension);
            this.putSignedBlockPosition(this.centerPosition);

            this.putLInt(this.subChunks.size());
            for (SubChunkData subChunk : this.subChunks) {
                this.putByte((byte) subChunk.offset.x);
                this.putByte((byte) subChunk.offset.y);
                this.putByte((byte) subChunk.offset.z);

                this.putByte((byte) subChunk.result.ordinal());

                if (subChunk.result != SubChunkRequestResult.SUCCESS_ALL_AIR || !this.cacheEnabled) {
                    this.putByteArray(subChunk.data == null ? NO_DATA : subChunk.data);
                }

                this.putByte((byte) subChunk.heightMapType.ordinal());
                if (subChunk.heightMapType == HeightMapDataType.HAS_DATA) {
                    this.put(subChunk.heightMapData);
                }

                if (this.protocol >= ProtocolInfo.v1_21_90) {
                    this.putByte((byte) subChunk.renderHeightMapType.ordinal());
                    if (subChunk.renderHeightMapType == HeightMapDataType.HAS_DATA) {
                        this.put(subChunk.renderHeightMapData);
                    }
                }

                if (this.cacheEnabled) {
                    this.putLLong(subChunk.blobId);
                }
            }
        }

        private void encodeV471() {
            this.putVarInt(this.dimension);

            if (!this.subChunks.isEmpty()) {
                SubChunkData subChunk = this.subChunks.get(0);
                this.putSignedBlockPosition(subChunk.position);
                this.putByteArray(subChunk.data == null ? NO_DATA : subChunk.data);
                this.putVarInt(subChunk.result.ordinal());

                this.putByte((byte) subChunk.heightMapType.ordinal());
                if (subChunk.heightMapType == HeightMapDataType.HAS_DATA) {
                    this.put(subChunk.heightMapData);
                } else if (this.protocol < ProtocolInfo.v1_18_0) {
                    this.put(new byte[HEIGHT_MAP_LENGTH]);
                }
            }

            if (this.protocol >= ProtocolInfo.v1_18_0) {
                this.putBoolean(this.cacheEnabled);
                if (this.cacheEnabled && !this.subChunks.isEmpty()) {
                    this.putLLong(this.subChunks.get(0).blobId);
                }
            }
        }
    }
}
