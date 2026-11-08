package cn.nukkit.level.explosion;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Prefix metadata and block-runtime state are independent client fields. */
@Isolated("native Server.instance, item registries and hashed block palette mode")
public class ExplosionDropStateTest {
    @BeforeAll static void bootstrap() throws Exception {cn.nukkit.MockServer.init();}
    static int coralCases;
    private static Item pick(boolean silk) {
        Item pick=Item.get(Item.DIAMOND_PICKAXE);
        if(silk)pick.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_SILK_TOUCH).setLevel(1));
        return pick;
    }
    private static void wire(Item drop,int expectedId,int expectedMeta) {
        assertEquals(expectedId,drop.getBlockUnsafe().getId());
        assertEquals(expectedMeta,drop.getBlockUnsafe().getDamage());
        boolean prior=GlobalBlockPalette.useHashedBlockNetworkIds();
        try {for(boolean hashed:new boolean[]{false,true}) {
            GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
            for(GameVersion version:GameVersion.values()) {
                if(version.isNetEase() || version.getProtocol()<589 || version.getProtocol()>2193)continue;
                BinaryStream output=new BinaryStream();output.putSlot(version,drop.clone(),false);
                BinaryStream wire=new BinaryStream(output.getBuffer());
                int networkId=version.getProtocol()>=2168?(short)wire.getLShort():wire.getVarInt();
                assertNotEquals(0,networkId,version.toString());assertEquals(1,wire.getLShort());
                wire.getUnsignedVarInt();if(wire.getBoolean())wire.getVarInt();
                int actual=version.getProtocol()>=2168?(int)wire.getUnsignedVarInt():wire.getVarInt();
                int expected=GlobalBlockPalette.getOrCreateRuntimeId(version,expectedId,expectedMeta);
                assertEquals(expected,actual,"source variant "+expectedId+":"+expectedMeta+" "+version+" hashed="+hashed);
                coralCases++;
            }
        }} finally {GlobalBlockPalette.setUseHashedBlockNetworkIds(prior);}
    }
    @Test void allValidCoralVariantsWithAndWithoutSilkTouch() {
        for(int living=0;living<5;living++)for(boolean alreadyDead:new boolean[]{false,true})for(boolean silk:new boolean[]{false,true}) {
            int sourceMeta=living|(alreadyDead?8:0);Block source=Block.get(Block.CORAL_BLOCK,sourceMeta);int original=source.getFullId();
            Item[] drops=source.getDrops(pick(silk));assertEquals(1,drops.length);int expected=silk?sourceMeta:living|8;
            assertEquals(expected,drops[0].getDamage());wire(drops[0],Block.CORAL_BLOCK,expected);
            assertEquals(original,source.getFullId(),"drop creation must not mutate the source");
            assertEquals(0,source.getDrops(Item.get(Item.AIR)).length);
        }
    }
    @Test void quartzVariantsNormalizePillarOrientation() {
        for(int meta=0;meta<16;meta++) {
            Block source=Block.get(Block.QUARTZ_BLOCK,meta);int original=source.getFullId();
            Item drop=source.toItem();int expected=meta&3;
            assertEquals(expected,drop.getDamage());wire(drop,Block.QUARTZ_BLOCK,expected);
            assertEquals(original,source.getFullId(),"item normalization must not mutate the placed block");
        }
    }

    /** Independently specified material families, direct item bytes and actual item actors. */
    private static final String[] STONE = {"smooth_stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab", "quartz_slab", "nether_brick_slab"};
    private static final String[] RED = {"red_sandstone_slab", "purpur_slab", "prismarine_slab", "dark_prismarine_slab", "prismarine_brick_slab", "mossy_cobblestone_slab", "smooth_sandstone_slab", "red_nether_brick_slab"};
    private static int cases;

    private static void wire(Item item, int targetId, int targetMeta, int count, String name) {
        assertEquals(count, item.getCount());
        assertEquals(targetMeta, item.getDamage());
        assertEquals(targetId, item.getBlockUnsafe().getId());
        assertEquals(targetMeta, item.getBlockUnsafe().getDamage());
        var expected = BlockStateMapping.get().getState(targetId, targetMeta).getVanillaState();
        assertEquals("minecraft:" + name, expected.getString("name"), "Hardcoded material-family oracle agrees with Mojang canonical names");
        boolean previous = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hashed : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                int versions = 0;
                for (GameVersion version : GameVersion.values()) {
                    if (version.isNetEase() || version.getProtocol() < 589 || version.getProtocol() > 2193) continue;
                    BinaryStream output = new BinaryStream();
                    output.putSlot(version, item.clone(), false);
                    BinaryStream slot = new BinaryStream(output.getBuffer());
                    int network = version.getProtocol() >= 2168 ? (short) slot.getLShort() : slot.getVarInt();
                    assertNotEquals(0, network);
                    assertEquals(count, slot.getLShort());
                    slot.getUnsignedVarInt();
                    if (slot.getBoolean()) slot.getVarInt();
                    int runtime = version.getProtocol() >= 2168 ? (int) slot.getUnsignedVarInt() : slot.getVarInt();
                    int full = GlobalBlockPalette.shouldUseHashedBlockNetworkIds(version)
                            ? GlobalBlockPalette.getLegacyFullIdFromHashId(version, runtime)
                            : GlobalBlockPalette.getPaletteByProtocol(version).getLegacyFullId(runtime);
                    assertTrue(full >= 0);
                    var actual = BlockStateMapping.get().getState(full >> Block.DATA_BITS, full & Block.DATA_MASK).getVanillaState();
                    assertEquals(expected, actual, name + " " + version + " hashed=" + hashed);
                    versions++;
                    cases++;
                }
                assertEquals(40, versions);
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(previous); }
    }

    private static void sourceRoutes(int id, int meta, int target, int normalized, int count, String name) throws Exception {
        Block source = Block.get(id, meta);
        int original = source.getFullId();
        wire(source.toItem(), target, normalized, 1, name);
        Item[] drops = source.getDrops(Item.get(Item.DIAMOND_PICKAXE));
        assertEquals(1, drops.length);
        wire(drops[0], target, normalized, count, name);
        assertEquals(original, source.getFullId(), "Item normalization must not mutate source state");
    }

    @Test void bothDoubleSlabFamiliesPreserveAllMaterialTypesAndNormalizeFlags() throws Exception {
        int before = cases;
        for (int family = 0; family < 2; family++) {
            int id = family == 0 ? Block.DOUBLE_STONE_SLAB : Block.DOUBLE_RED_SANDSTONE_SLAB;
            int target = family == 0 ? Block.STONE_SLAB : Block.RED_SANDSTONE_SLAB;
            String[] names = family == 0 ? STONE : RED;
            for (int meta = 0; meta < 16; meta++) {
                sourceRoutes(id, meta, target, meta & 7, 2, names[meta & 7]);
                assertEquals(0, Block.get(id, meta).getDrops(Item.get(Item.AIR)).length, "Legacy harvest flag unchanged");
            }
        }
        assertEquals(5120, cases - before);
    }

    @Test void purpurBlockAndPillarNormalizeAllSupportedAxes() throws Exception {
        int before = cases;
        for (int meta : new int[]{0, 2, 4, 6, 8, 10, 12, 14}) {
            int normalized = meta & 3;
            sourceRoutes(Block.PURPUR_BLOCK, meta, Block.PURPUR_BLOCK, normalized, 1,
                    normalized == 0 ? "purpur_block" : "purpur_pillar");
            assertEquals(1, Block.get(Block.PURPUR_BLOCK, meta).getDrops(Item.get(Item.AIR)).length,
                    "Existing native hand-harvest behavior remains scoped outside this item-state fix");
        }
        assertEquals(1280, cases - before);
    }

    @Test void reservedPurpurMetadataKeepsExistingDamageAndCountContract() {
        for (int meta : new int[]{1, 3, 5, 7, 9, 11, 13, 15}) {
            Block source = Block.get(Block.PURPUR_BLOCK, meta);
            int original = source.getFullId();
            Item item = source.toItem();
            assertEquals(meta & 3, item.getDamage());
            assertEquals(1, item.getCount());
            assertEquals(original, source.getFullId());
        }
    }
}
