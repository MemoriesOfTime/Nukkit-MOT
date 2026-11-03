package cn.nukkit.level.util;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockShelf;
import cn.nukkit.block.BlockShelfOak;
import cn.nukkit.block.BlockStone;
import cn.nukkit.math.BlockFace;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EnumLookupScratchTest {
    private static BitArrayVersion originalLookup(int input, boolean read) {
        for (BitArrayVersion version : BitArrayVersion.values()) {
            if ((!read && version.entriesPerWord <= input) || (read && version.bits == input)) {
                return version;
            }
        }
        throw new IllegalArgumentException("Invalid palette version: " + input);
    }

    @Test void selectionAndFailureMessagesMatchOriginalOrder() {
        List<Integer> inputs = new ArrayList<>();
        for (int input = -64; input <= 256; input++) inputs.add(input);
        inputs.add(Integer.MIN_VALUE);
        inputs.add(Integer.MAX_VALUE);
        Random random = new Random(0x7719aaL);
        for (int i = 0; i < 512; i++) inputs.add(random.nextInt());
        for (int input : inputs) for (boolean read : new boolean[]{false, true}) {
            BitArrayVersion expected;
            try {
                expected = originalLookup(input, read);
            } catch (IllegalArgumentException failure) {
                IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                        () -> BitArrayVersion.get(input, read));
                assertEquals(failure.getMessage(), actual.getMessage());
                continue;
            }
            assertSame(expected, BitArrayVersion.get(input, read), input + ":" + read);
        }
    }

    @Test void persistedPaletteReadsKeepEveryVersionAndCell() {
        for (BitArrayVersion version : BitArrayVersion.values()) for (int flag = 0; flag < 2; flag++) {
            ByteBuf wire = Unpooled.buffer();
            try {
                wire.writeByte((version.getId() << 1) | flag);
                if (version != BitArrayVersion.V0) {
                    BitArray words = version.createPalette(4096);
                    for (int index = 0; index < 4096; index++) words.set(index, index & 1);
                    for (int word : words.getWords()) wire.writeIntLE(word);
                    wire.writeIntLE(2);
                }
                wire.writeIntLE(117);
                if (version != BitArrayVersion.V0) wire.writeIntLE(311);
                int expectedBytes = wire.writerIndex();
                PalettedBlockStorage storage = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 999);
                storage.readFromStorage(wire);
                assertEquals(expectedBytes, wire.readerIndex());
                for (int index = 0; index < 4096; index++) {
                    assertEquals(version == BitArrayVersion.V0 || (index & 1) == 0 ? 117 : 311,
                            storage.getBlock(index), version + ":" + index);
                }
            } finally {
                wire.release();
            }
        }
    }

    @Test void mutatingPublicValuesSnapshotsDoesNotAffectLookups() {
        BitArrayVersion[] versions = BitArrayVersion.values();
        java.util.Arrays.fill(versions, null);
        for (BitArrayVersion version : BitArrayVersion.values()) {
            assertSame(version, BitArrayVersion.get(version.getId(), true));
        }
        BlockShelf.PoweredShelfType[] shelfTypes = BlockShelf.PoweredShelfType.values();
        java.util.Arrays.fill(shelfTypes, null);
        for (int type = 0; type < 4; type++) {
            assertSame(BlockShelf.PoweredShelfType.values()[type], new BlockShelfOak(type).getShelfType());
        }
    }

    @Test void shelfMetadataAndSetterPreserveAllUnrelatedBits() {
        BlockShelf shelf = new BlockShelfOak();
        for (int metadata = 0; metadata <= 0xffff; metadata++) {
            shelf.setDamage(metadata);
            assertSame(BlockShelf.PoweredShelfType.values()[shelf.getDamage() & 3], shelf.getShelfType());
        }
        for (int metadata : new int[]{Integer.MIN_VALUE, -7, -1, 0, 1, 2, 3, 31, Integer.MAX_VALUE}) {
            for (BlockShelf.PoweredShelfType type : BlockShelf.PoweredShelfType.values()) {
                shelf.setDamage(metadata);
                int before = shelf.getDamage();
                shelf.setShelfType(type);
                assertSame(type, shelf.getShelfType());
                assertEquals(before & ~3, shelf.getDamage() & ~3);
            }
        }
        assertThrows(NullPointerException.class, () -> shelf.setShelfType(null));
    }

    @Test void poweredConnectionBranchesKeepTypeAndSideObservations() {
        for (int type = 0; type < 4; type++) for (boolean powered : new boolean[]{false, true}) {
            for (int otherType = 0; otherType < 4; otherType++) {
                ConnectionShelf subject = new ConnectionShelf(type, powered);
                BlockShelfOak candidate = new BlockShelfOak();
                candidate.x = 100;
                BlockShelfOak other = new BlockShelfOak(otherType);
                other.x = 200;
                subject.side = other;
                boolean expected = powered && (type == 0 || type == 2
                        || type == 3 && otherType == 1 || type == 1 && otherType == 3);
                assertEquals(expected, subject.canConnect(candidate));
                assertEquals(powered && (type == 1 || type == 3) ? 1 : 0, subject.sideCalls);
                if (subject.sideCalls != 0) {
                    assertSame(type == 3 ? BlockFace.NORTH.rotateYCCW() : BlockFace.NORTH.rotateY(), subject.seen);
                }
                subject.side = candidate;
                assertEquals(powered, subject.canConnect(candidate));
                subject.side = new BlockStone();
                assertEquals(powered && (type == 0 || type == 2), subject.canConnect(candidate));
            }
        }
    }

    private static final class ConnectionShelf extends BlockShelfOak {
        final boolean powered;
        Block side;
        int sideCalls;
        BlockFace seen;
        ConnectionShelf(int metadata, boolean powered) { super(metadata); this.powered = powered; }
        @Override public boolean isGettingPower() { return powered; }
        @Override public BlockFace getBlockFace() { return BlockFace.NORTH; }
        @Override public Block getSide(BlockFace face) { sideCalls++; seen = face; return side; }
    }
}
