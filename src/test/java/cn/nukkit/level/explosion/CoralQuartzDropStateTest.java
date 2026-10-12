package cn.nukkit.level.explosion;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Prefix metadata and block-runtime state are independent client fields. */
@Isolated("native Server.instance, item registries and hashed block palette mode")
public final class CoralQuartzDropStateTest {
    @BeforeAll static void bootstrap() throws Exception {cn.nukkit.MockServer.init();}
    static int cases;
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
                cases++;
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
    
}
