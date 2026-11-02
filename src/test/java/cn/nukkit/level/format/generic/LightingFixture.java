package cn.nukkit.level.format.generic;

import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Chunk;
import static org.mockito.Mockito.*;

/** Array-backed fixture exercising production population methods without disk or palette setup. */
public class LightingFixture extends Chunk {
    public static final int MIN = -64, MAX = 319, HEIGHT = 384;
    public final int[] ids = new int[16 * 16 * HEIGHT];
    public final byte[] emitted = new byte[ids.length], sky = new byte[ids.length];
    public final int[] tops = new int[256];
    public long lightReads, blockReads, lightWrites, skyWrites;
    private final LevelProvider provider;
    public LightingFixture() {
        super((LevelProvider) null);
        provider = mock(LevelProvider.class);
        when(provider.getMinBlockY()).thenReturn(MIN);
        when(provider.getMaxBlockY()).thenReturn(MAX);
        java.util.Arrays.fill(tops, MIN);
        sections = new cn.nukkit.level.format.ChunkSection[24];
        for (int i=0; i<24; i++) sections[i] = new FixtureSection(i-4);
    }
    public static int index(int x, int y, int z) { return (x * 16 + z) * HEIGHT + y - MIN; }
    @Override public LevelProvider getProvider() { return provider; }
    @Override public int getBlockId(int x, int y, int z) { blockReads++; return ids[index(x,y,z)]; }
    @Override public int getBlockLight(int x, int y, int z) { lightReads++; return emitted[index(x,y,z)] & 15; }
    @Override public void setBlockLight(int x, int y, int z, int v) { lightWrites++; emitted[index(x,y,z)] = (byte)(v & 15); }
    @Override public int getBlockSkyLight(int x, int y, int z) { return sky[index(x,y,z)] & 15; }
    @Override public void setBlockSkyLight(int x, int y, int z, int v) { skyWrites++; sky[index(x,y,z)] = (byte)(v & 15); }
    @Override public int getHeightMap(int x, int z) { return tops[x * 16 + z]; }
    @Override public void setHeightMap(int x, int z, int y) { tops[x * 16 + z] = y; }
    @Override public int getSectionOffset() { return 4; }
    private class FixtureSection extends cn.nukkit.level.format.anvil.ChunkSection {
        final int base;
        FixtureSection(int y) { super(y); base=y*16; }
        @Override public int getBlockId(int x, int y, int z) { return LightingFixture.this.getBlockId(x,base+y,z); }
        @Override public int getBlockLight(int x,int y,int z) { return LightingFixture.this.getBlockLight(x,base+y,z); }
        @Override public void setBlockLight(int x,int y,int z,int v) { LightingFixture.this.setBlockLight(x,base+y,z,v); }
        @Override public boolean maybeHasLightSource() {
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=base;y<base+16;y++)
                if(cn.nukkit.block.Block.getBlockLight(ids[index(x,y,z)])>0) return true;
            return false;
        }
        @Override public byte[] getLightArray() {
            byte[] packed = new byte[2048];
            int i=0;
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=base;y<base+16;y+=2)
                packed[i++]=(byte)(emitted[index(x,y,z)] | (emitted[index(x,y+1,z)]<<4));
            return packed;
        }
    }
}
