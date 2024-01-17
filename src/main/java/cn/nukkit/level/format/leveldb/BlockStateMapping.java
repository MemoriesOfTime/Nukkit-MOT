/*
 * Decompiled with CFR 0.152.
 */
package cn.nukkit.level.format.leveldb;

import cn.nukkit.level.format.leveldb.LegacyStateMapper;
import cn.nukkit.level.format.leveldb.NukkitLegacyMapper;
import cn.nukkit.level.format.leveldb.a;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.updater.BlockStateUpdaterChunker;
import cn.nukkit.level.format.leveldb.updater.BlockStateUpdaterVanilla;
import cn.nukkit.utils.MainLogger;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.network.util.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import lombok.extern.log4j.Log4j2;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudburstmc.blockstateupdater.*;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.lang.invoke.*;
import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Log4j2
public class BlockStateMapping {
    private static final Logger m;
    private static final Logger f;
    private static final int j = 284;
    private static final CompoundTagUpdaterContext e;
    private static final int a;
    private static final BlockStateMapping b;
    private static final ExpiringMap<NbtMap, NbtMap> l;
    private final Int2ObjectMap<BlockStateSnapshot> h = new Int2ObjectOpenHashMap<BlockStateSnapshot>();
    private final Object2ObjectMap<NbtMap, BlockStateSnapshot> g = new Object2ObjectOpenCustomHashMap<NbtMap, BlockStateSnapshot>(new a(this));
    private final int k;
    private LegacyStateMapper d;
    private int i = -1;
    private BlockStateSnapshot c;
    private static final long n;
    private static final String[] o;
    private static final String[] p;
    private static final Map q;

    public static BlockStateMapping get() {
        return b;
    }

    public BlockStateMapping(int n2) {
        this(n2, null);
    }

    public BlockStateMapping(int n2, LegacyStateMapper legacyStateMapper) {
        this.k = n2;
        this.d = legacyStateMapper;
    }

    public void a(int n2, NbtMap nbtMap, long l2) {
        l2 = n ^ l2;
        Preconditions.checkArgument(!this.h.containsKey(n2), (String)((Object)BlockStateMapping.a("c", (int)5635, (long)(0x34D6F3D66EEEBE27L ^ l2))) + n2 + (String)((Object)BlockStateMapping.a("c", (int)11502, (long)(0xAE6EA90F0AB84C8L ^ l2))));
        Preconditions.checkArgument(!this.g.containsKey(nbtMap), (String)((Object)BlockStateMapping.a("c", (int)3520, (long)(0x7CA96860E095A5FDL ^ l2))) + nbtMap);
        BlockStateSnapshot blockStateSnapshot = BlockStateSnapshot.l().b(this.k).a(nbtMap).a(n2).a();
        this.h.put(n2, blockStateSnapshot);
        this.g.put(nbtMap, blockStateSnapshot);
    }

    public void b() {
        this.h.clear();
        this.g.clear();
    }

    public BlockStateSnapshot b(int n2, int n3, long l2) {
        long l3 = l2 = n ^ l2;
        long l4 = l3 ^ 0x4C71BF7E7F65L;
        long l5 = l3 ^ 0x353C0062E615L;
        int n4 = this.d.a(n2, n3);
        if (n4 == -1) {
            m.warn((String)((Object)BlockStateMapping.a("c", (int)10265, (long)(0x42BF43C344CBBDB4L ^ l2))) + n2 + ":" + n3);
            return this.c(l4);
        }
        return this.getById(n4);
    }

    public BlockStateSnapshot getById(int id) {
        BlockStateSnapshot blockStateSnapshot = this.h.get(id);
        if (blockStateSnapshot == null) {
            m.warn((String)((Object)BlockStateMapping.a("c", (int)18016, (long)(0x12089897E4D0F999L ^ damage))) + id);
            return this.c(l3);
        }
        return blockStateSnapshot;
    }

    public BlockStateSnapshot a(NbtMap nbtMap, long l2) {
        long l3 = (l2 = n ^ l2) ^ 0x18C8CD6AF5ABL;
        BlockStateSnapshot blockStateSnapshot = (BlockStateSnapshot)this.g.get(nbtMap);
        if (blockStateSnapshot == null) {
            m.warn((String)((Object)BlockStateMapping.a("c", (int)13381, (long)(0x74AC469857DF2B20L ^ l2))) + nbtMap);
            return this.c(l3);
        }
        return blockStateSnapshot;
    }

    public BlockStateSnapshot g(NbtMap nbtMap) {
        return (BlockStateSnapshot)this.g.get(nbtMap);
    }

    public int a(int n2, int n3, long l2) {
        long l3 = (l2 = n ^ l2) ^ 0xAC7E9051C61L;
        int n4 = this.d.a(n2, n3);
        if (n4 == -1) {
            m.warn((String)((Object)BlockStateMapping.a("c", (int)18645, (long)(0x2A4F7D2BF831B9AAL ^ l2))) + n2 + ":" + n3);
            return this.a(l3);
        }
        return n4;
    }

    public int d(int n2, long l2) {
        long l3 = (l2 = n ^ l2) ^ 0xE6BF28004E3L;
        int n3 = this.d.c(n2);
        if (n3 == -1) {
            m.warn((String)((Object)BlockStateMapping.a("c", (int)18703, (long)(0x1F2AE401AAB6A0F0L ^ l2))) + n2);
            n3 = this.d.c(this.a(l3));
            Preconditions.checkArgument(n3 != -1, (String)((Object)BlockStateMapping.a("c", (int)2018, (long)(0x69BE9A21FDC36E11L ^ l2))) + this.a(l3));
        }
        return n3;
    }

    public int c(int n2, long l2) {
        long l3 = (l2 = n ^ l2) ^ 0x563FD51D20C2L;
        int n3 = this.d.b(n2);
        if (n3 == -1) {
            m.warn((String)((Object)BlockStateMapping.a("c", (int)393, (long)(0x2418F22013CBCC4AL ^ l2))) + n2);
            n3 = this.d.b(this.a(l3));
            Preconditions.checkArgument(n3 != -1, (String)((Object)BlockStateMapping.a("c", (int)25100, (long)(0x5B75E4398359AFDFL ^ l2))) + this.a(l3));
        }
        return n3;
    }

    public int a(int n2, long l2) {
        long l3 = (l2 = n ^ l2) ^ 0x72F13159EC41L;
        int n3 = this.d.a(n2);
        if (n3 == -1) {
            m.warn((String)((Object)BlockStateMapping.a("c", (int)30962, (long)(0x16703A9B424279A9L ^ l2))) + n2);
            n3 = this.d.a(this.a(l3));
            Preconditions.checkArgument(n3 != -1, (String)((Object)BlockStateMapping.a("c", (int)1525, (long)(0x455C51694F4584A3L ^ l2))) + this.a(l3));
        }
        return n3;
    }

    public void c(int n2, int n3, long l2) {
        l2 = n ^ l2;
        int n4 = this.d.a(n2, n3);
        Preconditions.checkArgument(n4 != -1, (String)((Object)BlockStateMapping.a("c", (int)20328, (long)(0x30859E44B9E55A9L ^ l2))) + n2 + ":" + n3);
        this.i = n4;
        BlockStateSnapshot blockStateSnapshot = (BlockStateSnapshot)this.h.get(n4);
        Preconditions.checkNotNull(blockStateSnapshot, (String)((Object)BlockStateMapping.a("c", (int)4632, (long)(0x25D13D65BF7708CFL ^ l2))) + n2 + ":" + n3);
        this.c = blockStateSnapshot;
    }

    public int a(long l2) {
        long l3 = (l2 = n ^ l2) ^ 0xABE8B70F7C1L;
        if (this.i == -1) {
            this.c(284, 0, l3);
        }
        return this.i;
    }

    public BlockStateSnapshot c(long l2) {
        long l3 = (l2 = n ^ l2) ^ 0xBA82DAF01CL;
        if (this.c == null) {
            this.c(284, 0, l3);
        }
        return this.c;
    }

    public BlockStateSnapshot f(NbtMap nbtMap) {
        long l2 = n ^ 0x3D4E669C88AFL;
        long l3 = l2 ^ 0x656B3C27E12AL;
        BlockStateSnapshot blockStateSnapshot = (BlockStateSnapshot)this.g.get(nbtMap);
        if (blockStateSnapshot == null) {
            blockStateSnapshot = this.c(nbtMap, l3);
        }
        return blockStateSnapshot;
    }

    public BlockStateSnapshot c(NbtMap nbtMap, long l2) {
        long l3 = l2 = n ^ l2;
        long l4 = l3 ^ 0x44D2A717C5E7L;
        long l5 = l3 ^ 0x723254FBA03EL;
        return this.a(this.b(nbtMap, l4), l5);
    }

    public BlockStateSnapshot d(NbtMap nbtMap, long l2) {
        long l3 = l2 = n ^ l2;
        long l4 = l3 ^ 0x58137AC5CDFCL;
        long l5 = l3 ^ 0x6EF38929A825L;
        if (this.g.get(nbtMap) == null) {
            return this.a(this.b(nbtMap, l4), l5);
        }
        return null;
    }

    public NbtMap b(NbtMap nbtMap, long l2) {
        l2 = n ^ l2;
        NbtMap nbtMap2 = l.get(nbtMap);
        if (nbtMap2 == null) {
            int n2 = nbtMap.getInt((String)((Object)BlockStateMapping.a("c", (int)18622, (long)(0xD9001AA709BB203L ^ l2))));
            nbtMap2 = e.update(nbtMap, a == n2 ? n2 - 1 : n2);
            l.put(nbtMap, nbtMap2);
        }
        return nbtMap2;
    }

    public BlockStateSnapshot e(NbtMap nbtMap, long l2) {
        long l3 = (l2 = n ^ l2) ^ 0x1DDBC3AA7180L;
        return this.a(nbtMap, this.b(nbtMap, l3));
    }

    public BlockStateSnapshot a(NbtMap nbtMap, NbtMap nbtMap2) {
        long l2 = n ^ 0xC87365C61A7L;
        long l3 = l2 ^ 0x3E58F5765DB7L;
        BlockStateSnapshot blockStateSnapshot = this.g(nbtMap2);
        if (blockStateSnapshot != null) {
            return blockStateSnapshot;
        }
        return BlockStateSnapshot.l().a(nbtMap).a(this.c(l3).getRuntimeId()).b(this.k).a(true).a();
    }

    public void a(LegacyStateMapper legacyStateMapper) {
        this.d = legacyStateMapper;
    }

    public LegacyStateMapper e() {
        return this.d;
    }

    public int d() {
        return this.k;
    }

    private static /* synthetic */ void lambda$static$0(CompoundTagUpdaterContext compoundTagUpdaterContext, BlockStateUpdater blockStateUpdater) {
        blockStateUpdater.registerUpdaters(compoundTagUpdaterContext);
    }

    /*
     * Unable to fully structure code
     */
    static {
        block12: {
            block11: {
                BlockStateMapping.n = cn.nukkit.block.o.a(-6070458968663702330L, 5789674972446401269L, MethodHandles.lookup().lookupClass()).a(58915786172397L);
                var9 = BlockStateMapping.n ^ 45941112266836L;
                BlockStateMapping.q = new HashMap<K, V>(13);
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
                var7_3 = new String[20];
                var5_4 = 0;
                var4_5 = "\u00be\u00a0e\u00b3l\\\u00b2\u0086\u008ck\u00f8tL\u00f5>\u00aa\u00ab\u0004\u0097\u00bfu\u00fe<\u00d1\u009f\u00108d\u00da\u00ab\u009b\u00a4h\u0011\u00a9\u0015\u00fe\u00a9\u0090\u00f7\u00f3\u00af\u00c70\u009c\u00fb\u00bdg\u00bd8\u0091\u00811\u0005\u0015\u0086\u00c1n\u009b\u009f\u00d1\u0087l\"\u008f\u0003\u00ac\u00d9Fs\u00df\u00eeP\u00ea\u000bv\u0092\u001c\u00da\u00ee\u00a2\u0002Rt3w+\u0093\u0098\u00fbT\u0013m\u0095\u0091\u00e59\u009b\u00eb\u00ad\u00a2\u0097ql\u00dbM\u00ebAU\u0086J7hg\u00ce\u0080\u00fc4o\u0090p&&a\u001b\u00d4\u0090\u00d0\u00840\u0015\u00f0j2\u00c5\u0091s\u00e5\u0099\u00c2\u0094\u008d\u00a3\u00d6\u0098\u00d2k\u0001or\u0016\u00feZ\u0010\u00dc+\u00af)\u00e8\u0010\u00d6\u00d9\u00d2g\u00e4\u00b0\u0013I\u0003\f8\u00a5U\u0092\u008477\u008e\u008ds7\u00e2\u008cJB\u0001j\u00e4\u00f0\n\u00d7\u007f\u00a9\u00f3']\u00df\u0014\u00e9\u00d0\u0081\u0098\u00d5\u00fb\u009b\u001a\u00a84\u00ce\u00af\u00b5]\u00d2<\u00b9\u00cdWXmc\u0082>\u00af?\u0092\u00ecr\u0018\u00a7?3\"\u00e0\u009bz\u000e\u00c1pe\u00d9Ore\u00e0R\u00f2k2\u00ad\u008a\u00b3\tX\u000fK\u0087!9P\u00a5F\u009a\u009b\u00c0\u00826r\u00e1\u00e25:l\u0093\u00ee\u00d4\u0099\u00c1\u00a9\u00c1q\u0086Hz\u00ae\u008a\u00d4\u00b4\u00bf\u00ca\u00bc\u00cbr\u00b3\u00f0d\u0088=t\\\u00bc\u00ccW\u00cf\u00b7\u00a2\"\u007f\u001e8\u00ec\u00cd\u00f67J\u00c2G\u000e\u00e5\u0019J\u0090Y\\\u00d7\u0013\u0011\u009d\u00b6\u0082.\f\u00f3\u00b9l\u0012\u00e2\u00efC\u00c0\u00d7\r@\u00c8\u00c2\u0096nfn\u0093\u000619\u00b9\u00f0\u00fd\u00a6#\u00a3\u007fu*\u00b1!\u00c4\u00bf\"\b{4o\u00b8\u0016\u00e3z\u0081\u00bb@\u008bv\n\u009f\u00feq\u0013Are\u00f6TL\u0098\u00bf\u0012CU5M\u0018I\u0014\u00bb\u0098\u0001\u007f\u00d2\u00cbH\u00d0\u000f\r\u0080\u009f\u00ce]\u00d1\u00c2#\u00ef\u00f9l\u00f4\u00f7\u0002d\u00ac%e\u00b69\u001c\u00be\u00d8`]>g\u00dbK\u0081\u00e82\npv\u00c8\u0010\u00cd\u008ed\u00dc\u00aef\u0000\u0082\u00f0\u0093);^\u00a8K\u0090\u0019\u001f\u00fd^\u0081B\u0095\u00d0e#\u00e4\u00ab\u00a3\u00b2\u008cl\u00c9X\u0093\u00e7%\u00f3\u0016N\u009c\u00c3\u0017\u000fv\u0082\u00c7\u0004(\u00c9/\u0013\u0017 \u00de\u001d\u00cc\u00ef\u001a\u00c6Q?c\u0093@\u00c9\u00e0\u00f3<\t\n\u00ca<\u00bf@D\u00abp\u00d1\u00de\u0019\u0011}\u0014\u001eoq&*\u00976e&\u00038woE\u00ef\u00a2N\u0004Y;\u0007\t4\u001b\u008e!\u008c\u00e4\u008b\u0085w\u00d6\n\u0000\u0097A\u00de\u00aa(\u0011\u0006Y+\u00b7\u00a6\u0010\u00e7\u009d]Xa\u0086J \u00e21\u0015|m\u00c9\u00edfV9\u008eB\u001br\u00a3\u001c8\u00fdS\u000f\u00e0\u00f6i\u008a\u00b9X`ICC\u001e8\u00810\u00fb4\u001fX\u00cf\u0085c5r\u0092\u008f]N\u00d4\bCA\u009f\u00a4v?\u00c31\u00a1V\u00880Z\u00ef\u00f8\u00ce\u00a9\\\u00d0\u0098\u00c3\u0084\u00e7\u00f9\u00f6`|\u00d4\u00dd\b}4,\u00c0\u00a9\u00c2\u008fa\u00cc4)nk\u0088\u0093ks\u0016\u00ad*\u00c4\u0095gwP\u0089\u0014\u00e0O\u00c0[\u00caXwE(\u00f8\u0093\u001d8\u0011\u00da\u00fetr\u0095v\u00c7\u00a4<\u00d1\u00f0\u0013\u001b\u0097}Bf#h\u00e5^\u00b6\u00ba\u00f2\u00a4\f>\u009f\u00eefX\u00a47L\u00baX+\u00ba\r!\u00b9k\u007fJ\u00f9\u00b8\u00a4\f4\u00b0\r\u00bdr\u00a0[\u000b\u0093|\u0007\u009f\u00d2\u00b4\u00f9\u0016\u00f0\u00f7\u0013\u00dd\u00c5\u008b\u0005!B\u00ab\u00d7\u00d1\u00c1\u00df'_\u00a5AT\u009e\u001dY\u0013\u00a8\u00b4\u00ca\u00e5\u00e9b\u00cd 4$=b\u0014\u0097\u00c4uB\u00b6\u0015\u00a0\u00cb3\u00ad\u00f1py\u009e\u0011H\u001c\u00dcl6\u008e\u00fb\u001a?x\u00fd\u00f9\u00ee\u00f83@\u00db\u008aS\u00f7\u00d35\u00fa\u00ca\u0094\u00e2L\u00d8s\u00a1\u0084\u00f7~\u0099>y\u00d0\u00f9\u00b3R\u00f1\u008ek\u008fiX\u009b\u00e3_[\u001b,\u00c9\n\u0087\u00a7\u00e0bo\u00fc\u00d7x\u00bd\u00ee\u00eb\u00039\u00a8e\u00a9\u00f0\u00f8\u000f\u00d9\u0004\u00aerM<\u009c\u00c6_\u0006\u0082\u0090\u00bd]\u00b4\u001a\u0006a\u00d3\fQt\u0017\u0085\u00ad\u00ed+z\u00e7\u00fb\u00b52c\u0003\u00d7\u0005\u0099\u00b4\ft\u00da\u00f7\u00bf0T=\u00efY\u00c7\u00d6\u0087}\u00bb\u00c6\u001c\u0007GX\u001c\u008d\u00fcpH\u0012\u008d\u001cz\u00b9c\u008a\u0011v\u0084\u000e\u008a\u00f2\u008aak\u009e\u0011LEb\u00a8\u00afj\u00d5\u0085\u00ac\u00e3\u00bb\u009cU\u0000\u00f3\u0092\u0006\u0098\u00fc.$\u00a22\u008b\u00ab\u009d\u0014\u008c\u0088\u00d7\u00a4\u00a4\u00ce\\\u00f8B0+\u00a2y`\u0093)+}\u001f\u0094\u009eCw\u00a0\u001e\u009a\u00d1\u0011\u00e5=d\u00d8^\u00a7\u00e2t:\u00ce \u00ba2@\u00a7\u00f3\u00e4\u0088'i\u00ad\u00bb]\u0019\u0014AZme}\u00a0\u00ddo9\u00f6Ox\u0012\u00d0\\\u00cb0\u0019X\u00be\u008c\u00e3\u0099\u00d4\u00cf\u00ee\u00dd\u001f\u0094_\u00a4^!\u0016&]Y8\u00d4\u001b\u0000\u000f\u00ddG\u00d4\u0094\u00f5\u0087\u0095\u00dd\u009a\u00b4\u00b3\u00c5yG\u00ae\u0088\u00a8\u00b7\u0015\u00f7)\u00b5\u00e6Q\u000b\u00b4\u00ceU\u00d1\u00a3wf\u0088^m\u00e9Np\u0010\u00b1\u00e38\u0002\u00165\u00a6\f\u00c1\u00f1\u0094\u00d4F\u00f8\u001a^\u00113\u0082\u001cA\u0095\u0086\u0011x\u0093X\u00b5\u00e8\u00c8\u00987J)\u00c6\u009a\u00c6Y\u00b7\u00cd\u00f3\u00d9\u001e;\u00cdW\u00cf\u009f\u0001P\u00c4x\u00ab\u00b6\u00c6M\u00ad\u00be1\u0015{\u00cd\u00fb\u00dc\u0002q\u0084\u00dc\u009c\u00d0\u00b9C`\u00f0]\u001a\u00a2\u00d2\u00e3\u008bn\u00059\u00f7\u00c7\u0092\u00aa\u0002\u00db\u008a&dT\u00ff\u00ec\u0097\u00e8\u0014\u00f7\u00a5\u008c\u00e9\u008bQ\u00c4\u00a1,\u0000nE\u001a\u008aa+\u000e";
                var6_6 = "\u00be\u00a0e\u00b3l\\\u00b2\u0086\u008ck\u00f8tL\u00f5>\u00aa\u00ab\u0004\u0097\u00bfu\u00fe<\u00d1\u009f\u00108d\u00da\u00ab\u009b\u00a4h\u0011\u00a9\u0015\u00fe\u00a9\u0090\u00f7\u00f3\u00af\u00c70\u009c\u00fb\u00bdg\u00bd8\u0091\u00811\u0005\u0015\u0086\u00c1n\u009b\u009f\u00d1\u0087l\"\u008f\u0003\u00ac\u00d9Fs\u00df\u00eeP\u00ea\u000bv\u0092\u001c\u00da\u00ee\u00a2\u0002Rt3w+\u0093\u0098\u00fbT\u0013m\u0095\u0091\u00e59\u009b\u00eb\u00ad\u00a2\u0097ql\u00dbM\u00ebAU\u0086J7hg\u00ce\u0080\u00fc4o\u0090p&&a\u001b\u00d4\u0090\u00d0\u00840\u0015\u00f0j2\u00c5\u0091s\u00e5\u0099\u00c2\u0094\u008d\u00a3\u00d6\u0098\u00d2k\u0001or\u0016\u00feZ\u0010\u00dc+\u00af)\u00e8\u0010\u00d6\u00d9\u00d2g\u00e4\u00b0\u0013I\u0003\f8\u00a5U\u0092\u008477\u008e\u008ds7\u00e2\u008cJB\u0001j\u00e4\u00f0\n\u00d7\u007f\u00a9\u00f3']\u00df\u0014\u00e9\u00d0\u0081\u0098\u00d5\u00fb\u009b\u001a\u00a84\u00ce\u00af\u00b5]\u00d2<\u00b9\u00cdWXmc\u0082>\u00af?\u0092\u00ecr\u0018\u00a7?3\"\u00e0\u009bz\u000e\u00c1pe\u00d9Ore\u00e0R\u00f2k2\u00ad\u008a\u00b3\tX\u000fK\u0087!9P\u00a5F\u009a\u009b\u00c0\u00826r\u00e1\u00e25:l\u0093\u00ee\u00d4\u0099\u00c1\u00a9\u00c1q\u0086Hz\u00ae\u008a\u00d4\u00b4\u00bf\u00ca\u00bc\u00cbr\u00b3\u00f0d\u0088=t\\\u00bc\u00ccW\u00cf\u00b7\u00a2\"\u007f\u001e8\u00ec\u00cd\u00f67J\u00c2G\u000e\u00e5\u0019J\u0090Y\\\u00d7\u0013\u0011\u009d\u00b6\u0082.\f\u00f3\u00b9l\u0012\u00e2\u00efC\u00c0\u00d7\r@\u00c8\u00c2\u0096nfn\u0093\u000619\u00b9\u00f0\u00fd\u00a6#\u00a3\u007fu*\u00b1!\u00c4\u00bf\"\b{4o\u00b8\u0016\u00e3z\u0081\u00bb@\u008bv\n\u009f\u00feq\u0013Are\u00f6TL\u0098\u00bf\u0012CU5M\u0018I\u0014\u00bb\u0098\u0001\u007f\u00d2\u00cbH\u00d0\u000f\r\u0080\u009f\u00ce]\u00d1\u00c2#\u00ef\u00f9l\u00f4\u00f7\u0002d\u00ac%e\u00b69\u001c\u00be\u00d8`]>g\u00dbK\u0081\u00e82\npv\u00c8\u0010\u00cd\u008ed\u00dc\u00aef\u0000\u0082\u00f0\u0093);^\u00a8K\u0090\u0019\u001f\u00fd^\u0081B\u0095\u00d0e#\u00e4\u00ab\u00a3\u00b2\u008cl\u00c9X\u0093\u00e7%\u00f3\u0016N\u009c\u00c3\u0017\u000fv\u0082\u00c7\u0004(\u00c9/\u0013\u0017 \u00de\u001d\u00cc\u00ef\u001a\u00c6Q?c\u0093@\u00c9\u00e0\u00f3<\t\n\u00ca<\u00bf@D\u00abp\u00d1\u00de\u0019\u0011}\u0014\u001eoq&*\u00976e&\u00038woE\u00ef\u00a2N\u0004Y;\u0007\t4\u001b\u008e!\u008c\u00e4\u008b\u0085w\u00d6\n\u0000\u0097A\u00de\u00aa(\u0011\u0006Y+\u00b7\u00a6\u0010\u00e7\u009d]Xa\u0086J \u00e21\u0015|m\u00c9\u00edfV9\u008eB\u001br\u00a3\u001c8\u00fdS\u000f\u00e0\u00f6i\u008a\u00b9X`ICC\u001e8\u00810\u00fb4\u001fX\u00cf\u0085c5r\u0092\u008f]N\u00d4\bCA\u009f\u00a4v?\u00c31\u00a1V\u00880Z\u00ef\u00f8\u00ce\u00a9\\\u00d0\u0098\u00c3\u0084\u00e7\u00f9\u00f6`|\u00d4\u00dd\b}4,\u00c0\u00a9\u00c2\u008fa\u00cc4)nk\u0088\u0093ks\u0016\u00ad*\u00c4\u0095gwP\u0089\u0014\u00e0O\u00c0[\u00caXwE(\u00f8\u0093\u001d8\u0011\u00da\u00fetr\u0095v\u00c7\u00a4<\u00d1\u00f0\u0013\u001b\u0097}Bf#h\u00e5^\u00b6\u00ba\u00f2\u00a4\f>\u009f\u00eefX\u00a47L\u00baX+\u00ba\r!\u00b9k\u007fJ\u00f9\u00b8\u00a4\f4\u00b0\r\u00bdr\u00a0[\u000b\u0093|\u0007\u009f\u00d2\u00b4\u00f9\u0016\u00f0\u00f7\u0013\u00dd\u00c5\u008b\u0005!B\u00ab\u00d7\u00d1\u00c1\u00df'_\u00a5AT\u009e\u001dY\u0013\u00a8\u00b4\u00ca\u00e5\u00e9b\u00cd 4$=b\u0014\u0097\u00c4uB\u00b6\u0015\u00a0\u00cb3\u00ad\u00f1py\u009e\u0011H\u001c\u00dcl6\u008e\u00fb\u001a?x\u00fd\u00f9\u00ee\u00f83@\u00db\u008aS\u00f7\u00d35\u00fa\u00ca\u0094\u00e2L\u00d8s\u00a1\u0084\u00f7~\u0099>y\u00d0\u00f9\u00b3R\u00f1\u008ek\u008fiX\u009b\u00e3_[\u001b,\u00c9\n\u0087\u00a7\u00e0bo\u00fc\u00d7x\u00bd\u00ee\u00eb\u00039\u00a8e\u00a9\u00f0\u00f8\u000f\u00d9\u0004\u00aerM<\u009c\u00c6_\u0006\u0082\u0090\u00bd]\u00b4\u001a\u0006a\u00d3\fQt\u0017\u0085\u00ad\u00ed+z\u00e7\u00fb\u00b52c\u0003\u00d7\u0005\u0099\u00b4\ft\u00da\u00f7\u00bf0T=\u00efY\u00c7\u00d6\u0087}\u00bb\u00c6\u001c\u0007GX\u001c\u008d\u00fcpH\u0012\u008d\u001cz\u00b9c\u008a\u0011v\u0084\u000e\u008a\u00f2\u008aak\u009e\u0011LEb\u00a8\u00afj\u00d5\u0085\u00ac\u00e3\u00bb\u009cU\u0000\u00f3\u0092\u0006\u0098\u00fc.$\u00a22\u008b\u00ab\u009d\u0014\u008c\u0088\u00d7\u00a4\u00a4\u00ce\\\u00f8B0+\u00a2y`\u0093)+}\u001f\u0094\u009eCw\u00a0\u001e\u009a\u00d1\u0011\u00e5=d\u00d8^\u00a7\u00e2t:\u00ce \u00ba2@\u00a7\u00f3\u00e4\u0088'i\u00ad\u00bb]\u0019\u0014AZme}\u00a0\u00ddo9\u00f6Ox\u0012\u00d0\\\u00cb0\u0019X\u00be\u008c\u00e3\u0099\u00d4\u00cf\u00ee\u00dd\u001f\u0094_\u00a4^!\u0016&]Y8\u00d4\u001b\u0000\u000f\u00ddG\u00d4\u0094\u00f5\u0087\u0095\u00dd\u009a\u00b4\u00b3\u00c5yG\u00ae\u0088\u00a8\u00b7\u0015\u00f7)\u00b5\u00e6Q\u000b\u00b4\u00ceU\u00d1\u00a3wf\u0088^m\u00e9Np\u0010\u00b1\u00e38\u0002\u00165\u00a6\f\u00c1\u00f1\u0094\u00d4F\u00f8\u001a^\u00113\u0082\u001cA\u0095\u0086\u0011x\u0093X\u00b5\u00e8\u00c8\u00987J)\u00c6\u009a\u00c6Y\u00b7\u00cd\u00f3\u00d9\u001e;\u00cdW\u00cf\u009f\u0001P\u00c4x\u00ab\u00b6\u00c6M\u00ad\u00be1\u0015{\u00cd\u00fb\u00dc\u0002q\u0084\u00dc\u009c\u00d0\u00b9C`\u00f0]\u001a\u00a2\u00d2\u00e3\u008bn\u00059\u00f7\u00c7\u0092\u00aa\u0002\u00db\u008a&dT\u00ff\u00ec\u0097\u00e8\u0014\u00f7\u00a5\u008c\u00e9\u008bQ\u00c4\u00a1,\u0000nE\u001a\u008aa+\u000e".length();
                var3_7 = 72;
                var2_8 = -1;
lbl20:
                // 2 sources

                while (true) {
                    v3 = ++var2_8;
                    v4 = var4_5.substring(v3, v3 + var3_7);
                    v5 = -1;
                    break block11;
                    break;
                }
lbl25:
                // 1 sources

                while (true) {
                    var7_3[var5_4++] = BlockStateMapping.a(var8_9).intern();
                    if ((var2_8 += var3_7) < var6_6) {
                        var3_7 = var4_5.charAt(var2_8);
                        ** continue;
                    }
                    var4_5 = "\u00cc\u00f8\u00a47\u008a!\u00f7\u00c7\u0080\u001d\u00fe\u00c6>\u00cb\u00f8s\u007f\\\u001fB`\u008b$\u00b2\u008cPpy\u008c\u00ca\u0084\u00da\u00c3\u0080\u0013\u0097K\nUi\u00f9\u0087aqJ\u0083o\"\u00bcYsr\u008c\u0098\u00d7O@yp\r\u00ce\u0004\u0015U\u00fc\u0090\u00ea\u00dc\u00a7\u001d\u00f4q\u00ffx4\u0015\u00e5\u00b5\u00d2\u00df\u00b40QY\u0086\u0010#\u009c\u00bf\u00b1\u00eb\u00c5%\u00c7\u00dcmP\u00eenCB\u00fcR\u009e\u008e\u00c1\u00e5'\u000bQ\u00c2H\u00cdF\u00d0\u00e9\u0097\u0005D\u008f\u00a6";
                    var6_6 = "\u00cc\u00f8\u00a47\u008a!\u00f7\u00c7\u0080\u001d\u00fe\u00c6>\u00cb\u00f8s\u007f\\\u001fB`\u008b$\u00b2\u008cPpy\u008c\u00ca\u0084\u00da\u00c3\u0080\u0013\u0097K\nUi\u00f9\u0087aqJ\u0083o\"\u00bcYsr\u008c\u0098\u00d7O@yp\r\u00ce\u0004\u0015U\u00fc\u0090\u00ea\u00dc\u00a7\u001d\u00f4q\u00ffx4\u0015\u00e5\u00b5\u00d2\u00df\u00b40QY\u0086\u0010#\u009c\u00bf\u00b1\u00eb\u00c5%\u00c7\u00dcmP\u00eenCB\u00fcR\u009e\u008e\u00c1\u00e5'\u000bQ\u00c2H\u00cdF\u00d0\u00e9\u0097\u0005D\u008f\u00a6".length();
                    var3_7 = 56;
                    var2_8 = -1;
lbl34:
                    // 2 sources

                    while (true) {
                        v6 = ++var2_8;
                        v4 = var4_5.substring(v6, v6 + var3_7);
                        v5 = 0;
                        break block11;
                        break;
                    }
                    break;
                }
lbl39:
                // 1 sources

                while (true) {
                    var7_3[var5_4++] = BlockStateMapping.a(var8_9).intern();
                    if ((var2_8 += var3_7) < var6_6) {
                        var3_7 = var4_5.charAt(var2_8);
                        ** continue;
                    }
                    break block12;
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
        BlockStateMapping.o = var7_3;
        BlockStateMapping.p = new String[20];
        BlockStateMapping.m = LogManager.getLogger((String)BlockStateMapping.a("c", (int)22061, (long)(8366797286689726624L ^ var9)));
        BlockStateMapping.f = LogManager.getLogger(MainLogger.class);
        BlockStateMapping.b = new BlockStateMapping(594);
        BlockStateMapping.l = ExpiringMap.builder().maxSize(1024).expiration(60L, TimeUnit.SECONDS).expirationPolicy(ExpirationPolicy.ACCESSED).build();
        BlockStateMapping.b.a(new NukkitLegacyMapper());
        NukkitLegacyMapper.registerStates(BlockStateMapping.b);
        var11_10 = new ArrayList<BlockStateUpdater>();
        var11_10.add(BlockStateUpdaterBase.INSTANCE);
        var11_10.add(BlockStateUpdater_1_10_0.INSTANCE);
        var11_10.add(BlockStateUpdater_1_12_0.INSTANCE);
        var11_10.add(BlockStateUpdater_1_13_0.INSTANCE);
        var11_10.add(BlockStateUpdater_1_14_0.INSTANCE);
        var11_10.add(BlockStateUpdater_1_15_0.INSTANCE);
        var11_10.add(BlockStateUpdater_1_16_0.INSTANCE);
        var11_10.add(BlockStateUpdater_1_16_210.INSTANCE);
        var11_10.add(BlockStateUpdater_1_17_30.INSTANCE);
        var11_10.add(BlockStateUpdater_1_17_40.INSTANCE);
        var11_10.add(BlockStateUpdater_1_18_10.INSTANCE);
        var11_10.add(BlockStateUpdater_1_18_30.INSTANCE);
        var11_10.add(BlockStateUpdater_1_19_0.INSTANCE);
        var11_10.add(BlockStateUpdater_1_19_20.INSTANCE);
        var11_10.add(BlockStateUpdater_1_19_70.INSTANCE);
        var11_10.add(BlockStateUpdater_1_19_80.INSTANCE);
        var11_10.add(BlockStateUpdater_1_20_0.INSTANCE);
        var11_10.add(BlockStateUpdater_1_20_10.INSTANCE);
        var11_10.add(BlockStateUpdaterVanilla.a);
        var12_11 = Boolean.parseBoolean(System.getProperty((String)BlockStateMapping.a("c", (int)27568, (long)(5356513000249777462L ^ var9))));
        if (var12_11) {
            var11_10.add(BlockStateUpdaterChunker.a);
            BlockStateMapping.f.warn((String)BlockStateMapping.a("c", (int)20247, (long)(4063791576101878163L ^ var9)));
        }
        var13_12 = new CompoundTagUpdaterContext();
        var11_10.forEach((Consumer<BlockStateUpdater>)LambdaMetafactory.metafactory(null, null, null, (Ljava/lang/Object;)V, lambda$static$0(org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext org.cloudburstmc.blockstateupdater.BlockStateUpdater ), (Lorg/cloudburstmc/blockstateupdater/BlockStateUpdater;)V)((CompoundTagUpdaterContext)var13_12));
        BlockStateMapping.e = var13_12;
        BlockStateMapping.a = var13_12.getLatestVersion();
        BlockStateMapping.m.info((String)BlockStateMapping.a("c", (int)17481, (long)(6817999545109115603L ^ var9)), (Object)var13_12.getLatestVersion());
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

    private static String e(int n2, long l2) {
        int n3 = n2 ^ (int)(l2 & 0x7FFFL) ^ 0x1A91;
        if (p[n3] == null) {
            Object[] objectArray;
            try {
                Long l3 = Thread.currentThread().getId();
                objectArray = (Object[])q.get(l3);
                if (objectArray == null) {
                    objectArray = new Object[]{Cipher.getInstance("DES/CBC/PKCS5Padding"), SecretKeyFactory.getInstance("DES"), new IvParameterSpec(new byte[8])};
                    q.put(l3, objectArray);
                }
            }
            catch (Exception exception) {
                throw new RuntimeException("cn/nukkit/level/format/leveldb/BlockStateMapping", exception);
            }
            byte[] byArray = new byte[8];
            byArray[0] = (byte)(l2 >>> 56);
            for (int i2 = 1; i2 < 8; ++i2) {
                byArray[i2] = (byte)(l2 << i2 * 8 >>> 56);
            }
            DESKeySpec dESKeySpec = new DESKeySpec(byArray);
            SecretKey secretKey = ((SecretKeyFactory)objectArray[1]).generateSecret(dESKeySpec);
            ((Cipher)objectArray[0]).init(2, (Key)secretKey, (IvParameterSpec)objectArray[2]);
            byte[] byArray2 = o[n3].getBytes("ISO-8859-1");
            BlockStateMapping.p[n3] = BlockStateMapping.a(((Cipher)objectArray[0]).doFinal(byArray2));
        }
        return p[n3];
    }

    private static Object a(MethodHandles.Lookup lookup, MutableCallSite mutableCallSite, String string, Object[] objectArray) {
        int n2 = (Integer)objectArray[0];
        long l2 = (Long)objectArray[1];
        String string2 = BlockStateMapping.e(n2, l2);
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
            throw new RuntimeException("cn/nukkit/level/format/leveldb/BlockStateMapping" + " : " + string + " : " + methodType.toString(), exception);
        }
        return mutableCallSite;
    }

    /*
     * Works around MethodHandle LDC.
     */
    static MethodHandle cfr_ldc_0() {
        try {
            return MethodHandles.lookup().findStatic(BlockStateMapping.class, "a", MethodType.fromMethodDescriptorString("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", null));
        }
        catch (NoSuchMethodException | IllegalAccessException except) {
            throw new IllegalArgumentException(except);
        }
    }
}

