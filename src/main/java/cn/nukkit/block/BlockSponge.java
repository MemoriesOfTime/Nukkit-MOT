package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.ExplodeParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BlockColor;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author Angelic47
 * Nukkit Project
 */
public class BlockSponge extends BlockSolidMeta {

    public static final int DRY = 0;
    public static final int WET = 1;

    private static final String[] NAMES = new String[]{
            "Sponge",
            "Wet sponge"
    };

    public BlockSponge() {
        this(0);
    }

    public BlockSponge(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SPONGE;
    }

    @Override
    public double getHardness() {
        return 0.6;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public String getName() {
        return NAMES[this.getDamage() & 0b1];
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CLOTH_BLOCK_COLOR;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_HOE;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (this.getDamage() == WET && level.getDimension() == Level.DIMENSION_NETHER) {
            level.setBlock(block, Block.get(BlockID.SPONGE, DRY), true, true);
            level.addSoundToViewers(block.getLocation(), Sound.RANDOM_FIZZ);
            level.addParticle(new ExplodeParticle(block.add(0.5, 1, 0.5)));
            return true;
        } else if (this.getDamage() == DRY
                && (block instanceof BlockWater
                        || block.getLevelBlockAround().stream().anyMatch(b -> b instanceof BlockWater)
                        || block.getLevelBlockAround(1).stream().anyMatch(b -> b instanceof BlockWater))
                && performWaterAbsorb(block)) {
            level.setBlock(block, Block.get(BlockID.SPONGE, WET), true, true);

            for (int i = 0; i < 4; i++) {
                LevelEventPacket packet = new LevelEventPacket();
                packet.evid = LevelEventPacket.EVENT_PARTICLE_DESTROY;
                packet.x = (float) block.getX() + 0.5f;
                packet.y = (float) block.getY() + 1f;
                packet.z = (float) block.getZ() + 0.5f;
                packet.data = GlobalBlockPalette.getOrCreateRuntimeId(ProtocolInfo.CURRENT_PROTOCOL, BlockID.WATER, 0);
                level.addChunkPacket(getChunkX(), getChunkZ(), packet);
            }

            return true;
        }

        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    private boolean performWaterAbsorb(Block block) {
        Queue<Entry> entries = new ArrayDeque<>();

        entries.add(new Entry(block, 0));

        Entry entry;
        int waterRemoved = 0;
        while (waterRemoved < 64 && (entry = entries.poll()) != null) {
            for (BlockFace face : BlockFace.values()) {
                Block faceBlock = entry.block.getSideAtLayer(0, face);
                Block faceBlockLayer1 = faceBlock.getLevelBlockAtLayer(1);

                if (faceBlock instanceof BlockWater) {
                    this.getLevel().setBlock(faceBlock, Block.get(BlockID.AIR));
                    waterRemoved++;
                    if (entry.distance < 6) {
                        entries.add(new Entry(faceBlock, entry.distance + 1));
                    }
                } else if (faceBlockLayer1 instanceof BlockWater) {
                    if (faceBlock.getId() == BlockID.BLOCK_KELP || faceBlock.getId() == BlockID.SEAGRASS || faceBlock.getId() == BlockID.SEA_PICKLE || faceBlock instanceof BlockCoralFan) {
                        faceBlock.getLevel().useBreakOn(faceBlock);
                    }
                    this.getLevel().setBlock(faceBlockLayer1, 1, Block.get(BlockID.AIR));
                    waterRemoved++;
                    if (entry.distance < 6) {
                        entries.add(new Entry(faceBlockLayer1, entry.distance + 1));
                    }
                }
            }
        }

        return waterRemoved > 0;
    }

    private static class Entry {
        private final Block block;
        private final int distance;

        public Entry(Block block, int distance) {
            this.block = block;
            this.distance = distance;
        }
    }
}
