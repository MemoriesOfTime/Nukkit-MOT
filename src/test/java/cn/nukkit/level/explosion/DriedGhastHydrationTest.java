package cn.nukkit.level.explosion;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDriedGhast;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityHappyGhast;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.parallel.Isolated("block and entity registries")
class DriedGhastHydrationTest {

    @Test void canBeWaterloggedByPlacementAndFlow() {
        assertEquals(Block.WaterloggingType.FLOW_INTO_BLOCK, new BlockDriedGhast().getWaterloggingType());
    }

    @ParameterizedTest @ValueSource(ints={0,1,2})
    void eachWetStepTakesFiveMinutesAndKeepsFacing(int stage) {
        try (Fixture f = new Fixture(stage, true)) {
            f.tick();
            assertEquals(stage+1, ((BlockDriedGhast)f.cell).getRehydrationLevel());
            assertEquals(2, f.cell.getDamage() & 3);
            assertEquals(List.of(6000), f.delays);
            assertEquals(Block.WATER, f.water.getId());
        }
    }

    @ParameterizedTest @ValueSource(ints={1,2,3})
    void dryingLosesOneStagePerFiveMinutes(int stage) {
        try (Fixture f = new Fixture(stage, false)) {
            f.tick();
            assertEquals(stage-1, ((BlockDriedGhast)f.cell).getRehydrationLevel());
            assertEquals(stage==1 ? List.of() : List.of(6000), f.delays);
        }
    }

    @Test void completelyDryBlockDoesNotRunAnIdleTimer() {
        try (Fixture f = new Fixture(0,false)) { f.tick(); assertTrue(f.delays.isEmpty()); }
    }

    @Test void normalUpdatesStartButDoNotAdvanceOrDuplicateTimer() {
        try (Fixture f = new Fixture(0,true)) {
            f.cell.onUpdate(Level.BLOCK_UPDATE_NORMAL);
            assertEquals(0, ((BlockDriedGhast)f.cell).getRehydrationLevel());
            assertEquals(List.of(6000),f.delays);
            when(f.level.isUpdateScheduled(any(),any())).thenReturn(true);
            f.cell.onUpdate(Level.BLOCK_UPDATE_NORMAL);
            assertEquals(List.of(6000),f.delays);
        }
    }

    @Test void fourthWetStepCreatesExactlyOneBabyAndRetainsWater() {
        try (Fixture f = new Fixture(3,true)) {
            f.tick();
            assertEquals(Block.AIR,f.cell.getId());
            assertEquals(Block.WATER,f.water.getId());
            verify(f.baby).spawnToAll();
            assertTrue(f.birth.getBoolean("Baby"));
            assertEquals(-24000,f.birth.getShort("Age"));
            assertTrue(f.delays.isEmpty());
            f.source.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            verify(f.baby,times(1)).spawnToAll();
        }
    }

    @ParameterizedTest @ValueSource(strings={"cancel","null","closed","replace","remove_water","reject_write"})
    void refusedBirthNeverConsumesSourceOrPublishesBaby(String refusal) {
        try (Fixture f = new Fixture(3,true)) {
            f.refusal=refusal;
            f.tick();
            if (!refusal.equals("replace")) assertEquals(Block.DRIED_GHAST,f.cell.getId());
            else assertEquals(Block.STONE,f.cell.getId());
            assertEquals(refusal.equals("remove_water")?Block.AIR:Block.WATER,f.water.getId());
            verify(f.baby,never()).spawnToAll();
            if (!refusal.equals("replace")) assertEquals(List.of(6000),f.delays);
        }
    }

    @Test void unloadedChunkAndReplacedBlockDoNotChangeWorld() {
        try (Fixture f = new Fixture(1,true)) {
            when(f.level.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            f.tick(); assertEquals(1,((BlockDriedGhast)f.cell).getRehydrationLevel());
            assertTrue(f.delays.isEmpty());
            when(f.level.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            f.cell=Block.get(Block.STONE); f.tick(); assertEquals(Block.STONE,f.cell.getId());
            assertTrue(f.delays.isEmpty());
        }
    }

    @Test void fourStepsRequireTwentyMinutesOfScheduledLoadedTime() {
        try (Fixture f=new Fixture(0,true)) {
            for(int stage=1;stage<=3;stage++) {
                f.tick(); assertEquals(stage,((BlockDriedGhast)f.cell).getRehydrationLevel());
                verify(f.baby,never()).spawnToAll();
            }
            f.tick(); assertEquals(Block.AIR,f.cell.getId()); verify(f.baby).spawnToAll();
            assertEquals(List.of(6000,6000,6000),f.delays);
        }
    }

    @Test void failedStageWriteRetriesWithoutAdvancing() {
        try(Fixture f=new Fixture(1,true)) {
            f.refusal="reject_write"; f.tick();
            assertEquals(1,((BlockDriedGhast)f.cell).getRehydrationLevel());
            assertEquals(List.of(6000),f.delays);
        }
    }

    @Test void failedFactoryCanRecoverOnNextScheduledAttempt() {
        try(Fixture f=new Fixture(3,true)) {
            f.refusal="null";f.tick(); assertEquals(Block.DRIED_GHAST,f.cell.getId());
            f.refusal="";f.tick(); assertEquals(Block.AIR,f.cell.getId());verify(f.baby).spawnToAll();
        }
    }

    @Test void minedItemAndNewPlacementResetHydration() {
        try(Fixture f=new Fixture(3,true)) {
            assertEquals(0,f.source.toItem().getDamage());
            assertTrue(f.source.place(f.source.toItem(),f.source,Block.get(Block.STONE),cn.nukkit.math.BlockFace.UP,0,0,0,null));
            assertEquals(0,((BlockDriedGhast)f.cell).getRehydrationLevel());
            assertEquals(List.of(6000),f.delays);
        }
    }

    @Test void rejectedPlacementDoesNotScheduleHydration() {
        try(Fixture f=new Fixture(0,true)) {
            f.refusal="reject_write";
            assertFalse(f.source.place(f.source.toItem(),f.source,Block.get(Block.STONE),cn.nukkit.math.BlockFace.UP,0,0,0,null));
            assertTrue(f.delays.isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(ints={-24000,-12000,-1,0})
    void babyAgeSurvivesRealNativeConstructionSaveAndReload(int age) throws Exception {
        FullChunk chunk=testChunk();
        CompoundTag tag=Entity.getDefaultNBT(new Vector3(0.5,64,0.5)).putBoolean("Baby",true).putShort("Age",age);
        EntityHappyGhast baby=new EntityHappyGhast(chunk,tag);
        assertTrue(baby.isBaby());
        assertEquals(age,baby.age);
        assertEquals(0.2375f,baby.getScale());
        assertEquals(0.95,baby.getBoundingBox().getMaxX()-baby.getBoundingBox().getMinX(),0.00001);
        assertEquals(0.95,baby.getBoundingBox().getMaxY()-baby.getBoundingBox().getMinY(),0.00001);
        baby.saveNBT();
        CompoundTag saved=baby.namedTag.clone();
        baby.close();
        EntityHappyGhast loaded=new EntityHappyGhast(chunk,saved);
        assertTrue(loaded.isBaby()); assertEquals(age,loaded.age); assertEquals(0.2375f,loaded.getScale());
        loaded.close();
    }

    @Test void freshBabyGetsTwentyMinutesAndAdultRemainsFullSize() throws Exception {
        FullChunk chunk=testChunk();
        EntityHappyGhast baby=new EntityHappyGhast(chunk,Entity.getDefaultNBT(new Vector3(0.5,64,0.5)));
        baby.setBaby(true); assertEquals(-24000,baby.age); assertEquals(0.2375f,baby.getScale());
        baby.setBaby(false); assertFalse(baby.isBaby()); assertEquals(1f,baby.getScale()); baby.close();
    }

    @ParameterizedTest @ValueSource(ints={589,766,800,818,819,1001,2168,2193,2225})
    void nativeSpawnPacketKeepsReleasedIdentifierOrExistingLegacyFallback(int protocol) {
        var packet=new cn.nukkit.network.protocol.AddEntityPacket();
        packet.protocol=protocol; packet.gameVersion=cn.nukkit.GameVersion.byProtocol(protocol,false);
        packet.type=EntityHappyGhast.NETWORK_ID; packet.encode();
        var wire=new cn.nukkit.utils.BinaryStream(packet.getBuffer());
        wire.getUnsignedVarInt(); wire.getVarLong(); wire.getUnsignedVarLong();
        assertEquals(protocol<818?"minecraft:ghast":"minecraft:happy_ghast",wire.getString());
    }

    @Test void spawnEventSeesRestoredBabyAndListenerChangesSurviveConstruction() throws Exception {
        Server server=mock(Server.class);
        PluginManager plugins=mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        FullChunk chunk=testChunk(server);
        int[] calls={0};
        boolean[] babyAtEvent={false};
        int[] ageAtEvent={0};
        float[] scaleAtEvent={0};
        doAnswer(i->{
            if(i.getArgument(0) instanceof cn.nukkit.event.entity.EntitySpawnEvent event
                    && event.getEntity() instanceof EntityHappyGhast ghast) {
                calls[0]++;
                babyAtEvent[0]=ghast.isBaby();
                ageAtEvent[0]=ghast.age;
                scaleAtEvent[0]=ghast.getScale();
                ghast.setBaby(false);
                ghast.setScale(0.5f);
                ghast.age=-1;
            }
            return null;
        }).when(plugins).callEvent(any());
        CompoundTag tag=Entity.getDefaultNBT(new Vector3(0.5,64,0.5)).putBoolean("Baby",true).putShort("Age",-12000);
        EntityHappyGhast ghast=new EntityHappyGhast(chunk,tag);
        try {
            assertEquals(1,calls[0]);
            assertTrue(babyAtEvent[0]);
            assertEquals(-12000,ageAtEvent[0]);
            assertEquals(0.2375f,scaleAtEvent[0]);
            assertFalse(ghast.isBaby());
            assertEquals(-1,ghast.age);
            assertEquals(0.5f,ghast.getScale());
        } finally {
            ghast.close();
        }
    }

    private static FullChunk testChunk() {
        return testChunk(MockServer.get());
    }

    private static FullChunk testChunk(Server server) {
        Level level=mock(Level.class);
        when(level.getServer()).thenReturn(server);
        // 让 Entity 构造发布路径上的 scheduleUpdate() 走 no-op 分支，避免触碰 mock 缺省为 null 的 updateEntities。
        level.isBeingConverted = true;
        cn.nukkit.level.format.LevelProvider provider=mock(cn.nukkit.level.format.LevelProvider.class);
        when(provider.getLevel()).thenReturn(level);
        FullChunk chunk=mock(FullChunk.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        return chunk;
    }

    private static final class Fixture implements AutoCloseable {
        final Level level=mock(Level.class);
        final Server server=mock(Server.class);
        final PluginManager plugins=mock(PluginManager.class);
        final cn.nukkit.level.format.generic.BaseFullChunk chunk=mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
        final EntityHappyGhast baby=mock(EntityHappyGhast.class);
        final BlockDriedGhast source=new BlockDriedGhast();
        Block cell,water;
        CompoundTag birth;
        String refusal="";
        final List<Integer> delays=new ArrayList<>();
        final MockedStatic<Entity> factory;
        Fixture(int stage,boolean wet) {
            source.level=level; source.x=0; source.y=64; source.z=0;
            source.setDamage(2|(stage<<2)); cell=source.clone();
            water=Block.get(wet?Block.WATER:Block.AIR);
            when(level.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
            when(level.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            when(level.getChunkIfLoaded(anyInt(),anyInt())).thenReturn(chunk);
            when(level.getBlock(any(Vector3.class))).thenAnswer(i->cell.clone());
            when(level.getBlock(any(Vector3.class),eq(1))).thenAnswer(i->water.clone());
            doAnswer(i->{delays.add(i.getArgument(1));return null;}).when(level).scheduleUpdate(any(Block.class),anyInt());
            when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true))).thenAnswer(i->{
                if(refusal.equals("reject_write"))return false;
                cell=((Block)i.getArgument(1)).clone();return true;
            });
            doAnswer(i->{
                if(i.getArgument(0) instanceof CreatureSpawnEvent event) {
                    if(refusal.equals("cancel"))event.setCancelled();
                    if(refusal.equals("replace"))cell=Block.get(Block.STONE);
                    if(refusal.equals("remove_water"))water=Block.get(Block.AIR);
                }
                return null;
            }).when(plugins).callEvent(any());
            when(baby.isAlive()).thenReturn(true);
            factory=mockStatic(Entity.class,CALLS_REAL_METHODS);
            factory.when(()->Entity.createEntity(eq(EntityHappyGhast.NETWORK_ID),eq(chunk),any(CompoundTag.class))).thenAnswer(i->{
                birth=((CompoundTag)i.getArgument(2)).clone(); baby.closed=refusal.equals("closed");
                return refusal.equals("null")?null:baby;
            });
        }
        void tick(){source.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);}
        public void close(){factory.close();}
    }
}
