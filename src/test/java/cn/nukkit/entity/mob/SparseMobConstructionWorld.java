package cn.nukkit.entity.mob;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import java.util.Map;
import java.util.List;
import java.lang.reflect.Field;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** Sparse native construction world: storage and environmental reads only. */
final class SparseMobConstructionWorld {
 final Server server;final Level level=mock(Level.class);final BaseFullChunk chunk=mock(BaseFullChunk.class);
 SparseMobConstructionWorld() throws Exception {
  MockServer.reset();server=MockServer.get();when(server.getDifficulty()).thenReturn(2);when(server.getTick()).thenReturn(1000);
  when(level.getServer()).thenReturn(server);when(level.getFolderName()).thenReturn("mob-test");when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
  when(level.getGameRules()).thenReturn(GameRules.getDefault());when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
  when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));when(level.getPlayers()).thenReturn(Map.of());when(level.getEntities()).thenReturn(new Entity[0]);
  LevelProvider provider=mock(LevelProvider.class);when(chunk.getProvider()).thenReturn(provider);when(provider.getLevel()).thenReturn(level);
  when(level.getChunk(anyInt(),anyInt(),anyBoolean())).thenReturn(chunk);when(level.getChunkIfLoaded(anyInt(),anyInt())).thenReturn(chunk);
  when(level.getChunkEntities(anyInt(),anyInt(),eq(false))).thenReturn(Map.of());
  when(level.getBlock(any(Vector3.class))).thenAnswer(call->air((Vector3)call.getArgument(0)));
  when(level.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(call->air(new Vector3(call.getArgument(0),call.getArgument(1),call.getArgument(2))));
  Field updates=Level.class.getDeclaredField("updateEntities");updates.setAccessible(true);updates.set(level,new Long2ObjectNonBlockingMap<>());
 }
 Block air(Vector3 at){Block b=Block.get(Block.AIR);b.x=at.x;b.y=at.y;b.z=at.z;b.level=level;return b;}
}
