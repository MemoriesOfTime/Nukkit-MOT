package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockFace.Axis;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;

/**
 * Created on 2016/1/5 by xtypr.
 * Package cn.nukkit.block in project nukkit .
 * The name NetherPortalBlock comes from minecraft wiki.
 */
public class BlockNetherPortal extends BlockFlowable implements Faceable {

    public BlockNetherPortal() {
        this(0);
    }

    public BlockNetherPortal(int meta) {
        super(0);
    }

    @Override
    public String getName() {
        return "Nether Portal Block";
    }

    @Override
    public int getId() {
        return NETHER_PORTAL;
    }

    @Override
    public boolean isBreakable(Item item) {
        return false;
    }

    @Override
    public double getHardness() {
        return -1;
    }

    @Override
    public int getLightLevel() {
        return 11;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(BlockID.AIR));
    }

    @Override
    public boolean canBeFlowedInto() {
        return false;
    }

    @Override
    public boolean onBreak(Item item) {
        boolean result = super.onBreak(item);
        for (BlockFace face : BlockFace.values()) {
            Block b = this.getSide(face);
            if (b != null) {
                if (b instanceof BlockNetherPortal) {
                    result &= b.onBreak(item);
                }
            }
        }
        return result;
    }

    @Override
    public boolean hasDynamicCollision() {
        return true;
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.AIR_BLOCK_COLOR;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        return this;
    }

    public static boolean trySpawnPortal(Level level, Vector3 pos) {
        return trySpawnPortal(level, pos, false);
    }

    public static boolean trySpawnPortal(Level level, Vector3 pos, boolean force) {
        PortalBuilder builder = new PortalBuilder(level, pos, Axis.X, force);

        if (builder.isValid() && builder.portalBlockCount == 0) {
            builder.placePortalBlocks();
            return true;
        } else {
            builder = new PortalBuilder(level, pos, Axis.Z, force);

            if (builder.isValid() && builder.portalBlockCount == 0) {
                builder.placePortalBlocks();
                return true;
            } else {
                return false;
            }
        }
    }

    public static class PortalBuilder {

        private final Level level;
        private final Axis axis;
        private final BlockFace rightDir;
        private final BlockFace leftDir;
        private int portalBlockCount;
        private Vector3 bottomLeft;
        private int height;
        private int width;

        private boolean force;

        public PortalBuilder(Level level, Vector3 pos, Axis axis, boolean force) {
            this.level = level;
            this.axis = axis;
            this.force = force;

            if (axis == Axis.X) {
                this.leftDir = BlockFace.EAST;
                this.rightDir = BlockFace.WEST;
            } else {
                this.leftDir = BlockFace.NORTH;
                this.rightDir = BlockFace.SOUTH;
            }


            for (Vector3 blockpos = pos; pos.getY() > blockpos.getY() - 21 && pos.getY() > 0 && this.isEmptyBlock(getBlockId(pos.down())); pos = pos.down()) {
            }

            int i = this.getDistanceUntilEdge(pos, this.leftDir) - 1;

            if (i >= 0) {
                this.bottomLeft = pos.getSide(this.leftDir, i);
                this.width = this.getDistanceUntilEdge(this.bottomLeft, this.rightDir);

                if (this.width < 2 || this.width > 21) {
                    this.bottomLeft = null;
                    this.width = 0;
                }
            }

            if (this.bottomLeft != null) {
                this.height = this.calculatePortalHeight();
            }
        }

        protected int getDistanceUntilEdge(Vector3 pos, BlockFace dir) {
            int i;

            for (i = 0; i < 22; ++i) {
                Vector3 v = pos.getSide(dir, i);

                if (!this.isEmptyBlock(getBlockId(v)) || getBlockId(v.down()) != OBSIDIAN) {
                    break;
                }
            }

            return getBlockId(pos.getSide(dir, i)) == OBSIDIAN ? i : 0;
        }

        public int getHeight() {
            return this.height;
        }

        public int getWidth() {
            return this.width;
        }

        protected int calculatePortalHeight() {

            loop:
            for (this.height = 0; this.height < 21; ++this.height) {
                for (int i = 0; i < this.width; ++i) {
                    Vector3 blockpos = this.bottomLeft.getSide(this.rightDir, i).up(this.height);
                    int block = getBlockId(blockpos);

                    if (!this.isEmptyBlock(block)) {
                        break loop;
                    }

                    if (block == NETHER_PORTAL) {
                        ++this.portalBlockCount;
                    }

                    if (i == 0) {
                        block = getBlockId(blockpos.getSide(this.leftDir));

                        if (block != OBSIDIAN) {
                            break loop;
                        }
                    } else if (i == this.width - 1) {
                        block = getBlockId(blockpos.getSide(this.rightDir));

                        if (block != OBSIDIAN) {
                            break loop;
                        }
                    }
                }
            }

            for (int i = 0; i < this.width; ++i) {
                if (getBlockId(this.bottomLeft.getSide(this.rightDir, i).up(this.height)) != OBSIDIAN) {
                    this.height = 0;
                    break;
                }
            }

            if (this.height <= 21 && this.height >= 3) {
                return this.height;
            } else {
                this.bottomLeft = null;
                this.width = 0;
                this.height = 0;
                return 0;
            }
        }

        private int getBlockId(Vector3 pos) {
            return this.level.getBlockIdAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
        }

        protected boolean isEmptyBlock(int id) {
            return force || id == AIR || id == FIRE || id == NETHER_PORTAL;
        }

        public boolean isValid() {
            return this.bottomLeft != null && this.width >= 2 && this.width <= 21 && this.height >= 3 && this.height <= 21;
        }

        public void placePortalBlocks() {
            for (int i = 0; i < this.width; ++i) {
                Vector3 blockpos = this.bottomLeft.getSide(this.rightDir, i);

                for (int j = 0; j < this.height; ++j) {
                    this.level.setBlock(blockpos.up(j), Block.get(NETHER_PORTAL, this.axis == Axis.X ? 1 : this.axis == Axis.Z ? 2 : 0));
                }
            }
        }
    }

    public static Position getSafePortal(Position portal) {
        Level level = portal.getLevel();
        Vector3 down = portal.down();
        while (level.getBlockIdAt(down.getFloorX(), down.getFloorY(), down.getFloorZ()) == NETHER_PORTAL) {
            down = down.down();
        }

        return Position.fromObject(down.up(), portal.getLevel());
    }

    /**
     * How far, in blocks, an existing portal is looked up on the other side.
     */
    private static final int PORTAL_SEARCH_RADIUS = 128;

    /**
     * How far the search may pull inactive chunks from disk.
     *
     * <p>Scaling a portal coordinate loses at most seven blocks, so the frame used for the trip
     * is always in the same or an adjacent chunk on the way back.
     */
    private static final int PORTAL_DISK_LOAD_CHUNK_RADIUS = 1;

    /**
     * Looks up the portal closest to the given position.
     *
     * <p>The search walks chunks in rings around the origin and stops at the first portal it
     * finds, so the result is the nearest one and the cost is paid only until then. Chunks that
     * are not generated yet are skipped instead of being generated on the spot: a player stepping
     * into a portal must not pay for generating the whole search box, and the portal is going to
     * be built next to the mirrored position anyway.
     */
    public static Position findNearestPortal(Position pos) {
        Level level = pos.getLevel();
        int originChunkX = pos.getFloorX() >> 4;
        int originChunkZ = pos.getFloorZ() >> 4;
        int chunkRadius = (PORTAL_SEARCH_RADIUS >> 4) + 1;

        Position found = null;
        for (int ring = 0; ring <= chunkRadius && found == null; ring++) {
            for (int chunkX = originChunkX - ring; chunkX <= originChunkX + ring && found == null; chunkX++) {
                for (int chunkZ = originChunkZ - ring; chunkZ <= originChunkZ + ring && found == null; chunkZ++) {
                    if (Math.max(Math.abs(chunkX - originChunkX), Math.abs(chunkZ - originChunkZ)) != ring) {
                        continue;
                    }
                    found = findPortalInChunk(
                            level, chunkX, chunkZ, ring <= PORTAL_DISK_LOAD_CHUNK_RADIUS);
                }
            }
        }

        if (found == null) {
            return null;
        }
        Vector3 up = found.up();
        int x = up.getFloorX(), y = up.getFloorY(), z = up.getFloorZ();
        int id = level.getBlockIdAt(x, y, z);
        if (id != AIR && id != OBSIDIAN && id != NETHER_PORTAL) {
            for (int xx = -1; xx < 4; xx++) {
                for (int yy = 1; yy < 4; yy++)  {
                    for (int zz = -1; zz < 3; zz++) {
                        level.setBlockAt(x + xx, y + yy, z + zz, AIR);
                    }
                }
            }
        }
        return found;
    }

    private static Position findPortalInChunk(
            Level level, int chunkX, int chunkZ, boolean mayLoadFromDisk) {
        FullChunk chunk = level.getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            // A cold far chunk is not worth blocking the transfer for. The mirror will get a new
            // frame if no nearby portal exists; already loaded chunks still use the full radius.
            if (!mayLoadFromDisk || !level.loadChunk(chunkX, chunkZ, false)) {
                return null;
            }
            chunk = level.getChunk(chunkX, chunkZ, false);
        }
        if (chunk == null || !chunk.isGenerated()) {
            return null;
        }

        int minY = level.getMinBlockY();
        int maxY = level.getMaxBlockY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    if (chunk.getBlockId(x, y, z) == NETHER_PORTAL) {
                        return new Position((chunkX << 4) + x, y, (chunkZ << 4) + z, level);
                    }
                }
            }
        }
        return null;
    }

    /**
     * How far up and down a footing for a freshly built portal is looked up.
     */
    private static final int PORTAL_GROUND_SEARCH = 96;

    /**
     * Builds a portal frame around the given position and returns where it ended up.
     *
     * <p>The Y that comes in is the one the player entered the portal at on the other side, see
     * {@link Level#calculatePortalMirror}, and it says nothing about the terrain here: coming back
     * from the nether at Y=72 built the frame in mid-air and left the player standing on its
     * obsidian floor high above the ground. The frame is therefore lowered onto the first footing
     * below the mirrored position, or raised onto the first one above it, and only stays at the
     * mirrored Y when the whole column is empty.
     */
    public static Position spawnPortal(Position pos) {
        Level lvl = pos.level;
        int x = pos.getFloorX(), z = pos.getFloorZ();
        int y = frameHeightFor(lvl, x, pos.getFloorY(), z);
        Position spawned = new Position(x, y, z, lvl);

        for (int xx = -1; xx < 4; xx++) {
            for (int yy = 1; yy < 4; yy++)  {
                for (int zz = -1; zz < 3; zz++) {
                    lvl.setBlockAt(x + xx, y + yy, z + zz, AIR);
                }
            }
        }

        lvl.setBlockAt(x + 1, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 2, y, z, OBSIDIAN);

        z += 1;
        lvl.setBlockAt(x, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 1, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 2, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 3, y, z, OBSIDIAN);

        z += 1;
        lvl.setBlockAt(x + 1, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 2, y, z, OBSIDIAN);

        z -= 1;
        y += 1;
        lvl.setBlockAt(x, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 1, y, z, NETHER_PORTAL);
        lvl.setBlockAt(x + 2, y, z, NETHER_PORTAL);
        lvl.setBlockAt(x + 3, y, z, OBSIDIAN);

        y += 1;
        lvl.setBlockAt(x, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 1, y, z, NETHER_PORTAL);
        lvl.setBlockAt(x + 2, y, z, NETHER_PORTAL);
        lvl.setBlockAt(x + 3, y, z, OBSIDIAN);

        y += 1;
        lvl.setBlockAt(x, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 1, y, z, NETHER_PORTAL);
        lvl.setBlockAt(x + 2, y, z, NETHER_PORTAL);
        lvl.setBlockAt(x + 3, y, z, OBSIDIAN);

        y += 1;
        lvl.setBlockAt(x, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 1, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 2, y, z, OBSIDIAN);
        lvl.setBlockAt(x + 3, y, z, OBSIDIAN);

        sealPortalSite(lvl, spawned.getFloorX(), spawned.getFloorY(), spawned.getFloorZ());

        return spawned;
    }

    /**
     * Makes the ground a fresh frame stands on safe to arrive at.
     *
     * <p>The frame itself is only two obsidian blocks wide, so a player coming through steps off
     * it right away. Over a lava sea, a cave mouth or a cliff that step is a fall, and the frame
     * may have been carved out of lava that flows straight back in. So the floor under the whole
     * cleared box is laid solid and the lava touching it is replaced with plain rock.
     */
    private static void sealPortalSite(Level level, int x, int y, int z) {
        for (int xx = -1; xx < 4; xx++) {
            for (int zz = -1; zz < 3; zz++) {
                if (!isSolidAt(level, x + xx, y, z + zz)) {
                    level.setBlockAt(x + xx, y, z + zz, OBSIDIAN);
                }
            }
        }

        // Liquid on the outside of the box turns into rock, liquid inside it into air: draining
        // the inside alone leaves the sea around it to flow straight back into the frame.
        int filler = level.getDimension() == Level.DIMENSION_NETHER ? NETHERRACK : STONE;
        for (int xx = -2; xx < 5; xx++) {
            for (int zz = -2; zz < 4; zz++) {
                for (int yy = 0; yy <= 5; yy++) {
                    int id = level.getBlockIdAt(x + xx, y + yy, z + zz);
                    if (!Block.isLava(id) && !Block.isWater(id)) {
                        continue;
                    }
                    boolean shell = xx == -2 || xx == 4 || zz == -2 || zz == 3 || yy == 0 || yy == 5;
                    level.setBlockAt(x + xx, y + yy, z + zz, shell ? filler : AIR);
                }
            }
        }
    }

    /**
     * Y the portal floor is laid at for a frame asked for at the given position.
     */
    private static int frameHeightFor(Level level, int x, int y, int z) {
        int minY = level.getMinBlockY() + 1;
        int maxY = level.getMaxBlockY() - 5;
        if (level.getDimension() == Level.DIMENSION_NETHER) {
            // Under the bedrock roof: a frame built into it is walled in and unreachable.
            maxY = Math.min(maxY, 122);
        }
        if (maxY <= minY) {
            return y;
        }

        int start = Math.max(minY, Math.min(maxY, y));
        for (int yy = start; yy >= Math.max(minY, start - PORTAL_GROUND_SEARCH); yy--) {
            if (hasFooting(level, x, yy, z)) {
                return yy + 1;
            }
        }
        for (int yy = start + 1; yy <= Math.min(maxY, start + PORTAL_GROUND_SEARCH); yy++) {
            if (hasFooting(level, x, yy, z)) {
                return yy + 1;
            }
        }
        return start;
    }

    /**
     * Whether a frame standing on top of the given layer has ground under it and room above.
     *
     * <p>Only the two columns the portal blocks themselves sit in are checked: the frame clears
     * the space around it anyway, so demanding a flat 4x3 patch would reject every slope and send
     * the frame back into mid-air.
     */
    private static boolean hasFooting(Level level, int x, int y, int z) {
        if (!isSolidAt(level, x + 1, y, z + 1) && !isSolidAt(level, x + 2, y, z + 1)) {
            return false;
        }
        for (int yy = 1; yy <= 4; yy++) {
            if (isBlockedAt(level, x + 1, y + yy, z + 1) || isBlockedAt(level, x + 2, y + yy, z + 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the given cell keeps a portal frame from standing here.
     *
     * <p>Lava counts, and that is the whole point of this method: it is not a footing and it is
     * not free room either. Treating it as free room is what built frames inside the lava seas of
     * the nether, so a player stepping through arrived standing in lava.
     */
    private static boolean isBlockedAt(Level level, int x, int y, int z) {
        int id = level.getBlockIdAt(x, y, z);
        if (id == AIR || id == NETHER_PORTAL) {
            return false;
        }
        return Block.isBlockSolidById(id) || Block.isLava(id) || Block.isWater(id);
    }

    private static boolean isSolidAt(Level level, int x, int y, int z) {
        int id = level.getBlockIdAt(x, y, z);
        if (id == AIR || id == NETHER_PORTAL) {
            return false;
        }
        // Water and lava are not a footing: standing on them means falling through or burning.
        return Block.isBlockSolidById(id);
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }
}
