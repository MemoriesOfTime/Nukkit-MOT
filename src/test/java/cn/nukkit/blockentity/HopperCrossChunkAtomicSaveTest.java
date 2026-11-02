package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import java.io.ByteArrayInputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.iq80.leveldb.DB;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native world-only full-chunk group acceptance through process termination and fresh LevelDB reopen. */
public final class HopperCrossChunkAtomicSaveTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings={"no-write","source-only","destination-only","source-then-destination","destination-then-source","same-chunk","saveChunks","saveChunk","saveChunkIfChanged","sync","unload","unloadAll","base-unload","close","chain-source","chain-destination","chain-bounded","preparing","event-half","cancelled","double-source","double-destination","unload-callback","level-unload","level-replace","read-offthread"})
    void preserveNativeUnitAcrossEverySaveEntryAndProcessRestart(String phase) throws Exception {
        Path caseRoot=Files.createDirectory(root.resolve(phase));
        Process child=start(phase,caseRoot);
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
            while(!Files.exists(caseRoot.resolve("ready")) && child.isAlive() && System.nanoTime()<deadline)Thread.sleep(10);
            assertTrue(Files.exists(caseRoot.resolve("ready")),()->log(caseRoot,phase));
            child.destroyForcibly();assertTrue(child.waitFor(10,TimeUnit.SECONDS));assertNotEquals(0,child.exitValue());
        } finally {if(child.isAlive()){child.destroyForcibly();child.waitFor(10,TimeUnit.SECONDS);}}
        Process recovery=start("recover:"+phase,caseRoot);
        try {
            assertTrue(recovery.waitFor(45,TimeUnit.SECONDS));assertEquals(0,recovery.exitValue(),()->log(caseRoot,"recover:"+phase));
            String actual=Files.readString(caseRoot.resolve("recovered"));
            String expected=switch(phase){case "no-write","unload-callback"->"1,0";default->"0,1";};
            assertEquals(expected,actual,"source,destination durable unit counts");
            assertEquals(1,java.util.Arrays.stream(actual.split(",")).mapToInt(Integer::parseInt).sum(),"original native unit conservation after fresh reopen");
            System.out.println("CROSS_CHUNK_CRASH phase="+phase+" durable="+actual+" total="+java.util.Arrays.stream(actual.split(",")).mapToInt(Integer::parseInt).sum());
        } finally {if(recovery.isAlive()){recovery.destroyForcibly();recovery.waitFor(10,TimeUnit.SECONDS);}}
    }
    static Process start(String phase,Path root)throws Exception {
        String cp=System.getProperty("java.class.path");List<String> cmd=new ArrayList<>();cmd.add(Path.of(System.getProperty("java.home"),"bin","java").toString());cmd.add("-Xmx256m");cmd.add("-Dnet.bytebuddy.experimental=true");
        for(String part:cp.split(java.io.File.pathSeparator))if(part.contains("mockito-core")&&part.endsWith(".jar")){cmd.add("-javaagent:"+part);break;}
        cmd.addAll(List.of("-Dcontainer.groups.native="+Boolean.getBoolean("container.groups.native"),"-cp",cp,Worker.class.getName(),phase,root.toString()));return new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(root.resolve(phase.replace(':','-')+".log").toFile()).start();
    }
    static String log(Path root,String phase){try{return Files.readString(root.resolve(phase.replace(':','-')+".log"));}catch(Exception error){return error.toString();}}
    public static final class Worker {
        public static void main(String[] args)throws Exception {
            String phase=args[0];boolean recovering=phase.startsWith("recover:");if(recovering)phase=phase.substring("recover:".length());Path root=Path.of(args[1]);Path world=root.resolve("world");
            if(phase.startsWith("double-")){doubleChest(phase,recovering,root,world);return;}
            if(phase.startsWith("chain-")){chain(phase,recovering,root,world);return;}
            MockServer.init();Server server=Server.getInstance();when(server.getPluginManager()).thenReturn(new cn.nukkit.plugin.PluginManager(server,null));server.useNativeLevelDB=Boolean.getBoolean("container.groups.native");server.levelDbCache=8;server.asyncChunkSending=true;server.asyncChunkLoadCompletion=true;
            Level level=Mockito.mock(Level.class);when(level.getServer()).thenReturn(server);when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
            if(!recovering)LevelDBProvider.generate(world.toString(),"cross-chunk-hopper",404L,Flat.class);
            LevelDBProvider provider=new LevelDBProvider(level,world.toString());
            if(Boolean.getBoolean("container.groups.native"))assertTrue(net.daporkchop.ldbjni.LevelDB.PROVIDER.isNative(),"requested JNI must not fall back to Java");
            if(Boolean.getBoolean("container.groups.native"))assertEquals("net.daporkchop.ldbjni.natives.NativeDB",database(provider).getClass().getName());
            System.out.println("CONTAINER_GROUP_BACKEND native="+server.useNativeLevelDB+" selected="+net.daporkchop.ldbjni.LevelDB.PROVIDER.isNative()+" actual="+database(provider).getClass().getName());
            when(level.getProvider()).thenReturn(provider);when(level.requireProvider()).thenReturn(provider);
            int destinationX=phase.equals("same-chunk")?14:16;int destinationChunk=destinationX>>4;
            if(recovering){DB db=database(provider);int source=count(db,0,15);int target=count(db,destinationChunk,destinationX);Files.writeString(root.resolve("recovered"),source+","+target);provider.close();System.exit(0);}
            LevelDBChunk sourceChunk=provider.getEmptyChunk(0,0);sourceChunk.setGenerated(true);provider.setChunk(0,0,sourceChunk);
            LevelDBChunk targetChunk=destinationChunk==0?sourceChunk:provider.getEmptyChunk(destinationChunk,0);targetChunk.setGenerated(true);provider.setChunk(destinationChunk,0,targetChunk);
            when(level.getChunkIfLoaded(anyInt(),anyInt())).thenAnswer(call->{int x=call.getArgument(0);int z=call.getArgument(1);return z==0?(x==0?sourceChunk:x==destinationChunk?targetChunk:null):null;});
            when(level.getChunk(anyInt(),anyInt(),anyBoolean())).thenAnswer(call->provider.getChunk(call.getArgument(0),call.getArgument(1),false));
            sourceChunk.setBlock(15,64,0,Block.HOPPER_BLOCK,phase.equals("same-chunk")?4:5);targetChunk.setBlock(destinationX&15,64,0,Block.CHEST);
            BlockEntityHopper source=new BlockEntityHopper(sourceChunk,nbt(BlockEntity.HOPPER,15));BlockEntityChest target=new BlockEntityChest(targetChunk,nbt(BlockEntity.CHEST,destinationX));
            when(level.getBlockDataAt(15,64,0)).thenReturn(phase.equals("same-chunk")?4:5);when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.CHEST));
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->{Vector3 pos=call.getArgument(0);return pos.getFloorX()==destinationX&&pos.getFloorY()==64&&pos.getFloorZ()==0?target:pos.getFloorX()==15&&pos.getFloorY()==64&&pos.getFloorZ()==0?source:null;});
            Item unit=Item.get(Item.DIAMOND,0,1).setNamedTag(new CompoundTag().putString("opaque_unit","cross-chunk-one-original").putCompound("future_component",new CompoundTag().putInt("raw",178)));
            source.getInventory().setItem(0,unit);provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);if(destinationChunk!=0)provider.saveChunkFuture(destinationChunk,0,targetChunk).get(10,TimeUnit.SECONDS);
            if(phase.equals("unload-callback")) {
                cn.nukkit.Player viewer=Mockito.mock(cn.nukkit.Player.class);source.getInventory().getViewers().add(viewer);
                java.util.concurrent.atomic.AtomicBoolean callback=new java.util.concurrent.atomic.AtomicBoolean();
                doAnswer(call->{callback.set(true);assertFalse(source.pushItems(),"lifecycle reservation must reject a reentrant transfer before detach");assertEquals(1,units(source.getInventory()));assertEquals(0,units(target.getInventory()));return null;}).when(viewer).removeWindow(any(Inventory.class));
                assertTrue(provider.unloadChunk(0,0,false));assertTrue(callback.get());assertEquals(1,count(database(provider),0,15));assertEquals(0,count(database(provider),1,16));
                Files.writeString(root.resolve("ready"),phase);if(!new CountDownLatch(1).await(60,TimeUnit.SECONDS))throw new AssertionError("Parent did not terminate owned child");return;
            }
            if(phase.equals("preparing")) {
                var field=LevelDBProvider.class.getDeclaredField("containerGroups");field.setAccessible(true);Object groups=field.get(provider);
                var reserve=groups.getClass().getDeclaredMethod("reserveStandalone",long.class);reserve.setAccessible(true);
                try(AutoCloseable reservation=(AutoCloseable)reserve.invoke(groups,Level.chunkHash(0,0))) {
                    assertFalse(source.pushItems());assertEquals(1,units(source.getInventory()));assertEquals(0,units(target.getInventory()));assertTrue(source.persistenceRetryRequested());
                }
            }
            java.util.concurrent.atomic.AtomicReference<Throwable> callbackFailure=new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicBoolean eventSeen=new java.util.concurrent.atomic.AtomicBoolean();
            cn.nukkit.plugin.RegisteredListener listener=null;
            if(phase.equals("event-half")||phase.equals("cancelled")) {
                cn.nukkit.plugin.Plugin plugin=Mockito.mock(cn.nukkit.plugin.Plugin.class);when(plugin.isEnabled()).thenReturn(true);
                String callbackPhase=phase;
                listener=new cn.nukkit.plugin.RegisteredListener(new cn.nukkit.event.Listener(){},(ignored,event)-> {
                    try {
                    eventSeen.set(true);assertEquals(1,units(source.getInventory()));assertEquals(0,units(target.getInventory()));
                    assertTrue(provider.saveChunkFuture(0,0,sourceChunk).isCompletedExceptionally());assertThrows(org.iq80.leveldb.DBException.class,provider::close);
                    if(callbackPhase.equals("cancelled"))((cn.nukkit.event.inventory.InventoryMoveItemEvent)event).setCancelled();
                    } catch(RuntimeException|Error failure) {callbackFailure.set(failure);throw failure;}
                },cn.nukkit.event.EventPriority.NORMAL,plugin,false);
                cn.nukkit.event.inventory.InventoryMoveItemEvent.getHandlers().register(listener);
                if(phase.equals("cancelled")){assertFalse(source.pushItems());assertTrue(eventSeen.get());assertEquals(1,units(source.getInventory()));assertEquals(0,units(target.getInventory()));cn.nukkit.event.inventory.InventoryMoveItemEvent.getHandlers().unregister(listener);listener=null;}
            }
            var oldRead=provider.openChunkRead(0,0);
            if(!source.pushItems())throw new AssertionError("Actual native hopper push refused");if(units(source.getInventory())!=0||units(target.getInventory())!=1)throw new AssertionError("Expected atomic memory debit0/credit1");
            if(callbackFailure.get()!=null)throw new AssertionError("Native event dispatcher swallowed fixture assertion",callbackFailure.get());
            if(listener!=null){assertTrue(eventSeen.get());cn.nukkit.event.inventory.InventoryMoveItemEvent.getHandlers().unregister(listener);}
            if(!sourceChunk.hasChanged()||!targetChunk.hasChanged())throw new AssertionError("Container changes failed to dirty physical chunks");
            assertEquals(1,count(database(provider),0,15));assertEquals(0,count(database(provider),destinationChunk,destinationX),"admission and native setters perform no storage write");
            assertThrows(java.util.concurrent.CancellationException.class,oldRead::read); oldRead.close();
            switch(phase){
                case "source-only","preparing","event-half","cancelled"->provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);
                case "destination-only"->provider.saveChunkFuture(destinationChunk,0,targetChunk).get(10,TimeUnit.SECONDS);
                case "source-then-destination"->{provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);provider.saveChunkFuture(destinationChunk,0,targetChunk).get(10,TimeUnit.SECONDS);}
                case "destination-then-source"->{provider.saveChunkFuture(destinationChunk,0,targetChunk).get(10,TimeUnit.SECONDS);provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "same-chunk"->provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);
                case "saveChunks"->{provider.saveChunks();provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "saveChunk"->{provider.saveChunk(0,0);provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "saveChunkIfChanged"->{assertTrue(provider.saveChunkIfChanged(Level.chunkHash(0,0)));provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "sync"->{try{provider.saveChunkSync(0,0,sourceChunk);}catch(org.iq80.leveldb.DBException queued){}provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "unload"->{assertFalse(provider.unloadChunk(0,0,false));provider.maintainContainerGroups();provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "unloadAll"->{provider.unloadChunks();assertSame(sourceChunk,provider.getChunk(0,0,false));provider.maintainContainerGroups();provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "base-unload"->{assertFalse(sourceChunk.unload(true,false));provider.maintainContainerGroups();provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "level-unload"->{doCallRealMethod().when(level).unloadChunk(0,0,false,false);assertFalse(level.unloadChunk(0,0,false,false));provider.maintainContainerGroups();provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "level-replace"->{doCallRealMethod().when(level).setChunk(eq(0),eq(0),any(cn.nukkit.level.format.generic.BaseFullChunk.class),eq(false));assertThrows(IllegalStateException.class,()->level.setChunk(0,0,provider.getEmptyChunk(0,0),false));assertSame(sourceChunk,source.chunk);assertSame(source,sourceChunk.getBlockEntities().get(source.getId()));provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "read-offthread"->{assertThrows(java.util.concurrent.CancellationException.class,()->provider.readChunkOffThread(0,0));provider.saveChunkFuture(0,0,sourceChunk).get(10,TimeUnit.SECONDS);}
                case "close"->provider.close();
                case "no-write"->{}
                default->throw new AssertionError(phase);
            }
            Files.writeString(root.resolve("ready"),phase);if(!new CountDownLatch(1).await(60,TimeUnit.SECONDS))throw new AssertionError("Parent did not terminate owned child");
        }
        static void doubleChest(String phase,boolean recovering,Path root,Path world)throws Exception {
            MockServer.init();Server server=Server.getInstance();when(server.getPluginManager()).thenReturn(new cn.nukkit.plugin.PluginManager(server,null));server.useNativeLevelDB=Boolean.getBoolean("container.groups.native");server.levelDbCache=8;server.asyncChunkSending=true;server.asyncChunkLoadCompletion=true;
            Level level=Mockito.mock(Level.class);when(level.getServer()).thenReturn(server);when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
            if(!recovering)LevelDBProvider.generate(world.toString(),"native-double-chest",404L,Flat.class);
            LevelDBProvider provider=new LevelDBProvider(level,world.toString());when(level.getProvider()).thenReturn(provider);when(level.requireProvider()).thenReturn(provider);
            if(Boolean.getBoolean("container.groups.native"))assertTrue(net.daporkchop.ldbjni.LevelDB.PROVIDER.isNative(),"JNI fallback denied");
            if(Boolean.getBoolean("container.groups.native"))assertEquals("net.daporkchop.ldbjni.natives.NativeDB",database(provider).getClass().getName());
            System.out.println("CONTAINER_GROUP_BACKEND native="+server.useNativeLevelDB+" selected="+net.daporkchop.ldbjni.LevelDB.PROVIDER.isNative()+" actual="+database(provider).getClass().getName());
            if(recovering){Files.writeString(root.resolve("recovered"),count(database(provider),0,14)+","+(count(database(provider),0,15)+count(database(provider),1,16)));provider.close();System.exit(0);}
            LevelDBChunk[] chunks={provider.getEmptyChunk(0,0),provider.getEmptyChunk(1,0)};for(int i=0;i<2;i++){chunks[i].setGenerated(true);provider.setChunk(i,0,chunks[i]);}
            when(level.getChunkIfLoaded(anyInt(),anyInt())).thenAnswer(call->{int x=call.getArgument(0),z=call.getArgument(1);return z==0&&x>=0&&x<2?chunks[x]:null;});
            when(level.getChunk(anyInt(),anyInt(),anyBoolean())).thenAnswer(call->provider.getChunk(call.getArgument(0),call.getArgument(1),false));
            chunks[0].setBlock(14,64,0,Block.HOPPER_BLOCK,5);chunks[0].setBlock(15,64,0,Block.CHEST);chunks[1].setBlock(0,64,0,Block.CHEST);
            BlockEntityHopper hopper=new BlockEntityHopper(chunks[0],nbt(BlockEntity.HOPPER,14));BlockEntityChest left=new BlockEntityChest(chunks[0],nbt(BlockEntity.CHEST,15)),right=new BlockEntityChest(chunks[1],nbt(BlockEntity.CHEST,16));
            cn.nukkit.inventory.DoubleChestInventory pair=new cn.nukkit.inventory.DoubleChestInventory(left,right);left.doubleInventory=pair;right.doubleInventory=pair;
            for(int i=0;i<27;i++)assertTrue(pair.setItem(i,Item.get(Item.COAL,0,64)));
            when(level.getBlockDataAt(anyInt(),anyInt(),anyInt())).thenReturn(5);when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.CHEST));
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->{Vector3 v=call.getArgument(0);return v.getFloorY()!=64||v.getFloorZ()!=0?null:switch(v.getFloorX()){case 14->hopper;case 15->left;case 16->right;default->null;};});
            Item unit=Item.get(Item.DIAMOND,0,1).setNamedTag(new CompoundTag().putString("opaque_unit","cross-chunk-one-original").putCompound("future_component",new CompoundTag().putInt("raw",178)));hopper.getInventory().setItem(0,unit);
            for(int i=0;i<2;i++)provider.saveChunkFuture(i,0,chunks[i]).get(10,TimeUnit.SECONDS);
            assertTrue(hopper.pushItems());assertEquals(0,units(hopper.getInventory()));assertEquals(1,units(right.getRealInventory()));assertEquals(2,provider.containerCaptureCost(Level.chunkHash(0,0)));
            int save=phase.equals("double-source")?0:1;provider.saveChunkFuture(save,0,chunks[save]).get(10,TimeUnit.SECONDS);
            Files.writeString(root.resolve("ready"),phase);if(!new CountDownLatch(1).await(60,TimeUnit.SECONDS))throw new AssertionError("Parent did not terminate owned child");
        }
        static void chain(String phase,boolean recovering,Path root,Path world)throws Exception {
            MockServer.init();Server server=Server.getInstance();when(server.getPluginManager()).thenReturn(new cn.nukkit.plugin.PluginManager(server,null));server.useNativeLevelDB=Boolean.getBoolean("container.groups.native");server.levelDbCache=8;server.asyncChunkSending=true;server.asyncChunkLoadCompletion=true;
            Level level=Mockito.mock(Level.class);when(level.getServer()).thenReturn(server);when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
            if(!recovering)LevelDBProvider.generate(world.toString(),"native-hopper-chain",404L,Flat.class);
            LevelDBProvider provider=new LevelDBProvider(level,world.toString());
            if(Boolean.getBoolean("container.groups.native"))assertTrue(net.daporkchop.ldbjni.LevelDB.PROVIDER.isNative(),"requested JNI must not fall back to Java");
            if(Boolean.getBoolean("container.groups.native"))assertEquals("net.daporkchop.ldbjni.natives.NativeDB",database(provider).getClass().getName());
            System.out.println("CONTAINER_GROUP_BACKEND native="+server.useNativeLevelDB+" selected="+net.daporkchop.ldbjni.LevelDB.PROVIDER.isNative()+" actual="+database(provider).getClass().getName());
            when(level.getProvider()).thenReturn(provider);when(level.requireProvider()).thenReturn(provider);
            if(recovering){int remaining=0;for(int x=15;x<32;x++)remaining+=count(database(provider),x>>4,x);Files.writeString(root.resolve("recovered"),remaining+","+count(database(provider),2,32));provider.close();System.exit(0);}
            LevelDBChunk[] chunks=new LevelDBChunk[3];for(int i=0;i<3;i++){chunks[i]=provider.getEmptyChunk(i,0);chunks[i].setGenerated(true);provider.setChunk(i,0,chunks[i]);}
            when(level.getChunkIfLoaded(anyInt(),anyInt())).thenAnswer(call->{int x=call.getArgument(0),z=call.getArgument(1);return z==0&&x>=0&&x<3?chunks[x]:null;});
            when(level.getChunk(anyInt(),anyInt(),anyBoolean())).thenAnswer(call->provider.getChunk(call.getArgument(0),call.getArgument(1),false));
            java.util.Map<Integer,BlockEntity> holders=new java.util.HashMap<>();for(int x=15;x<32;x++){chunks[x>>4].setBlock(x&15,64,0,Block.HOPPER_BLOCK,5);holders.put(x,new BlockEntityHopper(chunks[x>>4],nbt(BlockEntity.HOPPER,x)));}
            chunks[2].setBlock(0,64,0,Block.CHEST);BlockEntityChest destination=new BlockEntityChest(chunks[2],nbt(BlockEntity.CHEST,32));holders.put(32,destination);
            when(level.getBlockDataAt(anyInt(),anyInt(),anyInt())).thenReturn(5);when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.CHEST));
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->{Vector3 p=call.getArgument(0);return p.getFloorY()==64&&p.getFloorZ()==0?holders.get(p.getFloorX()):null;});
            Item unit=Item.get(Item.DIAMOND,0,1).setNamedTag(new CompoundTag().putString("opaque_unit","cross-chunk-one-original").putCompound("future_component",new CompoundTag().putInt("raw",178)));
            ((BlockEntityHopper)holders.get(15)).getInventory().setItem(0,unit);for(int i=0;i<3;i++)provider.saveChunkFuture(i,0,chunks[i]).get(10,TimeUnit.SECONDS);
            if(phase.equals("chain-bounded")){var field=LevelDBProvider.class.getDeclaredField("containerGroups");field.setAccessible(true);Object groups=field.get(provider);var cap=groups.getClass().getDeclaredField("maxChunks");cap.setAccessible(true);cap.setInt(groups,2);}
            for(int x=15;x<31;x++)assertTrue(((BlockEntityHopper)holders.get(x)).pushItems(),"native hopper chain hop "+x);
            if(phase.equals("chain-bounded")){
                BlockEntityHopper bridge=(BlockEntityHopper)holders.get(31);assertFalse(bridge.pushItems());assertEquals(1,units(bridge.getInventory()));assertEquals(0,units(destination.getInventory()));assertTrue(bridge.persistenceRetryRequested());
                provider.maintainContainerGroups();provider.saveChunkFuture(0,0,chunks[0]).get(10,TimeUnit.SECONDS);assertTrue(bridge.pushItems(),"bridge retries after complete old group ACK");
                provider.saveChunkFuture(2,0,chunks[2]).get(10,TimeUnit.SECONDS);
            }else{
                assertTrue(((BlockEntityHopper)holders.get(31)).pushItems());assertEquals(3,provider.containerCaptureCost(Level.chunkHash(0,0)),"transitive A-B-C members");
            }
            assertEquals(1,units(destination.getInventory()));
            int save=phase.equals("chain-source")?0:2;provider.saveChunkFuture(save,0,chunks[save]).get(10,TimeUnit.SECONDS);
            Files.writeString(root.resolve("ready"),phase);if(!new CountDownLatch(1).await(60,TimeUnit.SECONDS))throw new AssertionError("Parent did not terminate owned child");
        }
        static CompoundTag nbt(String type,int x){return new CompoundTag().putString("id",type).putInt("x",x).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items"));}
        static int units(Inventory inventory){return inventory.getContents().values().stream().filter(item->item.getId()==Item.DIAMOND).mapToInt(Item::getCount).sum();}
        static DB database(LevelDBProvider provider)throws Exception{var field=LevelDBProvider.class.getDeclaredField("db");field.setAccessible(true);return (DB)field.get(provider);}
        static int count(DB db,int chunkX,int x)throws Exception {
            byte[] data=db.get(LevelDBKey.BLOCK_ENTITIES.getKey(chunkX,0,Level.DIMENSION_OVERWORLD));if(data==null)return 0;int units=0;
            try(var input=new ByteArrayInputStream(data)){while(input.available()>0){CompoundTag tag=NBTIO.read(input,ByteOrder.LITTLE_ENDIAN);if(tag.getInt("x")!=x)continue;for(CompoundTag entry:tag.getList("Items",CompoundTag.class).getAll()){Item item=NBTIO.getItemHelper(entry);if(item.getId()==Item.DIAMOND){if(!item.getNamedTag().getString("opaque_unit").equals("cross-chunk-one-original")||item.getNamedTag().getCompound("future_component").getInt("raw")!=178)throw new AssertionError("Opaque serialized source payload drift");units+=item.getCount();}}}}
            return units;
        }
    }
}
