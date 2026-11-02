package cn.nukkit.level.explosion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import cn.nukkit.block.*;
import cn.nukkit.blockentity.*;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.inventory.*;
import cn.nukkit.item.*;
import cn.nukkit.level.*;
import cn.nukkit.math.*;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import java.lang.reflect.*;
import java.util.*;
/** Every concrete native inventory family and paired cell under refusals/replays. */
@Isolated("native Server.instance, item registries and plugin handler lists")
public final class NativeInventoryCompositeTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
 @BeforeAll static void nativeBootstrap() throws Exception {NativeExplosionFixture.boot();}

    static int passed;
    record Family(int blockId,Class<? extends BlockEntity> tile,Class<? extends BaseInventory> inventory){}
    static List<Family> families=List.of(
      new Family(Block.CHEST,BlockEntityChest.class,ChestInventory.class),
      new Family(Block.BARREL,BlockEntityBarrel.class,BarrelInventory.class),
      new Family(Block.HOPPER_BLOCK,BlockEntityHopper.class,HopperInventory.class),
      new Family(Block.CRAFTER,BlockEntityCrafter.class,CrafterInventory.class),
      new Family(Block.FURNACE,BlockEntityFurnace.class,FurnaceInventory.class),
      new Family(Block.BLAST_FURNACE,BlockEntityBlastFurnace.class,FurnaceInventory.class),
      new Family(Block.SMOKER,BlockEntitySmoker.class,FurnaceInventory.class),
      new Family(Block.BREWING_STAND_BLOCK,BlockEntityBrewingStand.class,BrewingInventory.class),
      new Family(Block.CAMPFIRE_BLOCK,BlockEntityCampfire.class,CampfireInventory.class),
      new Family(Block.CHISELED_BOOKSHELF,BlockEntityChiseledBookshelf.class,ChiseledBookshelfInventory.class),
      new Family(Block.DISPENSER,BlockEntityDispenser.class,DispenserInventory.class),
      new Family(Block.DROPPER,BlockEntityDropper.class,DropperInventory.class),
      new Family(Block.OAK_SHELF,BlockEntityShelf.class,ShelfInventory.class),
      new Family(Block.COMMAND_BLOCK,BlockEntityCommandBlock.class,CommandBlockInventory.class));
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;}
    static BlockEntity holder(NativeExplosionFixture.MemoryLevel w,Family f)throws Exception{
        BlockEntity t=NativeExplosionFixture.allocated(f.tile());t.namedTag=new CompoundTag();NativeExplosionFixture.attach(w,t);
        BlockEntity.registerBlockEntity(f.tile().getSimpleName().replace("BlockEntity",""),f.tile());
        Constructor<?> ctor=Arrays.stream(f.inventory().getConstructors()).filter(c->c.getParameterCount()==1).findFirst().orElseThrow();
        BaseInventory inv=(BaseInventory)ctor.newInstance(t);
        Class<?> type=t.getClass();Field field=null;while(field==null){try{field=type.getDeclaredField("inventory");}catch(NoSuchFieldException ex){type=type.getSuperclass();}}
        field.setAccessible(true);field.set(t,inv);return t;
    }
    @Test void inventories()throws Exception{
        for(Family f:families)for(int mode=0;mode<=6;mode++){
            var w=NativeExplosionConservationTest.world(f.blockId(),0);BlockEntity t=holder(w,f);BaseInventory inv=(BaseInventory)((InventoryHolder)t).getInventory();
            Item payload=NativeExplosionConservationTest.payload(3);inv.slots.put(0,payload.clone());inv.slots.put(99,payload.clone());
            w.setterMode=mode==6?0:mode;
            Runnable action=()->{try{NativeExplosionConservationTest.explode(w,w.cell);}catch(Exception expected){if(modeIsHealthy(w) && modeIsNotRefusal(w))throw new RuntimeException(expected);}};
            if(mode==6)w.rejectSpawn=true;
            if(mode==6)NativeExplosionConservationTest.listener(event->event.getEntity().close(),action);else action.run();
            int before=inv.slots.values().stream().filter(i->i.equals(payload,true,true)).mapToInt(Item::getCount).sum();
            int after=NativeExplosionFixture.matching(w.allDrops(),payload);
            boolean accepted=mode==0 || mode==4 || mode==5;
            check(before+after==6 && w.cell.isAir()==accepted && after==(accepted?6:0),"family="+f.tile().getSimpleName()+" mode="+mode+" standing="+!w.cell.isAir()+" source="+before+" world="+after);
            if(accepted){NativeExplosionConservationTest.explode(w,w.cell);check(NativeExplosionFixture.matching(w.allDrops(),payload)==6,"family replay="+f.tile().getSimpleName());}
        }
    }
    static boolean modeIsNotRefusal(NativeExplosionFixture.MemoryLevel w){return !w.rejectSpawn;}
    static boolean modeIsHealthy(NativeExplosionFixture.MemoryLevel w){return w.setterMode==0 || w.setterMode==5;}
    @Test void composite()throws Exception{
        for(int id:List.of(Block.WOODEN_DOOR_BLOCK,Block.BED_BLOCK,Block.DOUBLE_PLANT))for(int scope=0;scope<3;scope++)for(int mode=0;mode<=5;mode++){
            var w=NativeExplosionConservationTest.world(id,0);Block first=w.cell;Block other;
            if(id==Block.BED_BLOCK){other=Block.get(id,8,w,0,64,1);w.cells.put("0,64,1,0",other);}else{other=Block.get(id,8,w,0,65,0);w.cells.put("0,65,0,0",other);}
            w.setterMode=mode;
            List<Block> affected=scope==0?List.of(first):scope==1?List.of(other):List.of(other,first);
            try{NativeExplosionConservationTest.explode(w,affected.toArray(Block[]::new));}catch(IllegalStateException expected){}
            boolean accepted=mode==0||mode==4||mode==5;Item expected=first.getDrops(Item.get(Item.AIR))[0];
            int drops=NativeExplosionFixture.matching(w.allDrops(),expected);
            boolean firstAir=w.getBlock(first).isAir(),otherAir=w.getBlock(other).isAir();
            check(firstAir==accepted && otherAir==accepted && drops==(accepted?1:0),"pair="+id+" scope="+scope+" mode="+mode+" first="+firstAir+" other="+otherAir+" count="+drops);
            if(accepted){NativeExplosionConservationTest.explode(w,affected.toArray(Block[]::new));check(NativeExplosionFixture.matching(w.allDrops(),expected)==1,"pair replay="+id);}
        }
    }
}
