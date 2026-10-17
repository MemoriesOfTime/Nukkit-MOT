package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemComponentVanillaCacheTest {
    // New-factory contracts are GREEN-only; the baseline selector uses existing public APIs.
    private static ItemComponentPacket.ItemDefinition vanilla(String name, int runtimeId,
            boolean componentBased, int version, CompoundTag networkData) {
        try {
            return (ItemComponentPacket.ItemDefinition) ItemComponentPacket.ItemDefinition.class
                    .getMethod("vanilla", String.class, int.class, boolean.class, int.class, CompoundTag.class)
                    .invoke(null, name, runtimeId, componentBased, version, networkData);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("new owned-vanilla factory contract", error);
        }
    }

    @BeforeAll static void setup() { MockServer.init(); }
    static Stream<GameVersion> versions() {
        return Arrays.stream(GameVersion.values()).filter(v -> !v.isNetEase()
                && v.getProtocol() >= ProtocolInfo.v1_20_0 && v.getProtocol() <= ProtocolInfo.v1_26_50);
    }
    static Stream<GameVersion> modernVersions() {
        return versions().filter(v -> v.getProtocol() >= ProtocolInfo.v1_21_60);
    }
    static byte[] encode(GameVersion v, ItemComponentPacket.ItemDefinition definition) {
        ItemComponentPacket p = new ItemComponentPacket();
        p.gameVersion = v; p.protocol = v.getProtocol(); p.entries = List.of(definition);
        p.encode(); return p.getBuffer();
    }
    @ParameterizedTest @MethodSource("modernVersions")
    void nativeVanillaDoesNotReopenItsNbtOnEachLogin(GameVersion v) {
        var vanilla = RuntimeItems.getMapping(v).getVanillaItemDefinitions().iterator().next();
        var observed = spy(vanilla);
        encode(v, observed); assertNotNull(observed.toString()); encode(v, observed);
        verify(observed, never()).getNetworkData();
    }
    @ParameterizedTest @MethodSource("versions")
    void ordinaryCustomConstructorRetainsInputAliasAndMutation(GameVersion v) {
        CompoundTag tag = new CompoundTag("named_root").putString("probe", "before");
        var custom = new ItemComponentPacket.ItemDefinition("test:mutable", 123, true, 1, tag);
        byte[] before = encode(v, custom);
        tag.putString("probe", "after");
        assertFalse(Arrays.equals(before, encode(v, custom)));
        assertSame(tag, custom.getNetworkData());
    }
    @ParameterizedTest @MethodSource("versions")
    void customSubclassVirtualGetterKeepsOriginalCallCount(GameVersion v) {
        class Custom extends ItemComponentPacket.ItemDefinition {
            int calls;
            Custom() { super("test:subclass", 124, true, 1, new CompoundTag()); }
            @Override public CompoundTag getNetworkData() {
                calls++; return new CompoundTag("virtual").putInt("probe", calls);
            }
        }
        Custom custom = new Custom(); encode(v, custom);
        assertEquals(v.getProtocol() >= ProtocolInfo.v1_21_60 ? 2 : 1, custom.calls);
    }

    @ParameterizedTest @MethodSource("versions")
    void vanillaFactoryOwnsInputAndPreservesNamedRoot(GameVersion v) {
        CompoundTag input = new CompoundTag("named_root")
                .putCompound("nested", new CompoundTag().putString("probe", "before"));
        var owned = vanilla("test:owned", 125, true, 1, input);
        var reference = new ItemComponentPacket.ItemDefinition("test:owned", 125, true, 1, input.copy());
        byte[] expected = encode(v, reference);
        input.setName("outside"); input.getCompound("nested").putString("probe", "outside");
        assertArrayEquals(expected, encode(v, owned));
        assertEquals("named_root", owned.getNetworkData().getName());
    }
    @ParameterizedTest @MethodSource("versions")
    void lateExposureAndHeldReferenceMutationsRemainVisibleForever(GameVersion v) {
        var owned = vanilla("test:exposed", 126, true, 1,
                new CompoundTag("root").putString("probe", "before"));
        byte[] initial = encode(v, owned);
        CompoundTag held = owned.getNetworkData();
        for (int i = 1; i <= 3; i++) {
            held.putString("probe", "later" + i); held.setName("root" + i);
            byte[] actual = encode(v, owned);
            assertFalse(Arrays.equals(initial, actual));
            assertArrayEquals(encode(v, new ItemComponentPacket.ItemDefinition("test:exposed",
                    126, true, 1, held.copy())), actual);
            assertSame(held, owned.getNetworkData());
        }
    }
    @ParameterizedTest @MethodSource("modernVersions")
    void nullVanillaTagUsesIdenticalEmptyCompoundBytes(GameVersion v) {
        var owned = vanilla("test:empty", 127, false, 0, null);
        var ordinary = new ItemComponentPacket.ItemDefinition("test:empty", 127, false, 0, null);
        assertArrayEquals(encode(v, ordinary), encode(v, owned));
        assertNull(owned.getNetworkData());
        assertArrayEquals(encode(v, ordinary), encode(v, owned));
    }
    @Test void packetBufferAndLiveEntryListCannotCorruptDefinitionCache() {
        var v = GameVersion.V1_26_50;
        var a = vanilla("test:a", 128, true, 1,
                new CompoundTag("root").putString("probe", "a"));
        var b = vanilla("test:b", 129, true, 1,
                new CompoundTag("root").putString("probe", "b"));
        ItemComponentPacket p = new ItemComponentPacket(); p.protocol = v.getProtocol(); p.gameVersion = v;
        p.entries = new java.util.ArrayList<>(List.of(a, b)); p.encode();
        byte[] initial = p.getBuffer(); Arrays.fill(p.getBufferUnsafe(), (byte) 0);
        p.encode(); assertArrayEquals(initial, p.getBuffer());
        p.entries.remove(b); p.encode(); assertArrayEquals(encode(v, a), p.getBuffer());
        p.entries.clear(); p.encode();
        ItemComponentPacket empty = new ItemComponentPacket(); empty.protocol = v.getProtocol();
        empty.gameVersion = v; empty.encode(); assertArrayEquals(empty.getBuffer(), p.getBuffer());
    }

    @ParameterizedTest @MethodSource("versions")
    void customSubclassStringificationStillCallsVirtualGetter(GameVersion v) {
        class Custom extends ItemComponentPacket.ItemDefinition {
            int calls;
            Custom() { super("test:logging", 130, true, 1, new CompoundTag()); }
            @Override public CompoundTag getNetworkData() { calls++; return new CompoundTag("virtual"); }
        }
        Custom custom = new Custom(); assertTrue(custom.toString().contains("test:logging"));
        assertEquals(1, custom.calls);
        encode(v, custom);
        assertEquals(v.getProtocol() >= ProtocolInfo.v1_21_60 ? 3 : 2, custom.calls);
    }
}
