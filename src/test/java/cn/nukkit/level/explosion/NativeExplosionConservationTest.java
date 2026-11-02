package cn.nukkit.level.explosion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.*;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.*;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.*;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.plugin.*;
import java.util.*;
import java.util.function.Consumer;
/** Conservation oracle uses source cells, physical raw inventory, and actual published actors. */
@Isolated("native Server.instance, item registries and plugin handler lists")
public final class NativeExplosionConservationTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
 @BeforeAll static void nativeBootstrap() throws Exception {NativeExplosionFixture.boot();}

    static int passed;
    static NativeExplosionFixture.MemoryLevel world(int id,int meta) throws Exception {
        var w=NativeExplosionFixture.level(id);w.cell=Block.get(id,meta,w,0,64,0);return w;
    }
    static BlockEntityChest chest(NativeExplosionFixture.MemoryLevel w,int x) throws Exception {
        var c=NativeExplosionFixture.allocated(BlockEntityChest.class);
        c.namedTag=new CompoundTag();c.level=w;c.chunk=w.chunk;c.x=x;c.y=64;c.z=0;
        var inv=new ChestInventory(c);NativeExplosionFixture.set(BlockEntitySpawnableContainer.class,c,"inventory",inv);
        if(x==0)w.tile=c;else {w.tiles.put(x+",64,0,0",c);w.cells.put(x+",64,0,0",Block.get(Block.CHEST,0,w,x,64,0));}
        NativeExplosionFixture.register(w,c); return c;
    }
    static BlockEntityShulkerBox shulker(NativeExplosionFixture.MemoryLevel w) throws Exception {
        var t=NativeExplosionFixture.allocated(BlockEntityShulkerBox.class);
        t.namedTag=new CompoundTag().putCompound("SourceItemTag",new CompoundTag().putString("opaque", "source-tag")).putString("CustomName","named-box");
        NativeExplosionFixture.attach(w,t);
        NativeExplosionFixture.set(BlockEntityShulkerBox.class,t,"inventory",new ShulkerBoxInventory(t));return t;
    }
    static Item payload(int count) {return Item.get(Item.DIAMOND,0,count).setNamedTag(new CompoundTag().putString("opaque","exact-payload"));}
    static int amount(List<Item> items,Item expected) {return NativeExplosionFixture.matching(items,expected);}
    static void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);passed++;}
    static void explode(NativeExplosionFixture.MemoryLevel w,Block...affected) throws Exception {
        var e=new Explosion(new Position(0,64,0,w),1,(Entity)null);
        NativeExplosionFixture.set(Explosion.class,e,"affectedBlocks",new LinkedHashSet<>(List.of(affected)));
        e.explodeB();
    }
    @Test void setterPhases() throws Exception {
        for(int mode=1;mode<=5;mode++) for(boolean container:List.of(false,true)) {
            var w=world(container?Block.CHEST:Block.PLANKS,0);BlockEntityChest c=container?chest(w,0):null;
            Item diamonds=payload(64);if(c!=null)c.getRealInventory().slots.put(0,diamonds.clone());
            w.setterMode=mode;boolean threw=false;
            try{explode(w,w.cell);}catch(IllegalStateException expected){threw=true;}
            boolean accepted=mode>=4;
            int own=w.allDrops().stream().filter(i->i.getId()==(container?Block.CHEST:Block.PLANKS)).mapToInt(Item::getCount).sum();
            int source=c==null?0:c.getRealInventory().slots.values().stream().filter(i->i.equals(diamonds,true,true)).mapToInt(Item::getCount).sum();
            check(w.cell.isAir()==accepted && own==(accepted?1:0) && (c==null || source+amount(w.allDrops(),diamonds)==64),"setter conservation mode="+mode+" container="+container);
            check(threw==(mode==3 || mode==4),"setter exception phase="+mode);
        }
    }
    @Test void shulkers() throws Exception {
        for(int variant=-1;variant<16;variant++) {
            var w=world(variant<0?Block.UNDYED_SHULKER_BOX:Block.SHULKER_BOX,Math.max(variant,0));var t=shulker(w);
            Item diamonds=payload(64),book=Item.get(Item.WRITTEN_BOOK).setNamedTag(new CompoundTag().putString("author","test").putString("title","opaque-book"));
            t.getRealInventory().slots.put(0,diamonds.clone());t.getRealInventory().slots.put(26,book.clone());
            CompoundTag expected=w.cell.toItem().getNamedTag().copy();explode(w,w.cell);
            List<Item> all=w.allDrops();check(w.cell.isAir() && all.size()==1 && t.getRealInventory().isEmpty() && all.get(0).getNamedTag().equals(expected),"shulker variant="+variant+" drops="+all);
            var contents=all.get(0).getNamedTag().getList("Items",CompoundTag.class);
            check(contents.size()==2 && NBTIO.getItemHelper(contents.get(0)).equalsExact(diamonds) && NBTIO.getItemHelper(contents.get(1)).equalsExact(book),"shulker payload variant="+variant);
        }
    }
    @Test void frames() throws Exception {
        for(int id:List.of(Block.ITEM_FRAME_BLOCK,Block.GLOW_FRAME)) {
            var w=world(id,0);var t=NativeExplosionFixture.allocated(BlockEntityItemFrame.class);
            Item item=payload(1);t.namedTag=new CompoundTag().putCompound("Item",NBTIO.putItemHelper(item)).putFloat("ItemDropChance",1f);NativeExplosionFixture.attach(w,t);
            explode(w,w.cell);check(w.cell.isAir() && amount(w.allDrops(),item)==1 && w.allDrops().size()==2,"frame exact once id="+id);
        }
    }
    @Test void pairedChest() throws Exception {
        var w=world(Block.CHEST,0);var left=chest(w,0);var right=chest(w,1);
        Item a=payload(5),b=Item.get(Item.EMERALD,0,7);left.getRealInventory().slots.put(0,a.clone());right.getRealInventory().slots.put(0,b.clone());
        left.createPair(right);var pair=new DoubleChestInventory(left,right);
        NativeExplosionFixture.set(BlockEntityChest.class,left,"doubleInventory",pair);NativeExplosionFixture.set(BlockEntityChest.class,right,"doubleInventory",pair);
        explode(w,w.cell);
        check(w.cell.isAir() && !w.getBlock(1,64,0).isAir() && amount(w.allDrops(),a)==5 && amount(w.allDrops(),b)==0 && right.getRealInventory().getItem(0).equalsExact(b),"paired chest preserves unbroken half");
        explode(w,w.getBlock(1,64,0));check(amount(w.allDrops(),a)==5 && amount(w.allDrops(),b)==7 && w.allDrops().stream().filter(i->i.getId()==Block.CHEST).mapToInt(Item::getCount).sum()==2,"paired chest totals exact once");
    }
    static void listener(Consumer<ItemSpawnEvent> body,Runnable action) {
        var plugin=new PluginBase(){};plugin.setEnabled(true);
        RegisteredListener reg=new RegisteredListener(new Listener(){},(l,e)->body.accept((ItemSpawnEvent)e),EventPriority.NORMAL,plugin,false);
        ItemSpawnEvent.getHandlers().register(reg);
        try{action.run();}finally{ItemSpawnEvent.getHandlers().unregister(reg);}
    }
    @Test void spawnRefusal() throws Exception {
        for(boolean box:List.of(false,true)) {
            var w=world(box?Block.SHULKER_BOX:Block.CHEST,14);Inventory inv=box?shulker(w).getRealInventory():chest(w,0).getRealInventory();
            Item diamonds=payload(64);((BaseInventory)inv).slots.put(0,diamonds.clone());
            listener(event->event.getEntity().close(),()->{try{explode(w,w.cell);}catch(Exception expected){}});
            check(!w.cell.isAir() && amount(w.allDrops(),diamonds)==0 && inv.getItem(0).equalsExact(diamonds),"native spawn refusal box="+box);
        }
    }
    @Test void activeSource() throws Exception {
        for(boolean preparation:List.of(false,true)) {
            var w=world(Block.CHEST,0);var c=chest(w,0);Item item=payload(6);c.getRealInventory().slots.put(0,item);
            if(preparation)try(var p=NativeContainerTransaction.tryPrepare(c.getRealInventory())){explode(w,w.cell);}else try(var l=InventoryMutationGuard.tryClaim(List.of(c.getRealInventory()))){explode(w,w.cell);}
            check(!w.cell.isAir() && w.allDrops().isEmpty() && c.getRealInventory().getItem(0).equalsExact(item),"active source preparation="+preparation);
        }
    }
    @Test void pairedBlockParity() throws Exception {
        for(int id:List.of(Block.WOODEN_DOOR_BLOCK,Block.BED_BLOCK,Block.DOUBLE_PLANT)) {
            var w=world(id,0);w.cells.put("0,65,0,0",Block.get(id,8,w,0,65,0));
            Block low=w.cell,high=w.getBlock(0,65,0);explode(w,high,low);
            System.out.println("PAIR_PARITY id="+id+" lowAir="+w.cell.isAir()+" highAir="+w.getBlock(0,65,0).isAir()+" drops="+w.allDrops());
        }
    }

    static final class FaultPot extends BlockEntityDecoratedPot {
        boolean failBefore;
        FaultPot(cn.nukkit.level.format.FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }
        @Override public void setItem(Item item) {
            if(item.isNull() && failBefore) throw new IllegalStateException("pot before debit");
            super.setItem(item);
            if(item.isNull()) throw new IllegalStateException("pot after debit");
        }
    }
    @Test void potSetterPhases() throws Exception {
        BlockEntity.registerBlockEntity("FaultDecoratedPot", FaultPot.class);
        for(boolean before:List.of(true,false)) {
            var w=world(Block.DECORATED_POT,0);var pot=NativeExplosionFixture.allocated(FaultPot.class);
            var sherds=new ListTag<StringTag>("sherds");for(int i=0;i<4;i++)sherds.add(new StringTag("","minecraft:brick"));
            pot.namedTag=new CompoundTag().putList(sherds);pot.failBefore=before;
            Item stored=payload(7);NativeExplosionFixture.set(BlockEntityDecoratedPot.class,pot,"item",stored.clone());NativeExplosionFixture.attach(w,pot);
            boolean threw=false;try{explode(w,w.cell);}catch(IllegalStateException expected){threw=true;}
            check(threw && w.cell.isAir()==!before && amount(w.allDrops(),stored)==(before?0:7)
                    && amount(w.allDrops(),stored)+(pot.getItem().equalsExact(stored)?7:0)==7,"pot callback debit phase before="+before);
        }
    }


    @Test void absentTileIsReceiptIdentity() throws Exception {
        var w=world(Block.CHEST,0);
        listener(event->{if(w.tile==null)try{var replacement=chest(w,0);replacement.getRealInventory().slots.put(0,payload(7));}catch(Exception failure){throw new RuntimeException(failure);}},()->{try{explode(w,w.cell);}catch(Exception failure){throw new RuntimeException(failure);}});
        check(w.cell.getId()==Block.CHEST && w.tile instanceof BlockEntityChest && !w.tile.closed
                && ((BlockEntityChest)w.tile).getRealInventory().getItem(0).equalsExact(payload(7)) && w.allDrops().isEmpty(),"absent source tile cannot fund new replacement inventory");
    }
}
