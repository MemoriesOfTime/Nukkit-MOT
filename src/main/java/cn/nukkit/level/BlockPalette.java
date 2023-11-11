package cn.nukkit.level;

import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Log4j2
public class BlockPalette {

    private final int protocol;
    private final Int2IntMap legacyToRuntimeId = new Int2IntOpenHashMap();
    private final Int2IntMap runtimeIdToLegacy = new Int2IntOpenHashMap();
    private final Object2IntMap<CompoundTag> stateToLegacy = new Object2IntOpenHashMap<>();
    private final Int2ObjectMap<CompoundTag> legacyToState = new Int2ObjectOpenHashMap<>();

    public BlockPalette(int protocol) {
        this.protocol = protocol;
        legacyToRuntimeId.defaultReturnValue(-1);
        runtimeIdToLegacy.defaultReturnValue(-1);

        loadBlockStates(paletteFor(protocol));
        loadBlockStatesExtras();
    }

    private ListTag<CompoundTag> paletteFor(int protocol) {
        ListTag<CompoundTag> tag;
        try (InputStream stream = Server.class.getClassLoader().getResourceAsStream("runtime_block_states_" + protocol + ".dat")) {
            if (stream == null) {
                throw new AssertionError("Unable to locate block state nbt " + protocol);
            }
            //noinspection unchecked
            tag = (ListTag<CompoundTag>) NBTIO.readTag(new BufferedInputStream(new GZIPInputStream(stream)), ByteOrder.BIG_ENDIAN, false);
        } catch (IOException e) {
            throw new AssertionError("Unable to load block palette " + protocol, e);
        }
        return tag;
    }

    private void loadBlockStates(ListTag<CompoundTag> blockStates) {
        for (CompoundTag state : blockStates.getAll()) {
            int id = state.getInt("id");
            int data = state.getShort("data");
            int runtimeId = state.getInt("runtimeId");
            int legacyId = id << 6 | data;
            if (data >= 0) {
                legacyToRuntimeId.put(legacyId, runtimeId);
                if (!runtimeIdToLegacy.containsKey(runtimeId)) {
                    runtimeIdToLegacy.put(runtimeId, legacyId);
                }
            }
            CompoundTag vState = state.copy()
                    .remove("stateOverload")
                    .remove("data")
                    .remove("id")
                    .remove("runtimeId")
                    .remove("version");
            stateToLegacy.put(vState, legacyId);
            legacyToState.put(legacyId, vState);
        }
    }

    /**
     * 加载扩展数据，用于在不修改runtime_block_states.dat文件的情况下额外增加一些内容
     */
    private void loadBlockStatesExtras() {
        try (InputStream resourceAsStream = Server.class.getClassLoader().getResourceAsStream("RuntimeBlockStatesExtras/" + protocol + ".json")) {
            if (resourceAsStream == null) {
                return;
            }
            List<Map> extras = new Config().loadFromStream(resourceAsStream).getMapList("extras");
            //noinspection unchecked
            for (Map<String, Object> map : extras) {
                int id = Utils.toInt(map.get("id"));
                int data = Utils.toInt(map.getOrDefault("data", 0));
                int runtimeId = Utils.toInt(map.get("runtimeId"));
                int legacyId = id << 6 | data;
                legacyToRuntimeId.put(legacyId, runtimeId);
                if (!runtimeIdToLegacy.containsKey(runtimeId)) {
                    runtimeIdToLegacy.put(runtimeId, legacyId);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void clearStates() {
        this.legacyToRuntimeId.clear();
        this.runtimeIdToLegacy.clear();
        this.stateToLegacy.clear();
        this.legacyToState.clear();
    }

    public int getRuntimeId(int id) {
        return this.getRuntimeId(id, 0);
    }

    public int getRuntimeId(int id, int meta) {
        int legacyId = protocol >= 388 ? ((id << 6) | meta) : ((id << 4) | meta);
        int runtimeId;
        runtimeId = legacyToRuntimeId.get(legacyId);
        if (runtimeId == -1) {
            runtimeId = legacyToRuntimeId.get(id << 6);
            if (runtimeId == -1) {
                log.info("(" + protocol + ") Missing block runtime id mappings for " + id + ':' + meta);
                runtimeId = legacyToRuntimeId.get(BlockID.INFO_UPDATE << 6);
            }
        }
        return runtimeId;
    }

    public int getLegacyFullId(int runtimeId) {
        return runtimeIdToLegacy.get(runtimeId);
    }

    public int getLegacyFullId(CompoundTag compoundTag) {
        compoundTag = compoundTag.copy().remove("version");
        int fullId = stateToLegacy.getOrDefault(compoundTag, -1);
        if (fullId == -1) {
            //TODO 优化
            for (Map.Entry<CompoundTag, Integer> entry : stateToLegacy.entrySet()) {
                if (checkCompoundTag(compoundTag, entry.getKey())) {
                    fullId = entry.getValue();
                    break;
                }
            }
        }
        if (fullId == -1) {
            log.warn("Missing block state mappings for " + compoundTag);
        }
        return fullId;
    }

    public CompoundTag getState(int legacyFullId) {
        return legacyToState.get(legacyFullId);
    }

    private boolean checkCompoundTag(CompoundTag tag, CompoundTag tag2) {
        if (tag == null || tag2 == null) {
            return false;
        }
        for (Tag t : tag.getAllTags()) {
            if (!tag2.contains(t.getName())) {
                return false;
            }
            if (tag2.containsCompound(t.getName()) && !checkCompoundTag((CompoundTag) t, tag2.getCompound(t.getName()))) {
                return false;
            }
            if (!tag2.get(t.getName()).parseValue().equals(t.parseValue())) {
                return false;
            }
        }
        for (Tag t : tag2.getAllTags()) {
            if (!tag.contains(t.getName())) {
                return false;
            }
            if (tag.containsCompound(t.getName()) && !checkCompoundTag((CompoundTag) t, tag.getCompound(t.getName()))) {
                return false;
            }
            if (!tag.get(t.getName()).parseValue().equals(t.parseValue())) {
                return false;
            }
        }
        return true;
    }

}
