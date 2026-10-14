package cn.nukkit.level.explosion;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.item.Item;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.level.Sound;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Source-only prospective fixture. Its Level setter, block lookup, tile lookup and
 * tile registration/removal are inherited native methods. Its exact LevelDBChunk
 * keeps the native getAndSetBlock/removeInvalidTile/removeBlockEntity path.
 * Only empty-world routing, players/sounds and stable game rules are supplied.
 */
public final class ActualContainerRetirementFixture {
    private ActualContainerRetirementFixture() { }

    static void boot() throws Exception {
        NativeExplosionFixture.boot();
        NativeExplosionFixture.set(Server.class, NativeExplosionFixture.server,
                "craftingManager", new CraftingManager());
        Entity.registerEntity("XpOrb", EntityXPOrb.class, true);
        BlockEntity.registerBlockEntity(BlockEntity.FURNACE, BlockEntityFurnace.class);
    }

    static World world(int id) throws Exception {
        var seed = NativeExplosionFixture.level(id);
        World world = NativeExplosionFixture.allocated(World.class);
        for (Field field : Level.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            field.set(world, field.get(seed));
        }
        NativeExplosionFixture.set(Level.class, world, "server", NativeExplosionFixture.server);
        NativeExplosionFixture.set(Level.class, world, "blockEntities", new Long2ObjectNonBlockingMap<BlockEntity>());
        NativeExplosionFixture.set(Level.class, world, "changedBlocks", new Long2ObjectOpenHashMap<>());
        NativeExplosionFixture.set(Level.class, world, "callbackBlockSet", new Int2ObjectOpenHashMap<>());
        NativeExplosionFixture.set(Level.class, world, "normalUpdateQueue", new java.util.concurrent.ConcurrentLinkedDeque<>());
        NativeExplosionFixture.set(Level.class, world, "normalUpdateQueueSize", new java.util.concurrent.atomic.AtomicInteger());
        NativeExplosionFixture.set(Level.class, world, "lightQueue", new java.util.concurrent.ConcurrentHashMap<>());
        LevelDBProvider provider = (LevelDBProvider) world.getProvider();
        NativeExplosionFixture.set(LevelDBProvider.class, provider, "level", world);
        world.chunk = new LevelDBChunk(provider, 0, 0);
        NativeExplosionFixture.set(BaseFullChunk.class, world.chunk, "entities", new Long2ObjectNonBlockingMap<Entity>());
        var chunks = new Long2ObjectOpenHashMap<BaseFullChunk>();
        chunks.put(Level.chunkHash(0, 0), world.chunk);
        NativeExplosionFixture.set(LevelDBProvider.class, provider, "chunks", chunks);
        world.actors = seed.actors;
        world.rules = GameRules.getDefault();
        world.trace = new ArrayList<>();
        world.chunk.setBlock(0, 64, 0, id, 0);
        if (world.chunk.getClass() != LevelDBChunk.class
                || world.chunk.getProvider().getClass() != LevelDBProvider.class
                || world.chunk.getProvider().getLevel() != world) {
            throw new AssertionError("Noncanonical native chunk/provider");
        }
        return world;
    }

    static CompoundTag tileTag(String id, int x) {
        return new CompoundTag().putString("id", id).putInt("x", x).putInt("y", 64).putInt("z", 0)
                .putList(new ListTag<CompoundTag>("Items"));
    }

    static BlockEntityFurnace lawfulFurnace(World world) {
        BlockEntityFurnace furnace = new BlockEntityFurnace(world.chunk, tileTag(BlockEntity.FURNACE, 0));
        if (!furnace.getInventory().setSmelting(Item.get(Item.IRON_ORE, 0, 3))
                || !furnace.getInventory().setFuel(Item.get(Item.COAL, 0, 1))) {
            throw new AssertionError("Lawful furnace input refused");
        }
        // Three actual conversions accrue native recipe XP. No setExperience, fabricated
        // StoredXpInt, seeded cook timer, fake XP marker or dropExpOrb override is used.
        for (int tick = 0; tick < 600; tick++) furnace.onUpdate();
        if (furnace.getInventory().getSmelting().getCount() != 0
                || furnace.getInventory().getResult().getId() != Item.IRON_INGOT
                || furnace.getInventory().getResult().getCount() != 3
                || furnace.getExperience() < 2f || furnace.getExperience() >= 3f) {
            throw new AssertionError("Lawful three-smelt positive control did not accrue native XP");
        }
        furnace.saveNBT();
        if (furnace.namedTag.getShort("StoredXpInt") != 2) {
            throw new AssertionError("Native furnace XP persistence control changed");
        }
        return furnace;
    }

    static int physicalXp(World world) {
        int result = 0;
        for (Entity actor : world.actors.values()) {
            if (!(actor instanceof EntityXPOrb) || actor.closed || !actor.isAlive()
                    || actor.isPublicationDeferred()) continue;
            if (world.chunk.getEntities().get(actor.getId()) != actor
                    || world.getEntity(actor.getId()) != actor) {
                throw new AssertionError("XP actor lacks native world/chunk ownership");
            }
            actor.saveNBT();
            result += actor.namedTag.getShort("Value");
        }
        return result;
    }

    static int physicalItems(World world, int id) {
        int result = 0;
        for (Entity actor : world.actors.values()) {
            if (actor instanceof EntityItem item && !actor.closed && actor.isAlive()
                    && !actor.isPublicationDeferred() && item.getItem().getId() == id) {
                if (world.chunk.getEntities().get(actor.getId()) != actor) {
                    throw new AssertionError("Item actor lacks native chunk ownership");
                }
                result += item.getItem().getCount();
            }
        }
        return result;
    }

    static void observe(World world, String phase, BlockEntity tile) {
        boolean chunkLookup = world.chunk.getTile(0, 64, 0) == tile;
        boolean levelLookup = world.getBlockEntity(new Vector3(0, 64, 0)) == tile;
        boolean levelRegistry = world.getBlockEntityById(tile.getId()) == tile;
        String extra = tile instanceof BlockEntityFurnace f
                ? " inventory=" + f.getInventory().getContents().values().stream().mapToInt(Item::getCount).sum()
                  + " xp=" + f.getExperience() + " storedXp=" + f.namedTag.getShort("StoredXpInt")
                : tile instanceof cn.nukkit.blockentity.BlockEntityChest chest
                  ? " inventory=" + chest.getRealInventory().getContents().values().stream().mapToInt(Item::getCount).sum() : "";
        cn.nukkit.inventory.Inventory realInventory = tile instanceof cn.nukkit.blockentity.BlockEntityChest chest
                ? chest.getRealInventory() : tile instanceof BlockEntityFurnace f ? f.getInventory() : null;
        String inventoryWitness = realInventory == null ? "none" : realInventory.getContents().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + ":" + entry.getValue().getId()
                    + ":" + entry.getValue().getDamage() + ":" + entry.getValue().getCount() + ":"
                    + java.util.HexFormat.of().formatHex(entry.getValue().getCompoundTag()))
                .collect(java.util.stream.Collectors.joining(";"));
        if (inventoryWitness.isEmpty()) inventoryWitness = "empty";
        int publishedItems = 0, publishedItemUnits = 0, publishedXpActors = 0, publishedXpValue = 0;
        int deferredActors = 0, deferredItems = 0, deferredItemUnits = 0, deferredXpActors = 0, deferredXpValue = 0;
        int publishedOwnershipMismatch = 0;
        List<String> actorWitnesses = new ArrayList<>();
        for (Entity actor : world.actors.values().stream().sorted(java.util.Comparator.comparingLong(Entity::getId)).toList()) {
            boolean deferred = actor.isPublicationDeferred();
            boolean published = !actor.closed && actor.isAlive() && !deferred;
            if (deferred) deferredActors++;
            if (published && (world.getEntity(actor.getId()) != actor
                    || world.chunk.getEntities().get(actor.getId()) != actor || actor.getLevel() != world)) {
                publishedOwnershipMismatch++;
            }
            String goods = "none";
            if (actor instanceof EntityItem item) {
                Item actual = item.getItem();
                goods = "item:" + actual.getId() + ":" + actual.getDamage() + ":" + actual.getCount()
                        + ":" + java.util.HexFormat.of().formatHex(actual.getCompoundTag());
                if (published) { publishedItems++; publishedItemUnits += actual.getCount(); }
                if (deferred) { deferredItems++; deferredItemUnits += actual.getCount(); }
            } else if (actor instanceof EntityXPOrb xp) {
                goods = "xp:" + xp.getExp();
                if (published) { publishedXpActors++; publishedXpValue += xp.getExp(); }
                if (deferred) { deferredXpActors++; deferredXpValue += xp.getExp(); }
            }
            actorWitnesses.add(actor.getId() + ":" + actor.getClass().getName() + ":" + actor.closed
                    + ":" + actor.isAlive() + ":" + deferred + ":" + goods);
        }
        String actorWitness = actorWitnesses.isEmpty() ? "empty" : String.join(";", actorWitnesses);
        String row = "RETIREMENT phase=" + phase + " block=" + world.getBlock(0, 64, 0).getFullId()
                + " chunkLookup=" + chunkLookup + " levelLookup=" + levelLookup
                + " levelRegistry=" + levelRegistry + " closed=" + tile.closed
                + " physicalXp=" + physicalXp(world) + extra
                + " inventoryWitness=" + inventoryWitness + " publishedItems=" + publishedItems
                + " publishedItemUnits=" + publishedItemUnits + " publishedXpActors=" + publishedXpActors
                + " publishedXpValue=" + publishedXpValue + " deferredActors=" + deferredActors
                + " deferredItems=" + deferredItems + " deferredItemUnits=" + deferredItemUnits
                + " deferredXpActors=" + deferredXpActors + " deferredXpValue=" + deferredXpValue
                + " publishedOwnershipMismatch=" + publishedOwnershipMismatch + " actorWitness=" + actorWitness;
        world.trace.add(row);
        System.out.println(row);
    }

    public static final class World extends Level {
        LevelDBChunk chunk;
        Map<Long, Entity> actors;
        GameRules rules;
        List<String> trace;
        public World() { super(null, "", "/", null); }
        @Override public Server getServer() { return NativeExplosionFixture.server; }
        @Override public GameRules getGameRules() { return rules; }
        @Override public String getFolderName() { return "container-retirement-isolated"; }
        @Override public DimensionData getDimensionData() { return DimensionData.LEGACY_DIMENSION; }
        @Override public int getMinBlockY() { return 0; }
        @Override public int getMaxBlockY() { return 255; }
        @Override public BaseFullChunk getChunk(int x, int z) { return getChunk(x, z, false); }
        @Override public BaseFullChunk getChunk(int x, int z, boolean create) { return x == 0 && z == 0 ? chunk : null; }
        @Override public BaseFullChunk getChunkIfLoaded(int x, int z) { return getChunk(x, z, false); }
        @Override public Map<Integer, cn.nukkit.Player> getChunkPlayers(int x, int z) { return Map.of(); }
        @Override public cn.nukkit.level.ChunkLoader[] getChunkLoaders(int x, int z) { return new cn.nukkit.level.ChunkLoader[0]; }
        @Override public Map<Long, cn.nukkit.Player> getPlayers() { return Map.of(); }
        @Override public Entity[] getNearbyEntities(AxisAlignedBB box, Entity ignored) { return new Entity[0]; }
        @Override public void addParticle(Particle particle) { }
        @Override public void addSound(Vector3 position, Sound sound) { }
        @Override public void addLevelSoundEvent(Vector3 position, int event) { }
        @Override public void updateComparatorOutputLevel(Vector3 position) { }
    }
}
