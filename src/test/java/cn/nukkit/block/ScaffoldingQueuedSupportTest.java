package cn.nukkit.block;
import cn.nukkit.MockServer;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ScaffoldingQueuedSupportTest {
 @BeforeAll static void boot(){MockServer.init();}
 static final class Scaffold extends BlockScaffolding {
  final EnumMap<BlockFace,Block> neighbors=new EnumMap<>(BlockFace.class);
  @Override public Block getSide(BlockFace face){return neighbors.getOrDefault(face,Block.get(Block.AIR));}
  @Override public Block down(){return getSide(BlockFace.DOWN);}
 }
 Scaffold source(){Scaffold s=new Scaffold();s.level=mock(Level.class);s.setStability(7);return s;}
 @Test void restoredSolidSupportSurvivesQueuedBreak(){
  Scaffold s=source();s.neighbors.put(BlockFace.DOWN,Block.get(Block.STONE));
  assertEquals(Level.BLOCK_UPDATE_SCHEDULED,s.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));verify(s.level,never()).useBreakOn(s);assertEquals(0,s.getStability());
 }
 @Test void restoredHorizontalSupportSurvivesQueuedBreak(){
  Scaffold s=source();BlockScaffolding neighbor=new BlockScaffolding();neighbor.setStability(2);s.neighbors.put(BlockFace.EAST,neighbor);
  s.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);verify(s.level,never()).useBreakOn(s);assertEquals(3,s.getStability());
 }
 @Test void stillUnsupportedSourceBreaksOnce(){Scaffold s=source();s.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);verify(s.level,times(1)).useBreakOn(s);}
 @Test void excessiveHorizontalDistanceStillBreaks(){
  Scaffold s=source();BlockScaffolding neighbor=new BlockScaffolding();neighbor.setStability(6);s.neighbors.put(BlockFace.WEST,neighbor);
  s.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);verify(s.level,times(1)).useBreakOn(s);
 }
 @Test void normalSupportUsesExistingDistanceAndWrite(){
  Scaffold s=source();BlockScaffolding neighbor=new BlockScaffolding();neighbor.setStability(4);s.neighbors.put(BlockFace.DOWN,neighbor);
  s.onUpdate(Level.BLOCK_UPDATE_NORMAL);assertEquals(4,s.getStability());verify(s.level).setBlock(eq(s),eq(s),eq(true),eq(true));verify(s.level,never()).useBreakOn(s);
 }
}
