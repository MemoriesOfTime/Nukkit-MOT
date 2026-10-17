package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.LegacyStateMapper;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class StateBlockStorageDynamicMapperTest {
    private static final int RUNTIME=237000000;
    @BeforeAll static void init(){MockServer.init();}
    @AfterAll static void cleanup(){MockServer.reset();}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void foreignVersionSnapshotRetainsLivePublicMapperReads(boolean antiXray){
        BlockStateMapping mapping=BlockStateMapping.get();LegacyStateMapper previous=mapping.getLegacyMapper();
        assertNotSame(GameVersion.V1_20_0,mapping.getVersion());
        var state=BlockStateSnapshot.builder().runtimeId(RUNTIME).version(GameVersion.V1_20_0).build();
        assertEquals(BlockStateSnapshot.class,state.getClass());
        StateBlockStorage storage=new StateBlockStorage();storage.set(0,state);
        try {
            mapping.setLegacyMapper(mapper(previous,Block.STONE));
            byte[] before=encoded(storage,antiXray);assertArrayEquals(reference(storage,antiXray),before);
            mapping.setLegacyMapper(mapper(previous,Block.PLANKS));
            byte[] expected=reference(storage,antiXray);assertFalse(Arrays.equals(before,expected));
            assertArrayEquals(expected,encoded(storage,antiXray),"public mapper replacement must not reuse old layer bytes");
            assertArrayEquals(expected,encoded(storage.copy(),antiXray));
        } finally {mapping.setLegacyMapper(previous);}
    }
    @Test void presetFeatureSnapshotRetainsItsOriginalLegacyIds(){
        BlockStateMapping mapping=BlockStateMapping.get();LegacyStateMapper previous=mapping.getLegacyMapper();
        StateBlockStorage storage=new StateBlockStorage();storage.set(0,BlockStateSnapshot.builder().runtimeId(RUNTIME)
                .version(mapping.getVersion()).legacyId(Block.STONE).legacyData(0).build());
        try {
            mapping.setLegacyMapper(mapper(previous,Block.STONE));byte[] before=encoded(storage,false);
            mapping.setLegacyMapper(mapper(previous,Block.PLANKS));assertArrayEquals(before,encoded(storage,false));
            assertArrayEquals(reference(storage,false),encoded(storage.copy(),false));
        } finally {mapping.setLegacyMapper(previous);}
    }
    static LegacyStateMapper mapper(LegacyStateMapper delegate,int id){return new LegacyStateMapper(){
        public int legacyToRuntime(int legacyId,int meta){return delegate.legacyToRuntime(legacyId,meta);}
        public int runtimeToFullId(int runtime){return runtime==RUNTIME?id<<Block.DATA_BITS:delegate.runtimeToFullId(runtime);}
        public int runtimeToLegacyId(int runtime){return runtime==RUNTIME?id:delegate.runtimeToLegacyId(runtime);}
        public int runtimeToLegacyData(int runtime){return runtime==RUNTIME?0:delegate.runtimeToLegacyData(runtime);}
    };}
    static byte[] encoded(StateBlockStorage storage,boolean anti){BinaryStream out=new BinaryStream();storage.writeTo(GameVersion.V1_26_50,out,anti);return out.getBuffer();}
    static byte[] reference(StateBlockStorage storage,boolean anti){
        var palette=GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_50);
        boolean hash=GlobalBlockPalette.shouldUseHashedBlockNetworkIds(GameVersion.V1_26_50);
        var cells=PalettedBlockStorage.createFromBlockPalette(GameVersion.V1_26_50);
        for(int i=0;i<4096;i++){
            int full=storage.get(i),id=full>>Block.DATA_BITS,meta=full&Block.DATA_MASK;
            if(anti&&id<Block.MAX_BLOCK_ID&&cn.nukkit.level.Level.xrayableBlocks[id]){id=Block.STONE;meta=0;}
            cells.setBlock(i,hash?palette.getHashId(id,meta):palette.getRuntimeId(id,meta));
        }
        BinaryStream out=new BinaryStream();cells.writeTo(out);return out.getBuffer();
    }
}
