package cn.nukkit.utils;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.math.SimpleAxisAlignedBB;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CollisionCandidateCellBoundsTest {
 @BeforeAll static void boot(){MockServer.init();}
 private void compare(double low,double high){
  CollisionCandidatesTest.World world=CollisionCandidatesTest.World.flat(Block.NETHER_PORTAL,64);
  CollisionHelper helper=world.helperAt((low+high)/2,.5);var box=new SimpleAxisAlignedBB(low,64.1,.1,high,64.9,.9);
  var expected=Arrays.stream(helper.getBlocksInBoundingBox(box)).map(b->b.getFloorX()+":"+b.getFloorY()+":"+b.getFloorZ()).toList();
  var actual=Arrays.stream(helper.getCollisionCandidates(box,box)).map(b->b.getFloorX()+":"+b.getFloorY()+":"+b.getFloorZ()).toList();
  assertFalse(expected.isEmpty());assertEquals(expected,actual,"Dynamic candidates use the same occupied cell range as the native full scan");
 }
 @Test void positiveFractionalUpperBoundaryKeepsNativeCellRange(){compare(.1,.9);}
 @Test void negativeFractionalUpperBoundaryKeepsNativeCellRange(){compare(-.9,-.1);}

 // Dynamic portals exercise the candidate mask even when the neighboring cell has no collision box.
 private void compareZ(double low, double high) {
  CollisionCandidatesTest.World world=CollisionCandidatesTest.World.flat(Block.NETHER_PORTAL,64);
  CollisionHelper helper=world.helperAt(.5,(low+high)/2);
  var box=new SimpleAxisAlignedBB(.1,64.1,low,.9,64.9,high);
  var expected=Arrays.stream(helper.getBlocksInBoundingBox(box)).map(b->b.getFloorX()+":"+b.getFloorY()+":"+b.getFloorZ()).toList();
  var actual=Arrays.stream(helper.getCollisionCandidates(box,box)).map(b->b.getFloorX()+":"+b.getFloorY()+":"+b.getFloorZ()).toList();
  assertFalse(expected.isEmpty());
  assertEquals(expected,actual,"Z candidate bounds match the occupied cells of the native full scan");
 }
 @Test void positiveFractionalZUpperBoundaryKeepsNativeCellRange(){compareZ(.1,.9);}
 @Test void negativeFractionalZUpperBoundaryKeepsNativeCellRange(){compareZ(-.9,-.1);}
 @Test void fractionalYUpperBoundaryDoesNotAdmitThePortalAboveTheBox(){
  CollisionCandidatesTest.World world=CollisionCandidatesTest.World.flat(Block.NETHER_PORTAL,64);
  world.set(0,65,0,Block.NETHER_PORTAL);
  CollisionHelper helper=world.helperAt(.5,.5);
  var box=new SimpleAxisAlignedBB(.1,64.1,.1,.9,64.9,.9);
  var expected=Arrays.stream(helper.getBlocksInBoundingBox(box)).map(b->b.getFloorX()+":"+b.getFloorY()+":"+b.getFloorZ()).toList();
  var actual=Arrays.stream(helper.getCollisionCandidates(box,box)).map(b->b.getFloorX()+":"+b.getFloorY()+":"+b.getFloorZ()).toList();
  assertEquals(java.util.List.of("0:64:0"),expected);
  assertEquals(expected,actual,"A dynamic portal at Y65 is outside the native full scan ending at Y64.9");
 }
}
