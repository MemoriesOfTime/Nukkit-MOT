package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native inbound decode regression: mushroom state must survive inbound item decoding. */
class BinaryStreamMushroomStateTest {
    private static final List<GameVersion> VERSIONS = Arrays.stream(GameVersion.values())
            .filter(v -> !v.isNetEase() && v.getProtocol() >= ProtocolInfo.v1_20_0
                    && v.getProtocol() <= ProtocolInfo.v1_26_50).toList();
    private static Method craftingSlotReader;

    @BeforeAll
    static void init() throws Exception {
        MockServer.init();
        craftingSlotReader = BinaryStream.class.getDeclaredMethod("getSlotNew", GameVersion.class, boolean.class);
        craftingSlotReader.setAccessible(true);
    }

    @Test void ordinarySlotsPreserveMushroomState() throws Exception { roundTrips(Mode.SLOT); }
    @Test void craftingSlotsPreserveMushroomState() throws Exception { roundTrips(Mode.CRAFTING); }
    @Test void stackDescriptorsPreserveMushroomState() throws Exception { roundTrips(Mode.DESCRIPTOR); }
    @Test void instanceDescriptorsPreserveMushroomState() throws Exception { roundTrips(Mode.INSTANCE); }

    @Test
    void naturalCapsAndStemsRetainExactItemEquality() throws Exception {
        Checks checks = new Checks("natural-drops");
        boolean oldHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (GameVersion version : VERSIONS) for (Mode mode : Mode.values()) {
                    if (!mode.supports(version)) continue;
                    for (int[] drop : new int[][]{{Block.BROWN_MUSHROOM_BLOCK, 14},
                            {Block.RED_MUSHROOM_BLOCK, 14}, {Block.BROWN_MUSHROOM_BLOCK, 15}}) {
                        for (boolean tagged : new boolean[]{false, true}) {
                            Item original = item(drop[0], drop[1], tagged);
                            Item decoded = mode.decode(version, mode.encode(version, original));
                            String at = version + " " + mode + " hash=" + hash + " natural=" + original;
                            // Independent player-facing invariant: no palette-derived expected value.
                            checks.expect(original.equals(decoded), at + " actual=" + decoded);
                            checks.expect(decoded.getDamage() == drop[1], at + " changed cap/stem variant");
                            verifyPayload(checks, original, decoded, at, tagged);
                            checks.cases++;
                        }
                    }
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(oldHash); }
        checks.finish();
    }

    private static void roundTrips(Mode mode) throws Exception {
        Checks checks = new Checks(mode.name());
        boolean oldHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (GameVersion version : VERSIONS) {
                    if (!mode.supports(version)) continue;
                    for (int id : new int[]{Block.BROWN_MUSHROOM_BLOCK, Block.RED_MUSHROOM_BLOCK}) {
                        for (int meta = 0; meta < 16; meta++) {
                            for (boolean tagged : new boolean[]{false, true}) {
                                Item original = item(id, meta, tagged);
                                int runtime = GlobalBlockPalette.getOrCreateRuntimeId(version, id, meta);
                                int full = inverse(version, runtime);
                                String at = version + " hash=" + hash + " " + id + ":" + meta + " tag=" + tagged;
                                checks.expect(full != -1, at + " palette must reverse its own runtime=" + runtime);
                                // Palette aliases may collapse raw variants. Never graft state from another block identity.
                                int expectedDamage = full != -1 && (full >> Block.DATA_BITS) == id
                                        ? full & Block.DATA_MASK : 0;
                                if (expectedDamage != meta) checks.aliases++;
                                Item decoded = mode.decode(version, mode.encode(version, original));
                                checks.expect(decoded.getId() == id, at + " item identity=" + decoded.getId());
                                checks.expect(decoded.getDamage() == expectedDamage,
                                        at + " expected damage=" + expectedDamage + " actual=" + decoded.getDamage());
                                verifyPayload(checks, original, decoded, at, tagged);
                                checks.cases++;
                            }
                        }
                    }
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(oldHash); }
        checks.finish();
    }

    @Test
    void unrelatedAndUnknownRuntimeIdsCannotGraftDamage() throws Exception {
        Checks checks = new Checks("hostile-runtime");
        boolean oldHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (GameVersion version : VERSIONS) for (Mode mode : Mode.values()) {
                    if (!mode.supports(version)) continue;
                    int foreign = GlobalBlockPalette.getOrCreateRuntimeId(version, Block.WOOL, 3);
                    int unknown = Integer.MAX_VALUE;
                    while (inverse(version, unknown) != -1) unknown--;
                    for (int id : new int[]{Block.BROWN_MUSHROOM_BLOCK, Block.RED_MUSHROOM_BLOCK}) {
                        int otherMushroom = GlobalBlockPalette.getOrCreateRuntimeId(version,
                                id == Block.BROWN_MUSHROOM_BLOCK ? Block.RED_MUSHROOM_BLOCK : Block.BROWN_MUSHROOM_BLOCK, 14);
                        for (int forged : new int[]{foreign, otherMushroom, unknown}) {
                            Item original = item(id, 0, true);
                            String at = version + " " + mode + " hash=" + hash + " id=" + id + " runtime=" + forged;
                            Item decoded = mode.decode(version, replaceRuntime(mode.encode(version, original), version, mode, forged));
                            checks.expect(decoded.getId() == id, at + " changed identity");
                            checks.expect(decoded.getDamage() == 0, at + " grafted damage=" + decoded.getDamage());
                            verifyPayload(checks, original, decoded, at, true);
                            checks.cases++;
                        }
                    }
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(oldHash); }
        checks.finish();
    }

    @Test
    void neighboringItemsRemainUnaffected() throws Exception {
        Checks checks = new Checks("neighbors");
        boolean oldHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (GameVersion version : VERSIONS) for (Mode mode : Mode.values()) {
                    if (!mode.supports(version)) continue;
                    for (int[] neighbor : new int[][]{{Block.STONE, 0}, {Block.WOOL, 3}, {Item.DIAMOND_SWORD, 0}}) {
                        Item original = item(neighbor[0], neighbor[1], true);
                        Item decoded = mode.decode(version, mode.encode(version, original));
                        String at = version + " " + mode + " hash=" + hash + " neighbor=" + original;
                        checks.expect(original.equals(decoded), at + " actual=" + decoded);
                        verifyPayload(checks, original, decoded, at, true);
                        checks.cases++;
                    }
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(oldHash); }
        checks.finish();
    }

    private static Item item(int id, int meta, boolean tagged) {
        Item item = Item.get(id, meta, 4);
        if (tagged) item.setNamedTag(new CompoundTag().putString("qa_marker", "mushroom-state").putInt("qa_number", 1031));
        return item;
    }

    private static void verifyPayload(Checks checks, Item original, Item actual, String at, boolean tagged) {
        checks.expect(actual.getCount() == original.getCount(), at + " count=" + actual.getCount());
        if (tagged) {
            checks.expect(actual.getNamedTag().getString("qa_marker").equals("mushroom-state"), at + " lost string tag");
            checks.expect(actual.getNamedTag().getInt("qa_number") == 1031, at + " lost integer tag");
        } else checks.expect(!actual.hasCompoundTag(), at + " added NBT");
    }

    private static int inverse(GameVersion version, int runtime) {
        return GlobalBlockPalette.shouldUseHashedBlockNetworkIds(version)
                ? GlobalBlockPalette.getLegacyFullIdFromHashId(version, runtime)
                : GlobalBlockPalette.getLegacyFullId(version, runtime);
    }

    private static byte[] replaceRuntime(byte[] encoded, GameVersion version, Mode mode, int runtime) {
        BinaryStream reader = new BinaryStream(encoded);
        boolean descriptor = mode.descriptor || version.getProtocol() >= ProtocolInfo.v1_26_40;
        if (descriptor && !mode.instance) reader.getLShort(); else reader.getVarInt();
        reader.getLShort();
        reader.getUnsignedVarInt();
        if (!mode.instance && reader.getBoolean()) {
            if (descriptor && version.getProtocol() < ProtocolInfo.v1_26_40) reader.getUnsignedVarInt();
            reader.getVarInt();
        }
        int start = reader.getOffset();
        if (descriptor && !mode.instance) reader.getUnsignedVarInt(); else reader.getVarInt();
        int end = reader.getOffset();
        BinaryStream result = new BinaryStream();
        result.put(Arrays.copyOfRange(encoded, 0, start));
        if (descriptor && !mode.instance) result.putUnsignedVarInt(runtime); else result.putVarInt(runtime);
        result.put(Arrays.copyOfRange(encoded, end, encoded.length));
        return result.getBuffer();
    }

    private enum Mode {
        SLOT(false, false), CRAFTING(false, true), DESCRIPTOR(true, false), INSTANCE(true, true);
        final boolean descriptor;
        final boolean instance;
        Mode(boolean descriptor, boolean instance) { this.descriptor = descriptor; this.instance = instance; }
        boolean supports(GameVersion version) { return !descriptor || version.getProtocol() >= ProtocolInfo.v1_26_20_26; }
        byte[] encode(GameVersion version, Item item) {
            BinaryStream stream = new BinaryStream();
            if (descriptor) stream.putNetworkItemStackDescriptor(version, item, instance);
            else stream.putSlot(version, item, instance);
            return stream.getBuffer();
        }
        Item decode(GameVersion version, byte[] data) throws Exception {
            BinaryStream stream = new BinaryStream(data);
            if (descriptor) return stream.getNetworkItemStackDescriptor(version, instance);
            if (instance) return (Item) craftingSlotReader.invoke(stream, version, true);
            return stream.getSlot(version);
        }
    }

    private static final class Checks {
        final String name;
        final List<String> firstFailures = new ArrayList<>();
        int failures;
        int cases;
        int aliases;
        Checks(String name) { this.name = name; }
        void expect(boolean ok, String context) {
            if (!ok) { failures++; if (firstFailures.size() < 12) firstFailures.add(context); }
        }
        void finish() {
            System.out.println(name + ": cases=" + cases + " aliases=" + aliases + " mismatches=" + failures);
            assertEquals(0, failures, () -> String.join("\n", firstFailures));
        }
    }
}
