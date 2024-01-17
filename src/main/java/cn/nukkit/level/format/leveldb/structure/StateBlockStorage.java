package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.updater.BlockUpgrader;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.*;
import java.security.Key;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Log4j2
public class StateBlockStorage {

    private static final int SIZE = 16 * 16 * 16;
    private final List<BlockStateSnapshot> blockStateSnapshots;
    private BitArray bitArray;
    private static final long e;
    private static final String[] f;
    private static final String[] g;
    private static final Map h;

    public StateBlockStorage() {
        this(BitArrayVersion.V2);
    }

    public StateBlockStorage(BitArrayVersion version) {
        this.bitArray = version.createPalette();
        this.blockStateSnapshots = new ObjectArrayList<>(16);
        this.blockStateSnapshots.add(BlockStateMapping.get().getById(0));
    }

    public StateBlockStorage(BitArray bitArray, List<BlockStateSnapshot> list) {
        this.blockStateSnapshots = list;
        this.bitArray = bitArray;
    }

    private int getPaletteHeader(BitArrayVersion version, boolean runtime) {
        return (version.getId() << 1) | (runtime ? 1 : 0);
    }

    private static BitArrayVersion getBitArrayVersionByHeader(byte header) {
        return BitArrayVersion.get(header >> 1, true);
    }

    public void writeTo(ByteBuf byteBuf) {
        block35: {
            BitArrayVersion bitArrayVersion;
            int n2 = this.blockStateSnapshots.size();
            bitArrayVersion = n2 <= 1 ? BitArrayVersion.V0 : this.bitArray.getVersion();
            BitArrayVersion bitArrayVersion2 = bitArrayVersion;
            byteBuf.writeByte(this.getPaletteHeader(bitArrayVersion2, false));
            if (bitArrayVersion2 != BitArrayVersion.V0) {
                for (int n3 : this.bitArray.getWords()) {
                    byteBuf.writeIntLE(n3);
                }
                byteBuf.writeIntLE(n2);
            }
            try {
                Throwable throwable;
                Object object;
                block36: {
                    block31: {
                        Throwable throwable2;
                        NBTOutputStream nBTOutputStream;
                        block32: {
                            object = new ByteBufOutputStream(byteBuf);
                            throwable = null;
                            nBTOutputStream = NbtUtils.createWriterLE((OutputStream)object);
                            throwable2 = null;
                            for (BlockStateSnapshot blockStateSnapshot : this.blockStateSnapshots) {
                                nBTOutputStream.writeTag(blockStateSnapshot.getVanillaState());
                            }
                            try {
                                if (nBTOutputStream == null) break block31;
                                if (throwable2 == null) break block32;
                            }
                            catch (Throwable throwable3) {
                                throw StateBlockStorage.a(throwable3);
                            }
                            try {
                                nBTOutputStream.close();
                            }
                            catch (Throwable throwable4) {
                                throwable2.addSuppressed(throwable4);
                            }
                            break block31;
                        }
                        nBTOutputStream.close();
                        break block31;
                        catch (Throwable throwable5) {
                            try {
                                throwable2 = throwable5;
                                throw throwable5;
                            }
                            catch (Throwable throwable6) {
                                block33: {
                                    block34: {
                                        try {
                                            if (nBTOutputStream == null) break block33;
                                            if (throwable2 == null) break block34;
                                        }
                                        catch (Throwable throwable7) {
                                            throw StateBlockStorage.a(throwable7);
                                        }
                                        try {
                                            nBTOutputStream.close();
                                        }
                                        catch (Throwable throwable8) {
                                            throwable2.addSuppressed(throwable8);
                                        }
                                        break block33;
                                    }
                                    nBTOutputStream.close();
                                }
                                throw throwable6;
                            }
                        }
                    }
                    try {
                        if (object == null) break block35;
                        if (throwable == null) break block36;
                    }
                    catch (Throwable throwable9) {
                        throw StateBlockStorage.a(throwable9);
                    }
                    try {
                        ((ByteBufOutputStream)object).close();
                    }
                    catch (Throwable throwable10) {
                        throwable.addSuppressed(throwable10);
                    }
                    break block35;
                }
                ((ByteBufOutputStream)object).close();
                break block35;
                catch (Throwable throwable11) {
                    try {
                        throwable = throwable11;
                        throw throwable11;
                    }
                    catch (Throwable throwable12) {
                        block37: {
                            block38: {
                                try {
                                    if (object == null) break block37;
                                    if (throwable == null) break block38;
                                }
                                catch (Throwable throwable13) {
                                    throw StateBlockStorage.a(throwable13);
                                }
                                try {
                                    ((ByteBufOutputStream)object).close();
                                }
                                catch (Throwable throwable14) {
                                    throwable.addSuppressed(throwable14);
                                }
                                break block37;
                            }
                            ((ByteBufOutputStream)object).close();
                        }
                        throw throwable12;
                    }
                }
            }
            catch (IOException iOException) {
                throw new RuntimeException(iOException);
            }
        }
    }

    public void ofBlock(ByteBuf byteBuf, ChunkBuilder chunkBuilder) {
        BitArrayVersion version = StateBlockStorage.getBitArrayVersionByHeader(byteBuf.readByte());
        this.blockStateSnapshots.clear();
        if (version == BitArrayVersion.V0) {
            this.bitArray = version.createPalette(SIZE, null);
        } else {
            int expectedWordSize = version.getWordsForSize(SIZE);
            int[] words = new int[expectedWordSize];
            for (int i = 0; i < expectedWordSize; ++i) {
                words[i] = byteBuf.readIntLE();
            }
            this.bitArray = version.createPalette(SIZE, words);
        }

        int paletteSize = byteBuf.readIntLE();

        if (version.getMaxEntryValue() < paletteSize - 1) {
            //TODO
            throw new IllegalArgumentException("");
        }

        ByteBufInputStream stream = null;
        try {
            stream = new ByteBufInputStream(byteBuf);
            NBTInputStream inputStream = NbtUtils.createReaderLE(stream);
            for (int i = 0; i < paletteSize; ++i) {
                NbtMap nbtMap;
                try {
                    nbtMap = (NbtMap) inputStream.readTag();
                    nbtMap.hashCode();
                } catch (IOException e) {
                    throw new ChunkException("Invalid blockstate NBT at offset " + i + " in paletted storage", e);
                }

                CompoundTag tag = BlockUpgrader.upgrade(nbtMap);
                palette[i] = GlobalBlockPalette.getLegacyFullId(ProtocolInfo.CURRENT_LEVEL_PROTOCOL, tag);

                int meta;
                String name = tag.getString("name");
                int id = Blocks.getIdByBlockName(name);
                if (id == -1) {
                    id = Block.INFO_UPDATE;
                    meta = 0;
                    log.warn("Unmapped block name: {}", name);
                } else {
                    meta = tag.getShort("val");
                }
                palette[i] = (id << Block.BLOCK_META_BITS) | (meta & Block.BLOCK_META_MASK);
            }
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public void a(int n2, NbtMap nbtMap, long l2) {
        long l3 = l2 = e ^ l2;
        long l4 = l3 ^ 0x7FB549D5F6AAL;
        long l5 = l3 ^ 0x78619EEFC537L;
        BlockStateSnapshot blockStateSnapshot = BlockStateMapping.get().g(nbtMap);
        if (blockStateSnapshot == null) {
            blockStateSnapshot = BlockStateMapping.get().c(nbtMap, l4);
        }
        this.a(n2, blockStateSnapshot, l5);
    }

    public void a(int n2, BlockStateSnapshot blockStateSnapshot, long l2) {
        l2 = e ^ l2;
        try {
            int n3 = this.a(blockStateSnapshot);
            this.bitArray.set(n2, n3);
        }
        catch (IllegalArgumentException illegalArgumentException) {
            throw new IllegalArgumentException((String)((Object)StateBlockStorage.a("l", (int)10663, (long)(0x1FC59C62296F220CL ^ l2))) + blockStateSnapshot + (String)((Object)StateBlockStorage.a("l", (int)14218, (long)(0x396F161AB80F3C2EL ^ l2))) + this.blockStateSnapshots, illegalArgumentException);
        }
    }

    public BlockStateSnapshot d(int n2, int n3, int n4) {
        int n5 = ChunkBuilder.b(n2, n3, n4);
        return this.c(this.bitArray.get(n5));
    }

    public BlockStateSnapshot b(int n2) {
        return this.c(this.bitArray.get(n2));
    }

    public int c(int n2, int n3, int n4) {
        long l2 = e ^ 0x11733BFF84A0L;
        long l3 = l2 ^ 0x160ED360AB64L;
        return this.d(n2, n3, n4).h(l3);
    }

    public int b(int n2, int n3, int n4) {
        long l2 = e ^ 0x3C0772D1C7BDL;
        long l3 = l2 ^ 0x5A99C54B86E1L;
        return this.d(n2, n3, n4).k(l3);
    }

    public void b(int n2, int n3, int n4, int n5, long l2) {
        long l3 = l2 = e ^ l2;
        long l4 = l3 ^ 0x8291BB2005DL;
        long l5 = l3 ^ 0x4F614654D57CL;
        int n6 = ChunkBuilder.b(n2, n3, n4);
        this.a(n6, BlockStateMapping.get().b(n5, 0, l5), l4);
    }

    public void c(int n2, int n3, int n4, int n5, long l2) {
        long l3 = l2 = e ^ l2;
        long l4 = l3 ^ 0x533F3660E9F5L;
        long l5 = l3 ^ 0x14776B863CD4L;
        int n6 = ChunkBuilder.b(n2, n3, n4);
        this.a(n6, BlockStateMapping.get().b(this.b(n2, n3, n4), n5, l5), l4);
    }

    public int a(int n2, int n3, int n4) {
        return this.a(ChunkBuilder.b(n2, n3, n4));
    }

    public void d(int n2, int n3, int n4, int n5) {
        long l2 = e ^ 0x5AD63EFDB022L;
        long l3 = l2 ^ 0x5461E0610A89L;
        this.a(ChunkBuilder.b(n2, n3, n4), (short)n5, l3);
    }

    public int a(int n2, int n3, int n4, int n5) {
        long l2 = e ^ 0x1E7BE67FBBE7L;
        long l3 = l2 ^ 0x7E8212B1A38BL;
        return this.a(ChunkBuilder.b(n2, n3, n4), n5, l3);
    }

    private int a(int n2, int n3, long l2) {
        long l3 = l2 = e ^ l2;
        long l4 = l3 ^ 0x240572EA3C4BL;
        long l5 = l3 ^ 0x591FD57C8E38L;
        long l6 = l3 ^ 0x1E57889A5B19L;
        long l7 = l3 ^ 0x45E62DEF52D3L;
        BlockStateSnapshot blockStateSnapshot = this.b(n2);
        int n4 = n3 >> 4;
        int n5 = n3 & 0xF;
        this.a(n2, BlockStateMapping.get().b(n4, n5, l6), l5);
        return blockStateSnapshot.k(l4) << 4 | blockStateSnapshot.h(l7);
    }

    private int a(int n2) {
        long l2;
        long l3 = l2 = e ^ 0x521AFBBA6A89L;
        long l4 = l3 ^ 0x34844C202BD5L;
        long l5 = l3 ^ 0x55671325454DL;
        BlockStateSnapshot blockStateSnapshot = this.b(n2);
        return blockStateSnapshot.k(l4) << 4 | blockStateSnapshot.h(l5);
    }

    public void a(int n2, short s2, long l2) {
        long l3 = l2 = e ^ l2;
        long l4 = l3 ^ 0x3751FF2E2CFFL;
        long l5 = l3 ^ 0x7019A2C8F9DEL;
        int n3 = s2 >> 4;
        int n4 = s2 & 0xF;
        this.a(n2, BlockStateMapping.get().b(n3, n4, l5), l4);
    }

    public byte[] c(long l2) {
        l2 = e ^ l2;
        Throwable throwable = new Throwable();
        Server.getInstance().getLogger().warning((String)((Object)StateBlockStorage.a("l", (int)7911, (long)(0x5DC07A3AB81D1B9CL ^ l2))), throwable);
        return new byte[0];
    }

    public byte[] d(long l2) {
        l2 = e ^ l2;
        Throwable throwable = new Throwable();
        Server.getInstance().getLogger().warning((String)((Object)StateBlockStorage.a("l", (int)12903, (long)(0x2DA58773A54F15EL ^ l2))), throwable);
        return new byte[0];
    }

    public void a(int n2, BinaryStream binaryStream, boolean bl) {
        block10: {
            boolean bl2;
            block9: {
                boolean bl3;
                block8: {
                    try {
                        try {
                            if (!bl || this.blockStateSnapshots.size() <= 1) break block8;
                        }
                        catch (RuntimeException runtimeException) {
                            throw StateBlockStorage.a(runtimeException);
                        }
                        this.a(binaryStream, n2);
                        return;
                    }
                    catch (RuntimeException runtimeException) {
                        throw StateBlockStorage.a(runtimeException);
                    }
                }
                try {
                    bl3 = n2 < 475;
                }
                catch (RuntimeException runtimeException) {
                    throw StateBlockStorage.a(runtimeException);
                }
                bl2 = bl3;
                int n3 = Level.getChunkProtocol(BlockStateMapping.get().d());
                try {
                    if (n2 != n3) break block9;
                    this.a(binaryStream, bl2, BlockStateSnapshot::getRuntimeId);
                    break block10;
                }
                catch (RuntimeException runtimeException) {
                    throw StateBlockStorage.a(runtimeException);
                }
            }
            this.a(binaryStream, bl2, blockStateSnapshot -> GlobalBlockPalette.getOrCreateRuntimeId(n2, blockStateSnapshot.b(n2), blockStateSnapshot.a(n2)));
        }
    }

    private void a(BinaryStream binaryStream, boolean bl, Function<BlockStateSnapshot, Integer> function) {
        BitArray bitArray;
        block6: {
            block5: {
                try {
                    if (!bl || this.bitArray.getVersion() != BitArrayVersion.V0) break block5;
                }
                catch (RuntimeException runtimeException) {
                    throw StateBlockStorage.a(runtimeException);
                }
                bitArray = BitArrayVersion.V1.createPalette(SIZE);
                break block6;
            }
            bitArray = this.bitArray;
        }
        binaryStream.putByte((byte)this.getPaletteHeader(bitArray.getVersion(), true));
        if (bitArray.getVersion() != BitArrayVersion.V0) {
            for (int n2 : bitArray.getWords()) {
                binaryStream.putLInt(n2);
            }
            binaryStream.putVarInt(this.blockStateSnapshots.size());
        }
        Object object = this.blockStateSnapshots.iterator();
        while (object.hasNext()) {
            BlockStateSnapshot blockStateSnapshot = (BlockStateSnapshot)object.next();
            binaryStream.putVarInt(function.apply(blockStateSnapshot));
        }
    }

    private void a(BinaryStream binaryStream, int n2) {
        PalettedBlockStorage palettedBlockStorage = PalettedBlockStorage.createFromBlockPalette(this.bitArray.getVersion(), n2);
        for (int i2 = 0; i2 < SIZE; ++i2) {
            int n3;
            BlockStateSnapshot blockStateSnapshot;
            block5: {
                int n4;
                block7: {
                    block6: {
                        blockStateSnapshot = this.b(i2);
                        n3 = blockStateSnapshot.b(n2);
                        try {
                            try {
                                if (!Level.xrayableBlocks[n3]) break block5;
                                if (n3 != 526) break block6;
                            }
                            catch (RuntimeException runtimeException) {
                                throw StateBlockStorage.a(runtimeException);
                            }
                            n4 = 87;
                            break block7;
                        }
                        catch (RuntimeException runtimeException) {
                            throw StateBlockStorage.a(runtimeException);
                        }
                    }
                    n4 = 1;
                }
                n3 = n4;
            }
            palettedBlockStorage.setBlock(i2, GlobalBlockPalette.getOrCreateRuntimeId(n2, n3, blockStateSnapshot.a(n2)));
        }
        palettedBlockStorage.writeTo(binaryStream);
    }

    private void a(BitArrayVersion bitArrayVersion) {
        BitArray bitArray = bitArrayVersion.createPalette();
        try {
            for (int i2 = 0; i2 < SIZE; ++i2) {
                bitArray.set(i2, this.bitArray.get(i2));
            }
        }
        catch (RuntimeException runtimeException) {
            throw StateBlockStorage.a(runtimeException);
        }
        this.bitArray = bitArray;
    }

    private int a(BlockStateSnapshot blockStateSnapshot) {
        int n2 = this.blockStateSnapshots.indexOf(blockStateSnapshot);
        try {
            if (n2 != -1) {
                return n2;
            }
        }
        catch (RuntimeException runtimeException) {
            throw StateBlockStorage.a(runtimeException);
        }
        n2 = this.blockStateSnapshots.size();
        BitArrayVersion bitArrayVersion = this.bitArray.getVersion();
        if (n2 > bitArrayVersion.getMaxEntryValue()) {
            BitArrayVersion bitArrayVersion2 = bitArrayVersion.next();
            try {
                if (bitArrayVersion2 != null) {
                    this.a(bitArrayVersion2);
                }
            }
            catch (RuntimeException runtimeException) {
                throw StateBlockStorage.a(runtimeException);
            }
        }
        this.blockStateSnapshots.add(blockStateSnapshot);
        return n2;
    }

    private BlockStateSnapshot c(int n2) {
        return this.blockStateSnapshots.get(n2);
    }

    public boolean b() {
        try {
            if (this.blockStateSnapshots.size() == 1) {
                return true;
            }
        }
        catch (RuntimeException runtimeException) {
            throw StateBlockStorage.a(runtimeException);
        }
        for (int n2 : this.bitArray.getWords()) {
            try {
                if (Integer.toUnsignedLong(n2) == 0L) continue;
                return false;
            }
            catch (RuntimeException runtimeException) {
                throw StateBlockStorage.a(runtimeException);
            }
        }
        return true;
    }

    public StateBlockStorage a() {
        return new StateBlockStorage(this.bitArray.copy(), new ObjectArrayList<BlockStateSnapshot>(this.blockStateSnapshots));
    }

    /*
     * Unable to fully structure code
     */
    static {
        block11: {
            block10: {
                StateBlockStorage.e = o.a(-8523608782551448593L, 6432276953236183403L, MethodHandles.lookup().lookupClass()).a(255044732513637L);
                var9 = StateBlockStorage.e ^ 27198180577291L;
                StateBlockStorage.h = new HashMap<K, V>(13);
                var0_1 = Cipher.getInstance("DES/CBC/PKCS5Padding");
                v0 = SecretKeyFactory.getInstance("DES");
                v1 = new byte[8];
                v2 = v1;
                v1[0] = (byte)(var9 >>> 56);
                for (var1_2 = 1; var1_2 < 8; ++var1_2) {
                    v2 = v2;
                    v2[var1_2] = (byte)(var9 << var1_2 * 8 >>> 56);
                }
                var0_1.init(2, (Key)v0.generateSecret(new DESKeySpec(v2)), new IvParameterSpec(new byte[8]));
                var7_3 = new String[12];
                var5_4 = 0;
                var4_5 = "S\u00c5\u00a7?)V\u00f9,r\u00d9\u00f0\u00e9\u0099\u00f5\u008aV\u00a0\u00ff\u00ed\\\u00fev\u0003&kQ\u00c2\u008e\u00a0\u000eK6#\u0016\u00e0Vv\u00fe\u0081-<\u000f\u00899 \u00eb\u0086\u00d7h\u00da\u0015\u00a08}P\u00ca\u0001T\u00f8&\u007f\u00b4+\u00d6A\\\u0097\u00de\u00d8\u00dbe\u00f1\u001cP\u0088-\u009a\u0014\u0090\u00feH\u0099\u00f9\u00cb(3\u0007\u00b1\u009ca8cq\u0003\u00a5*\u0004\u00e2\u00dc\u008d\u00b9]\u00c3\u000b\u0096xt\u00fd\u000b\u00e2\u00d2\u00f7d\u008e\t\u00f5m\u00ccv\u0087\u00b7K3\u000b[r\u0011\u00d6Rs\u0085\u0081>&(\u0080\u00f6\u00f0\u00c9\u00e3\u00b6\u00c1\u00eeu9\u00a3\u000e\u0086a[\u00f6;>P\u009c$\u00c0\u0017+\u00e1\n\u00b2M\u0013\u001a\f\u008ev\u0099\u00c7\u00b1pr\u001c\u0015\u0094\u0086'\u0018\u00c0\u0095\u00d1\u001fV5\u000e\u00f8{\u00c4\u009aV\u00f3W\u00b7\u00f2\u007f59\u001e\u00ff#\u00c1\u00a0\u00b0\u00d1\u0015\u00e6\u00e2S\u00fbE\u00cd\u0087?\u00d0\u00e0\u008ekEF)\u00cb\u00fehM\u0010\u00df3\u0095\u008e\u00e8\u000f+\u00a9h\u00c7\u00ea\u00b2\u00ec\n$\u00d1LGs\u00bf\u00b9\u00b3\u00b4\u0003\u00d6dt4$\u00d2O\u001d=\u00c3\u00dd\u00e1\\%\u00ca_|\u00e5e\u00dc\"\u0081\u00dbW\u00ae#\u0089\t9\u00b1G:\u00df\u00cbE\t\u00c0\u001d;x\u00d1\u000e9x\n\u00bf%\u00eeU\u0007-\u00df\u0099(\u00ee\u0010\u00e3\u00a3\u00e6x\u00f5\u00df^\u008f\u00ee\u00e7M\u0014_\u0099\u00a0\u0006kU\u0096!\u00ec\u00b0_\u00d2\u00cd&I9\u00ab\u00af}(\u00bfH\u00fekBXM\u0097i[\b\u0098d\u00fc\u0083\u001f\u00cf2\u0001\u008d#q~\u00a3'-\u00e8\u00a7\u00b7b\u0084\u00ba\u00a7\u0091\u00abec\u00fe\u00c4\u00ce\u0095\u0092Zm\u00bbWRc\u00b2{\u008f\u00c7F\u00caD\u00d0k\u00f9\u00f7\u00c6\u00f6\u0098\u00bd\u00f1\u00f6$\u001c\u008db\u00e8\u001c\u00b83\u00cfPb\u00f1~\u00f3\u00df\u00a8\u0086\u00b8\u0097\u00f2\u00dag\u0098-\u0083^;\u0098x\u00b0\u0094\u008d\u0000{\u00f5\u00ce\u00b9\u000b\u00e7\u0099\u00fc\u00a1V\u00ae\u00b1&\u00d3\u0084\u007f\u00f6\u001a\u00e3+g\u00de\u00d6\u0088;\u008b\u00fd\\\u000e\u001c\u0013\u0014\u00dc\u00d1\u00d5\u00d17\u0003\u0093\u00cbP\u00c3\u00d5.rM\u0011}\u000f\u00cd\u00d6q\u0019\u00a1\u00a8\n\u00b7 \u00b4\u00d26\u0084\u001c\u00e1\u00bc\u0094\b\u00ddd\u00ac\u00b7A1\u00c1\u00a2\u008bi\u00a4\u00bf\r\u001b\u00c9ob\u00aa\u00aa:3\u00d5\u0099 \n\u0098$\u001b5~\u0092\u0096oW\u00a0AhX\u00f2_\u00f1\u00a8M\u0019T\u00dcKuC\u00e3~\"J\u008e\u000f\u008d(JJ\u000e\u0013\u00ac\u00d7\u00a4\u00ea\u00d4R\u0003D\u00e4\t$\u00e3Ci\u0083\f%bf\u00e5\u00dc\u00fd\u00fb\u00d5\u0092\u00c4\"\u0017\u00df\u00f4\u00ba8\u00e5\r\u0013\u00e40\u000f\u0092\u00c6\u00d3\u00c8\u00c0\u0016\u001e\u00ae\u00fd1h\u009cja\u00152A\u00b3 \u009f\u00ff^\u00a5\u0017\u00999\u0088\u000e\u00f9\u0093Ub\u00fb\u00e3W\u0093Vd\u009f@O\u0017v\u0012\u0094z>";
                var6_6 = "S\u00c5\u00a7?)V\u00f9,r\u00d9\u00f0\u00e9\u0099\u00f5\u008aV\u00a0\u00ff\u00ed\\\u00fev\u0003&kQ\u00c2\u008e\u00a0\u000eK6#\u0016\u00e0Vv\u00fe\u0081-<\u000f\u00899 \u00eb\u0086\u00d7h\u00da\u0015\u00a08}P\u00ca\u0001T\u00f8&\u007f\u00b4+\u00d6A\\\u0097\u00de\u00d8\u00dbe\u00f1\u001cP\u0088-\u009a\u0014\u0090\u00feH\u0099\u00f9\u00cb(3\u0007\u00b1\u009ca8cq\u0003\u00a5*\u0004\u00e2\u00dc\u008d\u00b9]\u00c3\u000b\u0096xt\u00fd\u000b\u00e2\u00d2\u00f7d\u008e\t\u00f5m\u00ccv\u0087\u00b7K3\u000b[r\u0011\u00d6Rs\u0085\u0081>&(\u0080\u00f6\u00f0\u00c9\u00e3\u00b6\u00c1\u00eeu9\u00a3\u000e\u0086a[\u00f6;>P\u009c$\u00c0\u0017+\u00e1\n\u00b2M\u0013\u001a\f\u008ev\u0099\u00c7\u00b1pr\u001c\u0015\u0094\u0086'\u0018\u00c0\u0095\u00d1\u001fV5\u000e\u00f8{\u00c4\u009aV\u00f3W\u00b7\u00f2\u007f59\u001e\u00ff#\u00c1\u00a0\u00b0\u00d1\u0015\u00e6\u00e2S\u00fbE\u00cd\u0087?\u00d0\u00e0\u008ekEF)\u00cb\u00fehM\u0010\u00df3\u0095\u008e\u00e8\u000f+\u00a9h\u00c7\u00ea\u00b2\u00ec\n$\u00d1LGs\u00bf\u00b9\u00b3\u00b4\u0003\u00d6dt4$\u00d2O\u001d=\u00c3\u00dd\u00e1\\%\u00ca_|\u00e5e\u00dc\"\u0081\u00dbW\u00ae#\u0089\t9\u00b1G:\u00df\u00cbE\t\u00c0\u001d;x\u00d1\u000e9x\n\u00bf%\u00eeU\u0007-\u00df\u0099(\u00ee\u0010\u00e3\u00a3\u00e6x\u00f5\u00df^\u008f\u00ee\u00e7M\u0014_\u0099\u00a0\u0006kU\u0096!\u00ec\u00b0_\u00d2\u00cd&I9\u00ab\u00af}(\u00bfH\u00fekBXM\u0097i[\b\u0098d\u00fc\u0083\u001f\u00cf2\u0001\u008d#q~\u00a3'-\u00e8\u00a7\u00b7b\u0084\u00ba\u00a7\u0091\u00abec\u00fe\u00c4\u00ce\u0095\u0092Zm\u00bbWRc\u00b2{\u008f\u00c7F\u00caD\u00d0k\u00f9\u00f7\u00c6\u00f6\u0098\u00bd\u00f1\u00f6$\u001c\u008db\u00e8\u001c\u00b83\u00cfPb\u00f1~\u00f3\u00df\u00a8\u0086\u00b8\u0097\u00f2\u00dag\u0098-\u0083^;\u0098x\u00b0\u0094\u008d\u0000{\u00f5\u00ce\u00b9\u000b\u00e7\u0099\u00fc\u00a1V\u00ae\u00b1&\u00d3\u0084\u007f\u00f6\u001a\u00e3+g\u00de\u00d6\u0088;\u008b\u00fd\\\u000e\u001c\u0013\u0014\u00dc\u00d1\u00d5\u00d17\u0003\u0093\u00cbP\u00c3\u00d5.rM\u0011}\u000f\u00cd\u00d6q\u0019\u00a1\u00a8\n\u00b7 \u00b4\u00d26\u0084\u001c\u00e1\u00bc\u0094\b\u00ddd\u00ac\u00b7A1\u00c1\u00a2\u008bi\u00a4\u00bf\r\u001b\u00c9ob\u00aa\u00aa:3\u00d5\u0099 \n\u0098$\u001b5~\u0092\u0096oW\u00a0AhX\u00f2_\u00f1\u00a8M\u0019T\u00dcKuC\u00e3~\"J\u008e\u000f\u008d(JJ\u000e\u0013\u00ac\u00d7\u00a4\u00ea\u00d4R\u0003D\u00e4\t$\u00e3Ci\u0083\f%bf\u00e5\u00dc\u00fd\u00fb\u00d5\u0092\u00c4\"\u0017\u00df\u00f4\u00ba8\u00e5\r\u0013\u00e40\u000f\u0092\u00c6\u00d3\u00c8\u00c0\u0016\u001e\u00ae\u00fd1h\u009cja\u00152A\u00b3 \u009f\u00ff^\u00a5\u0017\u00999\u0088\u000e\u00f9\u0093Ub\u00fb\u00e3W\u0093Vd\u009f@O\u0017v\u0012\u0094z>".length();
                var3_7 = 48;
                var2_8 = -1;
                lbl20:
                // 2 sources

                while (true) {
                    v3 = ++var2_8;
                    v4 = var4_5.substring(v3, v3 + var3_7);
                    v5 = -1;
                    break block10;
                    break;
                }
                lbl25:
                // 1 sources

                while (true) {
                    var7_3[var5_4++] = StateBlockStorage.a(var8_9).intern();
                    if ((var2_8 += var3_7) < var6_6) {
                        var3_7 = var4_5.charAt(var2_8);
                        ** continue;
                    }
                    var4_5 = "\u0093\u0098\r\u0096<\u00cb\u0094\u00d6\u00f2\u00ca\u00eb\u000e(\u00f3\u0097j\u00c1\u00d1\u00a9\u00d44u\u00ad\u00ff \u00b9\u00008?\u00c9\u000f\u009dPb\u009d\u00a4/\u00e0\u00dayr\u00c3~\u00e7G\u00f9\u0003/0\u0019c\u0005g<\u000e\u00e5QA]]\u00ef\u0016\u00bd\u00e1\u009b\u00a3\u00af\u0016\u0087\u00a7\u00e6\u0086\u0005\u00b3\u0090\u00e9\u00a5\u00d0\u00ea\u00fa\u00bd>\u00c7\u00e5#\u00df\u00cb\u00fe+$\u008e-\\\u00c1\u0003b,\u00fd%\u00f2\u0011\u00fb_<>\u0094\f\u00ef\u009e\u00cd\u00f1d\u0006";
                    var6_6 = "\u0093\u0098\r\u0096<\u00cb\u0094\u00d6\u00f2\u00ca\u00eb\u000e(\u00f3\u0097j\u00c1\u00d1\u00a9\u00d44u\u00ad\u00ff \u00b9\u00008?\u00c9\u000f\u009dPb\u009d\u00a4/\u00e0\u00dayr\u00c3~\u00e7G\u00f9\u0003/0\u0019c\u0005g<\u000e\u00e5QA]]\u00ef\u0016\u00bd\u00e1\u009b\u00a3\u00af\u0016\u0087\u00a7\u00e6\u0086\u0005\u00b3\u0090\u00e9\u00a5\u00d0\u00ea\u00fa\u00bd>\u00c7\u00e5#\u00df\u00cb\u00fe+$\u008e-\\\u00c1\u0003b,\u00fd%\u00f2\u0011\u00fb_<>\u0094\f\u00ef\u009e\u00cd\u00f1d\u0006".length();
                    var3_7 = 32;
                    var2_8 = -1;
                    lbl34:
                    // 2 sources

                    while (true) {
                        v6 = ++var2_8;
                        v4 = var4_5.substring(v6, v6 + var3_7);
                        v5 = 0;
                        break block10;
                        break;
                    }
                    break;
                }
                lbl39:
                // 1 sources

                while (true) {
                    var7_3[var5_4++] = StateBlockStorage.a(var8_9).intern();
                    if ((var2_8 += var3_7) < var6_6) {
                        var3_7 = var4_5.charAt(var2_8);
                        ** continue;
                    }
                    break block11;
                    break;
                }
            }
            var8_9 = var0_1.doFinal(v4.getBytes("ISO-8859-1"));
            switch (v5) {
                default: {
                    ** continue;
                }
                ** case 0:
                    lbl51:
                    // 1 sources

                ** continue;
            }
        }
        StateBlockStorage.f = var7_3;
        StateBlockStorage.g = new String[12];
        StateBlockStorage.c = LogManager.getLogger((String)StateBlockStorage.a("l", (int)21532, (long)(8994326815661070827L ^ var9)));
    }

    private static Throwable a(Throwable throwable) {
        return throwable;
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

    private static String a(int n2, long l2) {
        int n3 = n2 ^ (int)(l2 & 0x7FFFL) ^ 0x788D;
        if (g[n3] == null) {
            Object[] objectArray;
            try {
                Long l3 = Thread.currentThread().getId();
                objectArray = (Object[])h.get(l3);
                if (objectArray == null) {
                    objectArray = new Object[]{Cipher.getInstance("DES/CBC/PKCS5Padding"), SecretKeyFactory.getInstance("DES"), new IvParameterSpec(new byte[8])};
                    h.put(l3, objectArray);
                }
            }
            catch (Exception exception) {
                throw new RuntimeException("cn/nukkit/level/format/leveldb/structure/StateBlockStorage", exception);
            }
            byte[] byArray = new byte[8];
            byArray[0] = (byte)(l2 >>> 56);
            for (int i2 = 1; i2 < 8; ++i2) {
                byArray[i2] = (byte)(l2 << i2 * 8 >>> 56);
            }
            DESKeySpec dESKeySpec = new DESKeySpec(byArray);
            SecretKey secretKey = ((SecretKeyFactory)objectArray[1]).generateSecret(dESKeySpec);
            ((Cipher)objectArray[0]).init(2, (Key)secretKey, (IvParameterSpec)objectArray[2]);
            byte[] byArray2 = f[n3].getBytes("ISO-8859-1");
            StateBlockStorage.g[n3] = StateBlockStorage.a(((Cipher)objectArray[0]).doFinal(byArray2));
        }
        return g[n3];
    }

    private static Object a(MethodHandles.Lookup lookup, MutableCallSite mutableCallSite, String string, Object[] objectArray) {
        int n2 = (Integer)objectArray[0];
        long l2 = (Long)objectArray[1];
        String string2 = StateBlockStorage.a(n2, l2);
        MethodHandle methodHandle = MethodHandles.constant(String.class, string2);
        mutableCallSite.setTarget(MethodHandles.dropArguments(methodHandle, 0, Integer.TYPE, Long.TYPE));
        return string2;
    }

    private static CallSite a(MethodHandles.Lookup lookup, String string, MethodType methodType) {
        MutableCallSite mutableCallSite = new MutableCallSite(methodType);
        try {
            mutableCallSite.setTarget(MethodHandles.explicitCastArguments(MethodHandles.insertArguments(cfr_ldc_0().asCollector(Object[].class, methodType.parameterCount()), 0, lookup, mutableCallSite, string), methodType));
        }
        catch (Exception exception) {
            throw new RuntimeException("cn/nukkit/level/format/leveldb/structure/StateBlockStorage" + " : " + string + " : " + methodType.toString(), exception);
        }
        return mutableCallSite;
    }

    /*
     * Works around MethodHandle LDC.
     */
    static MethodHandle cfr_ldc_0() {
        try {
            return MethodHandles.lookup().findStatic(StateBlockStorage.class, "getBitArrayVersionByHeader", MethodType.fromMethodDescriptorString("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", null));
        }
        catch (NoSuchMethodException | IllegalAccessException except) {
            throw new IllegalArgumentException(except);
        }
    }
}

