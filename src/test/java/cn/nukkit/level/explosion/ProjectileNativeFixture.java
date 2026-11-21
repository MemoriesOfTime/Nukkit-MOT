package cn.nukkit.level.explosion;

import cn.nukkit.Server;
import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.*;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.level.vibration.*;
import cn.nukkit.math.*;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import cn.nukkit.utils.serverconfig.ServerConfig;
import java.lang.reflect.Field;
import java.util.*;

/** Native entity constructors, chunk registration, ray collision and dispatcher; sparse air world. */
final class ProjectileNativeFixture {
    private static final sun.misc.Unsafe ALLOCATOR = allocator();
    static Server server;
    private static Server previousServer;

    static void boot() throws Exception {
        previousServer = Server.getInstance();
        cn.nukkit.MockServer.init();
        server = allocated(Server.class);
        set(Server.class, null, "instance", server);
        set(Server.class, server, "currentThread", Thread.currentThread());
        set(Server.class, server, "dataPath", "/tmp/");
        set(Server.class, server, "baseLang", new cn.nukkit.lang.BaseLang("eng"));
        set(Server.class, server, "enableRawOres", true);
        set(Server.class, server, "playerList", new HashMap<>());
        ServerConfig config = new ServerConfig();
        config.customBlockSettings().autoDownloadVanillaPalette(false);
        set(Server.class, server, "serverConfig", config);
        set(Server.class, server, "pluginManager", new PluginManager(server, null));
    }
    static void restoreServer() throws Exception {
        set(Server.class, null, "instance", previousServer);
    }
    static <T extends MemoryLevel> T level(Class<T> type, int ignoredBlockId) throws Exception {
        T level = allocated(type);
        level.actors = new Long2ObjectNonBlockingMap<>();
        set(Level.class, level, "entities", level.actors);
        set(Level.class, level, "blockEntities", new Long2ObjectNonBlockingMap<>());
        set(Level.class, level, "updateEntities", new Long2ObjectNonBlockingMap<>());
        set(Level.class, level, "chunkPackets", new HashMap<>());
        set(Level.class, level, "callbackChunkPacketSend", new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
        set(Level.class, level, "updateBlockEntities", new java.util.concurrent.ConcurrentLinkedQueue<>());
        LevelDBProvider provider = allocated(LevelDBProvider.class);
        set(LevelDBProvider.class, provider, "level", level);
        set(Level.class, level, "provider", provider);
        level.chunk = new LevelDBChunk(provider, 0, 0);
        var chunks = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BaseFullChunk>();
        chunks.put(Level.chunkHash(0, 0), level.chunk);
        set(LevelDBProvider.class, provider, "chunks", chunks);
        return level;
    }
    private static <T> T allocated(Class<T> type) throws Exception { return type.cast(ALLOCATOR.allocateInstance(type)); }
    private static sun.misc.Unsafe allocator() {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    static class MemoryLevel extends Level {
        LevelDBChunk chunk;
        Long2ObjectNonBlockingMap<Entity> actors;
        public MemoryLevel() { super(null, "", "", null); }
        java.util.List<cn.nukkit.item.Item> allDrops() {
            java.util.List<cn.nukkit.item.Item> items = new java.util.ArrayList<>();
            for (Entity entity : actors.values()) if (!entity.closed && entity instanceof cn.nukkit.entity.item.EntityItem item) items.add(item.getItem().clone());
            return items;
        }
        @Override public Server getServer() { return server; }
        @Override public BaseFullChunk getChunkIfLoaded(int x, int z) { return chunk; }
        @Override public BaseFullChunk getChunk(int x, int z) { return chunk; }
        @Override public BaseFullChunk getChunk(int x, int z, boolean create) { return chunk; }
        @Override public DimensionData getDimensionData() { return DimensionData.LEGACY_DIMENSION; }
        @Override public GameRules getGameRules() { return GameRules.getDefault(); }
        @Override public Map<Integer, Player> getChunkPlayers(int x, int z) { return Collections.emptyMap(); }
        @Override public Map<Long, Player> getPlayers() { return Collections.emptyMap(); }
        @Override public void addEntity(Entity entity) { actors.put(entity.getId(), entity); }
        @Override public void removeEntity(Entity entity) { actors.remove(entity.getId()); }
        @Override public Entity getEntity(long id) { return actors.get(id); }
        @Override public Block getBlock(int x, int y, int z, int layer) { return Block.get(Block.AIR, 0, this, x, y, z, layer); }
        @Override public Block getBlock(int x, int y, int z) { return getBlock(x,y,z,0); }
        @Override public Block getBlock(int x,int y,int z,boolean load) { return getBlock(x,y,z,0); }
        @Override public Block getBlock(int x,int y,int z,int layer,boolean load) { return getBlock(x,y,z,layer); }
        @Override public Block getBlock(Vector3 pos,boolean load) { return getBlock(pos,0); }
        @Override public Block getBlock(Vector3 pos,int layer,boolean load) { return getBlock(pos,layer); }
        @Override public Block getBlock(Vector3 pos) { return getBlock(pos,0); }
        @Override public Block getBlock(Vector3 pos,int layer) { return getBlock(pos.getFloorX(),pos.getFloorY(),pos.getFloorZ(),layer); }
        @Override public Entity[] getNearbyEntities(AxisAlignedBB box,Entity ignored) { return new Entity[0]; }
        @Override public void addParticle(Particle particle) {}
        @Override public void addSound(Vector3 pos,Sound sound) {}
        @Override public void addLevelSoundEvent(Vector3 pos,int event) {}
        @Override public void addLevelEvent(Vector3 pos,int event) {}
        @Override public VibrationManager getVibrationManager() { return new SimpleVibrationManager(this); }
    }
}
