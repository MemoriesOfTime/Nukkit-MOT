package cn.nukkit.level.explosion;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.*;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.*;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.level.vibration.*;
import cn.nukkit.math.*;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.serverconfig.ServerConfig;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Isolated, deterministic proof of the pinned native Explosion.explodeB payload path.
 * Real core block, tile, inventory and event code; sparse in-memory world and captured output.
 * Affected-block selection is forced and size=1 supplies yield=100. This does not prove
 * explosion ray selection, full server callbacks, network delivery or persistence.
 */
public final class NativeExplosionFixture {
    static final sun.misc.Unsafe ALLOCATOR = allocator();
    static Server server;
    static int found;
    static boolean booted;
    private static boolean active;
    private static Server previousServer;

    static void boot() throws Exception {
        if (active) return;
        previousServer = Server.getInstance();
        active = true;
        if (booted) {
            set(Server.class, null, "instance", server);
            return;
        }
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
        BlockEntity.registerBlockEntity(BlockEntity.LECTERN, BlockEntityLectern.class);
        BlockEntity.registerBlockEntity(BlockEntity.JUKEBOX, BlockEntityJukebox.class);
        BlockEntity.registerBlockEntity(BlockEntity.DECORATED_POT, BlockEntityDecoratedPot.class);
        BlockEntity.registerBlockEntity(BlockEntity.CHEST, BlockEntityChest.class);

        BlockEntity.registerBlockEntity(BlockEntity.SHULKER_BOX, BlockEntityShulkerBox.class);
        BlockEntity.registerBlockEntity(BlockEntity.ITEM_FRAME, BlockEntityItemFrame.class);
        booted = true;
    }

    static void restoreServer() throws Exception {
        if (active) {
            set(Server.class, null, "instance", previousServer);
            active = false;
            previousServer = null;
        }
    }

    static void payload(String label, int blockId, int itemId, int count) throws Exception {
        MemoryLevel level = level(blockId);
        Item payload = Item.get(itemId, 0, count).setNamedTag(new CompoundTag().putString("opaque", label));
        BlockEntity tile;
        if (blockId == Block.LECTERN) {
            tile = allocated(BlockEntityLectern.class);
            tile.namedTag = new CompoundTag().putCompound("book", NBTIO.putItemHelper(payload));
        } else if (blockId == Block.JUKEBOX) {
            tile = allocated(BlockEntityJukebox.class);
            tile.namedTag = new CompoundTag();
            set(BlockEntityJukebox.class, tile, "recordItem", payload);
        } else {
            tile = allocated(BlockEntityDecoratedPot.class);
            ListTag<StringTag> sherds = new ListTag<>("sherds");
            for (int i = 0; i < 4; ++i) sherds.add(new StringTag("", "minecraft:brick"));
            tile.namedTag = new CompoundTag().putList(sherds);
            set(BlockEntityDecoratedPot.class, tile, "item", payload);
        }
        attach(level, tile);
        boolean accepted = explode(level);
        int actual = matching(level.allDrops(), payload);
        List<Item> nativeDrops = new ArrayList<>(level.allDrops());
        level.output.clear();
        if (!tile.closed) tile.onBreak();
        int control = tile.closed ? -1 : matching(level.allDrops(), payload);
        boolean missing = !level.cell.isAir() || actual != count;
        if (missing) ++found;
        System.out.println("NATIVE_PAYLOAD " + label + " accepted=" + accepted + " sourceAir="
                + level.cell.isAir() + " wanted=" + count + " nativePayload=" + actual
                + " onBreakControl=" + control + " missing=" + missing + " nativeDrops=" + nativeDrops);
    }

    static void chestOwnBlock() throws Exception {
        MemoryLevel level = level(Block.CHEST);
        BlockEntityChest tile = allocated(BlockEntityChest.class);
        tile.namedTag = new CompoundTag();
        attach(level, tile);
        ChestInventory inventory = new ChestInventory(tile);
        set(BlockEntitySpawnableContainer.class, tile, "inventory", inventory);
        Item diamonds = Item.get(Item.DIAMOND, 0, 64);
        inventory.slots.put(0, diamonds);
        boolean accepted = explode(level);
        int contents = matching(level.allDrops(), diamonds);
        int own = level.allDrops().stream().filter(it -> it.getId() == Block.CHEST).mapToInt(Item::getCount).sum();
        boolean missing = !level.cell.isAir() || contents != 64 || !inventory.isEmpty() || own != 1;
        if (missing) ++found;
        System.out.println("NATIVE_CHEST accepted=" + accepted + " sourceAir=" + level.cell.isAir()
                + " contents=" + contents + " ownBlock=" + own + " missingOwnBlock=" + missing);
    }

    static void rejectedWorldRemoval() throws Exception {
        MemoryLevel level = level(Block.PLANKS);
        level.rejectRemoval = true;
        boolean accepted = explode(level);
        int published = level.allDrops().stream().filter(it -> it.getId() == Block.PLANKS).mapToInt(Item::getCount).sum();
        boolean duplicated = level.cell.isAir() || published != 0;
        if (duplicated) ++found;
        System.out.println("NATIVE_REJECT accepted=" + accepted + " sourceStillStanding="
                + !level.cell.isAir() + " published=" + published + " duplicated=" + duplicated);
    }

    static int matching(List<Item> items, Item wanted) {
        return items.stream().filter(it -> it.equals(wanted, true, true)).mapToInt(Item::getCount).sum();
    }

    static boolean explode(MemoryLevel level) throws Exception {
        Explosion explosion = new Explosion(new Position(0, 64, 0, level), 1, (Entity) null);
        set(Explosion.class, explosion, "affectedBlocks", new LinkedHashSet<>(List.of(level.cell)));
        return explosion.explodeB();
    }

    static void attach(MemoryLevel level, BlockEntity tile) throws Exception {
        // Unsafe tiles need the constructor identity and registry setup below.
        tile.level = level;
        tile.chunk = new LevelDBChunk(null, 0, 0);
        tile.x = 0; tile.y = 64; tile.z = 0;
        level.tile = tile;
        tile.chunk = level.chunk; register(level, tile);
    }

    static MemoryLevel level(int id) throws Exception {
        MemoryLevel level = allocated(MemoryLevel.class);
        level.output = new ArrayList<>();
        level.actors = new Long2ObjectNonBlockingMap<Entity>();
        set(Level.class, level, "entities", level.actors); set(Level.class, level, "blockEntities", new Long2ObjectNonBlockingMap<BlockEntity>());
        set(Level.class, level, "chunkPackets", new HashMap<Long,java.util.Deque<cn.nukkit.network.protocol.DataPacket>>());
        set(Level.class, level, "callbackChunkPacketSend", new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
        set(Level.class, level, "updateBlockEntities", new java.util.concurrent.ConcurrentLinkedQueue<BlockEntity>());
        level.cells = new HashMap<>();
        level.tiles = new HashMap<>();
        LevelDBProvider provider = allocated(LevelDBProvider.class);
        set(LevelDBProvider.class, provider, "level", level);
        set(Level.class, level, "provider", provider);
        set(Level.class, level, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
        level.chunk = new LevelDBChunk(provider, 0, 0);
        set(BaseFullChunk.class, level.chunk, "entities", new Long2ObjectNonBlockingMap<Entity>());
        var chunks = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<cn.nukkit.level.format.generic.BaseFullChunk>();
        chunks.put(Level.chunkHash(0,0), level.chunk);
        set(LevelDBProvider.class, provider, "chunks", chunks);
        if (level.chunk.getProvider().getClass() != LevelDBProvider.class || level.chunk.getProvider().getLevel() != level) throw new AssertionError("Noncanonical provider");
        level.cell = Block.get(id, 0, level, 0, 64, 0);
        return level;
    }

    static <T> T allocated(Class<T> type) throws Exception { return type.cast(ALLOCATOR.allocateInstance(type)); }
    static sun.misc.Unsafe allocator() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    static void set(Class<?> type, Object object, String field, Object value) throws Exception {
        Field f = type.getDeclaredField(field); f.setAccessible(true); f.set(object, value);
    }

    /** Constructor-equivalent registration for explicitly allocated test tiles. */
    static void register(Level level, BlockEntity tile) throws Exception {
        if (tile.closed || tile.getLevel() != level || tile.chunk == null
                || tile.chunk.getProvider() != level.getProvider()
                || tile.chunk.getProvider().getLevel() != level
                || tile.chunk != level.getChunkIfLoaded(tile.getFloorX() >> 4, tile.getFloorZ() >> 4)
                || tile.chunk.getX() != (tile.getFloorX() >> 4) || tile.chunk.getZ() != (tile.getFloorZ() >> 4)) {
            throw new AssertionError("Noncanonical allocated tile owner/chunk/coordinates");
        }
        if (tile.id <= 0) tile.id = BlockEntity.count++;
        if (tile.scheduledForBlockEntityUpdate == null) {
            set(BlockEntity.class, tile, "scheduledForBlockEntityUpdate", new java.util.concurrent.atomic.AtomicBoolean());
        }
        BlockEntity indexed = level.getBlockEntityById(tile.getId());
        if (indexed != null && indexed != tile) throw new AssertionError("Duplicate allocated tile ID");
        tile.chunk.addBlockEntity(tile);
        level.addBlockEntity(tile);
        if (level.getBlockEntityById(tile.getId()) != tile
                || tile.chunk.getBlockEntities().get(tile.getId()) != tile
                || level.getBlockEntityIfLoaded(tile.chunk, tile) != tile) {
            throw new AssertionError("Allocated tile missing native ID or coordinate index");
        }
    }

    public static final class MemoryLevel extends Level {
        Block cell;
        BlockEntity tile;
        List<Item> output;
        boolean rejectRemoval;
        boolean rejectSpawn;
        int setterMode;
        Map<String, Block> cells;
        Map<String, BlockEntity> tiles;
        static String key(int x, int y, int z, int layer) { return x+","+y+","+z+","+layer; }
        LevelDBChunk chunk;
        Map<Long, Entity> actors;
        List<Item> allDrops() {
            List<Item> all = new ArrayList<>(output);
            for (Entity actor : actors.values()) if (actor instanceof EntityItem item && !actor.closed && !actor.isPublicationDeferred()) all.add(item.getItem().clone());
            return all;
        }
        public MemoryLevel() { super(null, "", "", null); }
        @Override public Server getServer() { return server; }
        @Override public BlockEntity getBlockEntity(Vector3 pos) { return super.getBlockEntityIfLoaded(pos); }
        @Override public BaseFullChunk getChunkIfLoaded(int x, int z) { return chunk; }
        @Override public BaseFullChunk getChunk(int x, int z) { return chunk; }
        @Override public BaseFullChunk getChunk(int x, int z, boolean create) { return chunk; }
        @Override public DimensionData getDimensionData() { return DimensionData.LEGACY_DIMENSION; }
        @Override public GameRules getGameRules() { return GameRules.getDefault(); }
        @Override public Map<Integer,cn.nukkit.Player> getChunkPlayers(int x,int z) { return Collections.emptyMap(); }
        @Override public Map<Long,cn.nukkit.Player> getPlayers() { return Collections.emptyMap(); }
        @Override public void addEntity(Entity entity) { actors.put(entity.getId(), entity); }
        @Override public void removeEntity(Entity entity) { actors.remove(entity.getId()); }
        @Override public Entity getEntity(long id) { return actors.get(id); }
        @Override public void removeBlockEntity(BlockEntity block) { super.removeBlockEntity(block); if(tile == block) tile=null; tiles.values().removeIf(value -> value==block); }
        @Override public void updateComparatorOutputLevel(Vector3 pos) {}
        @Override public Block getBlock(int x, int y, int z, int layer) {
            if (x == 0 && y == 64 && z == 0 && layer == 0) return cell;
            return cells.getOrDefault(key(x,y,z,layer), Block.get(Block.AIR, 0, this, x, y, z, layer));
        }
        @Override public Block getBlock(int x,int y,int z) { return getBlock(x,y,z,0); }
        @Override public Block getBlock(int x,int y,int z,boolean load) { return getBlock(x,y,z,0); }
        @Override public Block getBlock(int x,int y,int z,int layer,boolean load) { return getBlock(x,y,z,layer); }
        @Override public Block getBlock(Vector3 pos,boolean load) { return getBlock(pos,0); }
        @Override public Block getBlock(Vector3 pos,int layer,boolean load) { return getBlock(pos,layer); }
        @Override public Block getBlock(Vector3 pos) { return getBlock(pos, 0); }
        @Override public Block getBlock(Vector3 pos, int layer) {
            return getBlock(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), layer);
        }
        @Override public boolean setBlock(Vector3 pos, int layer, Block block) {
            if (block.isAir()) {
                if (rejectRemoval || setterMode==1) return false;
                if (setterMode==2) return true;
                if (setterMode==3) throw new IllegalStateException("before mutation");
            }
            Block replacement=Block.get(block.getId(),block.getDamage(),this,pos.getFloorX(),pos.getFloorY(),pos.getFloorZ(),layer);
            if(pos.getFloorX()==0 && pos.getFloorY()==64 && pos.getFloorZ()==0 && layer==0) cell=replacement;
            else cells.put(key(pos.getFloorX(),pos.getFloorY(),pos.getFloorZ(),layer),replacement);
            if(block.isAir() && setterMode==4) throw new IllegalStateException("after mutation");
            return !block.isAir() || setterMode!=5;
        }
        @Override public boolean setBlock(Vector3 pos,Block block) { return setBlock(pos,0,block); }
        @Override public boolean setBlock(Vector3 pos,Block block,boolean direct) { return setBlock(pos,0,block); }
        @Override public boolean setBlock(Vector3 pos,int layer,Block block,boolean direct) { return setBlock(pos,layer,block); }
        @Override public boolean setBlock(Vector3 pos,Block block,boolean direct,boolean update) { return setBlock(pos,0,block); }
        @Override public boolean setBlock(Vector3 pos,Block block,boolean direct,boolean update,Runnable accepted) {
            boolean result=false;Throwable failure=null;
            try {result=setBlock(pos,0,block);}catch(RuntimeException|Error error){failure=error;}
            if(getBlock(pos).getFullId()==block.getFullId() && setterMode!=2 && accepted!=null) accepted.run();
            if(failure instanceof Error error)throw error;if(failure instanceof RuntimeException error)throw error;
            return result;
        }
        @Override public boolean setBlock(Vector3 pos,int layer,Block block,boolean direct,boolean update) { return setBlock(pos,layer,block); }
        @Override public boolean setBlock(int x,int y,int z,int layer,Block block,boolean direct,boolean update) { return setBlock(new Vector3(x,y,z),layer,block); }
        @Override public Entity[] getNearbyEntities(AxisAlignedBB box, Entity ignored) { return new Entity[0]; }
        @Override public void dropItem(Vector3 pos, Item item) { if (!item.isNull()) output.add(item.clone()); }
        @Override public void addParticle(Particle particle) {}
        @Override public void addSound(Vector3 pos, Sound sound) {}
        @Override public void addLevelSoundEvent(Vector3 pos, int event) {}
        @Override public VibrationManager getVibrationManager() { return new SimpleVibrationManager(this); }
    }
}
