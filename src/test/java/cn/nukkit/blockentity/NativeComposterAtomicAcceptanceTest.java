package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockComposter;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.ComposterEmptyEvent;
import cn.nukkit.event.block.ComposterFillEvent;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.item.Item;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.anvil.ChunkSection;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginDescription;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual inventories/events; acceptance-witness controls also run actual Level.setBlock and Anvil chunk/storage. */
@org.junit.jupiter.api.parallel.Isolated
@SuppressWarnings("try")
public class NativeComposterAtomicAcceptanceTest {
    private static Server originalServer;
    @org.junit.jupiter.api.BeforeAll static void rememberServer() { originalServer = Server.getInstance(); }
    @org.junit.jupiter.api.AfterAll static void restoreServer() throws Exception {
        var field = Server.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, originalServer);
    }
    private Server server;
    private PluginManager previous;
    private BaseLang previousLanguage;
    private final List<Registration> registrations = new ArrayList<>();
    private record Registration(HandlerList handlers, RegisteredListener listener) { }
    @BeforeEach void setup() {
        MockServer.init(); server = Server.getInstance(); previous = server.getPluginManager(); previousLanguage = server.getLanguage();
        when(server.getPluginManager()).thenReturn(new PluginManager(server, null)); when(server.getLanguage()).thenReturn(new BaseLang("eng"));
    }
    @AfterEach void cleanup() {
        registrations.forEach(r -> r.handlers().unregister(r.listener()));
        when(server.getPluginManager()).thenReturn(previous); when(server.getLanguage()).thenReturn(previousLanguage);
    }
    private <T extends Event> void listen(HandlerList handlers, Class<T> type, Consumer<T> body) {
        Plugin plugin = mock(Plugin.class); when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDescription()).thenReturn(new PluginDescription(Map.of("name","composter-atomic-control","version","1","main","test.Main","api",List.of("1.0.0"))));
        RegisteredListener registration = new RegisteredListener(new Listener() {}, (l,e) -> body.accept(type.cast(e)), EventPriority.NORMAL, plugin, false);
        handlers.register(registration); registrations.add(new Registration(handlers, registration));
    }
    private void fill(Consumer<ComposterFillEvent> body) { listen(ComposterFillEvent.getHandlers(), ComposterFillEvent.class, body); }
    private void empty(Consumer<ComposterEmptyEvent> body) { listen(ComposterEmptyEvent.getHandlers(), ComposterEmptyEvent.class, body); }
    private void move(Consumer<InventoryMoveItemEvent> body) { listen(InventoryMoveItemEvent.getHandlers(), InventoryMoveItemEvent.class, body); }

    @Test void nativeWorldAcceptanceThenIndependentReturnToOldCellAndFatalErrorCannotRefundIngredient() throws Exception {
        Fixture f = new Fixture(true); AssertionError failure = new AssertionError("after accepted world and independent native restoration");
        AtomicInteger calls = new AtomicInteger();
        doCallRealMethod().when(f.level).addCallbackBlockSet(any(BiConsumer.class));
        f.level.addCallbackBlockSet((before, after) -> {
            if (calls.getAndIncrement() == 0) {
                assertEquals(8, f.damage()); assertEquals(4, f.pies());
                assertTrue(f.level.setBlock(f.composter(7), f.composter(7), true, true));
                assertEquals(7, f.damage()); throw failure;
            }
        });
        assertSame(failure, assertThrows(AssertionError.class, f.source::pushItems));
        assertEquals(4, f.pies()); assertEquals(7, f.damage()); assertEquals(2, calls.get()); f.sourceExact();
    }
    @Test void nativeWorldAcceptanceThenIndependentReturnToReadyCellAndFatalErrorCannotRevokeOutput() throws Exception {
        Fixture f = new Fixture(true); f.ready(); AssertionError failure = new AssertionError("after accepted empty and independent native ready"); AtomicInteger calls = new AtomicInteger();
        doCallRealMethod().when(f.level).addCallbackBlockSet(any(BiConsumer.class));
        f.level.addCallbackBlockSet((before, after) -> {
            if (calls.getAndIncrement() == 0) {
                assertEquals(0, f.damage()); assertEquals(1, f.meals());
                assertTrue(f.level.setBlock(f.composter(8), f.composter(8), true, true)); throw failure;
            }
        });
        assertSame(failure, assertThrows(AssertionError.class, f.destination::pullItems));
        assertEquals(1, f.meals()); assertEquals(8, f.damage()); assertEquals(2, calls.get());
    }
    @Test void nativeOrdinaryWitnessAndLegacySetBlockKeepTheirAcceptanceBehavior() throws Exception {
        Fixture f = new Fixture(true); AtomicInteger accepted = new AtomicInteger();
        assertTrue(f.level.setBlock(f.composter(8), f.composter(8), true, true, accepted::incrementAndGet)); assertEquals(1, accepted.get());
        assertFalse(f.level.setBlock(f.composter(8), f.composter(8), true, true, accepted::incrementAndGet)); assertEquals(1, accepted.get());
        assertTrue(f.level.setBlock(f.composter(7), f.composter(7), true, true));
        assertTrue(f.source.pushItems()); assertTrue(f.destination.pullItems()); assertEquals(4,f.pies());assertEquals(1,f.meals());assertEquals(0,f.damage());
    }
    @Test void refusedWorldWriteRestoresExactIngredientBeforePublication() throws Exception {
        Fixture f=new Fixture(false); doReturn(false).when(f.level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true));
        assertFalse(f.source.pushItems()); assertEquals(5,f.pies());assertEquals(7,f.damage());f.sourceExact();
    }
    @Test void refusedWorldWriteRevokesOnlyProvisionalDestinationGrant() throws Exception {
        Fixture f=new Fixture(false);f.ready();doReturn(false).when(f.level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true));
        assertFalse(f.destination.pullItems());assertEquals(8,f.damage());assertEquals(0,f.meals());
    }
    @Test void nativeSourceDirtyCallbackRuntimeExceptionRestoresExactRawSlots() throws Exception {
        Fixture f=new Fixture(false);f.hidden(f.source.getInventory());Map<Integer,Item> raw=f.captureRaw(f.source.getInventory());IllegalStateException failure=new IllegalStateException("source dirty");
        doThrow(failure).when(f.chunk).setChanged();assertSame(failure,assertThrows(IllegalStateException.class,f.source::pushItems));f.rawEquals(raw,f.source.getInventory());assertEquals(7,f.damage());
    }
    @Test void nativeDestinationDirtyCallbackFatalErrorRestoresExactRawSlots() throws Exception {
        Fixture f=new Fixture(false);f.ready();f.hidden(f.destination.getInventory());Map<Integer,Item> raw=f.captureRaw(f.destination.getInventory());AssertionError failure=new AssertionError("destination dirty");
        doThrow(failure).when(f.chunk).setChanged();assertSame(failure,assertThrows(AssertionError.class,f.destination::pullItems));f.rawEquals(raw,f.destination.getInventory());assertEquals(8,f.damage());
    }
    @Test void worldRuntimeExceptionBeforePublicationRestoresIngredient() throws Exception {
        Fixture f=new Fixture(false);IllegalStateException failure=new IllegalStateException("world before publication");
        doThrow(failure).when(f.level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true));
        assertSame(failure,assertThrows(IllegalStateException.class,f.source::pushItems));assertEquals(5,f.pies());assertEquals(7,f.damage());f.sourceExact();
    }
    @Test void worldFatalErrorBeforePublicationRevokesProvisionalOutput() throws Exception {
        Fixture f=new Fixture(false);f.ready();AssertionError failure=new AssertionError("world before publication");
        doThrow(failure).when(f.level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true));
        assertSame(failure,assertThrows(AssertionError.class,f.destination::pullItems));assertEquals(0,f.meals());assertEquals(8,f.damage());
    }
    @Test void successfulConversionPreservesHiddenNegativeAndOpaqueSlots() throws Exception {
        Fixture f=new Fixture(false);f.hidden(f.source.getInventory());f.hidden(f.destination.getInventory());Item negative=f.raw(f.source.getInventory()).get(-7).clone();Item hidden=f.raw(f.destination.getInventory()).get(29).clone();
        assertTrue(f.source.pushItems());assertTrue(f.destination.pullItems());assertTrue(negative.equalsExact(f.raw(f.source.getInventory()).get(-7)));assertTrue(hidden.equalsExact(f.raw(f.destination.getInventory()).get(29)));f.sourceExact();
    }
    @Test void authoredOpaqueOutputFitsBeforeAnyReadySourceIsRemoved() throws Exception {
        Fixture f=new Fixture(false);f.ready();Item authored=Item.get(Item.EMERALD,0,13);authored.setNamedTag(new CompoundTag().putString("opaque","plugin-output").putByteArray("bytes",new byte[]{-1,23,0}));
        Item merge=authored.clone();merge.setCount(63);for(int i=0;i<5;i++)assertTrue(f.destination.getInventory().setItem(i,i==4?merge:Item.get(Item.DIAMOND,0,64)));
        empty(event->event.setDrop(authored));assertTrue(f.destination.pullItems());assertEquals(0,f.damage());Item result=f.destination.getInventory().getItem(4);assertEquals(64,result.getCount());assertArrayEquals(authored.getCompoundTag(),result.getCompoundTag());
    }
    @Test void moveEventRemainsCancellationGateWhileEmptyEventAuthorsExactOutput() throws Exception {
        Fixture f=new Fixture(false);f.ready();empty(event->event.setDrop(Item.get(Item.EMERALD,0,9)));move(event->event.setItem(Item.get(Item.DIAMOND,0,99)));
        assertTrue(f.destination.pullItems());assertEquals(Item.EMERALD,f.destination.getInventory().getItem(0).getId());assertEquals(1,f.destination.getInventory().getItem(0).getCount());assertEquals(0,f.damage());
    }
    @Test void acceptedIndependentEmptyCallbackTransferDoesNotGetErasedOrReplayed() throws Exception {
        Fixture f=new Fixture(false);f.ready();empty(event->{assertTrue(f.destination.getInventory().setItem(0,Item.get(Item.EMERALD,0,5)));});
        assertFalse(f.destination.pullItems());assertEquals(5,f.destination.getInventory().getItem(0).getCount());assertEquals(8,f.damage());assertEquals(0,f.meals());
    }
    @Test void acceptedIndependentFillCallbackWorldChangeSurvivesOuterRefusal() throws Exception {
        Fixture f=new Fixture(false);fill(event->{assertTrue(f.level.setBlock(f.composter(3),f.composter(3),true,true));});
        assertFalse(f.source.pushItems());assertEquals(5,f.pies());assertEquals(3,f.damage());f.sourceExact();
    }
    @Test void nativeHolderReplacementDuringEventRefusesOuterPlan() throws Exception {
        Fixture f=new Fixture(false);fill(event->when(f.level.getBlockEntity(f.source)).thenReturn(null));assertFalse(f.source.pushItems());assertEquals(5,f.pies());assertEquals(7,f.damage());
    }
    @Test void nativeStackLimitMutationDuringEventIsPreservedAndRefusesStaleOuter() throws Exception {
        Fixture f=new Fixture(false);fill(event->f.source.getInventory().setMaxStackSize(32));assertFalse(f.source.pushItems());assertEquals(32,f.source.getInventory().getMaxStackSize());assertEquals(5,f.pies());assertEquals(7,f.damage());
    }
    @Test void worldCallbackReentryFromDifferentNativeHopperCannotRepeatReadyDebit() throws Exception {
        Fixture f=new Fixture(false);f.ready();BlockEntityHopper other=new BlockEntityHopper(f.chunk,tag(BlockEntity.HOPPER,61));AtomicInteger calls=new AtomicInteger();
        doAnswer(call->{BlockComposter next=(BlockComposter)call.getArgument(1);f.world.set((BlockComposter)next.clone());((Runnable)call.getArgument(4)).run();if(calls.getAndIncrement()==0)assertFalse(other.pullItems(null,f.composter(8)));return true;})
                .when(f.level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true),any(Runnable.class));
        assertTrue(f.destination.pullItems());assertEquals(1,f.meals());assertEquals(0,other.getInventory().getContents().size());assertEquals(0,f.damage());
    }
    @Test void successfulFillDecisionCanBeCancelledWithoutLockingNextOrdinaryAttempt() throws Exception {
        Fixture f=new Fixture(false);AtomicInteger calls=new AtomicInteger();fill(event->{if(calls.getAndIncrement()==0)event.setCancelled(true);});
        assertFalse(f.source.pushItems());assertTrue(f.source.pushItems());assertEquals(4,f.pies());assertEquals(8,f.damage());
    }
    @Test void acceptedEmptySoundFatalErrorDoesNotRefundReadySourceOrOutput() throws Exception {
        Fixture f=new Fixture(false);f.ready();AssertionError failure=new AssertionError("empty sound");doThrow(failure).when(f.level).addSound(any(Vector3.class),any(Sound.class));
        assertSame(failure,assertThrows(AssertionError.class,f.destination::pullItems));assertEquals(0,f.damage());assertEquals(1,f.meals());
    }

    @Test void visibleOverstackThatWouldRouteProvisionalOverflowIsRefusedExactly() throws Exception {
        Fixture f=new Fixture(false);Item over=f.source.getInventory().getItem(0);over.setCount(129);f.raw(f.source.getInventory()).put(0,over);Map<Integer,Item> before=f.captureRaw(f.source.getInventory());
        assertFalse(f.source.pushItems());assertEquals(129,f.pies());assertEquals(7,f.damage());f.rawEquals(before,f.source.getInventory());f.sourceExact();
    }
    @Test void singleDebitAtNativeStackBoundaryDoesNotBlanketRejectLoadedSource() throws Exception {
        Fixture f=new Fixture(false);Item over=f.source.getInventory().getItem(0);over.setCount(65);f.raw(f.source.getInventory()).put(0,over);
        assertTrue(f.source.pushItems());assertEquals(64,f.pies());assertEquals(8,f.damage());f.sourceExact();
    }
    @Test void emptyEventMayAuthorRetainedReadyStateWithoutLosingItsOutputContract() throws Exception {
        Fixture f=new Fixture(false);f.ready();empty(event->event.setNewLevel(8));assertTrue(f.destination.pullItems());assertEquals(8,f.damage());assertEquals(1,f.meals());
    }
    @Test void invalidRawComposterLevelIsRefusedWithoutChangingInventory() throws Exception {
        Fixture f=new Fixture(false);f.world.set(f.composter(9));assertFalse(f.source.pushItems());assertEquals(9,f.damage());assertEquals(5,f.pies());f.sourceExact();
    }
    @Test void staleCellNativeHolderCreatedDuringEventCannotReceiveOldSourceDebit() throws Exception {
        Fixture f=new Fixture(false);fill(event->when(f.level.getBlockEntity(f.composter(7))).thenReturn(f.source));assertFalse(f.source.pushItems());assertEquals(5,f.pies());assertEquals(7,f.damage());
    }
    @Test void identicalCellInReplacementChunkIsNotTheCapturedNativeReadySource() throws Exception {
        Fixture f=new Fixture(false);f.ready();var replacement=mock(cn.nukkit.level.format.generic.BaseFullChunk.class);var provider=f.chunk.getProvider();when(replacement.getProvider()).thenReturn(provider);
        empty(event->doReturn(replacement).when(f.level).getChunkIfLoaded(0,0));assertFalse(f.destination.pullItems());assertEquals(8,f.damage());assertEquals(0,f.meals());
    }
    private final class Fixture {
        final Level level=mock(Level.class);final FullChunk chunk;final Chunk actual;
        final AtomicReference<BlockComposter> world=new AtomicReference<>();final BlockEntityHopper source,destination;final byte[] opaque;
        Fixture(boolean nativeWorld) throws Exception {
            LevelProvider provider=mock(LevelProvider.class);when(provider.getLevel()).thenReturn(level);when(provider.getMaximumLayer()).thenReturn(1);when(provider.getMinBlockY()).thenReturn(0);
            when(level.getServer()).thenReturn(server);when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
            if(nativeWorld){
                actual=new Chunk(provider);ChunkSection section=new ChunkSection(3);section.setBlock(0,15,0,Block.COMPOSTER,7);actual.setSection(3,section);chunk=actual;
                when(level.requireProvider()).thenReturn(provider);when(level.isYInRange(anyInt())).thenReturn(true);doReturn(actual).when(level).getChunk(0,0,true);
                when(level.getChunkLoaders(anyInt(),anyInt())).thenReturn(new ChunkLoader[0]);
                when(level.getNearbyEntities(any(cn.nukkit.math.AxisAlignedBB.class))).thenReturn(new cn.nukkit.entity.Entity[0]);
                when(level.getBlock(anyInt(),anyInt(),anyInt(),eq(1))).thenReturn(Block.get(Block.AIR));
                field(Level.class,level,"server",server);field(Level.class,level,"callbackBlockSet",new Int2ObjectOpenHashMap<BiConsumer<Block,Block>>());
                doCallRealMethod().when(level).setBlock(any(Vector3.class),any(Block.class),anyBoolean(),anyBoolean());
                doCallRealMethod().when(level).setBlock(anyInt(),anyInt(),anyInt(),anyInt(),any(Block.class),anyBoolean(),anyBoolean());
                doCallRealMethod().when(level).setBlock(any(Vector3.class),eq(0),any(Block.class),anyBoolean(),anyBoolean());
                doCallRealMethod().when(level).setBlock(any(Vector3.class),any(Block.class),anyBoolean(),anyBoolean(),any(Runnable.class));
            }else{
                actual=null;chunk=mock(cn.nukkit.level.format.generic.BaseFullChunk.class);when(chunk.getProvider()).thenReturn(provider);world.set(composter(7));
                doAnswer(call->{world.set((BlockComposter)((BlockComposter)call.getArgument(1)).clone());return true;}).when(level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true));
                doAnswer(call->{boolean accepted=level.setBlock(call.getArgument(0),call.getArgument(1),true,true);if(accepted)((Runnable)call.getArgument(4)).run();return accepted;}).when(level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true),any(Runnable.class));
            }
            doReturn(chunk).when(level).getChunkIfLoaded(0,0);
            when(level.isYInRange(anyInt())).thenReturn(true);
            doCallRealMethod().when(level).getBlockEntityIfLoaded(any(Vector3.class));
            doCallRealMethod().when(level).getBlockEntityIfLoaded(nullable(FullChunk.class),any(Vector3.class));
            if (actual == null) {
            Map<Long,BlockEntity> canonicalActors = new java.util.HashMap<>();
            when(chunk.getBlockEntities()).thenReturn(canonicalActors);
            doAnswer(call->{BlockEntity tile=call.getArgument(0);canonicalActors.put(tile.getId(),tile);return null;}).when(chunk).addBlockEntity(any(BlockEntity.class));
            doAnswer(call->{BlockEntity tile=call.getArgument(0);canonicalActors.remove(tile.getId(),tile);return null;}).when(chunk).removeBlockEntity(any(BlockEntity.class));
            when(chunk.getTile(anyInt(),anyInt(),anyInt())).thenAnswer(call->canonicalActors.values().stream().filter(tile->tile.getFloorX()==(int)call.getArgument(0)&&tile.getFloorY()==(int)call.getArgument(1)&&tile.getFloorZ()==(int)call.getArgument(2)).findFirst().orElse(null));
            }
            source=new BlockEntityHopper(chunk,tag(BlockEntity.HOPPER,64));destination=new BlockEntityHopper(chunk,tag(BlockEntity.HOPPER,62));
            when(level.getBlockDataAt(0,64,0)).thenReturn(0);
            when(level.getBlock(any(Vector3.class))).thenAnswer(call->{Vector3 at=call.getArgument(0);return at.y==63?composter(damage()):Block.get(Block.HOPPER_BLOCK);});
            when(level.getBlock(any(Vector3.class),eq(1))).thenReturn(Block.get(Block.AIR));
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->{Vector3 at=call.getArgument(0);return at.y==64?source:at.y==62?destination:null;});
            Item pies=Item.get(Item.PUMPKIN_PIE,0,5);pies.setNamedTag(new CompoundTag().putString("opaque","native-atomic-composter").putByteArray("bytes",new byte[]{-1,0,49}));opaque=pies.getCompoundTag().clone();assertTrue(source.getInventory().setItem(0,pies));
        }
        BlockComposter composter(int damage){BlockComposter b=new BlockComposter(damage);b.level=level;b.x=0;b.y=63;b.z=0;return b;}
        void ready(){if(actual==null)world.set(composter(8));else actual.getAndSetBlock(0,63,0,composter(8));}
        int damage(){return actual==null?world.get().getDamage():actual.getBlockData(0,63,0);}
        int pies(){return source.getInventory().getContents().values().stream().filter(i->i.getId()==Item.PUMPKIN_PIE).mapToInt(Item::getCount).sum();}
        int meals(){return destination.getInventory().getContents().values().stream().filter(i->i.getId()==Item.DYE&&i.getDamage()==15).mapToInt(Item::getCount).sum();}
        void sourceExact(){for(Item i:source.getInventory().getContents().values())if(i.getId()==Item.PUMPKIN_PIE)assertArrayEquals(opaque,i.getCompoundTag());}
        @SuppressWarnings("unchecked") Map<Integer,Item> raw(BaseInventory inv)throws Exception{Field slots=BaseInventory.class.getDeclaredField("slots");slots.setAccessible(true);return(Map<Integer,Item>)slots.get(inv);}
        void hidden(BaseInventory inv)throws Exception{Item i=Item.get(Item.EMERALD,0,3);i.setNamedTag(new CompoundTag().putString("opaque","hidden"));raw(inv).put(-7,i.clone());raw(inv).put(29,i.clone());}
        Map<Integer,Item> captureRaw(BaseInventory inventory)throws Exception{Map<Integer,Item> result=new java.util.HashMap<>();raw(inventory).forEach((slot,item)->result.put(slot,item.clone()));return result;}
        void rawEquals(Map<Integer,Item> before,BaseInventory inventory)throws Exception{Map<Integer,Item> after=raw(inventory);assertEquals(before.keySet(),after.keySet());before.forEach((slot,item)->assertTrue(item.equalsExact(after.get(slot))));}
    }
    private static CompoundTag tag(String type,int y){return new CompoundTag().putString("id",type).putInt("x",0).putInt("y",y).putInt("z",0).putList(new ListTag<CompoundTag>("Items"));}
    private static void field(Class<?> owner,Object target,String name,Object value)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
