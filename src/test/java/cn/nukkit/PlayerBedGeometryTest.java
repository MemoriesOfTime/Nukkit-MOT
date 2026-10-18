package cn.nukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.player.PlayerBedEnterEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Native sleep, wake, teleport, dimensions and solid-eye test; only world/network I/O fake. */
class PlayerBedGeometryTest {
    private static Server nativeServer;
    private static Server previousServer;

    @BeforeAll static void initializeNativeSdk() throws Exception {
        nativeServer=mock(Server.class);
        var instance=Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousServer=(Server)instance.get(null);
        instance.set(null,nativeServer);
        defaults(nativeServer);
        Block.init();
        cn.nukkit.item.enchantment.Enchantment.init();
        var paletteInitialized=cn.nukkit.level.GlobalBlockPalette.class.getDeclaredField("initialized");
        paletteInitialized.setAccessible(true);
        if (!paletteInitialized.getBoolean(null)) cn.nukkit.level.GlobalBlockPalette.init();
        var runtimeItemsInitialized=cn.nukkit.item.RuntimeItems.class.getDeclaredField("initialized");
        runtimeItemsInitialized.setAccessible(true);
        if (!runtimeItemsInitialized.getBoolean(null)) cn.nukkit.item.RuntimeItems.init();
        Item.init();
        cn.nukkit.entity.Attribute.init();
        if (cn.nukkit.block.custom.CustomBlockManager.get()==null)
            cn.nukkit.block.custom.CustomBlockManager.init(nativeServer);
    }

    @org.junit.jupiter.api.AfterAll static void restoreServerBoundary() throws Exception {
        var instance=Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null,previousServer);
    }

    private static void defaults(Server server) {
        server.maxPendingChunkWrites=128;
        when(server.getPluginManager()).thenReturn(mock(cn.nukkit.plugin.PluginManager.class));
        when(server.getLogger()).thenReturn(mock(cn.nukkit.utils.MainLogger.class));
        when(server.getGamemode()).thenReturn(0);
        when(server.getDifficulty()).thenReturn(2);
        when(server.isPrimaryThread()).thenReturn(true);
        when(server.getViewDistance()).thenReturn(1);
        when(server.getDataPath()).thenReturn(System.getProperty("java.io.tmpdir"));
        when(server.getOnlinePlayers()).thenReturn(new HashMap<>());
        var config=new cn.nukkit.utils.serverconfig.ServerConfig();
        config.customBlockSettings().autoDownloadVanillaPalette(false);
        when(server.getServerConfig()).thenReturn(config);
    }
    @ParameterizedTest @ValueSource(ints={589,786,844,975,2168,2169,2193})
    void highRoofControlRemainsClearBeforeSleepDuringSleepAndAfterWake(int protocol) {
        Fixture f = new Fixture(68,protocol);
        assertFalse(f.player.isInsideOfSolid());
        assertTrue(f.player.sleepOn(f.bed));
        f.log("sleep-high");
        assertTrue(f.player.isSleeping());
        assertEquals(65.5, f.player.y);
        assertFalse(f.player.isInsideOfSolid());
        f.player.stopSleep();
        f.log("wake-high");
        assertFalse(f.player.isSleeping());
        assertFalse(f.player.isInsideOfSolid());
    }

    @ParameterizedTest @ValueSource(ints={589,786,844,975,2168,2169,2193})
    void legalLowRoofSleepMustNotPutStandingEyesInRoof(int protocol) {
        Fixture f = new Fixture(67,protocol);
        assertFalse(f.player.isInsideOfSolid(), "before sleeping, standing feet65 eyes66.62 are clear");
        assertTrue(f.level.getBlock(f.bed).onActivate(Item.get(Item.AIR),f.player));
        f.log("sleep-low");
        assertTrue(f.player.isSleeping());
        assertFalse(f.player.isInsideOfSolid(), "native sleeping pose must not suffocate under the roof");
    }

    @ParameterizedTest @ValueSource(ints={589,786,844,975,2168,2169,2193})
    void wakingMustFindStandingSpaceBesideLowRoofBed(int protocol) {
        Fixture f = new Fixture(67,protocol);
        assertTrue(f.player.sleepOn(f.bed));
        f.player.stopSleep();
        f.log("wake-low");
        assertFalse(f.player.isSleeping());
        assertFalse(f.player.isInsideOfSolid(), "wake has clear adjacent ground at8.5,65,10.5");
    }

    @ParameterizedTest @ValueSource(ints={589,786,844,975,2168,2169,2193})
    void cancelledBedEntryKeepsPoseAndCoordinates(int protocol) {
        Fixture f = new Fixture(67,protocol);
        var manager=f.server.getPluginManager();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PlayerBedEnterEvent event) event.setCancelled(true);
            return null;
        }).when(manager).callEvent(any());
        assertFalse(f.player.sleepOn(f.bed));
        assertFalse(f.player.isSleeping());
        assertEquals(65, f.player.y);
        assertFalse(f.player.isInsideOfSolid());
        f.log("bed-cancelled");
    }

    @ParameterizedTest @ValueSource(ints={589,786,844,975,2168,2169,2193})
    void cancelledPluginTeleportStillReturnsFalseWithoutMoving(int protocol) {
        Fixture f = new Fixture(67,protocol);
        var manager=f.server.getPluginManager();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PlayerTeleportEvent event) event.setCancelled(true);
            return null;
        }).when(manager).callEvent(any());
        assertFalse(f.player.teleport(new Location(8.5,65.5,8.5,f.level),
                PlayerTeleportEvent.TeleportCause.PLUGIN));
        assertEquals(65, f.player.y);
        assertFalse(f.player.isInsideOfSolid());
        f.log("teleport-cancelled");
    }

    @ParameterizedTest @ValueSource(ints={589,786,844,975,2168,2169,2193})
    void lowRoofSleepMustNotReceiveNativeSuffocationDamage(int protocol) {
        Fixture f = new Fixture(67,protocol);
        var manager=f.server.getPluginManager();
        java.util.List<EntityDamageEvent> damage=new java.util.ArrayList<>();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityDamageEvent event
                    && event.getCause()==EntityDamageEvent.DamageCause.SUFFOCATION) damage.add(event);
            return null;
        }).when(manager).callEvent(any());
        assertTrue(f.player.sleepOn(f.bed));
        f.player.entityBaseTick(1);
        f.log("damage-low");
        System.out.println("NATIVE_BED suffocation_events="+damage.size());
        assertTrue(damage.isEmpty(),"native sleeping tick must not damage the player under legal roof");
        assertEquals(20,f.player.getHealth());
        assertTrue(f.player.isSleeping());
    }

    @ParameterizedTest @ValueSource(ints={786,2193})
    void realSaveNbtKeepsSleepingFeetCoordinateWithoutInventingEyeOffset(int protocol) {
        Fixture f = new Fixture(67,protocol);
        assertTrue(f.player.sleepOn(f.bed));
        f.player.saveNBT();
        double savedY=f.player.namedTag.getList("Pos",cn.nukkit.nbt.tag.DoubleTag.class).get(1).data;
        System.out.println("NATIVE_SAVE protocol="+protocol+" pose=sleep feet_y="+f.player.y
                +" saved_y="+savedY+" eye_height="+f.player.getEyeHeight());
        assertEquals(f.player.y,savedY,0,"native profile stores feet, not eyes");
    }

    @ParameterizedTest @ValueSource(ints={786,2193})
    void realSaveNbtKeepsOrdinarySneakingAndSwimmingFeet(int protocol) {
        Fixture f = new Fixture(68,protocol);
        assertTrue(f.player.setPosition(new Vector3(8.5,65.25,8.5)));
        for (int pose=0;pose<3;pose++) {
            f.player.setSneaking(pose==1);
            f.player.setSwimming(pose==2);
            f.player.saveNBT();
            double savedY=f.player.namedTag.getList("Pos",cn.nukkit.nbt.tag.DoubleTag.class).get(1).data;
            System.out.println("NATIVE_SAVE protocol="+protocol+" pose="+pose+" feet_y="+f.player.y
                    +" saved_y="+savedY+" eye_height="+f.player.getEyeHeight());
            assertEquals(f.player.y,savedY,0);
        }
    }

    @Test void raisedAdjacentFloorRemainsLegalWakeTarget() {
        Fixture f=new Fixture(67);
        for (int x=6;x<=10;x++) for (int z=6;z<=10;z++) {
            if (x==8 && (z==8 || z==7)) continue;
            f.blocks.put(Fixture.key(x,65,z),Block.STONE);
        }
        assertTrue(f.player.sleepOn(f.bed));
        f.player.stopSleep();
        f.log("raised-neighbor");
        assertFalse(f.player.isInsideOfSolid());
        assertEquals(66.00001,f.player.y,0.000001);
    }

    @Test void failedNativeBedTransferRollsBackPose() {
        Fixture f=new Fixture(67);
        // Bed centering adds 0.5, so the real native coordinate guard rejects the transfer.
        // This exercises false-return rollback without a provider-specific async-load fixture.
        assertFalse(f.player.sleepOn(new Vector3(Entity.ENTITY_COORDINATES_MAX_VALUE,65,8)));
        assertFalse(f.player.isSleeping());
        assertEquals(1.8,f.player.getHeight(),0.00001);
        assertEquals(8.5,f.player.x);
        assertEquals(65,f.player.y);
        assertEquals(8.5,f.player.z);
    }

    @Test void failedLeaveBlockReadStillCompletesWake() {
        Fixture f=new Fixture(67);
        assertTrue(f.player.sleepOn(f.bed));
        doThrow(new IllegalStateException("controlled cached-bed read failure"))
                .when(f.level).getBlock(any(Vector3.class),eq(false));
        assertThrows(IllegalStateException.class,f.player::stopSleep);
        assertFalse(f.player.isSleeping());
        assertEquals(1.8,f.player.getHeight(),0.00001);
        assertEquals(0,f.level.sleepTicks);
    }

    @Test void cancelledWakeTeleportCannotBeRetriedOrGrantDamageImmunity() {
        Fixture f=new Fixture(67);
        assertTrue(f.player.sleepOn(f.bed));
        java.util.concurrent.atomic.AtomicInteger events=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PlayerTeleportEvent event) {
                events.incrementAndGet(); event.setCancelled(true);
            }
            return null;
        }).when(f.manager).callEvent(any());
        clearInvocations(f.level);
        f.player.stopSleep();
        assertEquals(1,events.get());
        assertFalse(f.player.isSleeping());
        assertEquals(65.5,f.player.y);
        assertTrue(f.player.isInsideOfSolid(),"cancelled transfer leaves position under plugin authority");
        f.player.entityBaseTick(1);
        assertEquals(19,f.player.getHealth(),"wake must not mask suffocation damage or retain sleep pose");
        verify(f.level,never()).getChunk(anyInt(),anyInt(),eq(true));
    }

    @Test void unsafeListenerTargetMustBeRefusedAfterNativeYNormalization() {
        Fixture f=new Fixture(67);
        assertTrue(f.player.sleepOn(f.bed));
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PlayerTeleportEvent event)
                event.setTo(new Location(8.5,66,8.5,f.level));
            return null;
        }).when(f.manager).callEvent(any());
        f.player.stopSleep();
        assertFalse(f.player.isSleeping());
        assertEquals(65.5,f.player.y,"unsafe override is not teleported into roof");
    }

    @Test void listenerNativeTeleportMustNotBeOverwrittenByStaleWakeTarget() {
        Fixture f=new Fixture(67);
        assertTrue(f.player.sleepOn(f.bed));
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PlayerTeleportEvent)
                assertTrue(f.player.teleport(new Location(12.5,70,12.5,f.level),null));
            return null;
        }).when(f.manager).callEvent(any());
        f.player.stopSleep();
        assertFalse(f.player.isSleeping());
        assertEquals(12.5,f.player.x);assertEquals(70.00001,f.player.y,0.000001);assertEquals(12.5,f.player.z);
    }

    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void unknownOrUnreadyChunksCannotBeLoadedByWake(int refusal) {
        Fixture f=new Fixture(67);
        assertTrue(f.player.sleepOn(f.bed));
        if (refusal==0) when(f.level.getChunkIfLoaded(anyInt(),anyInt())).thenReturn(null);
        if (refusal==1) when(f.chunk.isInitialized()).thenReturn(false);
        if (refusal==2) when(f.chunk.isGenerated()).thenReturn(false);
        if (refusal==3) when(f.chunk.isPopulated()).thenReturn(false);
        clearInvocations(f.level);
        f.player.stopSleep();
        assertFalse(f.player.isSleeping());
        assertEquals(65.5,f.player.y);
        verify(f.level,never()).getChunk(anyInt(),anyInt(),anyBoolean());
        verify(f.level,never()).getBlock(anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean());
    }

    @Test void noClearExitMustRemainBoundedAndMustStillWake() {
        Fixture f=new Fixture(67);
        assertTrue(f.player.sleepOn(f.bed));
        for (int x=6;x<=10;x++) for (int z=6;z<=10;z++) f.blocks.put(Fixture.key(x,66,z),Block.STONE);
        clearInvocations(f.level);
        f.player.stopSleep();
        assertFalse(f.player.isSleeping());
        assertEquals(65.5,f.player.y);
        long reads=mockingDetails(f.level).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("getBlock")).count();
        assertTrue(reads<500,"small bounded neighborhood, actual block reads="+reads);
        verify(f.level,never()).getChunk(anyInt(),anyInt(),anyBoolean());
    }

    @ParameterizedTest @ValueSource(ints={589,786,844,975,2168,2169,2193})
    void nativeWireFeetOffsetStaysUnchangedAcrossSleepAndWake(int protocol) {
        Fixture f=new Fixture(67,protocol);
        float offset=f.player.wireOffset();
        assertTrue(f.player.sleepOn(f.bed));
        assertEquals(offset,f.player.wireOffset(),0);
        assertEquals(1.62,offset,0.00001);
        f.player.stopSleep();
        assertEquals(offset,f.player.wireOffset(),0);
    }

    private static final class Fixture {
        final Server server;
        final cn.nukkit.plugin.PluginManager manager;
        final Level level;
        final NativePlayer player;
        final BaseFullChunk chunk;
        final Vector3 bed = new Vector3(8,65,8);
        final Map<String,Integer> blocks = new HashMap<>();

        Fixture(int roofY) { this(roofY,cn.nukkit.network.protocol.ProtocolInfo.v1_26_50); }

        Fixture(int roofY,int protocol) {
            server = nativeServer;
            reset(server);
            defaults(server);
            manager=server.getPluginManager();
            level = mock(Level.class);
            when(server.getDefaultLevel()).thenReturn(level);
            when(level.getVibrationManager()).thenReturn(mock(cn.nukkit.level.vibration.VibrationManager.class));
            try {
                var field=Level.class.getDeclaredField("updateEntities");
                field.setAccessible(true);
                field.set(level,new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<Entity>());
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            server.bedSpawnpoints = false;
            when(server.getViewDistance()).thenReturn(1);
            when(level.getServer()).thenReturn(server);
            LevelProvider provider = mock(LevelProvider.class);
            when(level.getProvider()).thenReturn(provider);
            when(provider.getLevel()).thenReturn(level);
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getTime()).thenReturn(Level.TIME_NIGHT);
            when(level.getGameRules()).thenReturn(cn.nukkit.level.GameRules.getDefault());
            when(level.getNearbyEntities(any(AxisAlignedBB.class), any(Entity.class)))
                    .thenReturn(new Entity[0]);
            when(level.getNearbyEntities(any(AxisAlignedBB.class))).thenReturn(new Entity[0]);
            // Native BlockBed checks nearby hostile collisions before calling Player.sleepOn.
            when(level.getCollidingEntities(any(AxisAlignedBB.class))).thenReturn(new Entity[0]);
            when(level.getNearbyEntities(any(AxisAlignedBB.class),any(Entity.class),anyBoolean(),anyBoolean()))
                    .thenReturn(new Entity[0]);
            chunk = mock(BaseFullChunk.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(chunk.isInitialized()).thenReturn(true);
            when(chunk.isGenerated()).thenReturn(true);
            when(chunk.isPopulated()).thenReturn(true);
            when(level.getChunk(anyInt(),anyInt(),anyBoolean())).thenReturn(chunk);
            when(level.getChunk(anyInt(),anyInt())).thenReturn(chunk);
            when(level.getChunkIfLoaded(anyInt(),anyInt())).thenReturn(chunk);
            when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(new HashMap<>());
            for (int x=7;x<=10;x++) for (int z=7;z<=11;z++) blocks.put(key(x,64,z),Block.STONE);
            blocks.put(key(8,65,8),Block.BED_BLOCK);
            blocks.put(key(8,65,7),Block.BED_BLOCK);
            blocks.put(key(8,roofY,8),Block.STONE);
            blocks.put(key(8,roofY,7),Block.STONE);
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> block(call.getArgument(0)));
            when(level.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(call ->
                    block(new Vector3(call.getArgument(0,Integer.class),call.getArgument(1,Integer.class),
                            call.getArgument(2,Integer.class))));
            when(level.getBlock(any(Vector3.class),eq(false))).thenAnswer(call -> block(call.getArgument(0)));
            when(level.getBlock(anyInt(),anyInt(),anyInt(),eq(false))).thenAnswer(call ->
                    block(new Vector3(call.getArgument(0,Integer.class),call.getArgument(1,Integer.class),
                            call.getArgument(2,Integer.class))));
            when(level.getBlock(anyInt(),anyInt(),anyInt(),anyInt(),eq(false))).thenAnswer(call -> {
                Vector3 position=new Vector3(call.getArgument(0,Integer.class),call.getArgument(1,Integer.class),
                        call.getArgument(2,Integer.class));
                return call.getArgument(3,Integer.class)==0 ? block(position)
                        : Block.get(Block.AIR,0,level,position.getFloorX(),position.getFloorY(),position.getFloorZ());
            });
            SourceInterface source = mock(SourceInterface.class);
            when(source.getSession(any(InetSocketAddress.class)))
                    .thenReturn(mock(NetworkPlayerSession.class));
            player = new NativePlayer(source,protocol);
            player.initialize(chunk);
            player.loggedIn = true;
            player.spawned = true;
            for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++)
                player.usedChunks.put(Level.chunkHash(x,z),Boolean.TRUE);
            assertTrue(player.setPosition(new Vector3(8.5,65,8.5)));
            log("before");
        }

        private Block block(Vector3 p) {
            int id=blocks.getOrDefault(key(p.getFloorX(),p.getFloorY(),p.getFloorZ()),Block.AIR);
            return Block.get(id,id==Block.BED_BLOCK && p.getFloorZ()==8?8:0,
                    level,p.getFloorX(),p.getFloorY(),p.getFloorZ());
        }
        private static String key(int x,int y,int z) { return x+":"+y+":"+z; }
        void log(String phase) {
            AxisAlignedBB box=player.getBoundingBox();
            System.out.println("NATIVE_BED phase="+phase+" feet="+player.x+","+player.y+","+player.z
                    +" eye="+(player.y+player.getEyeHeight())+" height="+player.getHeight()
                    +" sleeping="+player.isSleeping()+" inside="+player.isInsideOfSolid()
                    +" aabb="+box.getMinX()+","+box.getMinY()+","+box.getMinZ()+":"
                    +box.getMaxX()+","+box.getMaxY()+","+box.getMaxZ()+" health="+player.getHealth());
        }
    }

    private static final class NativePlayer extends Player {
        NativePlayer(SourceInterface source,int selectedProtocol) {
            super(source,1L,new InetSocketAddress("127.0.0.1",19199));
            protocol = selectedProtocol;
            gameVersion = GameVersion.byProtocol(selectedProtocol,false);
        }
        float wireOffset() { return getBaseOffset(); }
        void initialize(BaseFullChunk chunk) { init(chunk,Entity.getDefaultNBT(new Vector3(8.5,65,8.5))); }
        // Outgoing packet boundary only. Responsible teleport and dimension methods remain native.
        @Override public boolean dataPacket(DataPacket packet) { return true; }
    }
}
