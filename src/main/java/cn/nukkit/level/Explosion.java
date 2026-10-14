package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockTNT;
import cn.nukkit.block.BlockDoor;
import cn.nukkit.block.BlockBed;
import cn.nukkit.block.BlockDoublePlant;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntityLectern;
import cn.nukkit.blockentity.BlockEntityJukebox;
import cn.nukkit.blockentity.BlockEntityDecoratedPot;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.block.BlockExplodeEvent;
import cn.nukkit.event.block.ExplosionBlockProcessing;
import cn.nukkit.event.block.NativeExplosionBlockProcessor;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.NativeContainerTransaction;
import cn.nukkit.inventory.NativeContainerBreak;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.entity.item.EntityPrimedTNT;
import cn.nukkit.level.particle.HugeExplodeSeedParticle;
import cn.nukkit.level.util.ExplosionSource;
import cn.nukkit.level.vibration.VibrationEvent;
import cn.nukkit.level.vibration.VibrationType;
import cn.nukkit.math.*;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.utils.Hash;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Explosion {

    private static final int RAYS = 16;
    private final double STEP_LEN = 0.3d;

    private final Level level;
    private final Position source;
    private final double size;
    private double fireSpawnChance;

    private Set<Block> affectedBlocks;
    private Set<Block> fireIgnitions;

    private final ExplosionSource sourceObject;
    private boolean doesDamage = true;
    private ExplosionBlockProcessing blockProcessing;
    public List<Block> getRemovedBlocks() { return blockProcessing == null ? List.of() : blockProcessing.getRemovedBlocks(); }

    public Explosion(Position center, double size, Entity sourceObject) {
        this(center, size, new ExplosionSource.EntitySource(sourceObject));
    }

    public Explosion(Position center, double size, Block sourceObject) {
        this(center, size, new ExplosionSource.BlockSource(sourceObject));
    }

    protected Explosion(Position center, double size, ExplosionSource sourceObject) {
        this.level = center.getLevel();
        this.source = center;
        this.size = Math.max(size, 0);
        this.sourceObject = sourceObject;
    }

    public double getFireSpawnChance() {
        return fireSpawnChance;
    }

    public void setFireSpawnChance(double fireSpawnChance) {
        this.fireSpawnChance = fireSpawnChance;
    }

    public boolean isIncendiary() {
        return fireSpawnChance > 0;
    }

    public void setIncendiary(boolean incendiary) {
        if (!incendiary) {
            fireSpawnChance = 0;
        } else if (fireSpawnChance <= 0) {
            fireSpawnChance = 1.0 / 3.0;
        }
    }

    /**
     * @return bool
     */
    public boolean explode() {
        return explodeA() && explodeB();
    }

    /**
     * Calculates which blocks will be destroyed by this explosion. If {@link #explodeB()} is called without calling this,
     * no blocks will be destroyed.
     *
     * @return {@code true} if success
     */
    public boolean explodeA() {
        if (this.sourceObject instanceof ExplosionSource.EntitySource entitySource) {
            Entity entity = entitySource.entity();
            if (entity instanceof EntityExplosive) {
                int blockLayer0 = level.getBlockIdAt(entity.getFloorX(), entity.getFloorY(), entity.getFloorZ());
                int blockLayer1 = level.getBlockIdAt(entity.getFloorX(), entity.getFloorY(), entity.getFloorZ(), 1);
                if (Block.isWater(blockLayer0) || Block.isWater(blockLayer1)) {
                    this.doesDamage = false;
                    return true;
                }
            }
        }

        if (this.size < 0.1) {
            return false;
        }

        if (affectedBlocks == null) {
            affectedBlocks = new LinkedHashSet<>();
        }

        boolean incendiary = fireSpawnChance > 0;
        if (incendiary && fireIgnitions == null) {
            fireIgnitions = new LinkedHashSet<>();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector3 vector = new Vector3(0, 0, 0);
        Vector3 vBlock = new Vector3(0, 0, 0);

        int mRays = RAYS - 1;
        for (int i = 0; i < RAYS; ++i) {
            for (int j = 0; j < RAYS; ++j) {
                for (int k = 0; k < RAYS; ++k) {
                    if (i == 0 || i == mRays || j == 0 || j == mRays || k == 0 || k == mRays) {
                        vector.setComponents((double) i / mRays * 2d - 1, (double) j / mRays * 2d - 1, (double) k / mRays * 2d - 1);
                        double len = vector.length();
                        vector.setComponents((vector.x / len) * STEP_LEN, (vector.y / len) * STEP_LEN, (vector.z / len) * STEP_LEN);
                        double pointerX = this.source.x;
                        double pointerY = this.source.y;
                        double pointerZ = this.source.z;

                        for (double blastForce = this.size * random.nextDouble(0.7, 1.3); blastForce > 0; blastForce -= STEP_LEN * 0.75d) {
                            int x = (int) pointerX;
                            int y = (int) pointerY;
                            int z = (int) pointerZ;
                            vBlock.setComponents(
                                    pointerX >= x ? x : x - 1,
                                    pointerY >= y ? y : y - 1,
                                    pointerZ >= z ? z : z - 1
                            );
                            if (!this.level.isYInRange((int) vBlock.y)) {
                                break;
                            }
                            Block block = this.level.getBlock(vBlock);
                            if (!block.isAir()) {
                                Block layer1 = block.getLevelBlockAtLayer(1);
                                double resistance = Math.max(block.getResistance(), layer1.getResistance());
                                blastForce -= (resistance / 5 + 0.3d) * STEP_LEN;
                                if (blastForce > 0) {
                                    if (this.affectedBlocks.add(block)) {
                                        if (incendiary && random.nextDouble() <= fireSpawnChance) {
                                            this.fireIgnitions.add(block);
                                        }
                                        if (!layer1.isAir()) {
                                            this.affectedBlocks.add(layer1);
                                        }
                                    }
                                }
                            }
                            pointerX += vector.x;
                            pointerY += vector.y;
                            pointerZ += vector.z;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Executes the explosion's effects on the world. This includes destroying blocks (if any),
     * harming and knocking back entities, and creating sounds and particles.
     *
     * @return {@code false} if explosion was canceled, otherwise {@code true}
     */
    public boolean explodeB() {
        LongArraySet updateBlocks = new LongArraySet();
        Vector3 sourceVec = this.source.floor();
        double yield = (1d / this.size) * 100d;

        if (affectedBlocks == null) {
            affectedBlocks = new LinkedHashSet<>();
        }

        Set<Block> selectedBlocks = Set.copyOf(affectedBlocks);
        if (this.sourceObject instanceof ExplosionSource.EntitySource entitySource) {
            Entity exploder = entitySource.entity();
            List<Block> affectedBlocksList = new ArrayList<>(this.affectedBlocks);
            EntityExplodeEvent ev = new EntityExplodeEvent(exploder, this.source, affectedBlocksList, yield);
            ev.setIgnitions(fireIgnitions == null ? new LinkedHashSet<>(0) : fireIgnitions);
            this.level.getServer().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return false;
            yield = ev.getYield();
            affectedBlocks.clear();
            affectedBlocks.addAll(ev.getBlockList());
            fireIgnitions = ev.getIgnitions();
            blockProcessing = ev.getBlockProcessing();
        } else if (this.sourceObject instanceof ExplosionSource.BlockSource blockSource) {
            BlockExplodeEvent ev = new BlockExplodeEvent(blockSource.block(), this.source, this.affectedBlocks,
                    fireIgnitions == null ? new LinkedHashSet<>(0) : fireIgnitions, yield, this.fireSpawnChance);
            this.level.getServer().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return false;
            yield = ev.getYield();
            affectedBlocks = ev.getAffectedBlocks();
            fireIgnitions = ev.getIgnitions();
            blockProcessing = ev.getBlockProcessing();
        }

        if (blockProcessing == null) blockProcessing = new ExplosionBlockProcessing();
        blockProcessing.begin(affectedBlocks,selectedBlocks);
        double explosionSize = this.size * 2d;
        AxisAlignedBB explosionBB = new SimpleAxisAlignedBB(
                NukkitMath.floorDouble(this.source.x - explosionSize - 1),
                NukkitMath.floorDouble(this.source.y - explosionSize - 1),
                NukkitMath.floorDouble(this.source.z - explosionSize - 1),
                NukkitMath.ceilDouble(this.source.x + explosionSize + 1),
                NukkitMath.ceilDouble(this.source.y + explosionSize + 1),
                NukkitMath.ceilDouble(this.source.z + explosionSize + 1)
        );

        Entity[] nearbyEntities = this.level.getNearbyEntities(explosionBB,
                this.sourceObject instanceof ExplosionSource.EntitySource es ? es.entity() : null);

        for (Entity entity : nearbyEntities) {
            double distance = entity.distance(this.source) / explosionSize;
            if (distance > 1) continue;

            Vector3 motion = entity.subtract(this.source).normalize();
            float blockDensity = 1 - level.getBlockDensity(this.source, entity.boundingBox);
            double force = this.size * 2.0;
            double d = entity.distance(sourceVec) / force;
            double impact = (1.0 - d) * blockDensity;
            float damage = this.doesDamage ? (float) ((impact * impact + impact) / 2.0 * 7.0 * force + 1.0) : 0f;

            EntityDamageEvent damageEvent;
            if (this.sourceObject instanceof ExplosionSource.EntitySource es) {
                damageEvent = new EntityDamageByEntityEvent(es.entity(), entity, DamageCause.ENTITY_EXPLOSION, damage);
            } else if (this.sourceObject instanceof ExplosionSource.BlockSource bs) {
                damageEvent = new EntityDamageByBlockEvent(bs.block(), entity, DamageCause.BLOCK_EXPLOSION, damage);
            } else {
                damageEvent = new EntityDamageEvent(entity, DamageCause.BLOCK_EXPLOSION, damage);
            }
            entity.attack(damageEvent);

            if (!(entity instanceof EntityItem || entity instanceof EntityXPOrb)) {
                entity.motionX += motion.x * impact;
                entity.motionY += motion.y * impact;
                entity.motionZ += motion.z * impact;
            }
        }

        ItemBlock air = new ItemBlock(Block.get(BlockID.AIR));
        List<Vector3> smokePositions = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (Block originalBlock : new ArrayList<>(this.affectedBlocks)) {
            Block currentBlock = this.level.getBlock(originalBlock.getFloorX(), originalBlock.getFloorY(), originalBlock.getFloorZ(), originalBlock.layer);

            if (currentBlock.isAir() || currentBlock.getFullId() != originalBlock.getFullId()) {
                continue;
            }

            NativeExplosionBlockProcessor.Result planned = blockProcessing.processBlock(currentBlock);
            if (planned != NativeExplosionBlockProcessor.Result.NATIVE) continue;

            if (currentBlock instanceof BlockTNT tnt) {
                Entity exploder = (this.sourceObject instanceof ExplosionSource.EntitySource es) ? es.entity() : null;
                if (!blockProcessing.canRemove(List.of(currentBlock))) continue;
                if (!tnt.primeAccepted(random.nextInt(10, 31), exploder, () -> { if (!blockProcessing.canRemove(List.of(currentBlock))) throw new IllegalStateException("TNT metadata validation refused"); }, () -> blockProcessing.blockRemoved(currentBlock))) continue;
            } else {
                if (!destroyExplosionBlock(currentBlock, explosionLootTool(currentBlock,air), yield, random)) continue;
            }

            if (random.nextInt(8) == 0) {
                smokePositions.add(currentBlock);
            }

            if (currentBlock.layer == 0) {
                Vector3 pos = new Vector3(currentBlock.x, currentBlock.y, currentBlock.z);
                for (BlockFace side : BlockFace.values()) {
                    Vector3 sideBlock = pos.getSide(side);
                    long index = Hash.hashBlock(sideBlock.getFloorX(), sideBlock.getFloorY(), sideBlock.getFloorZ());
                    if (!this.affectedBlocks.contains(sideBlock) && !updateBlocks.contains(index)) {
                        this.processBlockUpdate(sideBlock);
                        Block layer1 = this.level.getBlock(sideBlock, 1);
                        if (!layer1.isAir()) {
                            this.processBlockUpdate(layer1);
                        }
                        updateBlocks.add(index);
                    }
                }
            }
        }

        if (fireIgnitions != null) {
            for (Vector3 pos : fireIgnitions) {
                Block toIgnite = level.getBlock(pos);
                if (toIgnite.isAir() && toIgnite.down().isSolid(BlockFace.UP)) {
                    level.setBlock(toIgnite, Block.get(BlockID.FIRE));
                }
            }
        }

        int count = smokePositions.size();
        CompoundTag data = new CompoundTag(new Object2ObjectOpenHashMap<>(count, 0.999999f))
                .putFloat("originX", (float) this.source.x)
                .putFloat("originY", (float) this.source.y)
                .putFloat("originZ", (float) this.source.z)
                .putFloat("radius", (float) this.size)
                .putInt("size", count);
        for (int i = 0; i < count; i++) {
            Vector3 pos = smokePositions.get(i);
            String prefix = "pos" + i;
            data.putFloat(prefix + "x", (float) pos.x);
            data.putFloat(prefix + "y", (float) pos.y);
            data.putFloat(prefix + "z", (float) pos.z);
        }

        this.level.addParticle(new HugeExplodeSeedParticle(this.source));
        this.level.addSound(this.source, Sound.RANDOM_EXPLODE);

        Object initiator = null;
        if (this.sourceObject instanceof ExplosionSource.EntitySource entitySource) {
            initiator = entitySource.entity();
        } else if (this.sourceObject instanceof ExplosionSource.BlockSource blockSource) {
            initiator = blockSource.block();
        }
        this.level.getVibrationManager().callVibrationEvent(new VibrationEvent(initiator, this.source.clone(), VibrationType.EXPLODE));

        return true;
    }

    private List<Item> ownLootOverride;
    private List<Item> storedGoodsOverride;
    /** One owner item per native composite, including serialized filled shulker contents. */
    public static List<Item> canonicalOwnLoot(Block original,Item tool) {
        Block owner=sourceCells(original).get(0);
        BlockEntity tile=owner.layer==0 ? owner.level.getBlockEntity(owner) : null;
        Item[] drops=tile instanceof BlockEntityShulkerBox || owner instanceof cn.nukkit.block.BlockItemFrame
                ? new Item[]{owner.toItem()} : owner.getDrops(tool);
        return Arrays.stream(drops).filter(drop->!drop.isNull()).map(Item::clone).toList();
    }
    /** Shared source receipt for host-handled sources, preserving composite and tile semantics. */
    public static boolean removeSource(Block original, Item tool, boolean collectDrops,
                                       List<Item> ownLoot, NativeExplosionBlockProcessor processor) {
        return removeSource(original,tool,collectDrops,ownLoot,null,processor);
    }
    /** Trusted host frame policy may filter stored goods without changing the exact source witness. */
    public static boolean removeSource(Block original, Item tool, boolean collectDrops,
                                       List<Item> ownLoot, List<Item> storedGoodsOverride,
                                       NativeExplosionBlockProcessor processor) {
        Explosion receipt=new Explosion(new Position(original.x,original.y,original.z,original.level),1,(Entity)null);
        if (storedGoodsOverride != null) {
            if (!(sourceCells(original).get(0) instanceof cn.nukkit.block.BlockItemFrame))
                throw new IllegalArgumentException("Stored-goods override is restricted to item frames");
            receipt.storedGoodsOverride=storedGoodsOverride.stream().map(Item::clone).toList();
        }
        receipt.ownLootOverride=collectDrops ? ownLoot.stream().map(Item::clone).toList() : List.of();
        receipt.blockProcessing=new ExplosionBlockProcessing();
        receipt.blockProcessing.addBlockProcessor(processor);
        List<Block> cells=sourceCells(original);
        receipt.blockProcessing.begin(cells);
        return receipt.destroyExplosionBlock(original,tool,100,ThreadLocalRandom.current());
    }
    /** TNT loot should meet harvest requirements without silk touch, fortune or shears. */
    private Item explosionLootTool(Block block,Item air) {
        boolean tnt=sourceObject instanceof ExplosionSource.EntitySource es && es.entity() instanceof EntityPrimedTNT
                || sourceObject instanceof ExplosionSource.BlockSource bs && bs.block() instanceof BlockTNT;
        if(!tnt || block.getToolType()==ItemTool.TYPE_SHEARS)return air;
        return switch(block.getToolType()) {
            case ItemTool.TYPE_AXE -> Item.get(Item.DIAMOND_AXE);
            case ItemTool.TYPE_SHOVEL -> Item.get(Item.DIAMOND_SHOVEL);
            case ItemTool.TYPE_SWORD -> Item.get(Item.DIAMOND_SWORD);
            case ItemTool.TYPE_HOE -> Item.get(Item.DIAMOND_HOE);
            default -> Item.get(Item.DIAMOND_PICKAXE);
        };
    }

    private static final ThreadLocal<Set<CellKey>> REMOVING = ThreadLocal.withInitial(HashSet::new);

    /** Composite blocks and their payload share one observable removal decision. */
    private boolean destroyExplosionBlock(Block affected, Item tool, double yield, ThreadLocalRandom random) {
        List<Block> cells = sourceCells(affected);
        Set<CellKey> claimed = REMOVING.get();
        List<CellKey> keys = cells.stream().map(CellKey::of).toList();
        if (keys.stream().anyMatch(claimed::contains)) return false;
        claimed.addAll(keys);
        try { return destroyExplosionSource(cells, tool, yield, random); }
        finally { claimed.removeAll(keys); if (claimed.isEmpty()) REMOVING.remove(); }
    }

    private boolean destroyExplosionSource(List<Block> cells, Item tool, double yield, ThreadLocalRandom random) {
        Block block=cells.get(0);
        List<BlockEntity> tiles=cells.stream().map(cell -> cell.layer==0 ? level.getBlockEntity(cell) : null).toList();
        BlockEntity tile=tiles.get(0);
        Inventory inventory=tile instanceof BlockEntityChest chest ? chest.getRealInventory()
                : tile instanceof InventoryHolder holder ? holder.getInventory() : null;
        if(inventory!=null && (NativeContainerTransaction.isActive(inventory)
                || !InventoryMutationGuard.canCaptureProfile(List.of(inventory)))) return false;
        boolean shulker=tile instanceof BlockEntityShulkerBox;
        Item[] own=ownLootOverride!=null ? ownLootOverride.toArray(Item.EMPTY_ARRAY) : shulker ? new Item[]{block.toItem()}
                : random.nextDouble()*100<yield ? canonicalOwnLoot(block,tool).toArray(Item.EMPTY_ARRAY) : Item.EMPTY_ARRAY;
        StoredPayload payload=StoredPayload.capture(tile);
        NativeContainerTransaction.Snapshot source=inventory==null ? null : NativeContainerTransaction.snapshot(inventory);
        if(inventory!=null && source==null) return false;
        NativeContainerTransaction.Preparation preparation=inventory==null ? null : NativeContainerTransaction.tryPrepare(inventory);
        if(inventory!=null && preparation==null) return false;
        List<EntityItem.PreparedDrop> tickets=new ArrayList<>();
        boolean[] accepted={false}; boolean[] removalStarted=new boolean[cells.size()]; Throwable failure=null;
        try {
            List<Item> drops=new ArrayList<>(Arrays.asList(own));
            if(payload!=null) drops.addAll(storedGoodsOverride==null ? payload.goods() : storedGoodsOverride);
            if(source!=null && (!shulker || own.length==0)) for(var backing:source.rawSlots().values()) drops.addAll(backing.values());
            for(Item drop:drops) if(!drop.isNull()) {
                EntityItem.PreparedDrop ticket=prepareExplosionDrop(block.add(0.5,0.5,0.5),drop);
                if(ticket==null) return false; tickets.add(ticket);
            }
            if(!blockProcessing.canRemove(cells) || payload!=null && !payload.prepare()) return false;
            if(!validSource(cells,tiles,source,payload,tickets) || !blockProcessing.canRemove(cells)) return false;
            try(InventoryMutationGuard.Lease claim=inventory==null ? null : InventoryMutationGuard.tryClaim(List.of(inventory))) {
                if(inventory!=null && claim==null || !validSource(cells,tiles,source,payload,tickets)) return false;
                Runnable commit=()->{
                    if(!sameSourceTiles(cells,tiles,removalStarted)
                            || cells.stream().anyMatch(cell->!level.getBlock(cell,cell.layer).isAir())
                            || !tickets.stream().allMatch(EntityItem.PreparedDrop::isValid)
                            || source!=null && !source.matches() || payload!=null && !payload.matchesAfterNativeRemoval())
                        throw new IllegalStateException("Explosion source changed before accepted publication");
                    if(source!=null && !source.clear(claim)) throw new IllegalStateException("Explosion inventory removal refused");
                    try {
                        if(!blockProcessing.canRemove(cells))throw new RefusedRemoval();
                        if(!sameSourceTiles(cells,tiles,removalStarted) || cells.stream().anyMatch(cell->!level.getBlock(cell,cell.layer).isAir()))throw new IllegalStateException("Explosion metadata validation refused");
                    } catch(RuntimeException|Error error) {
                        if(source!=null)source.restore(claim);throw error;
                    }
                    Throwable[] metadataFailure={null};
                    Runnable acceptSiblings=()->{
                        accepted[0]=true;
                        try {blockProcessing.blocksRemoved(cells);}
                        catch(RuntimeException|Error error){metadataFailure[0]=error;}
                        finally {for(EntityItem.PreparedDrop ticket:tickets)ticket.release();}
                    };
                    Throwable afterClear=null;
                    if(payload==null)acceptSiblings.run();
                    else afterClear=payload.clearAfterNativeRemoval(acceptSiblings);
                    rethrow(retainFailure(metadataFailure[0],afterClear));
                };
                try {removeSourceCells(cells,tiles,removalStarted,0,commit);}
                catch(RuntimeException|Error error){if(!(error instanceof RefusedRemoval))failure=retainFailure(failure,error);}
                if(!accepted[0]) { failure=restoreRetainedCells(cells,tiles,removalStarted,source,payload,failure); rethrow(failure); return false; }
            }
            // Invoke lifecycle only after exact empty inventory/payload acceptance. Receipt 0356
            // observes an empty source and retains its normal unpairing, viewer and XP behavior.
            if(tile!=null) try { tile.onBreak(); } catch(RuntimeException|Error error){failure=retainFailure(failure,error);}
        } catch(RuntimeException|Error error) {failure=retainFailure(failure,error);}
        finally {
            if(!accepted[0]) {
                failure=restoreRetainedCells(cells,tiles,removalStarted,source,payload,failure);
                for(EntityItem.PreparedDrop ticket:tickets) ticket.discard();
            }
            if(payload!=null) try {payload.close();}catch(RuntimeException|Error error){failure=retainFailure(failure,error);}
            if(preparation!=null) try {preparation.close();}catch(RuntimeException|Error error){failure=retainFailure(failure,error);}
        }
        if(accepted[0]) {
            for(int i=0;i<cells.size();i++) {
                BlockEntity original=tiles.get(i);Block cell=cells.get(i);
                if(original!=null && removalStarted[i] && sameSourceTile(cell,original,true) && level.getBlock(cell,cell.layer).isAir())
                    try{original.close();}catch(RuntimeException|Error error){failure=retainFailure(failure,error);}
            }
            for(EntityItem.PreparedDrop ticket:tickets) try{ticket.notifyPublished();}catch(RuntimeException|Error error){failure=retainFailure(failure,error);}
        }
        rethrow(failure);return accepted[0];
    }
    private static final class RefusedRemoval extends RuntimeException { }
    /** Nested accepted hooks finish every raw partner before any notification unwinds. */
    private void removeSourceCells(List<Block> cells,List<BlockEntity> tiles,boolean[] removalStarted,int index,Runnable commit) {
        Block cell=cells.get(index);boolean[] written={false};
        if(!sameSourceTile(cell,tiles.get(index),false) || level.getBlock(cell,cell.layer).getFullId()!=cell.getFullId())throw new RefusedRemoval();
        removalStarted[index]=true;
        Runnable accepted=()->{
            if(!level.getBlock(cell,cell.layer).isAir())throw new RefusedRemoval();
            written[0]=true;
            if(index+1<cells.size())removeSourceCells(cells,tiles,removalStarted,index+1,commit);else commit.run();
        };
        if(cell.layer==0)level.setBlock(new Vector3(cell.x,cell.y,cell.z),Block.get(BlockID.AIR),false,false,accepted);
        else {
            level.setBlock(new Vector3(cell.x,cell.y,cell.z),cell.layer,Block.get(BlockID.AIR),false,false);
            if(level.getBlock(cell,cell.layer).isAir())accepted.run();
        }
        if(!written[0])throw new RefusedRemoval();
    }

    private boolean validSource(List<Block> cells,List<BlockEntity> tiles,NativeContainerTransaction.Snapshot source,StoredPayload payload,List<EntityItem.PreparedDrop> tickets) {
        if(!tickets.stream().allMatch(EntityItem.PreparedDrop::isValid) || source!=null && !source.matches() || payload!=null && !payload.matches())return false;
        if(!sameSourceTiles(cells,tiles))return false;
        for(Block cell:cells) if(level.getBlock(cell,cell.layer).getFullId()!=cell.getFullId())return false;
        return true;
    }
    private boolean sameSourceTiles(List<Block> cells,List<BlockEntity> tiles) {
        return sameSourceTiles(cells,tiles,null);
    }
    private boolean sameSourceTiles(List<Block> cells,List<BlockEntity> tiles,boolean[] removalStarted) {
        for(int i=0;i<cells.size();i++) if(!sameSourceTile(cells.get(i),tiles.get(i),removalStarted!=null && removalStarted[i]))return false;
        return true;
    }
    private boolean retainedSourceTile(Block cell,BlockEntity captured) {
        return !captured.closed && captured.getLevel()==level
                && captured.x==cell.x && captured.y==cell.y && captured.z==cell.z
                && captured.chunk!=null && captured.chunk==level.getChunkIfLoaded(cell.getFloorX()>>4,cell.getFloorZ()>>4)
                && level.getBlockEntityById(captured.getId())==captured;
    }
    private boolean sameSourceTile(Block cell,BlockEntity captured,boolean removalStarted) {
        if(cell.layer==0 && level.getChunkIfLoaded(cell.getFloorX()>>4,cell.getFloorZ()>>4)==null)return false;
        if(captured!=null && !retainedSourceTile(cell,captured))return false;
        BlockEntity current=cell.layer==0 ? level.getBlockEntityIfLoaded(cell) : null;
        if(captured==null)return current==null;
        BlockEntity chunkEntry=captured.chunk.getBlockEntities().get(captured.getId());
        if(current==captured)return chunkEntry==captured;
        // Only our fenced native write may detach the still-owned, unpublished source.
        // A foreign tile, including a native persistent-data replacement, never qualifies.
        return removalStarted && cell.layer==0 && current==null && chunkEntry==null
                && level.getBlock(cell,cell.layer).isAir();
    }
    private Throwable restoreRetainedCells(List<Block> cells,List<BlockEntity> tiles,boolean[] removalStarted,
                                          NativeContainerTransaction.Snapshot source,StoredPayload payload,Throwable failure) {
        for(int i=0;i<cells.size();i++) {
            Block cell=cells.get(i);BlockEntity original=tiles.get(i);
            if(!removalStarted[i] || source!=null && !source.matches() || payload!=null && !payload.matchesRetainedForNativeRollback())continue;
            if(!sameSourceTile(cell,original,true) || !level.getBlock(cell,cell.layer).isAir())continue;
            try {
                Runnable restoreTile=()->{
                    if(level.getChunkIfLoaded(cell.getFloorX()>>4,cell.getFloorZ()>>4)==null
                            || original!=null && !retainedSourceTile(cell,original))return;
                    if(level.getBlock(cell,cell.layer).getFullId()!=cell.getFullId()
                            || source!=null && !source.matches() || payload!=null && !payload.matchesRetainedForNativeRollback())return;
                    if(original==null)return;
                    BlockEntity current=level.getBlockEntityIfLoaded(cell);
                    if(current==original)return;
                    if(current!=null || original.chunk.getBlockEntities().get(original.getId())!=null || !original.isBlockEntityValid())return;
                    // Restore the block first; reattach the same original only into vacant chunk maps.
                    original.chunk.addBlockEntity(original);
                };
                try {
                    if(cell.layer==0)level.setBlock(new Vector3(cell.x,cell.y,cell.z),cell.clone(),false,false,restoreTile);
                    else level.setBlock(new Vector3(cell.x,cell.y,cell.z),cell.layer,cell.clone(),false,false);
                } catch(RuntimeException|Error error){failure=retainFailure(failure,error);}
                // A raw restoration can succeed even if the setter throws before its hook.
                if(cell.layer==0)restoreTile.run();
            } catch(RuntimeException|Error error){failure=retainFailure(failure,error);}
        }
        return failure;
    }

    public static List<Block> sourceCells(Block affected) {
        if (affected.layer != 0) return List.of(affected);
        Block other = null;
        boolean firstIsOwner = true;
        if (affected instanceof BlockDoor door) {
            other = door.isTop() ? affected.down() : affected.up();
            if (!(other instanceof BlockDoor pair) || pair.getId() != affected.getId()
                    || pair.isTop() == door.isTop()) return List.of(affected);
            firstIsOwner = !door.isTop();
        } else if (affected instanceof BlockDoublePlant) {
            boolean upper = (affected.getDamage() & BlockDoublePlant.TOP_HALF_BITMASK) != 0;
            other = upper ? affected.down() : affected.up();
            if (!(other instanceof BlockDoublePlant) || other.getId() != affected.getId()
                    || ((other.getDamage() & BlockDoublePlant.TOP_HALF_BITMASK) != 0) == upper) return List.of(affected);
            firstIsOwner = !upper;
        } else if (affected instanceof BlockBed bed) {
            BlockFace direction = bed.getBlockFace();
            other = affected.getSide(bed.isHeadPiece() ? direction.getOpposite() : direction);
            if (!(other instanceof BlockBed pair) || pair.isHeadPiece() == bed.isHeadPiece()
                    || pair.getBlockFace() != direction) return List.of(affected);
            firstIsOwner = !bed.isHeadPiece();
        }
        if (other == null) return List.of(affected);
        return firstIsOwner ? List.of(affected, other) : List.of(other, affected);
    }

    private record CellKey(Level level, int x, int y, int z, int layer) {
        static CellKey of(Block block) { return new CellKey(block.level, block.getFloorX(), block.getFloorY(), block.getFloorZ(), block.layer); }
    }
    private record Removal(boolean accepted, Throwable failure) { }

    /** Non-inventory stored goods are independent of the source shell and explosion yield. */
    public static StoredPayload captureStoredPayload(Block original) {
        Block owner=sourceCells(original).get(0);
        return StoredPayload.capture(owner.layer==0 ? owner.level.getBlockEntity(owner) : null);
    }
    public static final class StoredPayload implements AutoCloseable {
        private final BlockEntity tile;
        private final Level sourceLevel;
        private final cn.nukkit.level.format.FullChunk sourceChunk;
        private final double sourceX, sourceY, sourceZ;
        private final long sourceId;
        private final Item item;
        private final CompoundTag frameState;
        private final cn.nukkit.blockentity.BlockEntityBeehive.ExplosionOccupants bees;
        private StoredPayload(BlockEntity tile,Item item){
            this.tile=tile;this.item=item.clone();
            this.sourceLevel=tile.getLevel();this.sourceChunk=tile.chunk;
            this.sourceX=tile.x;this.sourceY=tile.y;this.sourceZ=tile.z;this.sourceId=tile.getId();
            this.frameState=tile instanceof BlockEntityItemFrame ? tile.namedTag.copy() : null;
            this.bees=tile instanceof cn.nukkit.blockentity.BlockEntityBeehive hive ? hive.captureExplosionOccupants() : null;
        }
        private Item item(){return item.clone();}
        public List<Item> goods(){return item.isNull() ? List.of() : List.of(item.clone());}
        static StoredPayload capture(BlockEntity tile) {
            Item item=read(tile);return item==null ? null : new StoredPayload(tile,item);
        }
        static Item read(BlockEntity tile) {
            if (tile instanceof cn.nukkit.blockentity.BlockEntityBeehive) return Item.get(Item.AIR);
            if (tile instanceof BlockEntityLectern lectern) return lectern.getBook();
            if (tile instanceof BlockEntityJukebox jukebox) return jukebox.getRecordItem();
            if (tile instanceof BlockEntityDecoratedPot pot) return pot.getItem();
            if (tile instanceof BlockEntityItemFrame frame) return frame.getItem();
            return null;
        }
        public boolean prepare() { return bees == null || bees.prepare(); }
        @Override public void close() { if (bees != null) bees.close(); }
        public boolean matches() { return (bees == null || bees.matches()) && !tile.closed && tile.getLevel()!=null && tile.getLevel().getBlockEntity(tile)==tile && read(tile).equalsExact(item) && (frameState==null || frameState.equals(tile.namedTag)); }
        /** Valid only after the caller's exact source fence accepted its own native AIR write. */
        public boolean matchesAfterNativeRemoval() { return matchesRetainedSource(true); }
        private boolean matchesRetainedForNativeRollback() { return matchesRetainedSource(false); }
        private boolean matchesRetainedSource(boolean requireAir) {
            if (tile.closed || sourceLevel==null || sourceChunk==null || tile.getLevel()!=sourceLevel
                    || tile.chunk!=sourceChunk || tile.getId()!=sourceId
                    || tile.x!=sourceX || tile.y!=sourceY || tile.z!=sourceZ
                    || sourceLevel.getChunkIfLoaded(tile.getFloorX()>>4,tile.getFloorZ()>>4)!=sourceChunk
                    || sourceLevel.getBlockEntityById(sourceId)!=tile || requireAir && !sourceLevel.getBlock(tile).isAir()) return false;
            BlockEntity coordinate=sourceLevel.getBlockEntityIfLoaded(tile);
            BlockEntity chunkEntry=sourceChunk.getBlockEntities().get(sourceId);
            if (!(coordinate==tile && chunkEntry==tile || coordinate==null && chunkEntry==null)) return false;
            return (bees==null || (requireAir ? bees.matchesAfterNativeRemoval() : bees.matchesRetainedForNativeRollback()))
                    && read(tile).equalsExact(item)
                    && (frameState==null || frameState.equals(tile.namedTag));
        }
        public Throwable clearAccepted(){return clear(false);}
        public Throwable clearAfterNativeRemoval(){return clear(true);}
        /** Releases already funded item siblings before payload publication notifications. */
        public Throwable clearAfterNativeRemoval(Runnable acceptSiblings) {
            java.util.Objects.requireNonNull(acceptSiblings);
            if (!matchesAfterNativeRemoval())
                throw new IllegalStateException("Explosion payload source changed before acceptance");
            if (bees != null) return bees.clearAfterNativeRemoval(acceptSiblings);
            Throwable failure=clear(true);
            acceptSiblings.run();
            return failure;
        }
        private Throwable clear(boolean afterNativeRemoval) {
            if (!(afterNativeRemoval ? matchesAfterNativeRemoval() : matches()))
                throw new IllegalStateException("Explosion payload source changed before acceptance");
            if (bees != null) return afterNativeRemoval ? bees.clearAfterNativeRemoval() : bees.clearAccepted();
            if (tile instanceof BlockEntityLectern) { tile.namedTag.remove("book"); tile.namedTag.remove("page"); }
            else if (tile instanceof BlockEntityJukebox jukebox) {
                try { jukebox.setRecordItem(Item.get(Item.AIR)); }
                catch (RuntimeException | Error failure) { if (jukebox.getRecordItem().isNull()) return failure; throw failure; }
            }
            else if (tile instanceof BlockEntityDecoratedPot pot) {
                try { pot.setItem(Item.get(Item.AIR)); }
                catch (RuntimeException | Error failure) { if (pot.getItem().isNull()) return failure; throw failure; }
            }
            else if (tile instanceof BlockEntityItemFrame frame) {
                try { frame.setItem(Item.get(Item.AIR),false); }
                catch (RuntimeException | Error failure) { if (frame.getItem().isNull()) return failure; throw failure; }
            }
            return null;
        }
    }

    private EntityItem.PreparedDrop prepareExplosionDrop(Vector3 pos, Item content) {
        CompoundTag item = NBTIO.putItemHelper(content); item.setName("Item");
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", pos.x))
                        .add(new DoubleTag("", pos.y)).add(new DoubleTag("", pos.z)))
                .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0.2)).add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("", 0)).add(new FloatTag("", 0)))
                .putShort("Health", 5).putCompound("Item", item).putShort("PickupDelay", 10);
        return EntityItem.prepareDrop(level.getChunk(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, true), nbt, content);
    }

    private static Throwable retainFailure(Throwable original, Throwable additional) {
        if (additional == null) return original;
        if (original == null) return additional;
        if (additional instanceof Error && !(original instanceof Error)) { additional.addSuppressed(original); return additional; }
        if (original != additional) original.addSuppressed(additional);
        return original;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    private void processBlockUpdate(Vector3 pos) {
        Block block = this.level.getBlock(pos);
        BlockUpdateEvent ev = new BlockUpdateEvent(block);
        this.level.getServer().getPluginManager().callEvent(ev);
        if (!ev.isCancelled()) {
            ev.getBlock().onUpdate(Level.BLOCK_UPDATE_NORMAL);
        }
    }
}
