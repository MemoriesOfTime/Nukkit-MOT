package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static cn.nukkit.level.Level.getBlockXYZ;
import static cn.nukkit.level.Level.localBlockHash;

@Getter
@Setter
public final class AntiXraySystem {
    private final Level level;
    private int fakeOreDenominator = 16;
    private boolean preDeObfuscate = true;
    private final Int2IntMap realOreToReplacedBlockIds = new Int2IntOpenHashMap(24);
    private final Int2ObjectOpenHashMap<IntList> fakeOreToPutBlockIds = new Int2ObjectOpenHashMap<>(4);

    public AntiXraySystem(Level level) {
        this.level = level;
    }

    public void addAntiXrayFakeBlock(int originBlock, @NotNull Collection<Integer> fakeBlocks) {
        var list = this.fakeOreToPutBlockIds.get(originBlock);
        if (list == null) {
            this.fakeOreToPutBlockIds.put(originBlock, list = new IntArrayList(8));
        }

        for (int id : fakeBlocks) {
            this.realOreToReplacedBlockIds.put(id, originBlock);
            list.add(id);
        }
    }

    public void removeAntiXrayFakeBlock(int originBlock, @NotNull Collection<Integer> fakeBlocks) {
        var list = this.fakeOreToPutBlockIds.get(originBlock);
        if (list != null) {
            for (var each : fakeBlocks) {
                list.removeIf(i -> i == each);
            }
        }
    }

    public void obfuscateSendBlocks(Long index, Player[] playerArray, Int2ObjectOpenHashMap<Object> blocks) {
        int size = blocks.size();
        var vectorSet = new IntOpenHashSet(size * 6);
        var vRidList = new ArrayList<Vector3WithBlockId>(size * 7);
        Vector3WithBlockId tmpV3Rid;
        for (int blockHash : blocks.keySet()) {
            Vector3 hash = getBlockXYZ(index, blockHash, level);
            var x = hash.getFloorX();
            var y = hash.getFloorY();
            var z = hash.getFloorZ();
            if (!vectorSet.contains(blockHash)) {
                vectorSet.add(blockHash);
                try {
                    tmpV3Rid = new Vector3WithBlockId(x, y, z, level.getBlockIdAt(x, y, z, 0), level.getBlockIdAt(x, y, z, 1));
                    vRidList.add(tmpV3Rid);
                    if (!Block.isBlockTransparentById(tmpV3Rid.getBlockIdLayer0())) {
                        continue;
                    }
                } catch (Exception ignore) {
                }
            }
            x++;
            blockHash = localBlockHash(x, y, z, level);
            if (!vectorSet.contains(blockHash)) {
                vectorSet.add(blockHash);
                try {
                    vRidList.add(new Vector3WithBlockId(x, y, z, level.getBlockIdAt(x, y, z, 0), level.getBlockIdAt(x, y, z, 1)));
                } catch (Exception ignore) {
                }
            }
            x -= 2;
            blockHash = localBlockHash(x, y, z, level);
            if (!vectorSet.contains(blockHash)) {
                vectorSet.add(blockHash);
                try {
                    vRidList.add(new Vector3WithBlockId(x, y, z, level.getBlockIdAt(x, y, z, 0), level.getBlockIdAt(x, y, z, 1)));
                } catch (Exception ignore) {
                }
            }
            x++;
            y++;
            blockHash = localBlockHash(x, y, z, level);
            if (!vectorSet.contains(blockHash)) {
                vectorSet.add(blockHash);
                try {
                    vRidList.add(new Vector3WithBlockId(x, y, z, level.getBlockIdAt(x, y, z, 0), level.getBlockIdAt(x, y, z, 1)));
                } catch (Exception ignore) {
                }
            }
            y -= 2;
            blockHash = localBlockHash(x, y, z, level);
            if (!vectorSet.contains(blockHash)) {
                vectorSet.add(blockHash);
                try {
                    vRidList.add(new Vector3WithBlockId(x, y, z, level.getBlockIdAt(x, y, z, 0), level.getBlockIdAt(x, y, z, 1)));
                } catch (Exception ignore) {
                }
            }
            y++;
            z++;
            blockHash = localBlockHash(x, y, z, level);
            if (!vectorSet.contains(blockHash)) {
                vectorSet.add(blockHash);
                try {
                    vRidList.add(new Vector3WithBlockId(x, y, z, level.getBlockIdAt(x, y, z, 0), level.getBlockIdAt(x, y, z, 1)));
                } catch (Exception ignore) {
                }
            }
            z -= 2;
            blockHash = localBlockHash(x, y, z, level);
            if (!vectorSet.contains(blockHash)) {
                vectorSet.add(blockHash);
                try {
                    vRidList.add(new Vector3WithBlockId(x, y, z, level.getBlockIdAt(x, y, z, 0), level.getBlockIdAt(x, y, z, 1)));
                } catch (Exception ignore) {
                }
            }
        }
        level.sendBlocks(playerArray, vRidList.toArray(Vector3[]::new), UpdateBlockPacket.FLAG_ALL);
    }

    public void deObfuscateBlock(Player player, BlockFace face, Block target) {
        var vecList = new ArrayList<Vector3WithBlockId>(5);
        Vector3WithBlockId tmpVec;
        for (var each : BlockFace.values()) {
            if (each == face) continue;
            var tmpX = target.getFloorX() + each.getXOffset();
            var tmpY = target.getFloorY() + each.getYOffset();
            var tmpZ = target.getFloorZ() + each.getZOffset();
            try {
                tmpVec = new Vector3WithBlockId(tmpX, tmpY, tmpZ, level.getBlockIdAt(tmpX, tmpY, tmpZ, 0), level.getBlockIdAt(tmpX, tmpY, tmpZ, 1));
                if (this.getFakeOreToPutBlockIds().containsKey(tmpVec.getBlockIdLayer0())) {
                    vecList.add(tmpVec);
                }
            } catch (Exception ignore) {
            }
        }
        level.sendBlocks(new Player[]{player}, vecList.toArray(Vector3[]::new), UpdateBlockPacket.FLAG_ALL);
    }

    public void reinitAntiXray() {
        this.fakeOreToPutBlockIds.clear();
        this.realOreToReplacedBlockIds.clear();

        this.addAntiXrayFakeBlock(BlockID.STONE, List.of(
                BlockID.COAL_ORE,
                BlockID.DIAMOND_ORE,
                BlockID.EMERALD_ORE,
                BlockID.GOLD_ORE,
                BlockID.IRON_ORE,
                BlockID.LAPIS_ORE,
                BlockID.REDSTONE_ORE,
                BlockID.COPPER_ORE
        ));
        this.addAntiXrayFakeBlock(BlockID.NETHERRACK, List.of(
                BlockID.QUARTZ_ORE,
                BlockID.NETHER_GOLD_ORE,
                BlockID.ANCIENT_DEBRIS
        ));
        this.addAntiXrayFakeBlock(BlockID.DEEPSLATE, List.of(
                BlockID.DEEPSLATE_COAL_ORE,
                BlockID.DEEPSLATE_DIAMOND_ORE,
                BlockID.DEEPSLATE_EMERALD_ORE,
                BlockID.DEEPSLATE_GOLD_ORE,
                BlockID.DEEPSLATE_IRON_ORE,
                BlockID.DEEPSLATE_LAPIS_ORE,
                BlockID.DEEPSLATE_REDSTONE_ORE,
                BlockID.DEEPSLATE_COPPER_ORE
        ));
    }
}