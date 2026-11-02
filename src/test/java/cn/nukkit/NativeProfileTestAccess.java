package cn.nukkit;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.ManualPlayerDataQueue;
import cn.nukkit.utils.PlayerDataSaveQueue;
import cn.nukkit.utils.PlayerDataSerializer;
import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Package bridge for existing physical-custody fixtures; does not add production APIs for mocks. */
public final class NativeProfileTestAccess {
    private NativeProfileTestAccess() { }
    public static PlayerDataSaveQueue storage(Server server, PlayerDataSerializer serializer, PluginManager plugins, Executor writes) throws Exception {
        PlayerDataSaveQueue queue = ManualPlayerDataQueue.create(writes, Runnable::run, 32);
        field(server, "playerDataLocks", Server.createPlayerDataLocks());
        field(server, "playerDataSaves", queue); field(server, "playerDataSerializer", serializer); field(server, "pluginManager", plugins);
        when(server.shouldSavePlayerData()).thenReturn(true);
        doCallRealMethod().when(server).isProfileOwnerThread(); doCallRealMethod().when(server).profileQueue();
        doCallRealMethod().when(server).preparePlayerData(anyString(), any(CompoundTag.class), anyBoolean(), any(BooleanSupplier.class));
        doCallRealMethod().when(server).admitPreparedPlayerData(any(Server.PreparedPlayerData.class));
        doCallRealMethod().when(server).awaitProfileIO(any(), anyBoolean());
        return queue;
    }
    private static void field(Server server, String name, Object value) throws Exception {
        Field field = Server.class.getDeclaredField(name); field.setAccessible(true); field.set(server, value);
    }
}
