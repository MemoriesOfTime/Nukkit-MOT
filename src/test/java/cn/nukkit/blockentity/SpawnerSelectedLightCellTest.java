package cn.nukkit.blockentity;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockAir;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.entity.passive.EntitySheep;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import java.util.Map;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class SpawnerSelectedLightCellTest {
 @BeforeAll static void boot(){MockServer.init();}
 @ParameterizedTest @CsvSource({"0,15,false","15,0,true","15,3,true","0,4,false","3,3,true"})
 void keepsThresholdThreeButReadsSelectedSpawnCell(int source,int target,boolean allowed){
  Fixture f=new Fixture(source,target);EntityZombie mob=mock(EntityZombie.class);f.run(mob,event->{});
  verify(mob,allowed?times(1):never()).spawnToAll();verify(mob,allowed?never():times(1)).close();
 }
 @Test void eventRelocatedNegativeFractionalCellUsesFloor(){
  Fixture f=new Fixture(0,0);when(f.level.getBlockLightAt(-1,64,-17)).thenReturn(15);EntityZombie mob=mock(EntityZombie.class);
  f.run(mob,event->{event.getPosition().x=-.25;event.getPosition().z=-16.25;});verify(mob,never()).spawnToAll();verify(mob).close();verify(f.level).getBlockLightAt(-1,64,-17);
 }
 @Test void cancelledSpawnDoesNotCreateOrReadLight(){Fixture f=new Fixture(0,0);EntityZombie mob=mock(EntityZombie.class);f.run(mob,event->event.setCancelled(true));assertEquals(0,f.factoryCalls);verifyNoInteractions(mob);verify(f.level,never()).getBlockLightAt(anyInt(),anyInt(),anyInt());}
 @Test void passiveSpawnStillIgnoresLight(){Fixture f=new Fixture(15,15);EntitySheep sheep=mock(EntitySheep.class);f.run(sheep,event->{});verify(sheep).spawnToAll();verify(sheep,never()).close();verify(f.level,never()).getBlockLightAt(anyInt(),anyInt(),anyInt());}
    private static final class Fixture {
        final Level level = mock(Level.class);
        final PluginManager plugins = mock(PluginManager.class);
        final BlockEntitySpawner spawner;
        int factoryCalls;

        Fixture(int sourceLight, int targetLight) {
            Server server = mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            Player player = mock(Player.class);
            player.y = 64;
            when(player.distanceSquared(any(Vector3.class))).thenCallRealMethod();
            when(level.getEntities()).thenReturn(new Entity[]{player});
            when(level.getChunkEntities(anyInt(), anyInt(), eq(false))).thenReturn(Map.of());
            when(level.getBlock(any(Vector3.class))).thenReturn(new BlockAir());
            when(level.getBlockLightAt(0, 64, 0)).thenReturn(sourceLight);
            when(level.getBlockLightAt(2, 64, 2)).thenReturn(targetLight);
            FullChunk chunk = mock(FullChunk.class);
            LevelProvider provider = mock(LevelProvider.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(provider.getLevel()).thenReturn(level);
            spawner = new BlockEntitySpawner(chunk, new CompoundTag()
                    .putInt("x", 0).putInt("y", 64).putInt("z", 0).putInt("EntityId", 32)
                    .putShort("SpawnRange", 4).putShort("MinSpawnDelay", 0).putShort("MaxSpawnDelay", 0)
                    .putShort("MinimumSpawnerCount", 1).putShort("MaximumSpawnerCount", 1));
        }

        void run(Entity mob, Consumer<CreatureSpawnEvent> policy) {
            doAnswer(call -> {
                if (call.getArgument(0) instanceof CreatureSpawnEvent event) policy.accept(event);
                return null;
            }).when(plugins).callEvent(any());
            try (MockedStatic<Utils> random = mockStatic(Utils.class);
                 MockedStatic<Entity> factory = mockStatic(Entity.class)) {
                random.when(() -> Utils.rand(0, 0)).thenReturn(0);
                random.when(() -> Utils.rand(-4, 4)).thenReturn(2);
                factory.when(() -> Entity.createEntity(eq(32), any(Position.class))).thenAnswer(call -> {
                    factoryCalls++;
                    return mob;
                });
                assertTrue(spawner.onUpdate());
            }
        }
    }
}
